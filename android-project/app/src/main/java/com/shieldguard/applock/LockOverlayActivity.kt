package com.shieldguard.applock

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
        private var currentTargetPkg: String? = null

        fun isForegroundFor(pkg: String): Boolean {
            return foregroundActive && currentTargetPkg == pkg
        }
    }

    private lateinit var prefs: PrefsManager
    private var targetPkg: String = ""
    private var lockMode: String = MODE_APP_LOCK
    private var pinBuffer = StringBuilder()
    private val pinDots = mutableListOf<View>()
    private lateinit var errorTv: TextView
    private var biometricPromptedThisView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        window.statusBarColor = UiStyleHelper.BG_CANVAS
        window.navigationBarColor = UiStyleHelper.BG_CANVAS

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
    }

    override fun onPause() {
        foregroundActive = false
        super.onPause()
    }

    override fun onDestroy() {
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

    private fun handleIntent(currentIntent: Intent) {
        targetPkg = currentIntent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        currentTargetPkg = targetPkg
        lockMode = currentIntent.getStringExtra(EXTRA_MODE) ?: MODE_APP_LOCK
        val usedMins = currentIntent.getIntExtra(EXTRA_USED_MINUTES, 0)
        val limitMins = currentIntent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        pinBuffer.clear()
        biometricPromptedThisView = false

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

        val biometricAllowed = lockMode != MODE_LIMIT_REACHED &&
            prefs.isBiometricAllowedForApp(targetPkg) &&
            canUseBiometric()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(UiStyleHelper.BG_CANVAS)
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 22),
                UiStyleHelper.dp(this@LockOverlayActivity, 44),
                UiStyleHelper.dp(this@LockOverlayActivity, 22),
                UiStyleHelper.dp(this@LockOverlayActivity, 28)
            )
        }

        // Sleek App Header Card
        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiStyleHelper.roundedBox(
                this@LockOverlayActivity,
                UiStyleHelper.BG_CARD,
                24,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 22),
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 20)
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
                    20,
                    UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                setPadding(
                    UiStyleHelper.dp(this@LockOverlayActivity, 12),
                    UiStyleHelper.dp(this@LockOverlayActivity, 12),
                    UiStyleHelper.dp(this@LockOverlayActivity, 12),
                    UiStyleHelper.dp(this@LockOverlayActivity, 12)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiStyleHelper.dp(this@LockOverlayActivity, 12) }
            }
            val iconView = ImageView(this).apply {
                setImageDrawable(appIcon)
                layoutParams = LinearLayout.LayoutParams(
                    UiStyleHelper.dp(this@LockOverlayActivity, 52),
                    UiStyleHelper.dp(this@LockOverlayActivity, 52)
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
            if (lockMode == MODE_LIMIT_REACHED) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.ACCENT_BLUE
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiStyleHelper.dp(this@LockOverlayActivity, 8) }
        }
        headerCard.addView(badge)

        val titleTv = TextView(this).apply {
            text = appLabel
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        }
        headerCard.addView(titleTv)

        val subtitleText = when {
            lockMode == MODE_LIMIT_REACHED ->
                "আজ ব্যবহার হয়েছে $usedMins মি. (সীমা $limitMins মি.)\nচালিয়ে যেতে ভল্ট মাস্টার লক দিন"
            prefs.hasCustomAppLock(targetPkg) ->
                "এই অ্যাপের জন্য আপনার সেট করা আলাদা লক দিন"
            else ->
                "প্রবেশ করতে আপনার লক যাচাই করুন"
        }

        val subTv = TextView(this).apply {
            text = subtitleText
            textSize = 13f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 4), 0, 0)
        }
        headerCard.addView(subTv)
        root.addView(headerCard)

        errorTv = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(UiStyleHelper.ACCENT_ROSE)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 12), 0, UiStyleHelper.dp(this@LockOverlayActivity, 4))
        }
        root.addView(errorTv)

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
        root.addView(unlockArea)

        // Bottom Action Row (Biometric only when explicitly allowed + Home Button)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (biometricAllowed) {
            val bioBtn = UiStyleHelper.actionButton(
                this,
                "ফিঙ্গারপ্রিন্ট / ফেস",
                UiStyleHelper.ACCENT_BLUE,
                UiStyleHelper.ACCENT_BLUE_PRESSED,
                UiStyleHelper.TEXT_PRIMARY,
                null,
                48
            ) {
                triggerBiometricPrompt(appLabel)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@LockOverlayActivity, 48), 1f).apply {
                    rightMargin = UiStyleHelper.dp(this@LockOverlayActivity, 8)
                }
            }
            bottomRow.addView(bioBtn)
        }

        val homeBtn = UiStyleHelper.actionButton(
            this,
            "হোম স্ক্রিনে ফিরুন",
            UiStyleHelper.BG_ELEVATED,
            UiStyleHelper.BG_PRESSED,
            UiStyleHelper.TEXT_SECONDARY,
            UiStyleHelper.BORDER_SUBTLE,
            48
        ) {
            goHome()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@LockOverlayActivity, 48), 1f)
        }
        bottomRow.addView(homeBtn)
        root.addView(bottomRow)

        setContentView(root)

        if (biometricAllowed && !biometricPromptedThisView) {
            biometricPromptedThisView = true
            root.postDelayed({
                if (!isFinishing && !isDestroyed && foregroundActive) {
                    triggerBiometricPrompt(appLabel)
                }
            }, 180)
        }
    }

    private fun lockTypeBengali(type: String): String {
        return when (type) {
            PrefsManager.TYPE_PATTERN -> "প্যাটার্ন"
            PrefsManager.TYPE_PASSWORD -> "পাসওয়ার্ড"
            else -> "পিন"
        }
    }

    private fun buildPinUnlockUi(container: LinearLayout) {
        pinDots.clear()
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiStyleHelper.dp(this@LockOverlayActivity, 22))
        }
        for (i in 0 until 6) {
            val dot = View(this).apply {
                background = UiStyleHelper.circleBox(
                    this@LockOverlayActivity,
                    UiStyleHelper.BG_CARD,
                    UiStyleHelper.BORDER_SUBTLE,
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

        val btnSize = UiStyleHelper.dp(this, 68)
        val margin = UiStyleHelper.dp(this, 10)

        for (rowKeys in keys) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (label in rowKeys) {
                val isConfirm = label == "✓"
                val keyBtn = TextView(this).apply {
                    text = label
                    textSize = if (label == "⌫" || isConfirm) 20f else 22f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                    background = UiStyleHelper.touchableCircleBox(
                        this@LockOverlayActivity,
                        if (isConfirm) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                        if (isConfirm) UiStyleHelper.ACCENT_BLUE_PRESSED else UiStyleHelper.BG_PRESSED,
                        if (isConfirm) null else UiStyleHelper.BORDER_SUBTLE,
                        1
                    )
                    layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                        setMargins(margin, margin / 2, margin, margin / 2)
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
                                if (pinBuffer.length < 6) {
                                    pinBuffer.append(label)
                                    updatePinDots()
                                    if (pinBuffer.length >= 4 && tryVerifySilent(pinBuffer.toString())) {
                                        completeUnlock()
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
        for (i in pinDots.indices) {
            val active = i < pinBuffer.length
            pinDots[i].background = UiStyleHelper.circleBox(
                this,
                if (active) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                if (active) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_SUBTLE,
                2
            )
        }
    }

    private fun buildPatternUnlockUi(container: LinearLayout) {
        val hintTv = TextView(this).apply {
            text = "আনলক করতে আপনার প্যাটার্ন আঁকুন"
            textSize = 13f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@LockOverlayActivity, 14))
        }
        container.addView(hintTv)

        val patternView = PatternLockView(this).apply {
            background = UiStyleHelper.roundedBox(
                this@LockOverlayActivity,
                UiStyleHelper.BG_CARD,
                24,
                UiStyleHelper.BORDER_SUBTLE,
                1
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
            background = UiStyleHelper.roundedBox(
                this@LockOverlayActivity,
                UiStyleHelper.BG_CARD,
                22,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 20),
                UiStyleHelper.dp(this@LockOverlayActivity, 22),
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

        val unlockBtn = UiStyleHelper.actionButton(
            this,
            "আনলক করুন",
            UiStyleHelper.ACCENT_BLUE,
            UiStyleHelper.ACCENT_BLUE_PRESSED
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
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    completeUnlock()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("$appLabel আনলক করুন")
            .setSubtitle("ফিঙ্গারপ্রিন্ট বা ফেস আনলক ব্যবহার করুন")
            .setNegativeButtonText("পিন / প্যাটার্ন ব্যবহার করুন")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun goHome() {
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
