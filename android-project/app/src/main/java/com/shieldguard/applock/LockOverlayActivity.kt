package com.shieldguard.applock

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
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
    }

    private lateinit var prefs: PrefsManager
    private var targetPkg: String = ""
    private var lockMode: String = MODE_APP_LOCK
    private var pinBuffer = StringBuilder()
    private val pinDots = mutableListOf<View>()
    private lateinit var errorTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(currentIntent: Intent) {
        targetPkg = currentIntent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        lockMode = currentIntent.getStringExtra(EXTRA_MODE) ?: MODE_APP_LOCK
        val usedMins = currentIntent.getIntExtra(EXTRA_USED_MINUTES, 0)
        val limitMins = currentIntent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        pinBuffer.clear()

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        val effectiveType = if (lockMode == MODE_LIMIT_REACHED) {
            prefs.getVaultLockType()
        } else {
            prefs.getEffectiveLockType(targetPkg)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(UiStyleHelper.BG_CANVAS)
            setPadding(
                UiStyleHelper.dp(this@LockOverlayActivity, 24),
                UiStyleHelper.dp(this@LockOverlayActivity, 48),
                UiStyleHelper.dp(this@LockOverlayActivity, 24),
                UiStyleHelper.dp(this@LockOverlayActivity, 32)
            )
        }

        // Top App Header Card
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
                UiStyleHelper.dp(this@LockOverlayActivity, 24),
                UiStyleHelper.dp(this@LockOverlayActivity, 24),
                UiStyleHelper.dp(this@LockOverlayActivity, 24),
                UiStyleHelper.dp(this@LockOverlayActivity, 20)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (appIcon != null) {
            val iconView = ImageView(this).apply {
                setImageDrawable(appIcon)
                layoutParams = LinearLayout.LayoutParams(
                    UiStyleHelper.dp(this@LockOverlayActivity, 64),
                    UiStyleHelper.dp(this@LockOverlayActivity, 64)
                ).apply {
                    bottomMargin = UiStyleHelper.dp(this@LockOverlayActivity, 12)
                }
            }
            headerCard.addView(iconView)
        }

        val titleTv = TextView(this).apply {
            text = if (lockMode == MODE_LIMIT_REACHED) {
                "$appLabel · সময়সীমা শেষ"
            } else {
                appLabel
            }
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        }
        headerCard.addView(titleTv)

        val subtitleText = when {
            lockMode == MODE_LIMIT_REACHED ->
                "আজকের ব্যবহার: $usedMins মি. / লিমিট: $limitMins মি.\nখুলতে ভল্ট মাস্টার লক দিন"
            prefs.hasCustomAppLock(targetPkg) ->
                "এই অ্যাপের নিজস্ব আলাদা লক সেট করা আছে"
            prefs.isSeparateAppLockEnabled() ->
                "আনলক করতে আপনার অ্যাপ-লক দিন"
            else ->
                "আনলক করতে আপনার ভল্ট লক দিন"
        }

        val subTv = TextView(this).apply {
            text = subtitleText
            textSize = 13f
            setTextColor(
                if (lockMode == MODE_LIMIT_REACHED) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.TEXT_SECONDARY
            )
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 6), 0, 0)
        }
        headerCard.addView(subTv)
        root.addView(headerCard)

        errorTv = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(UiStyleHelper.ACCENT_ROSE)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@LockOverlayActivity, 14), 0, UiStyleHelper.dp(this@LockOverlayActivity, 6))
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

        // Bottom Controls (Biometric + Go Home)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (canUseBiometric() && prefs.isBiometricEnabled() && lockMode != MODE_LIMIT_REACHED) {
            val bioBtn = Button(this).apply {
                text = "ফিঙ্গার / ফেস"
                textSize = 13f
                isAllCaps = false
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                background = UiStyleHelper.roundedBox(
                    this@LockOverlayActivity,
                    UiStyleHelper.ACCENT_BLUE,
                    14
                )
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@LockOverlayActivity, 48), 1f).apply {
                    rightMargin = UiStyleHelper.dp(this@LockOverlayActivity, 8)
                }
                setOnClickListener { triggerBiometricPrompt(appLabel) }
            }
            bottomRow.addView(bioBtn)
        }

        val homeBtn = Button(this).apply {
            text = "হোম স্ক্রিনে ফিরুন"
            textSize = 13f
            isAllCaps = false
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            background = UiStyleHelper.roundedBox(
                this@LockOverlayActivity,
                UiStyleHelper.BG_ELEVATED,
                14,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@LockOverlayActivity, 48), 1f)
            setOnClickListener { goHome() }
        }
        bottomRow.addView(homeBtn)
        root.addView(bottomRow)

        setContentView(root)

        if (canUseBiometric() && prefs.isBiometricEnabled() && lockMode != MODE_LIMIT_REACHED) {
            root.postDelayed({ triggerBiometricPrompt(appLabel) }, 180)
        }
    }

    private fun buildPinUnlockUi(container: LinearLayout) {
        pinDots.clear()
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiStyleHelper.dp(this@LockOverlayActivity, 24))
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
                    UiStyleHelper.dp(this@LockOverlayActivity, 16),
                    UiStyleHelper.dp(this@LockOverlayActivity, 16)
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
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                    background = UiStyleHelper.circleBox(
                        this@LockOverlayActivity,
                        if (isConfirm) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
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
            setPadding(0, 0, 0, UiStyleHelper.dp(this@LockOverlayActivity, 12))
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
                20,
                UiStyleHelper.BORDER_SUBTLE,
                1
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
            setHintTextColor(UiStyleHelper.TEXT_SECONDARY)
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

        val unlockBtn = Button(this).apply {
            text = "আনলক করুন"
            isAllCaps = false
            textSize = 15f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(
                this@LockOverlayActivity,
                UiStyleHelper.ACCENT_BLUE,
                14
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyleHelper.dp(this@LockOverlayActivity, 50)
            )
            setOnClickListener { verifyAndUnlock(input.text.toString()) }
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
        if (lockMode == MODE_LIMIT_REACHED) {
            prefs.grantTemporaryUnlock("${targetPkg}_limit_override", 10 * 60 * 1000L)
        } else {
            AppLockAccessibilityService.markSessionUnlocked(targetPkg)
            if (prefs.getRelockMode() != PrefsManager.RELOCK_IMMEDIATE) {
                prefs.grantTemporaryUnlock(targetPkg, 3 * 60 * 1000L)
            }
        }
        finish()
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
        AppLockAccessibilityService.clearOverlayState()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
