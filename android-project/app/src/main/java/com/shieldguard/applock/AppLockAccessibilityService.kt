package com.shieldguard.applock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var currentSessionUnlockedPkg: String? = null

        @Volatile
        private var lastOverlayLaunchedPkg: String? = null

        @Volatile
        private var lastOverlayLaunchTime: Long = 0L

        @Volatile
        private var lastObservedForegroundPkg: String? = null

        fun markSessionUnlocked(pkg: String) {
            currentSessionUnlockedPkg = pkg
            lastOverlayLaunchedPkg = null
            lastOverlayLaunchTime = 0L
        }

        fun clearOverlayState() {
            lastOverlayLaunchedPkg = null
            lastOverlayLaunchTime = 0L
        }
    }

    private lateinit var prefs: PrefsManager
    private var imePackages: Set<String> = emptySet()

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    currentSessionUnlockedPkg = null
                    lastOverlayLaunchedPkg = null
                    lastOverlayLaunchTime = 0L
                    prefs.clearAllSessionUnlocks()
                }
                Intent.ACTION_SCREEN_ON -> {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (km?.isKeyguardLocked == true) return
                    recheckForegroundLock()
                }
                Intent.ACTION_USER_PRESENT -> {
                    recheckForegroundLock()
                }
            }
        }
    }

    private fun recheckForegroundLock() {
        lastOverlayLaunchedPkg = null
        lastOverlayLaunchTime = 0L
        val fgPkg = lastObservedForegroundPkg
        if (!fgPkg.isNullOrBlank() && !isIgnoredSystemPackage(fgPkg)) {
            evaluatePackageLock(fgPkg)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
        isRunning = true
        refreshImePackages()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        isRunning = false
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun refreshImePackages() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imePackages = imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android" || pkg == "com.android.permissioncontroller") {
            return true
        }
        if (imePackages.contains(pkg)) return true
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (isIgnoredSystemPackage(pkg)) return

        lastObservedForegroundPkg = pkg

        val relockMode = prefs.getRelockMode()
        if (relockMode == PrefsManager.RELOCK_IMMEDIATE) {
            if (currentSessionUnlockedPkg != null && pkg != currentSessionUnlockedPkg) {
                currentSessionUnlockedPkg = null
            }
        }

        if (pkg != lastOverlayLaunchedPkg) {
            lastOverlayLaunchedPkg = null
        }

        evaluatePackageLock(pkg)
    }

    private fun evaluatePackageLock(pkg: String) {
        if (LockOverlayActivity.isForegroundFor(pkg)) return

        val relockMode = prefs.getRelockMode()
        val lockedApps = prefs.getLockedApps()
        val limits = prefs.getDailyLimits()
        val limitMinutes = limits[pkg] ?: 0
        val hasCustom = prefs.hasCustomAppLock(pkg)

        if (limitMinutes > 0) {
            val usedToday = AppDetector.getAppUsageMinutesToday(this, pkg)
            if (usedToday >= limitMinutes && !prefs.isTemporarilyUnlocked("${pkg}_limit_override")) {
                launchLockIfNotDebounced(pkg, LockOverlayActivity.MODE_LIMIT_REACHED, usedToday, limitMinutes)
                return
            }
        }

        if (lockedApps.contains(pkg) || hasCustom) {
            val isUnlockedNow = when (relockMode) {
                PrefsManager.RELOCK_IMMEDIATE -> currentSessionUnlockedPkg == pkg
                PrefsManager.RELOCK_SCREEN_OFF -> currentSessionUnlockedPkg == pkg || prefs.isTemporarilyUnlocked(pkg)
                else -> prefs.isTemporarilyUnlocked(pkg)
            }
            if (!isUnlockedNow) {
                launchLockIfNotDebounced(pkg, LockOverlayActivity.MODE_APP_LOCK, 0, limitMinutes)
            }
        }
    }

    private fun launchLockIfNotDebounced(pkg: String, mode: String, usedMinutes: Int, limitMinutes: Int) {
        val now = System.currentTimeMillis()
        if (lastOverlayLaunchedPkg == pkg && (now - lastOverlayLaunchTime) < 900L) {
            return
        }
        lastOverlayLaunchedPkg = pkg
        lastOverlayLaunchTime = now

        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(LockOverlayActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(LockOverlayActivity.EXTRA_MODE, mode)
            putExtra(LockOverlayActivity.EXTRA_USED_MINUTES, usedMinutes)
            putExtra(LockOverlayActivity.EXTRA_LIMIT_MINUTES, limitMinutes)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
