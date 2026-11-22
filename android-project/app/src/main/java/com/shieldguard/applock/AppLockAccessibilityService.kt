package com.shieldguard.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PrefsManager
    private var lastCheckedPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName || pkg == "com.android.systemui") return
        if (pkg == lastCheckedPackage) return
        lastCheckedPackage = pkg

        val lockedApps = prefs.getLockedApps()
        val limits = prefs.getDailyLimits()
        val limitMinutes = limits[pkg] ?: 0

        if (limitMinutes > 0) {
            val usedToday = AppDetector.getAppUsageMinutesToday(this, pkg)
            if (usedToday >= limitMinutes && !prefs.isTemporarilyUnlocked("${pkg}_limit_override")) {
                launchLockScreen(pkg, mode = LockOverlayActivity.MODE_LIMIT_REACHED, usedToday, limitMinutes)
                return
            }
        }

        if (lockedApps.contains(pkg) && !prefs.isTemporarilyUnlocked(pkg)) {
            launchLockScreen(pkg, mode = LockOverlayActivity.MODE_APP_LOCK, 0, limitMinutes)
        }
    }

    private fun launchLockScreen(pkg: String, mode: String, usedMinutes: Int, limitMinutes: Int) {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(LockOverlayActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(LockOverlayActivity.EXTRA_MODE, mode)
            putExtra(LockOverlayActivity.EXTRA_USED_MINUTES, usedMinutes)
            putExtra(LockOverlayActivity.EXTRA_LIMIT_MINUTES, limitMinutes)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
