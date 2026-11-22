package com.shieldguard.applock

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Calendar

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val usedMinutesToday: Int,
    val isLocked: Boolean,
    val dailyLimitMinutes: Int
)

object AppDetector {
    fun getAllLaunchableApps(context: Context, prefs: PrefsManager): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        val usageMap = getTodayUsageMinutesMap(context)
        val lockedSet = prefs.getLockedApps()
        val limitsMap = prefs.getDailyLimits()
        val seen = HashSet<String>()
        val result = mutableListOf<InstalledAppInfo>()

        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    usedMinutesToday = usageMap[pkg] ?: 0,
                    isLocked = lockedSet.contains(pkg),
                    dailyLimitMinutes = limitsMap[pkg] ?: 0
                )
            )
        }

        return result.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isLocked || it.dailyLimitMinutes > 0 }
                .thenByDescending { it.usedMinutesToday }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun getTodayUsageMinutesMap(context: Context): Map<String, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val map = mutableMapOf<String, Int>()
        if (stats != null) {
            for (stat in stats) {
                val mins = (stat.totalTimeInForeground / 60000L).toInt()
                if (mins > 0) {
                    map[stat.packageName] = (map[stat.packageName] ?: 0) + mins
                }
            }
        }
        return map
    }

    fun getAppUsageMinutesToday(context: Context, packageName: String): Int {
        return getTodayUsageMinutesMap(context)[packageName] ?: 0
    }
}
