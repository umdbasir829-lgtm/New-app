package com.shieldguard.applock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        }

        fun unlockFromBiometric(pkg: String) {
            markSessionUnlocked(pkg)
            activeServiceRef?.get()?.removeLockOverlayNow()
        }

        fun clearOverlayState() {
            lastOverlayLaunchedPkg = null
            lastOverlayLaunchTime = 0L
        }

        fun dismissInstantShieldCover() {
            activeServiceRef?.get()?.removeShieldCoverNow()
        }

        fun onPolicyUpdated() {
            activeServiceRef?.get()?.refreshPoliciesNow()
        }
    }

    private lateinit var prefs: PrefsManager
    private var imePackages: Set<String> = emptySet()
    private var homeLauncherPackages: Set<String> = emptySet()
    private val launchableCache = mutableMapOf<String, Boolean>()
    private val appLabelCache = mutableMapOf<String, String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScreenInteractive: Boolean = true
    private var lastTrackedDateKey: String = ""
    private val sessionUsageSeconds = mutableMapOf<String, Int>()
    private var usageFlushCounter = 0
    private var lastPolledUsagePkg: String? = null

    private var shieldCoverView: View? = null
    private val coverAutoDismissRunnable = Runnable { removeShieldCoverNow() }

    private var lockOverlayView: View? = null
    private var activeOverlayPkg: String? = null
    private var activeOverlayMode: String? = null

    private var countdownPillView: LinearLayout? = null
    private var countdownTextView: TextView? = null
    private var countdownShowingForPkg: String? = null

    private val foregroundPollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (isScreenInteractive && !isDeviceKeyguardLocked()) {
                val latest = AppDetector.getLatestForegroundPackage(this@AppLockAccessibilityService, 4500L)
                if (!latest.isNullOrBlank() && latest != lastPolledUsagePkg) {
                    lastPolledUsagePkg = latest
                    if (!isIgnoredSystemPackage(latest)) {
                        handleForegroundPackage(latest, allowImmediateRelockSwitch = true)
                    }
                }
            }
            mainHandler.postDelayed(this, 300L)
        }
    }

    private val usageTimerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (isScreenInteractive && !isDeviceKeyguardLocked()) {
                tickUsageSecond()
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    flushUsageSecondsToPrefs()
                    currentSessionUnlockedPkg = null
                    lastUnlockedPkg = null
                    lastUnlockElapsedMs = 0L
                    lastOverlayLaunchedPkg = null
                    lastOverlayLaunchTime = 0L
                    lastPolledUsagePkg = null
                    removeCountdownPillNow()
                    removeShieldCoverNow()
                    removeLockOverlayNow()
                    prefs.clearAllSessionUnlocks()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    if (isDeviceKeyguardLocked()) return
                    recheckForegroundLock()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    recheckForegroundLock()
                }
            }
        }
    }

    private fun isDeviceKeyguardLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun refreshUsageBaseline() {
        val today = todayDateKey()
        if (lastTrackedDateKey != today) {
            sessionUsageSeconds.clear()
            lastTrackedDateKey = today
        }
        val merged = AppDetector.getMergedTodayUsageSecondsMap(this, prefs)
        for ((pkg, sec) in merged) {
            sessionUsageSeconds[pkg] = maxOf(sessionUsageSeconds[pkg] ?: 0, sec)
        }
    }

    private fun flushUsageSecondsToPrefs() {
        if (sessionUsageSeconds.isNotEmpty()) {
            prefs.saveAllAppUsageSecondsToday(sessionUsageSeconds)
        }
    }

    private fun refreshPoliciesNow() {
        mainHandler.post {
            refreshUsageBaseline()
            val curPkg = activeOverlayPkg
            if (curPkg != null && activeOverlayMode == LockOverlayActivity.MODE_LIMIT_REACHED) {
                val limitSec = prefs.getEffectiveDailyLimitSeconds(curPkg)
                val usedSec = sessionUsageSeconds[curPkg] ?: 0
                if (limitSec <= 0 || usedSec < limitSec) {
                    removeLockOverlayNow()
                }
            }
            val fg = lastObservedForegroundPkg
            if (fg != null && !isIgnoredSystemPackage(fg) && !homeLauncherPackages.contains(fg)) {
                val limitSec = prefs.getEffectiveDailyLimitSeconds(fg)
                val usedSec = sessionUsageSeconds[fg] ?: 0
                if (limitSec <= 0 || limitSec - usedSec > 60) {
                    removeCountdownPillNow()
                }
            }
        }
    }

    private fun recheckForegroundLock() {
        lastOverlayLaunchedPkg = null
        lastOverlayLaunchTime = 0L
        val fgPkg = AppDetector.getLatestForegroundPackage(this, 4500L) ?: lastObservedForegroundPkg
        if (!fgPkg.isNullOrBlank() && !isIgnoredSystemPackage(fgPkg)) {
            lastObservedForegroundPkg = fgPkg
            evaluatePackageLock(fgPkg)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
        isRunning = true
        activeServiceRef = WeakReference(this)

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = pm?.isInteractive ?: true

        refreshSystemPackageLists()
        refreshUsageBaseline()

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

        mainHandler.removeCallbacks(foregroundPollRunnable)
        mainHandler.postDelayed(foregroundPollRunnable, 300L)
        mainHandler.removeCallbacks(usageTimerRunnable)
        mainHandler.postDelayed(usageTimerRunnable, 1000L)
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacks(foregroundPollRunnable)
        mainHandler.removeCallbacks(usageTimerRunnable)
        flushUsageSecondsToPrefs()
        removeCountdownPillNow()
        removeShieldCoverNow()
        removeLockOverlayNow()
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

    private fun getAppLabel(pkg: String): String {
        return appLabelCache.getOrPut(pkg) {
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }
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
            handleForegroundPackage(pkg, allowImmediateRelockSwitch = false)
            return
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (isIgnoredSystemPackage(pkg)) return

        handleForegroundPackage(pkg, allowImmediateRelockSwitch = true)
    }

    private fun handleForegroundPackage(pkg: String, allowImmediateRelockSwitch: Boolean) {
        lastObservedForegroundPkg = pkg

        if (allowImmediateRelockSwitch) {
            val relockMode = prefs.getRelockMode()
            if (relockMode == PrefsManager.RELOCK_IMMEDIATE &&
                currentSessionUnlockedPkg != null &&
                pkg != currentSessionUnlockedPkg
            ) {
                val wentHome = homeLauncherPackages.contains(pkg)
                val elapsedSinceUnlock = SystemClock.elapsedRealtime() - lastUnlockElapsedMs
                val switchedToAnotherUserApp = isUserLaunchableApp(pkg) && elapsedSinceUnlock > 1500L
                if (wentHome || switchedToAnotherUserApp) {
                    currentSessionUnlockedPkg = null
                }
            }
        }

        if (homeLauncherPackages.contains(pkg)) {
            removeCountdownPillNow()
            return
        }

        if (pkg != lastOverlayLaunchedPkg) {
            lastOverlayLaunchedPkg = null
        }

        evaluatePackageLock(pkg)
    }

    private fun tickUsageSecond() {
        val today = todayDateKey()
        if (lastTrackedDateKey != today) {
            refreshUsageBaseline()
        }

        val fg = lastPolledUsagePkg ?: lastObservedForegroundPkg ?: return
        if (isIgnoredSystemPackage(fg) || homeLauncherPackages.contains(fg)) {
            removeCountdownPillNow()
            return
        }
        if (activeOverlayPkg != null || LockOverlayActivity.isBiometricOverlayOpen()) {
            removeCountdownPillNow()
            return
        }
        if (!isUserLaunchableApp(fg)) {
            removeCountdownPillNow()
            return
        }

        // If app is currently supposed to be locked and hasn't been unlocked, do not count foreground usage
        val lockedApps = prefs.getLockedApps()
        val hasCustom = prefs.hasCustomAppLock(fg)
        if ((lockedApps.contains(fg) || hasCustom) && !isAppUnlockedNow(fg)) {
            evaluatePackageLock(fg)
            return
        }

        val currentSec = (sessionUsageSeconds[fg] ?: 0) + 1
        sessionUsageSeconds[fg] = currentSec
        usageFlushCounter++
        if (usageFlushCounter >= 5) {
            usageFlushCounter = 0
            flushUsageSecondsToPrefs()
        }

        val limitSec = prefs.getEffectiveDailyLimitSeconds(fg)
        if (limitSec <= 0) {
            removeCountdownPillNow()
            return
        }

        val remaining = limitSec - currentSec
        if (remaining <= 0) {
            removeCountdownPillNow()
            flushUsageSecondsToPrefs()
            performGlobalAction(GLOBAL_ACTION_HOME)
            launchLockOverlay(fg, LockOverlayActivity.MODE_LIMIT_REACHED, currentSec, limitSec)
        } else if (remaining <= 60) {
            showOrUpdateCountdownPill(fg, remaining)
        } else {
            removeCountdownPillNow()
        }
    }

    private fun isAppUnlockedNow(pkg: String): Boolean {
        return when (prefs.getRelockMode()) {
            PrefsManager.RELOCK_IMMEDIATE -> currentSessionUnlockedPkg == pkg
            PrefsManager.RELOCK_SCREEN_OFF -> currentSessionUnlockedPkg == pkg || prefs.isTemporarilyUnlocked(pkg)
            else -> prefs.isTemporarilyUnlocked(pkg)
        }
    }

    private fun evaluatePackageLock(pkg: String) {
        if (activeOverlayPkg == pkg || LockOverlayActivity.isForegroundFor(pkg)) return
        if (homeLauncherPackages.contains(pkg)) return

        val limitSec = prefs.getEffectiveDailyLimitSeconds(pkg)
        if (limitSec > 0) {
            val sysEventsSec = AppDetector.queryTodayUsageSecondsFromEvents(this)[pkg] ?: 0
            val usedSec = maxOf(sessionUsageSeconds[pkg] ?: 0, sysEventsSec)
            sessionUsageSeconds[pkg] = usedSec
            if (usedSec >= limitSec) {
                removeCountdownPillNow()
                performGlobalAction(GLOBAL_ACTION_HOME)
                launchLockOverlay(pkg, LockOverlayActivity.MODE_LIMIT_REACHED, usedSec, limitSec)
                return
            }
        }

        val lockedApps = prefs.getLockedApps()
        val hasCustom = prefs.hasCustomAppLock(pkg)
        if (lockedApps.contains(pkg) || hasCustom) {
            if (!isAppUnlockedNow(pkg)) {
                launchLockOverlay(pkg, LockOverlayActivity.MODE_APP_LOCK, 0, limitSec)
            }
        }
    }

    private fun launchLockOverlay(pkg: String, mode: String, usedSeconds: Int, limitSeconds: Int) {
        val elapsedSinceUnlock = SystemClock.elapsedRealtime() - lastUnlockElapsedMs
        if (mode != LockOverlayActivity.MODE_LIMIT_REACHED && pkg == lastUnlockedPkg && elapsedSinceUnlock < 1500L) {
            return
        }

        if (Settings.canDrawOverlays(this)) {
            if (activeOverlayPkg == pkg && activeOverlayMode == mode && lockOverlayView != null) {
                return
            }
            showWindowLockOverlay(pkg, mode, usedSeconds, limitSeconds)
            return
        }

        // Fallback to LockOverlayActivity only if overlay permission was revoked
        val now = System.currentTimeMillis()
        if (lastOverlayLaunchedPkg == pkg && (now - lastOverlayLaunchTime) < 900L) {
            return
        }
        lastOverlayLaunchedPkg = pkg
        lastOverlayLaunchTime = now

        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(LockOverlayActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(LockOverlayActivity.EXTRA_MODE, mode)
            putExtra(LockOverlayActivity.EXTRA_USED_MINUTES, usedSeconds / 60)
            putExtra(LockOverlayActivity.EXTRA_LIMIT_MINUTES, limitSeconds / 60)
        }
        startActivity(intent)
    }

    // =========================================================================
    // INSTANT 0ms WINDOWMANAGER LOCK & HARD-LIMIT OVERLAY
    // =========================================================================
    private fun showWindowLockOverlay(pkg: String, mode: String, usedSeconds: Int, limitSeconds: Int) {
        mainHandler.post {
            removeLockOverlayImmediateOnMain()
            UiStyleHelper.applyRuntimeTheme(prefs)

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val appLabel = getAppLabel(pkg)
            val appIcon = try {
                packageManager.getApplicationIcon(pkg)
            } catch (_: Exception) {
                null
            }

            val isLimitBlocked = mode == LockOverlayActivity.MODE_LIMIT_REACHED
            val effectiveType = if (isLimitBlocked) PrefsManager.TYPE_PASSWORD else prefs.getEffectiveLockType(pkg)
            val expectedPinLen = if (!isLimitBlocked && effectiveType == PrefsManager.TYPE_PIN) {
                prefs.getEffectivePinLength(pkg)
            } else {
                0
            }

            val pinBuffer = StringBuilder()
            val pinDots = mutableListOf<View>()
            var pinDotsRowRef: LinearLayout? = null
            var passwordInputRef: EditText? = null

            fun hideImeNow() {
                try {
                    passwordInputRef?.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    val token = passwordInputRef?.windowToken ?: lockOverlayView?.windowToken
                    if (token != null) imm?.hideSoftInputFromWindow(token, 0)
                } catch (_: Exception) {}
            }

            fun goHomeClean() {
                hideImeNow()
                clearOverlayState()
                removeLockOverlayImmediateOnMain()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            fun completeUnlock() {
                hideImeNow()
                markSessionUnlocked(pkg)
                if (prefs.getRelockMode() != PrefsManager.RELOCK_IMMEDIATE) {
                    prefs.grantTemporaryUnlock(pkg, 3 * 60 * 1000L)
                }
                removeLockOverlayImmediateOnMain()
            }

            val rootFrame = object : FrameLayout(this) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        goHomeClean()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                background = UiStyleHelper.canvasGradient()
                isFocusableInTouchMode = true
                requestFocus()
            }

            val mainCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val scroll = ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }

            val contentCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 38),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 10)
                )
            }

            val headerCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = UiStyleHelper.cardGradientBox(
                    this@AppLockAccessibilityService,
                    22,
                    if (isLimitBlocked) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.BORDER_GLOW
                )
                setPadding(
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 18),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 16),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 18),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 14)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            if (appIcon != null) {
                val iconFrame = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    background = UiStyleHelper.roundedBox(
                        this@AppLockAccessibilityService,
                        UiStyleHelper.BG_ELEVATED,
                        16,
                        UiStyleHelper.BORDER_SUBTLE,
                        1
                    )
                    setPadding(
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 9),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 9),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 9),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 9)
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = UiStyleHelper.dp(this@AppLockAccessibilityService, 8) }
                }
                iconFrame.addView(ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 46),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 46)
                    )
                })
                headerCard.addView(iconFrame)
            }

            val badgeText = when {
                isLimitBlocked -> "দৈনিক সময়সীমা শেষ"
                prefs.hasCustomAppLock(pkg) -> "নিজস্ব ${lockTypeBn(effectiveType)}"
                prefs.isSeparateAppLockEnabled() -> "সুরক্ষিত অ্যাপ · ${lockTypeBn(effectiveType)}"
                else -> "ভল্ট লক · ${lockTypeBn(effectiveType)}"
            }
            headerCard.addView(
                UiStyleHelper.chipBadge(
                    this,
                    badgeText,
                    if (isLimitBlocked) UiStyleHelper.ACCENT_ROSE_SOFT else UiStyleHelper.ACCENT_BLUE_SOFT,
                    if (isLimitBlocked) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.ACCENT_BLUE,
                    if (isLimitBlocked) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.BORDER_GLOW
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = UiStyleHelper.dp(this@AppLockAccessibilityService, 6) }
                }
            )

            headerCard.addView(TextView(this).apply {
                text = appLabel
                textSize = 19f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                gravity = Gravity.CENTER
            })

            val subtitleText = when {
                isLimitBlocked ->
                    "আজ ব্যবহার: ${usedSeconds / 60} মি. / সীমা: ${limitSeconds / 60} মি."
                prefs.hasCustomAppLock(pkg) ->
                    "এই অ্যাপের নিজস্ব লক দিয়ে আনলক করুন"
                else ->
                    prefs.getLockSubtitle()
            }
            headerCard.addView(TextView(this).apply {
                text = subtitleText
                textSize = 12.5f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, UiStyleHelper.dp(this@AppLockAccessibilityService, 4), 0, 0)
            })
            contentCol.addView(headerCard)

            val errorTv = TextView(this).apply {
                text = ""
                textSize = 12.5f
                setTextColor(UiStyleHelper.ACCENT_ROSE)
                gravity = Gravity.CENTER
                setPadding(0, UiStyleHelper.dp(this@AppLockAccessibilityService, 8), 0, UiStyleHelper.dp(this@AppLockAccessibilityService, 4))
            }
            contentCol.addView(errorTv)

            val centerArea = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }

            if (isLimitBlocked) {
                val blockCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = UiStyleHelper.cardGradientBox(
                        this@AppLockAccessibilityService,
                        22,
                        UiStyleHelper.ACCENT_ROSE
                    )
                    setPadding(
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 22),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 22)
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                blockCard.addView(TextView(this).apply {
                    text = "আজকের নির্ধারিত সময় শেষ হয়ে গেছে"
                    textSize = 16.5f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(UiStyleHelper.ACCENT_ROSE)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, UiStyleHelper.dp(this@AppLockAccessibilityService, 8))
                })
                blockCard.addView(TextView(this).apply {
                    text = "আজ আর এই অ্যাপটি খোলা যাবে না।\nপুনরায় ব্যবহার করতে ShieldGuard-এ ঢুকে লিমিট বন্ধ করুন অথবা আজকের জন্য এক্সট্রা সময় যোগ করুন।"
                    textSize = 13f
                    setTextColor(UiStyleHelper.TEXT_SECONDARY)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, UiStyleHelper.dp(this@AppLockAccessibilityService, 18))
                })
                blockCard.addView(
                    UiStyleHelper.actionButton(
                        this,
                        "ShieldGuard-এ সীমা পরিবর্তন করুন",
                        UiStyleHelper.ACCENT_BLUE,
                        UiStyleHelper.ACCENT_BLUE_DEEP
                    ) {
                        removeLockOverlayImmediateOnMain()
                        val sgIntent = Intent(this@AppLockAccessibilityService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(sgIntent)
                    }
                )
                centerArea.addView(blockCard)
            } else {
                when (effectiveType) {
                    PrefsManager.TYPE_PATTERN -> {
                        val hintTv = TextView(this).apply {
                            text = "আনলক করতে আপনার প্যাটার্ন আঁকুন"
                            textSize = 13f
                            setTextColor(UiStyleHelper.TEXT_SECONDARY)
                            setPadding(0, 0, 0, UiStyleHelper.dp(this@AppLockAccessibilityService, 12))
                        }
                        centerArea.addView(hintTv)

                        val pv = PatternLockView(this).apply {
                            background = UiStyleHelper.cardGradientBox(
                                this@AppLockAccessibilityService,
                                24,
                                UiStyleHelper.BORDER_GLOW
                            )
                            setPadding(
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 16),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 16),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 16),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 16)
                            )
                        }
                        pv.listener = object : PatternLockView.Listener {
                            override fun onPatternComplete(pattern: String) {
                                if (prefs.verifyAppUnlock(pkg, pattern)) {
                                    completeUnlock()
                                } else {
                                    errorTv.text = "ভুল প্যাটার্ন! আবার চেষ্টা করুন"
                                    pv.showErrorAndReset()
                                }
                            }
                        }
                        centerArea.addView(pv)
                    }
                    PrefsManager.TYPE_PASSWORD -> {
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            background = UiStyleHelper.cardGradientBox(
                                this@AppLockAccessibilityService,
                                22,
                                UiStyleHelper.BORDER_GLOW
                            )
                            setPadding(
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 20)
                            )
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                        val input = EditText(this).apply {
                            hint = "পাসওয়ার্ড লিখুন"
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            setTextColor(UiStyleHelper.TEXT_PRIMARY)
                            setHintTextColor(UiStyleHelper.TEXT_MUTED)
                            background = UiStyleHelper.roundedBox(
                                this@AppLockAccessibilityService,
                                UiStyleHelper.BG_ELEVATED,
                                14,
                                UiStyleHelper.BORDER_SUBTLE,
                                1
                            )
                            setPadding(
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 16),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 14),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 16),
                                UiStyleHelper.dp(this@AppLockAccessibilityService, 14)
                            )
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = UiStyleHelper.dp(this@AppLockAccessibilityService, 14) }
                        }
                        passwordInputRef = input
                        val unlockBtn = UiStyleHelper.actionButton(this, "আনলক করুন") {
                            if (prefs.verifyAppUnlock(pkg, input.text.toString())) {
                                completeUnlock()
                            } else {
                                errorTv.text = "ভুল পাসওয়ার্ড! আবার চেষ্টা করুন"
                                input.setText("")
                            }
                        }
                        card.addView(input)
                        card.addView(unlockBtn)
                        centerArea.addView(card)
                    }
                    else -> {
                        val initialDots = if (expectedPinLen in 4..8) expectedPinLen else 4
                        val maxLen = if (expectedPinLen in 4..8) expectedPinLen else 6

                        fun makeDot(): View {
                            return View(this).apply {
                                background = UiStyleHelper.circleBox(
                                    this@AppLockAccessibilityService,
                                    UiStyleHelper.BG_CARD,
                                    UiStyleHelper.BORDER_GLOW,
                                    2
                                )
                                layoutParams = LinearLayout.LayoutParams(
                                    UiStyleHelper.dp(this@AppLockAccessibilityService, 14),
                                    UiStyleHelper.dp(this@AppLockAccessibilityService, 14)
                                ).apply {
                                    setMargins(
                                        UiStyleHelper.dp(this@AppLockAccessibilityService, 8),
                                        0,
                                        UiStyleHelper.dp(this@AppLockAccessibilityService, 8),
                                        0
                                    )
                                }
                            }
                        }

                        val dotsRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER
                            setPadding(0, UiStyleHelper.dp(this@AppLockAccessibilityService, 4), 0, UiStyleHelper.dp(this@AppLockAccessibilityService, 16))
                        }
                        pinDotsRowRef = dotsRow
                        for (i in 0 until initialDots) {
                            val d = makeDot()
                            pinDots.add(d)
                            dotsRow.addView(d)
                        }
                        centerArea.addView(dotsRow)

                        fun updateDots() {
                            if (expectedPinLen == 0) {
                                val targetCount = maxOf(4, pinBuffer.length)
                                while (pinDots.size < targetCount) {
                                    val extra = makeDot()
                                    pinDots.add(extra)
                                    pinDotsRowRef?.addView(extra)
                                }
                                while (pinDots.size > targetCount) {
                                    val rem = pinDots.removeAt(pinDots.lastIndex)
                                    pinDotsRowRef?.removeView(rem)
                                }
                            }
                            for (i in pinDots.indices) {
                                val active = i < pinBuffer.length
                                pinDots[i].background = UiStyleHelper.circleBox(
                                    this,
                                    if (active) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                                    if (active) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_GLOW,
                                    2
                                )
                            }
                        }

                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("⌫", "0", "✓")
                        )
                        val screenH = resources.displayMetrics.heightPixels
                        val btnSize = if (screenH < UiStyleHelper.dp(this, 780)) UiStyleHelper.dp(this, 56) else UiStyleHelper.dp(this, 60)
                        val gapH = UiStyleHelper.dp(this, 10)
                        val gapV = UiStyleHelper.dp(this, 4)

                        for (rowKeys in rows) {
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER
                            }
                            for (label in rowKeys) {
                                val isConfirm = label == "✓"
                                val btn = TextView(this).apply {
                                    text = label
                                    textSize = if (label == "⌫" || isConfirm) 19f else 21f
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    gravity = Gravity.CENTER
                                    isClickable = true
                                    isFocusable = true
                                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                                    background = UiStyleHelper.touchableCircleBox(
                                        this@AppLockAccessibilityService,
                                        if (isConfirm) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                                        if (isConfirm) UiStyleHelper.ACCENT_BLUE_DEEP else UiStyleHelper.BG_PRESSED,
                                        if (isConfirm) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_GLOW,
                                        1
                                    )
                                    layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                                        setMargins(gapH, gapV, gapH, gapV)
                                    }
                                    setOnClickListener {
                                        when (label) {
                                            "⌫" -> {
                                                if (pinBuffer.isNotEmpty()) {
                                                    pinBuffer.deleteCharAt(pinBuffer.length - 1)
                                                    updateDots()
                                                }
                                            }
                                            "✓" -> {
                                                if (prefs.verifyAppUnlock(pkg, pinBuffer.toString())) {
                                                    completeUnlock()
                                                } else {
                                                    errorTv.text = "ভুল পিন! আবার চেষ্টা করুন"
                                                    pinBuffer.clear()
                                                    updateDots()
                                                }
                                            }
                                            else -> {
                                                if (pinBuffer.length < maxLen) {
                                                    pinBuffer.append(label)
                                                    updateDots()
                                                    if (pinBuffer.length >= 4 && prefs.verifyAppUnlock(pkg, pinBuffer.toString())) {
                                                        completeUnlock()
                                                    } else if (expectedPinLen in 4..8 && pinBuffer.length == expectedPinLen) {
                                                        errorTv.text = "ভুল পিন! আবার চেষ্টা করুন"
                                                        pinBuffer.clear()
                                                        updateDots()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                row.addView(btn)
                            }
                            centerArea.addView(row)
                        }
                    }
                }
            }
            contentCol.addView(centerArea)
            scroll.addView(contentCol)
            mainCol.addView(scroll)

            val bottomBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 8),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 20),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 24)
                )
            }

            if (!isLimitBlocked && prefs.isBiometricAllowedForApp(pkg)) {
                val bioCircle = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    background = UiStyleHelper.touchableCircleBox(
                        this@AppLockAccessibilityService,
                        UiStyleHelper.ACCENT_BLUE_SOFT,
                        UiStyleHelper.BG_PRESSED,
                        UiStyleHelper.ACCENT_BLUE,
                        1
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 48),
                        UiStyleHelper.dp(this@AppLockAccessibilityService, 48)
                    ).apply { rightMargin = UiStyleHelper.dp(this@AppLockAccessibilityService, 10) }
                    setOnClickListener {
                        val bioIntent = Intent(this@AppLockAccessibilityService, LockOverlayActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            putExtra(LockOverlayActivity.EXTRA_PACKAGE_NAME, pkg)
                            putExtra(LockOverlayActivity.EXTRA_BIOMETRIC_ONLY, true)
                        }
                        startActivity(bioIntent)
                    }
                }
                bioCircle.addView(UiStyleHelper.fingerprintIconView(this, 24, UiStyleHelper.ACCENT_BLUE))
                bottomBar.addView(bioCircle)
            }

            val homeBtn = UiStyleHelper.actionButton(
                this,
                "হোম স্ক্রিনে ফিরুন",
                UiStyleHelper.BG_CARD,
                UiStyleHelper.BG_PRESSED,
                UiStyleHelper.TEXT_SECONDARY,
                UiStyleHelper.BORDER_SUBTLE,
                46
            ) {
                goHomeClean()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@AppLockAccessibilityService, 46), 1f)
            }
            bottomBar.addView(homeBtn)
            mainCol.addView(bottomBar)

            rootFrame.addView(mainCol)

            val flags = if (effectiveType == PrefsManager.TYPE_PASSWORD && !isLimitBlocked) {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            }

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.OPAQUE
            ).apply {
                softInputMode = if (effectiveType == PrefsManager.TYPE_PASSWORD && !isLimitBlocked) {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                } else {
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                }
            }

            try {
                wm.addView(rootFrame, lp)
                lockOverlayView = rootFrame
                activeOverlayPkg = pkg
                activeOverlayMode = mode
            } catch (_: Exception) {}
        }
    }

    private fun lockTypeBn(type: String): String {
        return when (type) {
            PrefsManager.TYPE_PATTERN -> "প্যাটার্ন"
            PrefsManager.TYPE_PASSWORD -> "পাসওয়ার্ড"
            else -> "পিন"
        }
    }

    private fun removeLockOverlayImmediateOnMain() {
        val existing = lockOverlayView ?: run {
            activeOverlayPkg = null
            activeOverlayMode = null
            return
        }
        lockOverlayView = null
        activeOverlayPkg = null
        activeOverlayMode = null
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(existing)
        } catch (_: Exception) {}
    }

    private fun removeLockOverlayNow() {
        mainHandler.post { removeLockOverlayImmediateOnMain() }
    }

    // =========================================================================
    // 1-MINUTE LIVE COUNTDOWN FLOATING PILL
    // =========================================================================
    private fun showOrUpdateCountdownPill(pkg: String, remainingSeconds: Int) {
        if (!Settings.canDrawOverlays(this)) return
        val appName = getAppLabel(pkg)
        val urgent = remainingSeconds <= 15
        val label = "⏱ $appName: ${String.format(Locale.US, "00:%02d", remainingSeconds)} বাকি"

        val existingTv = countdownTextView
        val existingView = countdownPillView
        if (existingView != null && existingTv != null && countdownShowingForPkg == pkg) {
            existingTv.text = label
            existingView.background = UiStyleHelper.roundedBox(
                this,
                if (urgent) Color.parseColor("#DC2626") else Color.parseColor("#B45309"),
                20,
                Color.WHITE,
                1
            )
            return
        }

        removeCountdownPillImmediateOnMain()
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = UiStyleHelper.roundedBox(
                    this@AppLockAccessibilityService,
                    if (urgent) Color.parseColor("#DC2626") else Color.parseColor("#B45309"),
                    20,
                    Color.WHITE,
                    1
                )
                setPadding(
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 14),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 7),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 14),
                    UiStyleHelper.dp(this@AppLockAccessibilityService, 7)
                )
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 12.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setSingleLine(true)
            }
            pill.addView(tv)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = UiStyleHelper.dp(this@AppLockAccessibilityService, 42)
            }
            wm.addView(pill, lp)
            countdownPillView = pill
            countdownTextView = tv
            countdownShowingForPkg = pkg
        } catch (_: Exception) {}
    }

    private fun removeCountdownPillImmediateOnMain() {
        val existing = countdownPillView ?: return
        countdownPillView = null
        countdownTextView = null
        countdownShowingForPkg = null
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(existing)
        } catch (_: Exception) {}
    }

    private fun removeCountdownPillNow() {
        mainHandler.post { removeCountdownPillImmediateOnMain() }
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
