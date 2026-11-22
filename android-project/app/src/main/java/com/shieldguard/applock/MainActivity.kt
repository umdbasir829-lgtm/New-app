package com.shieldguard.applock

import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private var vaultUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        if (!prefs.isVaultPasswordSet()) {
            showInitialPasswordSetup()
        } else if (!vaultUnlocked) {
            showVaultLogin()
        } else {
            showDashboard()
        }
    }

    private fun showInitialPasswordSetup() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(56, 56, 56, 56)
        }

        val header = TextView(this).apply {
            text = "ShieldGuard সেটআপ — দুটি আলাদা পাসওয়ার্ড"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        }

        val vaultInput = EditText(this).apply {
            hint = "১) ভল্ট অ্যাপ খোলার মাস্টার পাসওয়ার্ড"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val appLockInput = EditText(this).apply {
            hint = "২) মোবাইলের লক করা অ্যাপ খোলার পাসওয়ার্ড (আলাদা)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val saveBtn = Button(this).apply {
            text = "সেটআপ সম্পন্ন করুন"
            setOnClickListener {
                val vPass = vaultInput.text.toString()
                val aPass = appLockInput.text.toString()
                if (vPass.length < 4 || aPass.length < 4) {
                    Toast.makeText(this@MainActivity, "কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.setVaultPassword(vPass)
                prefs.setAppLockPassword(aPass)
                prefs.setSeparateAppLockEnabled(true)
                vaultUnlocked = true
                showDashboard()
            }
        }

        root.addView(header)
        root.addView(vaultInput)
        root.addView(appLockInput)
        root.addView(saveBtn)
        setContentView(root)
    }

    private fun showVaultLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(56, 56, 56, 56)
        }

        val title = TextView(this).apply {
            text = "ShieldGuard ভল্ট আনলক"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        }

        val passInput = EditText(this).apply {
            hint = "ভল্ট মাস্টার পাসওয়ার্ড লিখুন"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val btn = Button(this).apply {
            text = "প্রবেশ করুন"
            setOnClickListener {
                if (prefs.verifyVaultPassword(passInput.text.toString())) {
                    vaultUnlocked = true
                    showDashboard()
                } else {
                    Toast.makeText(this@MainActivity, "ভুল ভল্ট পাসওয়ার্ড!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(title)
        root.addView(passInput)
        root.addView(btn)
        setContentView(root)
    }

    private fun showDashboard() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F17"))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 64)
        }

        val title = TextView(this).apply {
            text = "ShieldGuard — অ্যাপ লক ও টাইম লিমিট (v${BuildConfig.VERSION_NAME})"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)

        // Auto Update Button
        val updateBtn = Button(this).apply {
            text = "অটো আপডেট চেক ও ইনস্টল করুন"
            setOnClickListener {
                runAutoUpdateCheck()
            }
        }
        container.addView(updateBtn)

        // Permissions Section if needed
        if (!hasUsageStatsPermission()) {
            val usageBtn = Button(this).apply {
                text = "অনুমতি দিন: Usage Access (সময় মাপার জন্য)"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            container.addView(usageBtn)
        }

        if (!Settings.canDrawOverlays(this)) {
            val overlayBtn = Button(this).apply {
                text = "অনুমতি দিন: Display Over Other Apps"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            container.addView(overlayBtn)
        }

        val accBtn = Button(this).apply {
            text = "Accessibility Service চালু করুন (লক এনফোর্স করতে)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        container.addView(accBtn)

        val passSettingsBtn = Button(this).apply {
            text = "ভল্ট ও অ্যাপ-লক পাসওয়ার্ড পরিবর্তন"
            setOnClickListener { showPasswordChangeDialog() }
        }
        container.addView(passSettingsBtn)

        // Detected Apps List
        val apps = AppDetector.getAllLaunchableApps(this, prefs)
        val countLabel = TextView(this).apply {
            text = "ডিটেক্ট করা মোবাইল অ্যাপ: ${apps.size}টি"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 28, 0, 12)
        }
        container.addView(countLabel)

        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(96, 96)
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 0, 16, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameTv = TextView(this).apply {
                text = app.appName
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            val metaTv = TextView(this).apply {
                val limitText = if (app.dailyLimitMinutes > 0) "${app.dailyLimitMinutes} মি. লিমিট" else "কোনো লিমিট নেই"
                text = "আজ ব্যবহার: ${app.usedMinutesToday} মি. · $limitText"
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
            }
            infoCol.addView(nameTv)
            infoCol.addView(metaTv)

            val limitBtn = Button(this).apply {
                text = if (app.dailyLimitMinutes > 0) "${app.dailyLimitMinutes}m" else "লিমিট"
                setOnClickListener { showLimitPickerDialog(app) }
            }

            val lockSwitch = Switch(this).apply {
                isChecked = app.isLocked
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.setAppLocked(app.packageName, isChecked)
                }
            }

            row.addView(iconView)
            row.addView(infoCol)
            row.addView(limitBtn)
            row.addView(lockSwitch)
            container.addView(row)
        }

        scroll.addView(container)
        setContentView(scroll)
    }

    private fun showLimitPickerDialog(app: InstalledAppInfo) {
        val options = arrayOf("লিমিট বন্ধ", "১৫ মিনিট", "৩০ মিনিট", "৬০ মিনিট (১ ঘণ্টা)", "১২০ মিনিট (২ ঘণ্টা)")
        val values = intArrayOf(0, 15, 30, 60, 120)
        AlertDialog.Builder(this)
            .setTitle("${app.appName} — দৈনিক সময়সীমা")
            .setItems(options) { _, idx ->
                prefs.setDailyLimit(app.packageName, values[idx])
                showDashboard()
            }
            .show()
    }

    private fun showPasswordChangeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val separateSwitch = Switch(this).apply {
            text = "অ্যাপ খোলার ও লক করা অ্যাপের পাসওয়ার্ড আলাদা রাখুন"
            isChecked = prefs.isSeparateAppLockEnabled()
        }
        val vaultEt = EditText(this).apply {
            hint = "নতুন ভল্ট পাসওয়ার্ড (ফাঁকা রাখলে আগেরটাই থাকবে)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val appLockEt = EditText(this).apply {
            hint = "নতুন অ্যাপ-লক পাসওয়ার্ড (ফাঁকা রাখলে আগেরটাই থাকবে)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(separateSwitch)
        layout.addView(vaultEt)
        layout.addView(appLockEt)

        AlertDialog.Builder(this)
            .setTitle("ডুয়াল পাসওয়ার্ড সেটিংস")
            .setView(layout)
            .setPositiveButton("সেভ করুন") { _, _ ->
                prefs.setSeparateAppLockEnabled(separateSwitch.isChecked)
                val v = vaultEt.text.toString()
                val a = appLockEt.text.toString()
                if (v.length >= 4) prefs.setVaultPassword(v)
                if (a.length >= 4) prefs.setAppLockPassword(a)
                Toast.makeText(this, "পাসওয়ার্ড আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun runAutoUpdateCheck() {
        val url = prefs.getOtaServerUrl()
        Toast.makeText(this, "আপডেট ও রুলস চেক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val synced = UpdateManager.syncRemotePolicies(url, prefs)
                val result = UpdateManager.checkForUpdate(url)
                if (result.hasUpdate && result.apkDownloadUrl != null) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("নতুন আপডেট পাওয়া গেছে (${result.latestVersionName})")
                        .setMessage("${result.changelog}\n\nনিচের বাটনে ক্লিক করলে অটোমেটিক ডাউনলোড ও ইনস্টল শুরু হবে।")
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
                        "অ্যাপ সর্বশেষ সংস্করণে (${result.latestVersionName}) আছে · $synced টি রুল সিঙ্ক হয়েছে",
                        Toast.LENGTH_LONG
                    ).show()
                    showDashboard()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "সার্ভার সংযোগ ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
