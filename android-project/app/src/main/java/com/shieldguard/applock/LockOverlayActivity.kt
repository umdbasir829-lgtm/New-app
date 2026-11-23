package com.shieldguard.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_USED_MINUTES = "extra_used"
        const val EXTRA_LIMIT_MINUTES = "extra_limit"
        const val MODE_APP_LOCK = "APP_LOCK"
        const val MODE_LIMIT_REACHED = "LIMIT_REACHED"

        @Volatile
        private var foregroundActive: Boolean = false

        @Volatile
        private var biometricPromptActive: Boolean = false

        @Volatile
        private var currentTargetPkg: String? = null

        fun isForegroundFor(pkg: String): Boolean {
            return (foregroundActive || biometricPromptActive) && currentTargetPkg == pkg
        }
    }

    private lateinit var prefs: PrefsManager
    private var targetPkg: String = ""
    private var lockMode: String = MODE_APP_LOCK
    private var pinBuffer = StringBuilder()
    private val pinDots = mutableListOf<View>()
    private var expectedPinLength: Int = 0
    private var pinDotsRow: LinearLayout? = null
    private lateinit var errorTv: TextView
    private var passwordInputView: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        window.statusBarColor = UiStyleHelper.BG_CANVAS_TOP
        window.navigationBarColor = UiStyleHelper.BG_CANVAS
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        foregroundActive = true
        currentTargetPkg = targetPkg
        AppLockAccessibilityService.dismissInstantShieldCover()
    }

    override fun onPause() {
        foregroundActive = false
        hideSoftKeyboard()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!biometricPromptActive && !isFinishing) {
            foregroundActive = false
            if (currentTargetPkg == targetPkg) {
                currentTargetPkg = null
            }
            AppLockAccessibilityService.clearOverlayState()
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        biometricPromptActive = false
        if (currentTargetPkg == targetPkg) {
            foregroundActive = false
            currentTargetPkg = null
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun hideSoftKeyboard() {
        try {
            passwordInputView?.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val token = currentFocus?.windowToken ?: window.decorView.windowToken
            if (token != null) {
                imm?.hideSoftInputFromWindow(token, 0)
            }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } catch (_: Exception) {}
    }

    private fun handleIntent(currentIntent: Intent) {
        UiStyleHelper.applyRuntimeTheme(prefs)
        targetPkg = currentIntent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        currentTargetPkg = targetPkg
        lockMode = currentIntent.getStringExtra(EXTRA_MODE) ?: MODE_APP_LOCK
        val usedMins = currentIntent.getIntExtra(EXTRA_USED_MINUTES, 0)
        val limitMins = currentIntent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        pinBuffer.clear()
        passwordInputView = null

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(targetPkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            targetPkg
        }

        val appIcon = try {
            packageManager.getApplicationIcon(targetPkg)
        } catch (_: Exception) {
            null
        }

        val effectiveType = if (lockMode == MODE_LIMIT_REACHED) {
            prefs.getVaultLockType()
        } else {
            prefs.getEffectiveLockType(targetPkg)
        }

        expectedPinLength = if (effectiveType == PrefsManager.TYPE_PIN) {
            if (lockMode == MODE_LIMIT_REACHED) prefs.getVaultPinLength() else prefs.getEffectivePinLength(targetPkg)
        } else {
            0
        }

        val biometricAllowed = if (lockMode == MODE_LIMIT_REACHED) {
            prefs.isBiometricAllowedForVault() && canUseBiometric()
        } else {
            prefs.isBiometricAllowedForApp(targetPkg) && canUseBiometric()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.canvasGradient()
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 24),
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 10)
            )
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiStyleHelper.cardGradientBox(
                this@LockOverlayActivity,
                22,
                UiStyleHelper.BORDER_GLOW
            )
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 18),
                UiStyleHelper.dp(this@LockOverlayActivity, 16),
                UiStyleHelper.dp(this@LockOverlayActivity, 18),
                UiStyleHelper.dp(this@LockOverlayActivity, 14)
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
                    this@LockOverlayActivity,
                    UiStyleHelper.BG_ELEVATED,
                    16,
                    UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                setPadding(
                    UiStyleHelper.dp(this@LockOverlayActivity, 9),
                    UiStyleHelper.dp(this@LockOverlayActivity, 9),
                    UiStyleHelper.dp(this@LockOverlayActivity, 9),
                    UiStyleHelper.dp(this@LockOverlayActivity, 9)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiStyleHelper.dp(this@LockOverlayActivity, 8) }
            }
            val iconView = ImageView(this).apply {
                setImageDrawable(appIcon)
                layoutParams = LinearLayout.LayoutParams(
                    UiStyleHelper.dp(this@LockOverlayActivity, 44),
                    UiStyleHelper.dp(this@LockOverlayActivity, 44)
                )
            }
            iconFrame.addView(iconView)
            headerCard.addView(iconFrame)
        }

        val badgeLabel = when {
            lockMode == MODE_LIMIT_REACHED -> "দৈনিক সময়সীমা শেষ"
            prefs.hasCustomAppLock(targetPkg) -> "নিজস্ব ${lockTypeBengali(effectiveType)}"
            prefs.isSeparateAppLockEnabled() -> "সুরক্ষিত অ্যাপ · ${lockTypeBengali(effectiveType)}"
            else -> "ভল্ট লক · ${lockTypeBengali(effectiveType)}"
        }
        val badge = UiStyleHelper.chipBadge(
            this,
            badgeLabel,
            if (lockMode == MODE_LIMIT_REACHED) UiStyleHelper.ACCENT_ROSE_SOFT else UiStyleHelper.ACCENT_BLUE_SOFT,
            if (lockMode == MODE_LIMIT_REACHED) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.ACCENT_BLUE,
            if (lockMode == MODE_LIMIT_REACHED) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.BORDER_GLOW
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiStyleHelper.dp(this@LockOverlayActivity, 6) }
        }
        headerCard.addView(badge)

        headerCard.addView(TextView(this).apply {
            text = appLabel
            textSize = 18.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        })

        val subtitleText = when {
            lockMode == MODE_LIMIT_REACHED ->
                "আজ ব্যবহার: $usedMins মি. / সীমা: $limitMins মি. · খুলতে ভল্ট লক দিন"
            prefs.hasCustomAppLock(targetPkg) ->
                "এই অ্যাপের নিজস্ব লক দিয়ে আনলক করুন"
            else ->
                prefs.getLockSubtitle()
        }
        headerCard.addView(TextView(this).apply {
            text = subtitleText
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 3), 0, 0)
        })
        contentCol.addView(headerCard)

        errorTv = TextView(this).apply {
            text = ""
            textSize = 12.5f
            setTextColor(UiStyleHelper.ACCENT_ROSE)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 8), 0, UiStyleHelper.dp(this@LockOverlayActivity, 4))
        }
        contentCol.addView(errorTv)

        val unlockArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        when (effectiveType) {
            PrefsManager.TYPE_PATTERN -> buildPatternUnlockUi(unlockArea)
            PrefsManager.TYPE_PASSWORD -> buildPasswordUnlockUi(unlockArea)
            else -> buildPinUnlockUi(unlockArea)
        }
        contentCol.addView(unlockArea)

        scroll.addView(contentCol)
        root.addView(scroll)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 8),
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 20)
            )
        }

        if (biometricAllowed) {
            val bioCircle = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "ফিঙ্গারপ্রিন্ট আনলক"
                background = UiStyleHelper.touchableCircleBox(
                    this@LockOverlayActivity,
                    UiStyleHelper.ACCENT_BLUE_SOFT,
                    UiStyleHelper.BG_PRESSED,
                    UiStyleHelper.ACCENT_BLUE,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(
                    UiStyleHelper.dp(this@LockOverlayActivity, 48),
                    UiStyleHelper.dp(this@LockOverlayActivity, 48)
                ).apply { rightMargin = UiStyleHelper.dp(this@LockOverlayActivity, 10) }
                setOnClickListener { triggerBiometricPrompt(appLabel) }
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
            goHome()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@LockOverlayActivity, 46), 1f)
        }
        bottomBar.addView(homeBtn)
        root.addView(bottomBar)

        setContentView(root)
        if (effectiveType != PrefsManager.TYPE_PASSWORD) {
            hideSoftKeyboard()
        }
    }

    private fun lockTypeBengali(type: String): String {
        return when (type) {
            PrefsManager.TYPE_PATTERN -> "প্যাটার্ন"
            PrefsManager.TYPE_PASSWORD -> "পাসওয়ার্ড"
            else -> "পিন"
        }
    }

    private fun createPinDotView(): View {
        return View(this).apply {
            background = UiStyleHelper.circleBox(
                this@LockOverlayActivity,
                UiStyleHelper.BG_CARD,
                UiStyleHelper.BORDER_GLOW,
                2
            )
            layoutParams = LinearLayout.LayoutParams(
                UiStyleHelper.dp(this@LockOverlayActivity, 14),
                UiStyleHelper.dp(this@LockOverlayActivity, 14)
            ).apply {
                setMargins(
                    UiStyleHelper.dp(this@LockOverlayActivity, 8),
                    0,
                    UiStyleHelper.dp(this@LockOverlayActivity, 8),
                    0
                )
            }
        }
    }

    private fun buildPinUnlockUi(container: LinearLayout) {
        pinDots.clear()
        val initialDots = if (expectedPinLength in 4..8) expectedPinLength else 4
        val maxLen = if (expectedPinLength in 4..8) expectedPinLength else 6

        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 4), 0, UiStyleHelper.dp(this@LockOverlayActivity, 16))
        }
        pinDotsRow = dotsRow
        for (i in 0 until initialDots) {
            val dot = createPinDotView()
            pinDots.add(dot)
            dotsRow.addView(dot)
        }
        container.addView(dotsRow)

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        )

        val screenH = resources.displayMetrics.heightPixels
        val btnSize = if (screenH < UiStyleHelper.dp(this, 780)) UiStyleHelper.dp(this, 56) else UiStyleHelper.dp(this, 60)
        val gapH = UiStyleHelper.dp(this, 10)
        val gapV = UiStyleHelper.dp(this, 4)

        for (rowKeys in keys) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (label in rowKeys) {
                val isConfirm = label == "✓"
                val keyBtn = TextView(this).apply {
                    text = label
                    textSize = if (label == "⌫" || isConfirm) 19f else 21f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                    background = UiStyleHelper.touchableCircleBox(
                        this@LockOverlayActivity,
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
                                    updatePinDots()
                                }
                            }
                            "✓" -> verifyAndUnlock(pinBuffer.toString())
                            else -> {
                                if (pinBuffer.length < maxLen) {
                                    pinBuffer.append(label)
                                    updatePinDots()
                                    if (pinBuffer.length >= 4 && tryVerifySilent(pinBuffer.toString())) {
                                        completeUnlock()
                                    } else if (expectedPinLength in 4..8 && pinBuffer.length == expectedPinLength) {
                                        errorTv.text = "ভুল পিন! আবার চেষ্টা করুন"
                                        pinBuffer.clear()
                                        updatePinDots()
                                    }
                                }
                            }
                        }
                    }
                }
                row.addView(keyBtn)
            }
            container.addView(row)
        }
    }

    private fun updatePinDots() {
        val row = pinDotsRow
        if (expectedPinLength == 0 && row != null) {
            val targetCount = maxOf(4, pinBuffer.length)
            while (pinDots.size < targetCount) {
                val extra = createPinDotView()
                pinDots.add(extra)
                row.addView(extra)
            }
            while (pinDots.size > targetCount) {
                val removed = pinDots.removeAt(pinDots.lastIndex)
                row.removeView(removed)
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

    private fun buildPatternUnlockUi(container: LinearLayout) {
        val hintTv = TextView(this).apply {
            text = "আনলক করতে আপনার প্যাটার্ন আঁকুন"
            textSize = 13f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@LockOverlayActivity, 12))
        }
        container.addView(hintTv)

        val patternView = PatternLockView(this).apply {
            background = UiStyleHelper.cardGradientBox(
                this@LockOverlayActivity,
                24,
                UiStyleHelper.BORDER_GLOW
            )
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 16),
                UiStyleHelper.dp(this@LockOverlayActivity, 16),
                UiStyleHelper.dp(this@LockOverlayActivity, 16),
                UiStyleHelper.dp(this@LockOverlayActivity, 16)
            )
        }
        patternView.listener = object : PatternLockView.Listener {
            override fun onPatternComplete(pattern: String) {
                if (tryVerifySilent(pattern)) {
                    completeUnlock()
                } else {
                    errorTv.text = "ভুল প্যাটার্ন! আবার চেষ্টা করুন"
                    patternView.showErrorAndReset()
                }
            }
        }
        container.addView(patternView)
    }

    private fun buildPasswordUnlockUi(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(
                this@LockOverlayActivity,
                22,
                UiStyleHelper.BORDER_GLOW
            )
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 20)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val input = EditText(this).apply {
            hint = if (lockMode == MODE_LIMIT_REACHED) "ভল্ট মাস্টার পাসওয়ার্ড" else "পাসওয়ার্ড লিখুন"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setHintTextColor(UiStyleHelper.TEXT_MUTED)
            background = UiStyleHelper.roundedBox(
                this@LockOverlayActivity,
                UiStyleHelper.BG_ELEVATED,
                14,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 16),
                UiStyleHelper.dp(this@LockOverlayActivity, 14),
                UiStyleHelper.dp(this@LockOverlayActivity, 16),
                UiStyleHelper.dp(this@LockOverlayActivity, 14)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = UiStyleHelper.dp(this@LockOverlayActivity, 14)
            }
        }
        passwordInputView = input

        val unlockBtn = UiStyleHelper.actionButton(
            this,
            "আনলক করুন",
            UiStyleHelper.ACCENT_BLUE,
            UiStyleHelper.ACCENT_BLUE_DEEP
        ) {
            verifyAndUnlock(input.text.toString())
        }

        card.addView(input)
        card.addView(unlockBtn)
        container.addView(card)
    }

    private fun tryVerifySilent(secret: String): Boolean {
        return if (lockMode == MODE_LIMIT_REACHED) {
            prefs.verifyVaultPassword(secret)
        } else {
            prefs.verifyAppUnlock(targetPkg, secret)
        }
    }

    private fun verifyAndUnlock(secret: String) {
        if (tryVerifySilent(secret)) {
            completeUnlock()
        } else {
            errorTv.text = "ভুল পাসওয়ার্ড / পিন! আবার চেষ্টা করুন"
            pinBuffer.clear()
            updatePinDots()
        }
    }

    private fun completeUnlock() {
        hideSoftKeyboard()
        biometricPromptActive = false
        foregroundActive = false
        currentTargetPkg = null
        if (lockMode == MODE_LIMIT_REACHED) {
            prefs.grantTemporaryUnlock("${targetPkg}_limit_override", 10 * 60 * 1000L)
        } else {
            AppLockAccessibilityService.markSessionUnlocked(targetPkg)
            if (prefs.getRelockMode() != PrefsManager.RELOCK_IMMEDIATE) {
                prefs.grantTemporaryUnlock(targetPkg, 3 * 60 * 1000L)
            }
        }
        finishAndRemoveTask()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        val res = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return res == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun triggerBiometricPrompt(appLabel: String) {
        biometricPromptActive = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricPromptActive = false
                    completeUnlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricPromptActive = false
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("$appLabel আনলক করুন")
            .setSubtitle("ফিঙ্গারপ্রিন্ট বা ফেস স্পর্শ করুন")
            .setNegativeButtonText("পিন ব্যবহার করুন")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun goHome() {
        hideSoftKeyboard()
        biometricPromptActive = false
        foregroundActive = false
        currentTargetPkg = null
        AppLockAccessibilityService.clearOverlayState()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
