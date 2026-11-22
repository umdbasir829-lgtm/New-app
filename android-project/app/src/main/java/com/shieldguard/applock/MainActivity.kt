package com.shieldguard.applock

import android.app.AlertDialog
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
import android.view.ViewGroup
import android.widget.*
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
    private var currentFilter = "ALL" // ALL | LOCKED | LIMITED
    private var searchQuery = ""
    private lateinit var appsListContainer: LinearLayout
    private var allInstalledApps: List<InstalledAppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        window.statusBarColor = UiStyleHelper.BG_CANVAS
        window.navigationBarColor = UiStyleHelper.BG_CANVAS

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
        if (vaultUnlocked && ::appsListContainer.isInitialized) {
            showDashboard()
        }
    }

    // -------------------------------------------------------------------------
    // 1. INITIAL SETUP (VAULT + SEPARATE APP-LOCK WITH PIN / PATTERN / PASSWORD)
    // -------------------------------------------------------------------------
    private fun showInitialSetup() {
        val scroll = ScrollView(this).apply { setBackgroundColor(UiStyleHelper.BG_CANVAS) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 24),
                UiStyleHelper.dp(this@MainActivity, 40),
                UiStyleHelper.dp(this@MainActivity, 24),
                UiStyleHelper.dp(this@MainActivity, 40)
            )
        }

        val titleTv = TextView(this).apply {
            text = "ShieldGuard ভল্ট সেটআপ"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
        }
        val subTv = TextView(this).apply {
            text = "অ্যাপে ঢোকার লক এবং মোবাইলের লক করা অ্যাপ খোলার লক আলাদা সেট করুন (পিন, প্যাটার্ন বা পাসওয়ার্ড)।"
            textSize = 13f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 6), 0, UiStyleHelper.dp(this@MainActivity, 20))
        }
        root.addView(titleTv)
        root.addView(subTv)

        var chosenVaultSecret = ""
        var chosenVaultType = PrefsManager.TYPE_PIN
        var chosenAppSecret = ""
        var chosenAppType = PrefsManager.TYPE_PIN

        val vaultStatusTv = TextView(this).apply {
            text = "১) ভল্ট লক: এখনো সেট করা হয়নি"
            textSize = 14f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 8))
        }
        val setVaultBtn = Button(this).apply {
            text = "ভল্ট লক সেট করুন (পিন / প্যাটার্ন / পাসওয়ার্ড)"
            isAllCaps = false
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyleHelper.dp(this@MainActivity, 48)
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 18) }
            setOnClickListener {
                openCredentialCreatorDialog("ভল্ট লক সেট করুন") { secret, type ->
                    chosenVaultSecret = secret
                    chosenVaultType = type
                    vaultStatusTv.text = "১) ভল্ট লক সেট হয়েছে ($type)"
                    vaultStatusTv.setTextColor(UiStyleHelper.ACCENT_GREEN)
                }
            }
        }

        val separateSwitch = SwitchCompat(this).apply {
            text = "মোবাইলের লক করা অ্যাপের জন্য আলাদা লক রাখুন"
            isChecked = true
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 12))
        }

        val appStatusTv = TextView(this).apply {
            text = "২) অ্যাপ-লক: এখনো সেট করা হয়নি"
            textSize = 14f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 8))
        }
        val setAppLockBtn = Button(this).apply {
            text = "অ্যাপ-লক সেট করুন (পিন / প্যাটার্ন / পাসওয়ার্ড)"
            isAllCaps = false
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(
                this@MainActivity,
                UiStyleHelper.BG_ELEVATED,
                14,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyleHelper.dp(this@MainActivity, 48)
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 24) }
            setOnClickListener {
                openCredentialCreatorDialog("আলাদা অ্যাপ-লক সেট করুন") { secret, type ->
                    chosenAppSecret = secret
                    chosenAppType = type
                    appStatusTv.text = "২) অ্যাপ-লক সেট হয়েছে ($type)"
                    appStatusTv.setTextColor(UiStyleHelper.ACCENT_GREEN)
                }
            }
        }

        separateSwitch.setOnCheckedChangeListener { _, checked ->
            appStatusTv.visibility = if (checked) View.VISIBLE else View.GONE
            setAppLockBtn.visibility = if (checked) View.VISIBLE else View.GONE
        }

        val finishBtn = Button(this).apply {
            text = "সেটআপ সম্পন্ন করে ড্যাশবোর্ডে যান"
            isAllCaps = false
            textSize = 15f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_GREEN, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyleHelper.dp(this@MainActivity, 52)
            )
            setOnClickListener {
                if (chosenVaultSecret.isBlank()) {
                    Toast.makeText(this@MainActivity, "প্রথমে ভল্ট লক সেট করুন", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (separateSwitch.isChecked && chosenAppSecret.isBlank()) {
                    Toast.makeText(this@MainActivity, "আলাদা অ্যাপ-লক সেট করুন", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.setVaultCredential(chosenVaultSecret, chosenVaultType)
                prefs.setSeparateAppLockEnabled(separateSwitch.isChecked)
                if (separateSwitch.isChecked) {
                    prefs.setAppLockCredential(chosenAppSecret, chosenAppType)
                }
                vaultUnlocked = true
                showDashboard()
            }
        }

        root.addView(vaultStatusTv)
        root.addView(setVaultBtn)
        root.addView(separateSwitch)
        root.addView(appStatusTv)
        root.addView(setAppLockBtn)
        root.addView(finishBtn)
        scroll.addView(root)
        setContentView(scroll)
    }

    // -------------------------------------------------------------------------
    // 2. VAULT LOGIN SCREEN (PIN / PATTERN / PASSWORD + BIOMETRIC)
    // -------------------------------------------------------------------------
    private fun showVaultLogin() {
        val lockType = prefs.getVaultLockType()
        val pinBuffer = StringBuilder()
        val dots = mutableListOf<View>()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(UiStyleHelper.BG_CANVAS)
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 24),
                UiStyleHelper.dp(this@MainActivity, 56),
                UiStyleHelper.dp(this@MainActivity, 24),
                UiStyleHelper.dp(this@MainActivity, 32)
            )
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiStyleHelper.roundedBox(
                this@MainActivity,
                UiStyleHelper.BG_CARD,
                22,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 24),
                UiStyleHelper.dp(this@MainActivity, 22),
                UiStyleHelper.dp(this@MainActivity, 24),
                UiStyleHelper.dp(this@MainActivity, 22)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 20) }
        }

        val titleTv = TextView(this).apply {
            text = "ShieldGuard ভল্ট আনলক"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            gravity = Gravity.CENTER
        }
        val subTv = TextView(this).apply {
            text = "ভেতরে প্রবেশ করতে ভল্ট $lockType দিন বা ফিঙ্গারপ্রিন্ট/ফেস ব্যবহার করুন"
            textSize = 13f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 6), 0, 0)
        }
        headerCard.addView(titleTv)
        headerCard.addView(subTv)
        root.addView(headerCard)

        val errTv = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(UiStyleHelper.ACCENT_ROSE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 10))
        }
        root.addView(errTv)

        val centerArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        when (lockType) {
            PrefsManager.TYPE_PATTERN -> {
                val patternView = PatternLockView(this).apply {
                    background = UiStyleHelper.roundedBox(
                        this@MainActivity,
                        UiStyleHelper.BG_CARD,
                        24,
                        UiStyleHelper.BORDER_SUBTLE,
                        1
                    )
                    setPadding(
                        UiStyleHelper.dp(this@MainActivity, 16),
                        UiStyleHelper.dp(this@MainActivity, 16),
                        UiStyleHelper.dp(this@MainActivity, 16),
                        UiStyleHelper.dp(this@MainActivity, 16)
                    )
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
                val input = EditText(this).apply {
                    hint = "ভল্ট পাসওয়ার্ড লিখুন"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                    setHintTextColor(UiStyleHelper.TEXT_SECONDARY)
                    background = UiStyleHelper.roundedBox(
                        this@MainActivity,
                        UiStyleHelper.BG_CARD,
                        14,
                        UiStyleHelper.BORDER_SUBTLE,
                        1
                    )
                    setPadding(
                        UiStyleHelper.dp(this@MainActivity, 16),
                        UiStyleHelper.dp(this@MainActivity, 14),
                        UiStyleHelper.dp(this@MainActivity, 16),
                        UiStyleHelper.dp(this@MainActivity, 14)
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 14) }
                }
                val btn = Button(this).apply {
                    text = "ভল্ট আনলক করুন"
                    isAllCaps = false
                    setTextColor(UiStyleHelper.TEXT_PRIMARY)
                    background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 14)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        UiStyleHelper.dp(this@MainActivity, 50)
                    )
                    setOnClickListener {
                        if (prefs.verifyVaultPassword(input.text.toString())) {
                            vaultUnlocked = true
                            showDashboard()
                        } else {
                            errTv.text = "ভুল ভল্ট পাসওয়ার্ড!"
                        }
                    }
                }
                centerArea.addView(input)
                centerArea.addView(btn)
            }
            else -> {
                val dotsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, UiStyleHelper.dp(this@MainActivity, 20))
                }
                for (i in 0 until 6) {
                    val dot = View(this).apply {
                        background = UiStyleHelper.circleBox(
                            this@MainActivity,
                            UiStyleHelper.BG_CARD,
                            UiStyleHelper.BORDER_SUBTLE,
                            2
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            UiStyleHelper.dp(this@MainActivity, 16),
                            UiStyleHelper.dp(this@MainActivity, 16)
                        ).apply {
                            setMargins(UiStyleHelper.dp(this@MainActivity, 8), 0, UiStyleHelper.dp(this@MainActivity, 8), 0)
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
                            if (on) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_SUBTLE,
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
                val size = UiStyleHelper.dp(this, 66)
                val gap = UiStyleHelper.dp(this, 10)
                for (r in rows) {
                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    for (k in r) {
                        val tv = TextView(this).apply {
                            text = k
                            textSize = 21f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            gravity = Gravity.CENTER
                            setTextColor(UiStyleHelper.TEXT_PRIMARY)
                            background = UiStyleHelper.circleBox(
                                this@MainActivity,
                                if (k == "✓") UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                                if (k == "✓") null else UiStyleHelper.BORDER_SUBTLE,
                                1
                            )
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                setMargins(gap, gap / 2, gap, gap / 2)
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

        root.addView(centerArea)

        if (canUseBiometric() && prefs.isBiometricEnabled()) {
            val bioBtn = Button(this).apply {
                text = "ফিঙ্গারপ্রিন্ট / ফেস আনলক"
                isAllCaps = false
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    UiStyleHelper.dp(this@MainActivity, 50)
                )
                setOnClickListener { promptBiometricForVault() }
            }
            root.addView(bioBtn)
            root.postDelayed({ promptBiometricForVault() }, 180)
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
            .setSubtitle("ফিঙ্গারপ্রিন্ট বা ফেস দিয়ে প্রবেশ করুন")
            .setNegativeButtonText("পিন / প্যাটার্ন ব্যবহার করুন")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(info)
    }

    // -------------------------------------------------------------------------
    // 3. MODERN HOME DASHBOARD
    // -------------------------------------------------------------------------
    private fun showDashboard() {
        allInstalledApps = AppDetector.getAllLaunchableApps(this, prefs)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyleHelper.BG_CANVAS)
        }

        // Sticky Top Header Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiStyleHelper.BG_CARD)
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 18),
                UiStyleHelper.dp(this@MainActivity, 14),
                UiStyleHelper.dp(this@MainActivity, 18),
                UiStyleHelper.dp(this@MainActivity, 14)
            )
        }
        val brandCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val brandTitle = TextView(this).apply {
            text = "ShieldGuard"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
        }
        val versionSub = TextView(this).apply {
            text = "v${BuildConfig.VERSION_NAME} · স্মার্ট অ্যাপ লক ও টাইম লিমিট"
            textSize = 11f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
        }
        brandCol.addView(brandTitle)
        brandCol.addView(versionSub)

        val liveWebBtn = Button(this).apply {
            text = "লাইভ"
            isAllCaps = false
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(
                this@MainActivity,
                UiStyleHelper.BG_ELEVATED,
                12,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                UiStyleHelper.dp(this@MainActivity, 38)
            ).apply { rightMargin = UiStyleHelper.dp(this@MainActivity, 6) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, WebViewActivity::class.java))
            }
        }
        val updateBtn = Button(this).apply {
            text = "আপডেট"
            isAllCaps = false
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                UiStyleHelper.dp(this@MainActivity, 38)
            ).apply { rightMargin = UiStyleHelper.dp(this@MainActivity, 6) }
            setOnClickListener { runAutoUpdateCheck() }
        }
        val settingsBtn = Button(this).apply {
            text = "সেটিংস"
            isAllCaps = false
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(
                this@MainActivity,
                UiStyleHelper.BG_ELEVATED,
                12,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                UiStyleHelper.dp(this@MainActivity, 38)
            )
            setOnClickListener { showSecuritySettingsDialog() }
        }
        topBar.addView(brandCol)
        topBar.addView(liveWebBtn)
        topBar.addView(updateBtn)
        topBar.addView(settingsBtn)
        root.addView(topBar)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 32)
            )
        }

        // Auto-Detected Update Banner
        val availableUpdate = pendingUpdateResult
        if (availableUpdate != null && availableUpdate.hasUpdate && availableUpdate.apkDownloadUrl != null) {
            val updateBanner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    UiStyleHelper.BG_ELEVATED,
                    18,
                    UiStyleHelper.ACCENT_BLUE,
                    1
                )
                setPadding(
                    UiStyleHelper.dp(this@MainActivity, 16),
                    UiStyleHelper.dp(this@MainActivity, 14),
                    UiStyleHelper.dp(this@MainActivity, 16),
                    UiStyleHelper.dp(this@MainActivity, 14)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 12) }
            }
            val uTitle = TextView(this).apply {
                text = "নতুন আপডেট পাওয়া গেছে (${availableUpdate.latestVersionName})"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
            }
            val uDesc = TextView(this).apply {
                text = availableUpdate.changelog.ifBlank { "নতুন ফিচার ও উন্নতি যুক্ত হয়েছে।" }
                textSize = 12f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, UiStyleHelper.dp(this@MainActivity, 10))
            }
            val installNowBtn = Button(this).apply {
                text = "এখনই অটো আপডেট ইনস্টল করুন"
                isAllCaps = false
                textSize = 13f
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    UiStyleHelper.dp(this@MainActivity, 42)
                )
                setOnClickListener {
                    lifecycleScope.launch {
                        UpdateManager.downloadAndInstallApk(this@MainActivity, availableUpdate.apkDownloadUrl)
                    }
                }
            }
            updateBanner.addView(uTitle)
            updateBanner.addView(uDesc)
            updateBanner.addView(installNowBtn)
            body.addView(updateBanner)
        }

        // Protection Status Card
        val lockedCount = allInstalledApps.count { it.isLocked || prefs.hasCustomAppLock(it.packageName) }
        val limitedCount = allInstalledApps.count { it.dailyLimitMinutes > 0 }
        val serviceActive = AppLockAccessibilityService.isRunning

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyleHelper.roundedBox(
                this@MainActivity,
                UiStyleHelper.BG_CARD,
                20,
                if (serviceActive) UiStyleHelper.BORDER_SUBTLE else UiStyleHelper.ACCENT_AMBER,
                1
            )
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 18),
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 18),
                UiStyleHelper.dp(this@MainActivity, 16)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 14) }
        }

        val statusTitle = TextView(this).apply {
            text = if (serviceActive) "সুরক্ষা চালু আছে · লক সার্ভিস সক্রিয়" else "সতর্কতা: Accessibility সার্ভিস বন্ধ আছে"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (serviceActive) UiStyleHelper.ACCENT_GREEN else UiStyleHelper.ACCENT_AMBER)
        }
        val relockLabel = when (prefs.getRelockMode()) {
            PrefsManager.RELOCK_IMMEDIATE -> "বের হলেই পুনরায় লক"
            PrefsManager.RELOCK_SCREEN_OFF -> "স্ক্রিন অফ হলে লক"
            else -> "৩ মিনিট পর লক"
        }
        val statusMeta = TextView(this).apply {
            text = "${allInstalledApps.size}টি অ্যাপ · $lockedCountটি লক · $limitedCountটি লিমিট · $relockLabel"
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_SECONDARY)
            setPadding(0, UiStyleHelper.dp(this@MainActivity, 4), 0, 0)
        }
        statusCard.addView(statusTitle)
        statusCard.addView(statusMeta)

        if (!serviceActive || !hasUsageStatsPermission() || !Settings.canDrawOverlays(this)) {
            val permRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 12), 0, 0)
            }
            if (!hasUsageStatsPermission()) {
                permRow.addView(makePermButton("অনুমতি দিন: Usage Access (ব্যবহার সময়)") {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                })
            }
            if (!Settings.canDrawOverlays(this)) {
                permRow.addView(makePermButton("অনুমতি দিন: Display Over Other Apps") {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                })
            }
            if (!serviceActive) {
                permRow.addView(makePermButton("Accessibility Service চালু করুন") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
            }
            statusCard.addView(permRow)
        }
        body.addView(statusCard)

        // Search Box
        val searchEt = EditText(this).apply {
            hint = "অ্যাপ খুঁজুন (যেমন Facebook, WhatsApp)..."
            setText(searchQuery)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            setHintTextColor(UiStyleHelper.TEXT_SECONDARY)
            textSize = 14f
            background = UiStyleHelper.roundedBox(
                this@MainActivity,
                UiStyleHelper.BG_CARD,
                14,
                UiStyleHelper.BORDER_SUBTLE,
                1
            )
            setPadding(
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 12),
                UiStyleHelper.dp(this@MainActivity, 16),
                UiStyleHelper.dp(this@MainActivity, 12)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 12) }
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

        // Filter Chips Row
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 12) }
        }
        val filters = listOf("ALL" to "সব অ্যাপ", "LOCKED" to "লক করা", "LIMITED" to "সময়সীমা")
        for ((key, label) in filters) {
            val active = currentFilter == key
            val chip = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                setTextColor(if (active) UiStyleHelper.TEXT_PRIMARY else UiStyleHelper.TEXT_SECONDARY)
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    if (active) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BG_CARD,
                    12,
                    if (active) null else UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@MainActivity, 40), 1f).apply {
                    rightMargin = UiStyleHelper.dp(this@MainActivity, 6)
                }
                setOnClickListener {
                    currentFilter = key
                    showDashboard()
                }
            }
            filterRow.addView(chip)
        }
        body.addView(filterRow)

        appsListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(appsListContainer)
        renderAppsList()

        scroll.addView(body)
        root.addView(scroll)
        setContentView(root)

        if (!hasCheckedUpdateThisSession) {
            hasCheckedUpdateThisSession = true
            autoCheckUpdatesOnLaunch()
        }
    }

    private fun makePermButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(this@MainActivity, UiStyleHelper.ACCENT_BLUE, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyleHelper.dp(this@MainActivity, 42)
            ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 8) }
            setOnClickListener { onClick() }
        }
    }

    private fun renderAppsList() {
        appsListContainer.removeAllViews()
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
            val emptyTv = TextView(this).apply {
                text = "এই তালিকায় কোনো অ্যাপ পাওয়া যায়নি"
                textSize = 14f
                setTextColor(UiStyleHelper.TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 32), 0, 0)
            }
            appsListContainer.addView(emptyTv)
            return
        }

        for (app in filtered) {
            val hasCustom = prefs.hasCustomAppLock(app.packageName)
            val customCred = prefs.getCustomAppLock(app.packageName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    UiStyleHelper.BG_CARD,
                    18,
                    UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                setPadding(
                    UiStyleHelper.dp(this@MainActivity, 14),
                    UiStyleHelper.dp(this@MainActivity, 14),
                    UiStyleHelper.dp(this@MainActivity, 14),
                    UiStyleHelper.dp(this@MainActivity, 12)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = UiStyleHelper.dp(this@MainActivity, 10) }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(
                    UiStyleHelper.dp(this@MainActivity, 44),
                    UiStyleHelper.dp(this@MainActivity, 44)
                )
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiStyleHelper.dp(this@MainActivity, 12), 0, UiStyleHelper.dp(this@MainActivity, 8), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(this).apply {
                text = app.appName
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
            }

            val limitLabel = if (app.dailyLimitMinutes > 0) "${app.dailyLimitMinutes} মি. লিমিট" else "লিমিট নেই"
            val customLabel = if (customCred != null) " · নিজস্ব ${customCred.type}" else ""
            val metaTv = TextView(this).apply {
                text = "আজ: ${app.usedMinutesToday} মি. · $limitLabel$customLabel"
                textSize = 12f
                setTextColor(
                    if (app.dailyLimitMinutes > 0 && app.usedMinutesToday >= app.dailyLimitMinutes)
                        UiStyleHelper.ACCENT_ROSE
                    else
                        UiStyleHelper.TEXT_SECONDARY
                )
            }
            infoCol.addView(nameTv)
            infoCol.addView(metaTv)

            val lockSwitch = SwitchCompat(this).apply {
                isChecked = app.isLocked || hasCustom
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.setAppLocked(app.packageName, isChecked)
                    if (!isChecked) {
                        prefs.removeCustomAppLock(app.packageName)
                    }
                }
            }

            topRow.addView(iconView)
            topRow.addView(infoCol)
            topRow.addView(lockSwitch)
            card.addView(topRow)

            // Bottom Action Row per App
            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, UiStyleHelper.dp(this@MainActivity, 10), 0, 0)
            }

            val limitBtn = Button(this).apply {
                text = if (app.dailyLimitMinutes > 0) "সময়সীমা: ${app.dailyLimitMinutes} মি." else "টাইম লিমিট সেট"
                isAllCaps = false
                textSize = 11f
                setTextColor(UiStyleHelper.TEXT_PRIMARY)
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    UiStyleHelper.BG_ELEVATED,
                    10,
                    UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@MainActivity, 36), 1f).apply {
                    rightMargin = UiStyleHelper.dp(this@MainActivity, 6)
                }
                setOnClickListener { showLimitPickerDialog(app) }
            }

            val customLockBtn = Button(this).apply {
                text = if (hasCustom) "আলাদা লক (${customCred?.type})" else "আলাদা পাসওয়ার্ড/পিন"
                isAllCaps = false
                textSize = 11f
                setTextColor(if (hasCustom) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.TEXT_SECONDARY)
                background = UiStyleHelper.roundedBox(
                    this@MainActivity,
                    UiStyleHelper.BG_ELEVATED,
                    10,
                    if (hasCustom) UiStyleHelper.ACCENT_BLUE else UiStyleHelper.BORDER_SUBTLE,
                    1
                )
                layoutParams = LinearLayout.LayoutParams(0, UiStyleHelper.dp(this@MainActivity, 36), 1f)
                setOnClickListener { showPerAppLockDialog(app) }
            }

            actionsRow.addView(limitBtn)
            actionsRow.addView(customLockBtn)
            card.addView(actionsRow)

            appsListContainer.addView(card)
        }
    }

    // -------------------------------------------------------------------------
    // 4. DIALOGS (LIMIT, PER-APP CUSTOM LOCK, SECURITY SETTINGS, CREDENTIAL CREATOR)
    // -------------------------------------------------------------------------
    private fun showLimitPickerDialog(app: InstalledAppInfo) {
        val options = arrayOf("লিমিট বন্ধ", "১৫ মিনিট", "৩০ মিনিট", "৪৫ মিনিট", "৬০ মিনিট (১ ঘণ্টা)", "১২০ মিনিট (২ ঘণ্টা)")
        val values = intArrayOf(0, 15, 30, 45, 60, 120)
        AlertDialog.Builder(this)
            .setTitle("${app.appName} — দৈনিক সময়সীমা")
            .setItems(options) { _, idx ->
                prefs.setDailyLimit(app.packageName, values[idx])
                showDashboard()
            }
            .show()
    }

    private fun showPerAppLockDialog(app: InstalledAppInfo) {
        val hasCustom = prefs.hasCustomAppLock(app.packageName)
        val options = if (hasCustom) {
            arrayOf("নতুন পিন সেট করুন (শুধু ${app.appName})", "নতুন প্যাটার্ন সেট করুন", "নতুন পাসওয়ার্ড সেট করুন", "ডিফল্ট অ্যাপ-লকে ফিরিয়ে নিন")
        } else {
            arrayOf("এই অ্যাপের জন্য আলাদা পিন (PIN)", "এই অ্যাপের জন্য আলাদা প্যাটার্ন", "এই অ্যাপের জন্য আলাদা পাসওয়ার্ড")
        }

        AlertDialog.Builder(this)
            .setTitle("${app.appName} — আলাদা লক")
            .setItems(options) { _, idx ->
                when (idx) {
                    0 -> promptSpecificTypeSetup(PrefsManager.TYPE_PIN, "${app.appName} — পিন সেট") { s ->
                        prefs.setCustomAppLock(app.packageName, s, PrefsManager.TYPE_PIN)
                        showDashboard()
                    }
                    1 -> promptSpecificTypeSetup(PrefsManager.TYPE_PATTERN, "${app.appName} — প্যাটার্ন সেট") { s ->
                        prefs.setCustomAppLock(app.packageName, s, PrefsManager.TYPE_PATTERN)
                        showDashboard()
                    }
                    2 -> promptSpecificTypeSetup(PrefsManager.TYPE_PASSWORD, "${app.appName} — পাসওয়ার্ড সেট") { s ->
                        prefs.setCustomAppLock(app.packageName, s, PrefsManager.TYPE_PASSWORD)
                        showDashboard()
                    }
                    3 -> {
                        prefs.removeCustomAppLock(app.packageName)
                        showDashboard()
                    }
                }
            }
            .show()
    }

    private fun showSecuritySettingsDialog() {
        val items = arrayOf(
            "১. ভল্ট মাস্টার লক পরিবর্তন (বর্তমান: ${prefs.getVaultLockType()})",
            "২. ডিফল্ট অ্যাপ-লক পরিবর্তন (বর্তমান: ${prefs.getGlobalAppLockType()})",
            "৩. ফিঙ্গারপ্রিন্ট ও ফেস আনলক: ${if (prefs.isBiometricEnabled()) "চালু আছে" else "বন্ধ আছে"}",
            "৪. রি-লক নিয়ম: ${
                when (prefs.getRelockMode()) {
                    PrefsManager.RELOCK_IMMEDIATE -> "অ্যাপ থেকে বের হলেই লক"
                    PrefsManager.RELOCK_SCREEN_OFF -> "স্ক্রিন বন্ধ হলে লক"
                    else -> "৩ মিনিট পর লক"
                }
            }",
            "৫. আলাদা অ্যাপ-লক: ${if (prefs.isSeparateAppLockEnabled()) "চালু" else "বন্ধ (ভল্ট লকের মতো)"}",
            "৬. OTA সার্ভার URL পরিবর্তন"
        )

        AlertDialog.Builder(this)
            .setTitle("নিরাপত্তা ও লক সেটিংস")
            .setItems(items) { _, idx ->
                when (idx) {
                    0 -> openCredentialCreatorDialog("ভল্ট লক পরিবর্তন") { s, t ->
                        prefs.setVaultCredential(s, t)
                        Toast.makeText(this, "ভল্ট লক আপডেট হয়েছে ($t)", Toast.LENGTH_SHORT).show()
                    }
                    1 -> openCredentialCreatorDialog("অ্যাপ-লক পরিবর্তন") { s, t ->
                        prefs.setAppLockCredential(s, t)
                        prefs.setSeparateAppLockEnabled(true)
                        Toast.makeText(this, "অ্যাপ-লক আপডেট হয়েছে ($t)", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val next = !prefs.isBiometricEnabled()
                        prefs.setBiometricEnabled(next)
                        Toast.makeText(this, if (next) "ফিঙ্গারপ্রিন্ট/ফেস চালু হয়েছে" else "বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                    3 -> showRelockModePicker()
                    4 -> {
                        val next = !prefs.isSeparateAppLockEnabled()
                        prefs.setSeparateAppLockEnabled(next)
                        Toast.makeText(this, if (next) "আলাদা অ্যাপ-লক চালু" else "ভল্ট লকের সাথে একীভূত", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        val urlInput = EditText(this).apply {
                            setText(prefs.getOtaServerUrl())
                            hint = "https://..."
                        }
                        AlertDialog.Builder(this)
                            .setTitle("OTA আপডেট সার্ভার URL")
                            .setView(urlInput)
                            .setPositiveButton("সেভ") { _, _ ->
                                prefs.setOtaServerUrl(urlInput.text.toString())
                                Toast.makeText(this, "সার্ভার URL সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("বাতিল", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showRelockModePicker() {
        val options = arrayOf(
            "অ্যাপ থেকে বের হলেই সাথে সাথে আবার লক হবে (সুপারিশকৃত)",
            "ফোনের স্ক্রিন বন্ধ না করা পর্যন্ত আনলক থাকবে",
            "আনলক করার পর ৩ মিনিট খোলা থাকবে"
        )
        val modes = arrayOf(
            PrefsManager.RELOCK_IMMEDIATE,
            PrefsManager.RELOCK_SCREEN_OFF,
            PrefsManager.RELOCK_GRACE_3M
        )
        AlertDialog.Builder(this)
            .setTitle("পুনরায় লক হওয়ার সময়")
            .setItems(options) { _, idx ->
                prefs.setRelockMode(modes[idx])
                showDashboard()
            }
            .show()
    }

    private fun openCredentialCreatorDialog(title: String, onPicked: (String, String) -> Unit) {
        val choices = arrayOf("৪–৬ ডিজিট পিন (PIN)", "৩x৩ প্যাটার্ন (Pattern)", "টেক্সট পাসওয়ার্ড (Password)")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(choices) { _, idx ->
                val type = when (idx) {
                    0 -> PrefsManager.TYPE_PIN
                    1 -> PrefsManager.TYPE_PATTERN
                    else -> PrefsManager.TYPE_PASSWORD
                }
                promptSpecificTypeSetup(type, title) { secret -> onPicked(secret, type) }
            }
            .show()
    }

    private fun promptSpecificTypeSetup(type: String, title: String, onConfirmed: (String) -> Unit) {
        if (type == PrefsManager.TYPE_PATTERN) {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(40, 32, 40, 24)
            }
            val infoTv = TextView(this).apply {
                text = "কমপক্ষে ৪টি বিন্দু যুক্ত করে নতুন প্যাটার্ন আঁকুন"
                textSize = 13f
                setPadding(0, 0, 0, 16)
            }
            val pv = PatternLockView(this)
            layout.addView(infoTv)
            layout.addView(pv)
            val dlg = AlertDialog.Builder(this)
                .setTitle(title)
                .setView(layout)
                .setNegativeButton("বাতিল", null)
                .create()
            pv.listener = object : PatternLockView.Listener {
                override fun onPatternComplete(pattern: String) {
                    dlg.dismiss()
                    onConfirmed(pattern)
                }
            }
            dlg.show()
            return
        }

        val input = EditText(this).apply {
            if (type == PrefsManager.TYPE_PIN) {
                hint = "৪ থেকে ৬ সংখ্যার পিন দিন"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            } else {
                hint = "কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("সেভ করুন") { _, _ ->
                val txt = input.text.toString()
                val minLen = 4
                if (txt.length < minLen || (type == PrefsManager.TYPE_PIN && txt.length > 6)) {
                    Toast.makeText(
                        this,
                        if (type == PrefsManager.TYPE_PIN) "৪ থেকে ৬ ডিজিটের পিন দিন" else "কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onConfirmed(txt)
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // 5. OTA UPDATE & PERMISSIONS HELPERS
    // -------------------------------------------------------------------------
    private fun autoCheckUpdatesOnLaunch() {
        val url = prefs.getOtaServerUrl()
        lifecycleScope.launch {
            try {
                UpdateManager.syncRemotePolicies(url, prefs)
                val result = UpdateManager.checkForUpdate(url)
                if (result.hasUpdate && result.apkDownloadUrl != null) {
                    pendingUpdateResult = result
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
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("নতুন আপডেট (${result.latestVersionName})")
                        .setMessage("${result.changelog}\n\nনিচের বাটনে ক্লিক করলে আপডেট ডাউনলোড শুরু হবে।")
                        .setPositiveButton("এখনই আপডেট করুন") { _, _ ->
                            lifecycleScope.launch {
                                UpdateManager.downloadAndInstallApk(this@MainActivity, result.apkDownloadUrl)
                            }
                        }
                        .setNegativeButton("পরে", null)
                        .show()
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
