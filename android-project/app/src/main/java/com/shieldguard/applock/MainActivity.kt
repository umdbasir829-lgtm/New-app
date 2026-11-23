package com.shieldguard.applock

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private var vaultUnlocked = false
    private var hasCheckedUpdateThisSession = false
    private var pendingUpdateResult: UpdateCheckResult? = null
    private var currentTab = 0 // 0 = Apps, 1 = Security, 2 = Updates
    private var currentFilter = "ALL" // ALL | LOCKED | LIMITED
    private var searchQuery = ""
    private var appsListContainer: LinearLayout? = null
    private var allInstalledApps: List<InstalledAppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        UiStyleHelper.applyRuntimeTheme(prefs)
        window.statusBarColor = UiStyleHelper.BG_CANVAS_TOP
        window.navigationBarColor = UiStyleHelper.BG_SURFACE

        if (!prefs.isVaultPasswordSet()) {
            showInitialSetup()
        } else if (!vaultUnlocked) {
            showVaultLogin()
        } else {
            showDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        if (vaultUnlocked) {
            showDashboard()
        }
    }

    private fun lockTypeBn(type: String): String {
        return when (type) {
            PrefsManager.TYPE_PATTERN -> "প্যাটার্ন"
            PrefsManager.TYPE_PASSWORD -> "পাসওয়ার্ড"
            else -> "পিন"
        }
    }

    // -------------------------------------------------------------------------
    // 1. INITIAL SETUP (PREMIUM GRADIENT CARDS + EXPLICIT BIOMETRIC OPT-IN)
    // -------------------------------------------------------------------------
    private fun showInitialSetup() {
        val scroll = ScrollView(this).apply { background = UiStyleHelper.canvasGradient() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 20),
                UiStyleHelper.dp(this@MainActivity, 32),
                UiStyleHelper.dp(this@MainActivity, 20),
                UiStyleHelper.dp(this@MainActivity, 32)
            )
        }

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 22, UiStyleHelper.BORDER_GLOW)
            setPadding(UiStyleHelper.dp(this@MainActivity, 20), UiStyleHelper.dp(this@MainActivity, 22), UiStyleHelper.dp(this@MainActivity, 20), UiStyleHelper.dp(this@MainActivity, 20))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 16)
            }
        }
        heroCard.addView(UiStyleHelper.shieldBadgeView(this, true, 52).apply {
            layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 52), UiStyleHelper.dp(this@MainActivity, 52)).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 10)
            }
        })
        heroCard.addView(TextView(this).apply {
            text = "ShieldGuard সুরক্ষা সেটআপ"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        })
        heroCard.addView(TextView(this).apply {
            text = "ভল্ট ও অ্যাপের জন্য পিন, প্যাটার্ন বা পাসওয়ার্ড সেট করুন"
            textSize = 12.5f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, 0)
        })
        root.addView(heroCard)

        var chosenVaultSecret = ""
        var chosenVaultType = PrefsManager.TYPE_PIN
        var chosenAppSecret = ""
        var chosenAppType = PrefsManager.TYPE_PIN

        val vaultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 18)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 12)
            }
        }
        val vaultStatusTv = TextView(this).apply {
            text = "১) ভল্ট মাস্টার লক: এখনো সেট হয়নি"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 10))
        }
        val setVaultBtn = UiStyleHelper.actionButton(
            this,
            "ভল্ট লক সেট করুন (পিন / প্যাটার্ন / পাসওয়ার্ড)"
        ) {
            openCredentialCreatorDialog("ভল্ট লক সেট করুন") { secret, type ->
                chosenVaultSecret = secret
                chosenVaultType = type
                vaultStatusTv.text = "১) ভল্ট লক সেট হয়েছে (${lockTypeBn(type)})"
                vaultStatusTv.setTextColor(UiStyleHelper.ACCENT_GREEN)
            }
        }
        vaultCard.addView(vaultStatusTv)
        vaultCard.addView(setVaultBtn)
        root.addView(vaultCard)

        val appLockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 18)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 12)
            }
        }
        val separateSwitch = SwitchCompat(this).apply {
            text = "অ্যাপ লকের জন্য ভল্ট থেকে আলাদা লক রাখুন"
            isChecked = true
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            textSize = 13.5f
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 10))
        }
        val appStatusTv = TextView(this).apply {
            text = "২) অ্যাপ-লক: এখনো সেট হয়নি"
            textSize = 13.5f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 10))
        }
        val setAppLockBtn = UiStyleHelper.actionButton(
            this,
            "আলাদা অ্যাপ-লক সেট করুন",
            UiStyleHelper.BG_ELEVATED,
            UiStyleHelper.BG_PRESSED,
            UiStyleHelper.TEXT_PRIMARY,
            UiStyleHelper.BORDER_SUBTLE
        ) {
            openCredentialCreatorDialog("আলাদা অ্যাপ-লক সেট করুন") { secret, type ->
                chosenAppSecret = secret
                chosenAppType = type
                appStatusTv.text = "২) অ্যাপ-লক সেট হয়েছে (${lockTypeBn(type)})"
                appStatusTv.setTextColor(UiStyleHelper.ACCENT_GREEN)
            }
        }
        separateSwitch.setOnCheckedChangeListener { _, checked ->
            appStatusTv.visibility = if (checked) View.VISIBLE else View.GONE
            setAppLockBtn.visibility = if (checked) View.VISIBLE else View.GONE
        }
        appLockCard.addView(separateSwitch)
        appLockCard.addView(appStatusTv)
        appLockCard.addView(setAppLockBtn)
        root.addView(appLockCard)

        val bioCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 18)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 18)
            }
        }
        val bioSwitch = SwitchCompat(this).apply {
            text = "ফিঙ্গারপ্রিন্ট / ফেস আনলক চালু রাখুন"
            isChecked = false
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            textSize = 13.5f
        }
        bioCard.addView(bioSwitch)
        bioCard.addView(TextView(this).apply {
            text = "চালু থাকলে লক স্ক্রিনের নিচে ছোট ফিঙ্গারপ্রিন্ট আইকন দেখাবে (টাচ করলেই খুলবে)।"
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, 0)
        })
        root.addView(bioCard)

        val finishBtn = UiStyleHelper.actionButton(
            this,
            "সেটআপ সম্পন্ন করে ভেতরে যান",
            UiStyleHelper.ACCENT_GREEN,
            UiStyleHelper.ACCENT_GREEN,
            UiStyleHelper.TEXT_PRIMARY,
            null,
            50
        ) {
            if (chosenVaultSecret.isBlank()) {
                Toast.makeText(this@MainActivity, "প্রথমে ভল্ট লক সেট করুন", Toast.LENGTH_SHORT).show()
                return@actionButton
            }
            if (separateSwitch.isChecked && chosenAppSecret.isBlank()) {
                Toast.makeText(this@MainActivity, "আলাদা অ্যাপ-লক সেট করুন", Toast.LENGTH_SHORT).show()
                return@actionButton
            }
            prefs.setVaultCredential(chosenVaultSecret, chosenVaultType)
            prefs.setSeparateAppLockEnabled(separateSwitch.isChecked)
            if (separateSwitch.isChecked) {
                prefs.setAppLockCredential(chosenAppSecret, chosenAppType)
            }
            prefs.setBiometricEnabled(bioSwitch.isChecked)
            vaultUnlocked = true
            showDashboard()
        }
        root.addView(finishBtn)

        scroll.addView(root)
        setContentView(scroll)
    }

    // -------------------------------------------------------------------------
    // 2. VAULT LOGIN SCREEN (NO CLIPPING, COMPACT BOTTOM FINGERPRINT ICON)
    // -------------------------------------------------------------------------
    private fun showVaultLogin() {
        val lockType = prefs.getVaultLockType()
        val pinBuffer = StringBuilder()
        val dots = mutableListOf<View>()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.canvasGradient()
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
                UiStyleHelper.dp(this@MainActivity, 20),
                UiStyleHelper.dp(this@MainActivity, 28),
                UiStyleHelper.dp(this@MainActivity, 20),
                UiStyleHelper.dp(this@MainActivity, 12)
            )
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 22, UiStyleHelper.BORDER_GLOW)
            setPadding(UiStyleHelper.dp(this@MainActivity, 20), UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 20), UiStyleHelper.dp(this@MainActivity, 18))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 10)
            }
        }
        headerCard.addView(UiStyleHelper.shieldBadgeView(this, true, 46).apply {
            layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 46), UiStyleHelper.dp(this@MainActivity, 46)).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 8)
            }
        })
        headerCard.addView(TextView(this).apply {
            text = "ShieldGuard ভল্ট"
            textSize = 19.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        })
        headerCard.addView(TextView(this).apply {
            text = "প্রবেশ করতে ভল্ট ${lockTypeBn(lockType)} দিন"
            textSize = 12.5f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 3), 0, 0)
        })
        contentCol.addView(headerCard)

        val errTv = TextView(this).apply {
            text = ""
            textSize = 12.5f
            setTextColor(UiStyleHelper.ACCENT_ROSE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 6))
        }
        contentCol.addView(errTv)

        val centerArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        when (lockType) {
            PrefsManager.TYPE_PATTERN -> {
                val patternView = PatternLockView(this).apply {
                    background = UiStyleHelper.cardGradientBox(this@MainActivity, 24, UiStyleHelper.BORDER_GLOW)
                    setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
                }
                patternView.listener = object : PatternLockView.Listener {
                    override fun onPatternComplete(pattern: String) {
                        if (prefs.verifyVaultPassword(pattern)) {
                            vaultUnlocked = true
                            showDashboard()
                        } else {
                            errTv.text = "ভুল প্যাটার্ন! আবার চেষ্টা করুন"
                            patternView.showErrorAndReset()
                        }
                    }
                }
                centerArea.addView(patternView)
            }
            PrefsManager.TYPE_PASSWORD -> {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = UiStyleHelper.cardGradientBox(this@MainActivity, 22, UiStyleHelper.BORDER_GLOW)
                    setPadding(UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 18))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val input = EditText(this).apply {
                    hint = "ভল্ট পাসওয়ার্ড লিখুন"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                    setHintTextColor(UiStyleHelper.TEXT_MUTED)
                    background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_ELEVATED, 14, UiStyleHelper.BORDER_SUBTLE, 1)
                    setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 14))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
                    }
                }
                val btn = UiStyleHelper.actionButton(this, "ভল্ট আনলক করুন") {
                    if (prefs.verifyVaultPassword(input.text.toString())) {
                        vaultUnlocked = true
                        showDashboard()
                    } else {
                        errTv.text = "ভুল ভল্ট পাসওয়ার্ড!"
                    }
                }
                card.addView(input)
                card.addView(btn)
                centerArea.addView(card)
            }
            else -> {
                val dotsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 16))
                }
                for (i in 0 until 6) {
                    val dot = View(this).apply {
                        background = UiStyleHelper.circleBox(this@MainActivity, UiStyleHelper.BG_CARD, UiStyleHelper.BORDER_GLOW, 2)
                        layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 13), UiStyleHelper.dp(this@MainActivity, 13)).apply {
                            setMargins(UiStyleHelper.dp(this@MainActivity, 7), 0, UiStyleHelper.dp(this@MainActivity, 7), 0)
                        }
                    }
                    dots.add(dot)
                    dotsRow.addView(dot)
                }
                centerArea.addView(dotsRow)

                val updateDots = {
                    for (i in dots.indices) {
                        val on = i < pinBuffer.length
                        dots[i].background = UiStyleHelper.circleBox(
                            this@MainActivity,
                            if (on) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                            if (on) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_GLOW,
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
                val size = if (screenH < UiStyleHelper.dp(this, 780)) UiStyleHelper.dp(this, 56) else UiStyleHelper.dp(this, 60)
                val gapH = UiStyleHelper.dp(this, 10)
                val gapV = UiStyleHelper.dp(this, 4)

                for (r in rows) {
                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    for (k in r) {
                        val isConfirm = k == "✓"
                        val tv = TextView(this).apply {
                            text = k
                            textSize = if (k == "⌫" || isConfirm) 19f else 21f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            gravity = Gravity.CENTER
                            isClickable = true
                            isFocusable = true
                            setTextColor(UiStyleHelper.TEXT_PRIMARY)
                            background = UiStyleHelper.touchableCircleBox(
                                this@MainActivity,
                                if (isConfirm) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                                if (isConfirm) UiStyleHelper.ACCENT_BLUE_DEEP else UiStyleHelper.BG_PRESSED,
                                if (isConfirm) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_GLOW,
                                1
                            )
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                setMargins(gapH, gapV, gapH, gapV)
                            }
                            setOnClickListener {
                                when (k) {
                                    "⌫" -> {
                                        if (pinBuffer.isNotEmpty()) pinBuffer.deleteCharAt(pinBuffer.length - 1)
                                        updateDots()
                                    }
                                    "✓" -> {
                                        if (prefs.verifyVaultPassword(pinBuffer.toString())) {
                                            vaultUnlocked = true
                                            showDashboard()
                                        } else {
                                            errTv.text = "ভুল ভল্ট পিন!"
                                            pinBuffer.clear()
                                            updateDots()
                                        }
                                    }
                                    else -> {
                                        if (pinBuffer.length < 6) {
                                            pinBuffer.append(k)
                                            updateDots()
                                            if (pinBuffer.length >= 4 && prefs.verifyVaultPassword(pinBuffer.toString())) {
                                                vaultUnlocked = true
                                                showDashboard()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        rowLayout.addView(tv)
                    }
                    centerArea.addView(rowLayout)
                }
            }
        }
        contentCol.addView(centerArea)
        scroll.addView(contentCol)
        root.addView(scroll)

        // Compact Fingerprint Circle at bottom (tap to unlock, never auto-pops!)
        if (canUseBiometric() && prefs.isBiometricEnabled()) {
            val bioRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 8), 0, UiStyleHelper.dp(this@MainActivity, 20))
            }
            val bioCircle = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = UiStyleHelper.touchableCircleBox(
                    this@MainActivity,
                    UiStyleHelper.ACCENT_BLUE_SOFT,
                    UiStyleHelper.BG_PRESSED,
                    UiStyleHelper.ACCENT_BLUE,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 52), UiStyleHelper.dp(this@MainActivity, 52))
                setOnClickListener { promptBiometricForVault() }
            }
            bioCircle.addView(UiStyleHelper.fingerprintIconView(this, 26, UiStyleHelper.ACCENT_BLUE))
            bioRow.addView(bioCircle)
            bioRow.addView(TextView(this).apply {
                text = "ফিঙ্গারপ্রিন্ট টাচ করুন"
                textSize = 11f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 5), 0, 0)
            })
            root.addView(bioRow)
        }

        setContentView(root)
    }

    private fun promptBiometricForVault() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    vaultUnlocked = true
                    showDashboard()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ShieldGuard ভল্ট আনলক")
            .setSubtitle("ফিঙ্গারপ্রিন্ট বা ফেস স্পর্শ করুন")
            .setNegativeButtonText("পিন ব্যবহার করুন")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(info)
    }

    // -------------------------------------------------------------------------
    // 3. PREMIUM 3-TAB DASHBOARD (APPS / SECURITY / UPDATES)
    // -------------------------------------------------------------------------
    private fun showDashboard() {
        allInstalledApps = AppDetector.getAllLaunchableApps(this, prefs)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.canvasGradient()
        }

        // Sleek Sticky Header Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiStyleHelper.BG_SURFACE)
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 18),
                UiStyleHelper.dp(this@MainActivity, 14),
                UiStyleHelper.dp(this@MainActivity, 18),
                UiStyleHelper.dp(this@MainActivity, 14)
            )
        }
        topBar.addView(UiStyleHelper.shieldBadgeView(this, AppLockAccessibilityService.isRunning, 34).apply {
            layoutParams = LinearLayout.LayoutParams(
                UiStyleHelper.dp(this@MainActivity, 34),
                UiStyleHelper.dp(this@MainActivity, 34)
            ).apply { rightMargin = UiStyleHelper.dp(this@MainActivity, 10) }
        })

        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandCol.addView(TextView(this).apply {
            text = "ShieldGuard"
            textSize = 18.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
        })
        brandCol.addView(TextView(this).apply {
            text = when (currentTab) {
                1 -> "নিরাপত্তা ও লক নিয়ন্ত্রণ"
                2 -> "অটো আপডেট ও লাইভ ওয়েব"
                else -> "অ্যাপ লক ও দৈনিক সময়সীমা"
            }
            textSize = 11.5f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
        })
        topBar.addView(brandCol)

        val lockVaultChip = UiStyleHelper.chipBadge(
            this,
            "ভল্ট লক",
            UiStyleHelper.BG_ELEVATED,
            UiStyleHelper.TEXT_PRIMARY,
            UiStyleHelper.BORDER_SUBTLE
        ).apply {
            setOnClickListener {
                vaultUnlocked = false
                showVaultLogin()
            }
        }
        topBar.addView(lockVaultChip)
        root.addView(topBar)

        root.addView(View(this).apply {
            setBackgroundColor(UiStyleHelper.BORDER_SUBTLE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 24)
            )
        }

        when (currentTab) {
            0 -> buildAppsTab(body)
            1 -> buildSecurityTab(body)
            else -> buildUpdatesTab(body)
        }

        scroll.addView(body)
        root.addView(scroll)

        // Flagship Vector-Icon Bottom Navigation Bar
        root.addView(View(this).apply {
            setBackgroundColor(UiStyleHelper.BORDER_SUBTLE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(UiStyleHelper.BG_SURFACE)
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 8),
                UiStyleHelper.dp(this@MainActivity, 6),
                UiStyleHelper.dp(this@MainActivity, 8),
                UiStyleHelper.dp(this@MainActivity, 10)
            )
        }
        val navItems = listOf(0 to "অ্যাপস", 1 to "নিরাপত্তা", 2 to "আপডেট")
        for ((tabIdx, title) in navItems) {
            val active = currentTab == tabIdx
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    if (active) UiStyleHelper.ACCENT_BLUE_SOFT else UiStyleHelper.BG_SURFACE,
                    14,
                    if (active) UiStyleHelper.BORDER_GLOW else null,
                    1
                )
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 7), 0, UiStyleHelper.dp(this@MainActivity, 7))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(UiStyleHelper.dp(this@MainActivity, 4), 0, UiStyleHelper.dp(this@MainActivity, 4), 0)
                }
                setOnClickListener {
                    if (currentTab != tabIdx) {
                        currentTab = tabIdx
                        showDashboard()
                    }
                }
            }
            item.addView(UiStyleHelper.navIconView(this, tabIdx, active).apply {
                layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 20), UiStyleHelper.dp(this@MainActivity, 20)).apply {
                    bottomMargin = UiStyleHelper.dp(this@MainActivity, 3)
                }
            })
            item.addView(TextView(this).apply {
                text = title
                textSize = 11.5f
                setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(if (active) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.TEXT_SECONDARY)
                gravity = Gravity.CENTER
            })
            bottomNav.addView(item)
        }
        root.addView(bottomNav)

        setContentView(root)

        if (!hasCheckedUpdateThisSession) {
            hasCheckedUpdateThisSession = true
            autoCheckUpdatesOnLaunch()
        }
    }

    // --- TAB 0: APPS ---
    private fun buildAppsTab(body: LinearLayout) {
        UiStyleHelper.applyRuntimeTheme(prefs)
        val liveNote = prefs.getHomeAnnouncement()
        if (liveNote.isNotBlank()) {
            val noteCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiStyleHelper.cardGradientBox(
                    this@MainActivity,
                    18,
                    UiStyleHelper.ACCENT_GREEN,
                    UiStyleHelper.ACCENT_GREEN_SOFT,
                    UiStyleHelper.BG_CARD
                )
                setPadding(UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = UiStyleHelper.dp(this@MainActivity, 12)
                }
            }
            noteCard.addView(TextView(this).apply {
                text = liveNote
                textSize = 13f
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
            })
            body.addView(noteCard)
        }

        val availableUpdate = pendingUpdateResult
        if (availableUpdate != null && availableUpdate.hasUpdate && availableUpdate.apkDownloadUrl != null) {
            val updateBanner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiStyleHelper.cardGradientBox(
                    this@MainActivity,
                    20,
                    UiStyleHelper.ACCENT_BLUE,
                    UiStyleHelper.ACCENT_BLUE_SOFT,
                    UiStyleHelper.BG_CARD
                )
                setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
                }
            }
            updateBanner.addView(TextView(this).apply {
                text = "নতুন আপডেট প্রস্তুত (${availableUpdate.latestVersionName})"
                textSize = 14.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
            })
            updateBanner.addView(TextView(this).apply {
                text = availableUpdate.changelog.ifBlank { "নতুন ফিচার ও উন্নতি যুক্ত হয়েছে।" }
                textSize = 12f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, UiStyleHelper.dp(this@MainActivity, 10))
            })
            updateBanner.addView(UiStyleHelper.actionButton(this, "এখনই অটো আপডেট ইনস্টল করুন") {
                lifecycleScope.launch {
                    UpdateManager.downloadAndInstallApk(this@MainActivity, availableUpdate.apkDownloadUrl)
                }
            })
            body.addView(updateBanner)
        }

        val lockedCount = allInstalledApps.count { it.isLocked || prefs.hasCustomAppLock(it.packageName) }
        val limitedCount = allInstalledApps.count { it.dailyLimitMinutes > 0 }
        val serviceActive = AppLockAccessibilityService.isRunning
        val allPermsOk = serviceActive && hasUsageStatsPermission() && Settings.canDrawOverlays(this)

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(
                this@MainActivity,
                22,
                if (allPermsOk) UiStyleHelper.BORDER_GLOW else UiStyleHelper.ACCENT_AMBER
            )
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
            }
        }

        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 14))
        }
        heroTop.addView(UiStyleHelper.shieldBadgeView(this, allPermsOk, 40).apply {
            layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 40), UiStyleHelper.dp(this@MainActivity, 40)).apply {
                rightMargin = UiStyleHelper.dp(this@MainActivity, 12)
            }
        })
        val heroTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        heroTextCol.addView(TextView(this).apply {
            text = if (allPermsOk) prefs.getHeroActiveTitle() else "সুরক্ষার জন্য অনুমতি প্রয়োজন"
            textSize = 15.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (allPermsOk) UiStyleHelper.ACCENT_GREEN else UiStyleHelper.ACCENT_AMBER)
        })
        heroTextCol.addView(TextView(this).apply {
            text = "মোট ${allInstalledApps.size}টি অ্যাপ · ফিঙ্গারপ্রিন্ট: ${if (prefs.isBiometricEnabled()) "চালু" else "বন্ধ"}"
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
        })
        heroTop.addView(heroTextCol)
        heroCard.addView(heroTop)

        val relockShort = when (prefs.getRelockMode()) {
            PrefsManager.RELOCK_IMMEDIATE -> "বের হলেই"
            PrefsManager.RELOCK_SCREEN_OFF -> "স্ক্রিন অফ"
            else -> "৩ মি. পর"
        }
        val statsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val stats = listOf(
            Triple("${lockedCount}টি", "লক করা অ্যাপ", UiStyleHelper.ACCENT_BLUE),
            Triple("${limitedCount}টি", "দৈনিক সীমা", UiStyleHelper.ACCENT_GREEN),
            Triple(relockShort, "রি-লক সময়", UiStyleHelper.TEXT_PRIMARY)
        )
        for ((idx, triple) in stats.withIndex()) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_CANVAS, 14, UiStyleHelper.BORDER_SUBTLE, 1)
                setPadding(UiStyleHelper.dp(this@MainActivity, 10), UiStyleHelper.dp(this@MainActivity, 10), UiStyleHelper.dp(this@MainActivity, 10), UiStyleHelper.dp(this@MainActivity, 10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < 2) rightMargin = UiStyleHelper.dp(this@MainActivity, 8)
                }
            }
            box.addView(TextView(this).apply {
                text = triple.first
                textSize = 15.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(triple.third)
            })
            box.addView(TextView(this).apply {
                text = triple.second
                textSize = 11f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 2), 0, 0)
            })
            statsRow.addView(box)
        }
        heroCard.addView(statsRow)

        if (!allPermsOk) {
            val permCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 12), 0, 0)
            }
            if (!hasUsageStatsPermission()) {
                permCol.addView(makePermRow("Usage Access (ব্যবহারের সময়)") {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                })
            }
            if (!Settings.canDrawOverlays(this)) {
                permCol.addView(makePermRow("Display Over Other Apps") {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                })
            }
            if (!serviceActive) {
                permCol.addView(makePermRow("Accessibility Service (অ্যাপ লক)") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }
            heroCard.addView(permCol)
        }
        body.addView(heroCard)

        val searchEt = EditText(this).apply {
            hint = "অ্যাপ খুঁজুন (Facebook, WhatsApp, CapCut...)"
            setText(searchQuery)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setHintTextColor(UiStyleHelper.TEXT_MUTED)
            textSize = 14f
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_CARD, 14, UiStyleHelper.BORDER_SUBTLE, 1)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 12)
            }
        }
        searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                renderAppsList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        body.addView(searchEt)

        val filterTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_SURFACE, 14, UiStyleHelper.BORDER_SUBTLE, 1)
            setPadding(UiStyleHelper.dp(this@MainActivity, 4), UiStyleHelper.dp(this@MainActivity, 4), UiStyleHelper.dp(this@MainActivity, 4), UiStyleHelper.dp(this@MainActivity, 4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
            }
        }
        val filters = listOf("ALL" to "সব অ্যাপ", "LOCKED" to "লক করা", "LIMITED" to "সময়সীমা")
        for ((key, label) in filters) {
            val active = currentFilter == key
            filterTrack.addView(TextView(this).apply {
                text = label
                textSize = 12.5f
                setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setTextColor(if (active) UiStyleHelper.TEXT_PRIMARY else UiStyleHelper.TEXT_SECONDARY)
                background = if (active) UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 10) else null
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@MainActivity, 36), 1f)
                setOnClickListener {
                    currentFilter = key
                    showDashboard()
                }
            })
        }
        body.addView(filterTrack)

        val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsListContainer = listHolder
        body.addView(listHolder)
        renderAppsList()
    }

    private fun makePermRow(title: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_CANVAS, 12, UiStyleHelper.BORDER_SUBTLE, 1)
            setPadding(UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 10), UiStyleHelper.dp(this@MainActivity, 10), UiStyleHelper.dp(this@MainActivity, 10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 8)
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 12.5f
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(UiStyleHelper.chipBadge(
                this@MainActivity,
                "অনুমতি দিন →",
                UiStyleHelper.ACCENT_BLUE,
                UiStyleHelper.TEXT_PRIMARY
            ).apply {
                setOnClickListener { onClick() }
            })
        }
    }

    private fun renderAppsList() {
        val container = appsListContainer ?: return
        container.removeAllViews()
        val filtered = allInstalledApps.filter { app ->
            val matchSearch = searchQuery.isEmpty() ||
                app.appName.lowercase().contains(searchQuery) ||
                app.packageName.lowercase().contains(searchQuery)
            val hasCustom = prefs.hasCustomAppLock(app.packageName)
            val matchFilter = when (currentFilter) {
                "LOCKED" -> app.isLocked || hasCustom
                "LIMITED" -> app.dailyLimitMinutes > 0
                else -> true
            }
            matchSearch && matchFilter
        }

        if (filtered.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "এই তালিকায় কোনো অ্যাপ পাওয়া যায়নি"
                textSize = 14f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 32), 0, 0)
            })
            return
        }

        for (app in filtered) {
            val hasCustom = prefs.hasCustomAppLock(app.packageName)
            val customCred = prefs.getCustomAppLock(app.packageName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                background = UiStyleHelper.touchableRoundedBox(
                    this@MainActivity,
                    UiStyleHelper.BG_CARD,
                    UiStyleHelper.BG_ELEVATED,
                    16,
                    UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                setPadding(
                    UiStyleHelper.dp(this@MainActivity, 12),
                    UiStyleHelper.dp(this@MainActivity, 11),
                    UiStyleHelper.dp(this@MainActivity, 12),
                    UiStyleHelper.dp(this@MainActivity, 11)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 10) }
                setOnClickListener { showAppOptionsSheet(app) }
            }

            val iconFrame = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_ELEVATED, 13, UiStyleHelper.BORDER_SUBTLE, 1)
                setPadding(UiStyleHelper.dp(this@MainActivity, 6), UiStyleHelper.dp(this@MainActivity, 6), UiStyleHelper.dp(this@MainActivity, 6), UiStyleHelper.dp(this@MainActivity, 6))
                layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 46), UiStyleHelper.dp(this@MainActivity, 46))
            }
            iconFrame.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(UiStyleHelper.dp(this@MainActivity, 34), UiStyleHelper.dp(this@MainActivity, 34))
            })

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiStyleHelper.dp(this@MainActivity, 12), 0, UiStyleHelper.dp(this@MainActivity, 6), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text = app.appName
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                maxLines = 1
            })

            val badgesRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, 0)
            }
            badgesRow.addView(TextView(this).apply {
                text = "আজ: ${app.usedMinutesToday} মি."
                textSize = 11.5f
                setSingleLine(true)
                maxLines = 1
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, 0, UiStyleHelper.dp(this@MainActivity, 6), 0)
            })
            if (app.dailyLimitMinutes > 0) {
                val exceeded = app.usedMinutesToday >= app.dailyLimitMinutes
                badgesRow.addView(UiStyleHelper.chipBadge(
                    this,
                    "${app.dailyLimitMinutes} মি.",
                    if (exceeded) UiStyleHelper.ACCENT_ROSE_SOFT else UiStyleHelper.ACCENT_GREEN_SOFT,
                    if (exceeded) UiStyleHelper.ACCENT_ROSE else UiStyleHelper.ACCENT_GREEN
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = UiStyleHelper.dp(this@MainActivity, 5) }
                })
            }
            if (customCred != null) {
                badgesRow.addView(UiStyleHelper.chipBadge(
                    this,
                    "নিজস্ব ${lockTypeBn(customCred.type)}",
                    UiStyleHelper.ACCENT_BLUE_SOFT,
                    UiStyleHelper.ACCENT_BLUE
                ))
            }
            infoCol.addView(badgesRow)

            // Clean vector sliders icon button
            val configBtn = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = UiStyleHelper.touchableRoundedBox(
                    this@MainActivity,
                    UiStyleHelper.BG_ELEVATED,
                    UiStyleHelper.BG_PRESSED,
                    10,
                    UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(
                    UiStyleHelper.dp(this@MainActivity, 36),
                    UiStyleHelper.dp(this@MainActivity, 36)
                ).apply { rightMargin = UiStyleHelper.dp(this@MainActivity, 8) }
                setOnClickListener { showAppOptionsSheet(app) }
            }
            configBtn.addView(UiStyleHelper.slidersIconView(this, 17, UiStyleHelper.TEXT_SECONDARY))

            val lockSwitch = SwitchCompat(this).apply {
                isChecked = app.isLocked || hasCustom
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.setAppLocked(app.packageName, isChecked)
                    if (!isChecked) {
                        prefs.removeCustomAppLock(app.packageName)
                    }
                }
            }

            card.addView(iconFrame)
            card.addView(infoCol)
            card.addView(configBtn)
            card.addView(lockSwitch)
            container.addView(card)
        }
    }

    // --- TAB 1: SECURITY ---
    private fun buildSecurityTab(body: LinearLayout) {
        val lockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 20)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
            }
        }
        lockCard.addView(TextView(this).apply {
            text = "পাসওয়ার্ড ও আনলক ধরন"
            textSize = 15.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 12))
        })

        lockCard.addView(makeSettingActionRow(
            "ভল্ট মাস্টার লক",
            "বর্তমান: ${lockTypeBn(prefs.getVaultLockType())}",
            "পরিবর্তন"
        ) {
            openCredentialCreatorDialog("ভল্ট লক পরিবর্তন") { s, t ->
                prefs.setVaultCredential(s, t)
                Toast.makeText(this, "ভল্ট লক আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                showDashboard()
            }
        })

        lockCard.addView(makeSettingActionRow(
            "ডিফল্ট অ্যাপ-লক",
            "বর্তমান: ${lockTypeBn(prefs.getGlobalAppLockType())}",
            "পরিবর্তন"
        ) {
            openCredentialCreatorDialog("ডিফল্ট অ্যাপ-লক পরিবর্তন") { s, t ->
                prefs.setAppLockCredential(s, t)
                prefs.setSeparateAppLockEnabled(true)
                Toast.makeText(this, "অ্যাপ-লক আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                showDashboard()
            }
        })

        lockCard.addView(SwitchCompat(this).apply {
            text = "ভল্ট ও অ্যাপ-লক আলাদা রাখুন"
            isChecked = prefs.isSeparateAppLockEnabled()
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            textSize = 13.5f
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 6), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                prefs.setSeparateAppLockEnabled(checked)
            }
        })
        body.addView(lockCard)

        val bioCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 20)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
            }
        }
        bioCard.addView(SwitchCompat(this).apply {
            text = "ফিঙ্গারপ্রিন্ট ও ফেস আনলক চালু রাখুন"
            isChecked = prefs.isBiometricEnabled()
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            textSize = 14.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnCheckedChangeListener { _, checked ->
                prefs.setBiometricEnabled(checked)
                Toast.makeText(
                    this@MainActivity,
                    if (checked) "নিচে ফিঙ্গারপ্রিন্ট আইকন চালু হয়েছে" else "ফিঙ্গারপ্রিন্ট বন্ধ হয়েছে",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        bioCard.addView(TextView(this).apply {
            text = "চালু থাকলে লক স্ক্রিনের নিচে ছোট ফিঙ্গারপ্রিন্ট আইকন দেখাবে — টাচ করলেই সাথে সাথে খুলবে। বন্ধ থাকলে দেখাবে না।"
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 6), 0, 0)
        })
        body.addView(bioCard)

        val relockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 20)
            setPadding(UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 16))
        }
        relockCard.addView(TextView(this).apply {
            text = "পুনরায় লক হওয়ার সময়"
            textSize = 15.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 10))
        })
        val modes = listOf(
            Triple(PrefsManager.RELOCK_IMMEDIATE, "অ্যাপ থেকে বের হলেই সাথে সাথে লক", "সবচেয়ে নিরাপদ — বের হয়ে আবার ঢুকলেই লক চাইবে"),
            Triple(PrefsManager.RELOCK_SCREEN_OFF, "স্ক্রিন অফ না করা পর্যন্ত খোলা থাকবে", "ফোনের স্ক্রিন বন্ধ করে অন করলেই লক হবে"),
            Triple(PrefsManager.RELOCK_GRACE_3M, "৩ মিনিট পর্যন্ত আনলক থাকবে", "তিন মিনিটের মধ্যে বারবার ঢুকলে লক চাইবে না")
        )
        val activeMode = prefs.getRelockMode()
        for ((code, title, desc) in modes) {
            val selected = activeMode == code
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    if (selected) UiStyleHelper.ACCENT_BLUE_SOFT else UiStyleHelper.BG_CANVAS,
                    14,
                    if (selected) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                setPadding(UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = UiStyleHelper.dp(this@MainActivity, 8)
                }
                setOnClickListener {
                    prefs.setRelockMode(code)
                    showDashboard()
                }
            }
            row.addView(TextView(this).apply {
                text = if (selected) "✓ $title" else title
                textSize = 13.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (selected) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.TEXT_PRIMARY)
            })
            row.addView(TextView(this).apply {
                text = desc
                textSize = 11.5f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 2), 0, 0)
            })
            relockCard.addView(row)
        }
        body.addView(relockCard)
    }

    private fun makeSettingActionRow(
        title: String,
        subtitle: String,
        ctaLabel: String,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_CANVAS, 14, UiStyleHelper.BORDER_SUBTLE, 1)
            setPadding(UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 10)
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(UiStyleHelper.TEXT_SECONDARY)
                })
            })
            addView(UiStyleHelper.chipBadge(
                this@MainActivity,
                ctaLabel,
                UiStyleHelper.ACCENT_BLUE,
                UiStyleHelper.TEXT_PRIMARY
            ).apply {
                setOnClickListener { onClick() }
            })
        }
    }

    // --- TAB 2: OTA UPDATES ---
    private fun buildUpdatesTab(body: LinearLayout) {
        val verCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 20, UiStyleHelper.BORDER_GLOW)
            setPadding(UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 18))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
            }
        }
        verCard.addView(TextView(this).apply {
            text = "বর্তমান ভার্সন: v${BuildConfig.VERSION_NAME}"
            textSize = 16.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
        })
        verCard.addView(TextView(this).apply {
            text = "নতুন আপডেট এলে এখান থেকে এক ক্লিকে সরাসরি ইনস্টল ও রুলস সিঙ্ক করতে পারবেন।"
            textSize = 12.5f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 6), 0, UiStyleHelper.dp(this@MainActivity, 16))
        })
        verCard.addView(UiStyleHelper.actionButton(this, "এখনই অটো আপডেট চেক করুন") {
            runAutoUpdateCheck()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiStyleHelper.dp(this@MainActivity, 48)).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 10)
            }
        })
        verCard.addView(UiStyleHelper.actionButton(
            this,
            "লাইভ ওয়েব কন্ট্রোল প্যানেল খুলুন",
            UiStyleHelper.BG_ELEVATED,
            UiStyleHelper.BG_PRESSED,
            UiStyleHelper.TEXT_PRIMARY,
            UiStyleHelper.BORDER_SUBTLE
        ) {
            startActivity(Intent(this, WebViewActivity::class.java))
        })
        body.addView(verCard)

        val serverCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.cardGradientBox(this@MainActivity, 20)
            setPadding(UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 16), UiStyleHelper.dp(this@MainActivity, 18), UiStyleHelper.dp(this@MainActivity, 16))
        }
        serverCard.addView(TextView(this).apply {
            text = "OTA আপডেট সার্ভার URL"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 8))
        })
        val urlInput = EditText(this).apply {
            setText(prefs.getOtaServerUrl())
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setHintTextColor(UiStyleHelper.TEXT_MUTED)
            textSize = 12.5f
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_CANVAS, 12, UiStyleHelper.BORDER_SUBTLE, 1)
            setPadding(UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 10), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = UiStyleHelper.dp(this@MainActivity, 10)
            }
        }
        serverCard.addView(urlInput)
        serverCard.addView(UiStyleHelper.actionButton(
            this,
            "সার্ভার URL সেভ করুন",
            UiStyleHelper.BG_ELEVATED,
            UiStyleHelper.BG_PRESSED,
            UiStyleHelper.ACCENT_BLUE,
            UiStyleHelper.BORDER_SUBTLE,
            44
        ) {
            prefs.setOtaServerUrl(urlInput.text.toString())
            Toast.makeText(this, "সার্ভার URL সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        })
        body.addView(serverCard)
    }

    // -------------------------------------------------------------------------
    // 4. DARK MODAL DIALOGS (PER-APP CUSTOM LOCK + TIME LIMIT)
    // -------------------------------------------------------------------------
    private fun showAppOptionsSheet(app: InstalledAppInfo) {
        val customCred = prefs.getCustomAppLock(app.packageName)
        UiStyleHelper.showDarkDialog(
            this,
            app.appName,
            "আজ ব্যবহার: ${app.usedMinutesToday} মি. · ${if (app.dailyLimitMinutes > 0) "সীমা: ${app.dailyLimitMinutes} মি." else "কোনো সময়সীমা নেই"}"
        ) { dialog, content ->
            content.addView(TextView(this).apply {
                text = "দৈনিক ব্যবহারের সময়সীমা"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 8))
            })

            val limitValues = listOf(0 to "বন্ধ", 15 to "১৫ মি.", 30 to "৩০ মি.", 60 to "৬০ মি.", 120 to "১২০ মি.")
            val limitRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = UiStyleHelper.dp(this@MainActivity, 16)
                }
            }
            for ((mins, label) in limitValues) {
                val selected = app.dailyLimitMinutes == mins
                limitRow.addView(TextView(this).apply {
                    text = label
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(if (selected) UiStyleHelper.TEXT_PRIMARY else UiStyleHelper.TEXT_SECONDARY)
                    background = UiStyleHelper.roundedBox(
                        this@MainActivity,
                        if (selected) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_ELEVATED,
                        10,
                        if (selected) null else UiStyleHelper.BORDER_SUBTLE,
                        1
                    )
                    layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@MainActivity, 36), 1f).apply {
                        setMargins(UiStyleHelper.dp(this@MainActivity, 2), 0, UiStyleHelper.dp(this@MainActivity, 2), 0)
                    }
                    setOnClickListener {
                        prefs.setDailyLimit(app.packageName, mins)
                        dialog.dismiss()
                        showDashboard()
                    }
                })
            }
            content.addView(limitRow)

            content.addView(TextView(this).apply {
                text = "এই অ্যাপের জন্য আলাদা লক"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 8))
            })

            val customActions = listOf(
                Triple(PrefsManager.TYPE_PIN, "আলাদা পিন (PIN) সেট করুন", UiStyleHelper.BG_ELEVATED),
                Triple(PrefsManager.TYPE_PATTERN, "আলাদা প্যাটার্ন সেট করুন", UiStyleHelper.BG_ELEVATED),
                Triple(PrefsManager.TYPE_PASSWORD, "আলাদা পাসওয়ার্ড সেট করুন", UiStyleHelper.BG_ELEVATED)
            )
            for ((type, label, bg) in customActions) {
                content.addView(UiStyleHelper.actionButton(
                    this,
                    label,
                    bg,
                    UiStyleHelper.BG_PRESSED,
                    UiStyleHelper.TEXT_PRIMARY,
                    UiStyleHelper.BORDER_SUBTLE,
                    44
                ) {
                    dialog.dismiss()
                    promptSpecificTypeSetup(type, "${app.appName} — ${lockTypeBn(type)}") { secret ->
                        prefs.setCustomAppLock(app.packageName, secret, type, allowBiometric = false)
                        showDashboard()
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiStyleHelper.dp(this@MainActivity, 44)).apply {
                        bottomMargin = UiStyleHelper.dp(this@MainActivity, 8)
                    }
                })
            }

            if (customCred != null) {
                if (prefs.isBiometricEnabled()) {
                    content.addView(SwitchCompat(this).apply {
                        text = "এই অ্যাপে ফিঙ্গারপ্রিন্ট/ফেস অনুমোদন"
                        isChecked = customCred.allowBiometric
                        setTextColor(UiStyleHelper.TEXT_PRIMARY)
                        textSize = 13f
                        setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, UiStyleHelper.dp(this@MainActivity, 10))
                        setOnCheckedChangeListener { _, checked ->
                            prefs.setCustomAppBiometric(app.packageName, checked)
                        }
                    })
                }
                content.addView(UiStyleHelper.actionButton(
                    this,
                    "নিজস্ব লক মুছে ডিফল্ট লকে ফিরুন",
                    UiStyleHelper.ACCENT_ROSE_SOFT,
                    UiStyleHelper.ACCENT_ROSE_SOFT,
                    UiStyleHelper.ACCENT_ROSE,
                    UiStyleHelper.ACCENT_ROSE,
                    44
                ) {
                    prefs.removeCustomAppLock(app.packageName)
                    dialog.dismiss()
                    showDashboard()
                })
            }
        }
    }

    private fun openCredentialCreatorDialog(title: String, onPicked: (String, String) -> Unit) {
        UiStyleHelper.showDarkDialog(this, title, "পছন্দের লক পদ্ধতি বেছে নিন") { dialog, content ->
            val options = listOf(
                PrefsManager.TYPE_PIN to "৪–৬ ডিজিট পিন (PIN)",
                PrefsManager.TYPE_PATTERN to "৩x৩ প্যাটার্ন (Pattern)",
                PrefsManager.TYPE_PASSWORD to "টেক্সট পাসওয়ার্ড (Password)"
            )
            for ((type, label) in options) {
                content.addView(UiStyleHelper.actionButton(
                    this,
                    label,
                    UiStyleHelper.BG_ELEVATED,
                    UiStyleHelper.BG_PRESSED,
                    UiStyleHelper.TEXT_PRIMARY,
                    UiStyleHelper.BORDER_SUBTLE,
                    46
                ) {
                    dialog.dismiss()
                    promptSpecificTypeSetup(type, "$title · ${lockTypeBn(type)}") { secret ->
                        onPicked(secret, type)
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiStyleHelper.dp(this@MainActivity, 46)).apply {
                        bottomMargin = UiStyleHelper.dp(this@MainActivity, 8)
                    }
                })
            }
        }
    }

    private fun promptSpecificTypeSetup(type: String, title: String, onConfirmed: (String) -> Unit) {
        if (type == PrefsManager.TYPE_PATTERN) {
            UiStyleHelper.showDarkDialog(this, title, "কমপক্ষে ৪টি বিন্দু যুক্ত করে প্যাটার্ন আঁকুন") { dialog, content ->
                val pv = PatternLockView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                }
                pv.listener = object : PatternLockView.Listener {
                    override fun onPatternComplete(pattern: String) {
                        dialog.dismiss()
                        onConfirmed(pattern)
                    }
                }
                content.addView(pv)
            }
            return
        }

        UiStyleHelper.showDarkDialog(
            this,
            title,
            if (type == PrefsManager.TYPE_PIN) "৪ থেকে ৬ সংখ্যার পিন দিন" else "কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন"
        ) { dialog, content ->
            val input = EditText(this).apply {
                if (type == PrefsManager.TYPE_PIN) {
                    hint = "যেমন: 1234"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                } else {
                    hint = "নতুন পাসওয়ার্ড লিখুন"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                setHintTextColor(UiStyleHelper.TEXT_MUTED)
                background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.BG_ELEVATED, 12, UiStyleHelper.BORDER_SUBTLE, 1)
                setPadding(UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12), UiStyleHelper.dp(this@MainActivity, 14), UiStyleHelper.dp(this@MainActivity, 12))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = UiStyleHelper.dp(this@MainActivity, 14)
                }
            }
            content.addView(input)
            content.addView(UiStyleHelper.actionButton(this, "সেভ করুন") {
                val txt = input.text.toString()
                if (txt.length < 4 || (type == PrefsManager.TYPE_PIN && txt.length > 6)) {
                    Toast.makeText(
                        this,
                        if (type == PrefsManager.TYPE_PIN) "৪ থেকে ৬ ডিজিটের পিন দিন" else "কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    dialog.dismiss()
                    onConfirmed(txt)
                }
            })
        }
    }

    // -------------------------------------------------------------------------
    // 5. OTA UPDATE & PERMISSIONS HELPERS
    // -------------------------------------------------------------------------
    private fun autoCheckUpdatesOnLaunch() {
        val url = prefs.getOtaServerUrl()
        lifecycleScope.launch {
            try {
                val synced = UpdateManager.syncRemotePolicies(url, prefs)
                UiStyleHelper.applyRuntimeTheme(prefs)
                val result = UpdateManager.checkForUpdate(url)
                if (result.hasUpdate && result.apkDownloadUrl != null) {
                    pendingUpdateResult = result
                    showDashboard()
                } else if (synced > 0) {
                    showDashboard()
                }
            } catch (_: Exception) {}
        }
    }

    private fun runAutoUpdateCheck() {
        val url = prefs.getOtaServerUrl()
        Toast.makeText(this, "আপডেট ও রুলস চেক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val synced = UpdateManager.syncRemotePolicies(url, prefs)
                val result = UpdateManager.checkForUpdate(url)
                if (result.hasUpdate && result.apkDownloadUrl != null) {
                    pendingUpdateResult = result
                    UiStyleHelper.showDarkDialog(
                        this@MainActivity,
                        "নতুন আপডেট (${result.latestVersionName})",
                        result.changelog.ifBlank { "নতুন ভার্সন ডাউনলোডের জন্য প্রস্তুত।" }
                    ) { dialog, content ->
                        content.addView(UiStyleHelper.actionButton(this@MainActivity, "এখনই ইনস্টল করুন") {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                UpdateManager.downloadAndInstallApk(this@MainActivity, result.apkDownloadUrl)
                            }
                        })
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "সর্বশেষ ভার্সনে (${result.latestVersionName}) আছে · $synced টি রুল সিঙ্ক হয়েছে",
                        Toast.LENGTH_LONG
                    ).show()
                    showDashboard()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "সার্ভার সংযোগ ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
