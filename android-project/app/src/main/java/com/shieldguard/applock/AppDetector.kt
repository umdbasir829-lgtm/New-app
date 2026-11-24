package com.shieldguard.applock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import java.util.Calendar

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val usedMinutesToday: Int,
    val usedSecondsToday: Int,
    val isLocked: Boolean,
    val dailyLimitMinutes: Int,
    val bonusMinutesToday: Int = 0
)

object AppDetector {
    fun getAllLaunchableApps(context: Context, prefs: PrefsManager): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        val usageSecMap = getMergedTodayUsageSecondsMap(context, prefs)
        val lockedSet = prefs.getLockedApps()
        val limitsMap = prefs.getDailyLimits()
        val seen = HashSet<String>()
        val result = mutableListOf<InstalledAppInfo>()

        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val secs = usageSecMap[pkg] ?: 0
            val baseLimit = limitsMap[pkg] ?: 0
            val bonus = if (baseLimit > 0) prefs.getBonusMinutesToday(pkg) else 0
            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    usedMinutesToday = secs / 60,
                    usedSecondsToday = secs,
                    isLocked = lockedSet.contains(pkg),
                    dailyLimitMinutes = baseLimit,
                    bonusMinutesToday = bonus
                )
            )
        }

        return result.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isLocked || it.dailyLimitMinutes > 0 }
                .thenByDescending { it.usedSecondsToday }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Calculates exact per-second usage ONLY since today's local 00:00:00 using UsageEvents
     * (avoids Android INTERVAL_DAILY multi-day/UTC bucket leakage).
     */
    fun queryTodayUsageSecondsFromEvents(context: Context): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = cal.timeInMillis
            val endTime = System.currentTimeMillis()
            val events = usm.queryEvents(startTime, endTime) ?: return result

            val activeStartMs = mutableMapOf<String, Long>()
            val totalMs = mutableMapOf<String, Long>()
            val ev = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                val ts = ev.timeStamp
                @Suppress("DEPRECATION")
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (!activeStartMs.containsKey(pkg)) {
                            activeStartMs[pkg] = ts
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val started = activeStartMs.remove(pkg)
                        if (started != null && ts > started) {
                            totalMs[pkg] = (totalMs[pkg] ?: 0L) + (ts - started)
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN,
                    UsageEvents.Event.DEVICE_SHUTDOWN -> {
                        for ((openPkg, started) in activeStartMs.entries) {
                            if (ts > started) {
                                totalMs[openPkg] = (totalMs[openPkg] ?: 0L) + (ts - started)
                            }
                        }
                        activeStartMs.clear()
                    }
                }
            }

            for ((pkg, ms) in totalMs) {
                val secs = (ms / 1000L).toInt()
                if (secs > 0) result[pkg] = secs
            }
        } catch (_: Exception) {}
        return result
    }

    fun getMergedTodayUsageSecondsMap(context: Context, prefs: PrefsManager): Map<String, Int> {
        val fromEvents = queryTodayUsageSecondsFromEvents(context)
        val tracked = prefs.getAllAppUsageSecondsToday()
        val merged = mutableMapOf<String, Int>()
        for ((k, v) in fromEvents) merged[k] = v
        for ((k, v) in tracked) {
            merged[k] = maxOf(merged[k] ?: 0, v)
        }
        return merged
    }

    fun getAppUsageSecondsToday(context: Context, prefs: PrefsManager, packageName: String): Int {
        val tracked = prefs.getAppUsageSecondsToday(packageName)
        val fromEvents = queryTodayUsageSecondsFromEvents(context)[packageName] ?: 0
        return maxOf(tracked, fromEvents)
    }

    fun getAppUsageMinutesToday(context: Context, prefs: PrefsManager, packageName: String): Int {
        return getAppUsageSecondsToday(context, prefs, packageName) / 60
    }

    /**
     * Queries recent UsageEvents in the last [windowMs] to detect the true current foreground
     * package even when Xiaomi MIUI gesture navigation skips Accessibility window events.
     */
    fun getLatestForegroundPackage(context: Context, windowMs: Long = 4500L): String? {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - windowMs, now) ?: return null
            val ev = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                @Suppress("DEPRECATION")
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    val pkg = ev.packageName
                    if (!pkg.isNullOrBlank() && ev.timeStamp >= latestTs) {
                        latestTs = ev.timeStamp
                        latestPkg = pkg
                    }
                }
            }
            return latestPkg
        } catch (_: Exception) {
            return null
        }
    }
}
