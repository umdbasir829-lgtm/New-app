package com.shieldguard.applock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var currentSessionUnlockedPkg: String? = null

        @Volatile
        private var lastUnlockedPkg: String? = null

        @Volatile
        private var lastUnlockElapsedMs: Long = 0L

        @Volatile
        private var lastOverlayLaunchedPkg: String? = null

        @Volatile
        private var lastOverlayLaunchTime: Long = 0L

        @Volatile
        private var lastObservedForegroundPkg: String? = null

        @Volatile
        private var activeServiceRef: WeakReference<AppLockAccessibilityService>? = null

        fun markSessionUnlocked(pkg: String) {
            currentSessionUnlockedPkg = pkg
            lastUnlockedPkg = pkg
            lastUnlockElapsedMs = SystemClock.elapsedRealtime()
            lastOverlayLaunchedPkg = null
            lastOverlayLaunchTime = 0L
            dismissInstantShieldCover()
        }

        fun clearOverlayState() {
            lastOverlayLaunchedPkg = null
            lastOverlayLaunchTime = 0L
            dismissInstantShieldCover()
        }

        fun dismissInstantShieldCover() {
            activeServiceRef?.get()?.removeShieldCoverNow()
        }
    }

    private lateinit var prefs: PrefsManager
    private var imePackages: Set<String> = emptySet()
    private var homeLauncherPackages: Set<String> = emptySet()
    private val launchableCache = mutableMapOf<String, Boolean>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var shieldCoverView: View? = null
    private val coverAutoDismissRunnable = Runnable { removeShieldCoverNow() }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    currentSessionUnlockedPkg = null
                    lastUnlockedPkg = null
                    lastUnlockElapsedMs = 0L
                    lastOverlayLaunchedPkg = null
                    lastOverlayLaunchTime = 0L
                    removeShieldCoverNow()
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
        activeServiceRef = WeakReference(this)
        refreshSystemPackageLists()
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
        removeShieldCoverNow()
        activeServiceRef = null
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun refreshSystemPackageLists() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imePackages = imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        homeLauncherPackages = packageManager.queryIntentActivities(homeIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun isUserLaunchableApp(pkg: String): Boolean {
        return launchableCache.getOrPut(pkg) {
            packageManager.getLaunchIntentForPackage(pkg) != null
        }
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (
            pkg == "com.android.systemui" ||
            pkg == "android" ||
            pkg == "com.android.permissioncontroller" ||
            pkg == "com.google.android.permissioncontroller" ||
            pkg == "com.android.intentresolver" ||
            pkg == "com.google.android.gms" ||
            pkg == "com.miui.securitycenter" ||
            pkg == "com.miui.securityadd" ||
            pkg == "com.lbe.security.miui" ||
            pkg == "com.xiaomi.biometric"
        ) {
            return true
        }
        if (imePackages.contains(pkg)) return true
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (pkg == currentSessionUnlockedPkg) return
            if (pkg == lastObservedForegroundPkg) return
            if (isIgnoredSystemPackage(pkg)) return
            val hasRule = prefs.getLockedApps().contains(pkg) ||
                prefs.hasCustomAppLock(pkg) ||
                (prefs.getDailyLimits()[pkg] ?: 0) > 0
            if (!hasRule) return
        } else if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        if (isIgnoredSystemPackage(pkg)) return

        lastObservedForegroundPkg = pkg

        val relockMode = prefs.getRelockMode()
        if (relockMode == PrefsManager.RELOCK_IMMEDIATE &&
            currentSessionUnlockedPkg != null &&
            pkg != currentSessionUnlockedPkg
        ) {
            val wentHome = homeLauncherPackages.contains(pkg)
            val elapsedSinceUnlock = SystemClock.elapsedRealtime() - lastUnlockElapsedMs
            val switchedToAnotherUserApp = isUserLaunchableApp(pkg) && elapsedSinceUnlock > 1600L
            if (wentHome || switchedToAnotherUserApp) {
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
        val elapsedSinceUnlock = SystemClock.elapsedRealtime() - lastUnlockElapsedMs
        if (mode != LockOverlayActivity.MODE_LIMIT_REACHED && pkg == lastUnlockedPkg && elapsedSinceUnlock < 1600L) {
            return
        }

        val now = System.currentTimeMillis()
        if (lastOverlayLaunchedPkg == pkg && (now - lastOverlayLaunchTime) < 900L) {
            return
        }
        lastOverlayLaunchedPkg = pkg
        lastOverlayLaunchTime = now

        showInstantShieldCover()

        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(LockOverlayActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(LockOverlayActivity.EXTRA_MODE, mode)
            putExtra(LockOverlayActivity.EXTRA_USED_MINUTES, usedMinutes)
            putExtra(LockOverlayActivity.EXTRA_LIMIT_MINUTES, limitMinutes)
        }
        startActivity(intent)
    }

    private fun showInstantShieldCover() {
        if (!Settings.canDrawOverlays(this)) return
        mainHandler.post {
            try {
                if (shieldCoverView != null) {
                    mainHandler.removeCallbacks(coverAutoDismissRunnable)
                    mainHandler.postDelayed(coverAutoDismissRunnable, 1200L)
                    return@post
                }
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val cover = FrameLayout(this).apply {
                    setBackgroundColor(UiStyleHelper.BG_CANVAS)
                    addView(UiStyleHelper.shieldBadgeView(this@AppLockAccessibilityService, true, 56).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            UiStyleHelper.dp(this@AppLockAccessibilityService, 56),
                            UiStyleHelper.dp(this@AppLockAccessibilityService, 56),
                            Gravity.CENTER
                        )
                    })
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE
                )
                wm.addView(cover, lp)
                shieldCoverView = cover
                mainHandler.removeCallbacks(coverAutoDismissRunnable)
                mainHandler.postDelayed(coverAutoDismissRunnable, 1200L)
            } catch (_: Exception) {}
        }
    }

    private fun removeShieldCoverNow() {
        mainHandler.post {
            mainHandler.removeCallbacks(coverAutoDismissRunnable)
            val existing = shieldCoverView ?: return@post
            shieldCoverView = null
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(existing)
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {}
}
