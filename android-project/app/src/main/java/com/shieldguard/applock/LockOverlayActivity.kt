package com.shieldguard.applock

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        targetPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        lockMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_APP_LOCK
        val usedMins = intent.getIntExtra(EXTRA_USED_MINUTES, 0)
        val limitMins = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(targetPkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            targetPkg
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0F17"))
            setPadding(64, 64, 64, 64)
        }

        val title = TextView(this).apply {
            text = if (lockMode == MODE_LIMIT_REACHED) {
                "$appLabel — দৈনিক সময়সীমা শেষ"
            } else {
                "$appLabel লক করা আছে"
            }
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = if (lockMode == MODE_LIMIT_REACHED) {
                "আজকের ব্যবহার: $usedMins মি. / লিমিট: $limitMins মি.\nআনলক করতে ভল্ট মাস্টার পাসওয়ার্ড দিন"
            } else if (prefs.isSeparateAppLockEnabled()) {
                "খুলতে আপনার আলাদা অ্যাপ-লক পাসওয়ার্ড দিন"
            } else {
                "খুলতে আপনার পাসওয়ার্ড দিন"
            }
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 36)
        }

        val passwordInput = EditText(this).apply {
            hint = if (lockMode == MODE_LIMIT_REACHED) "ভল্ট মাস্টার পাসওয়ার্ড" else "অ্যাপ-লক পাসওয়ার্ড"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundColor(Color.parseColor("#161D2B"))
            setPadding(36, 32, 36, 32)
        }

        val unlockBtn = Button(this).apply {
            text = "আনলক করুন"
            setOnClickListener {
                val entered = passwordInput.text.toString()
                val valid = if (lockMode == MODE_LIMIT_REACHED) {
                    prefs.verifyVaultPassword(entered)
                } else {
                    prefs.verifyAppLockPassword(entered)
                }

                if (valid) {
                    if (lockMode == MODE_LIMIT_REACHED) {
                        prefs.grantTemporaryUnlock("${targetPkg}_limit_override", 10 * 60 * 1000L)
                    } else {
                        prefs.grantTemporaryUnlock(targetPkg, 3 * 60 * 1000L)
                    }
                    finish()
                } else {
                    Toast.makeText(this@LockOverlayActivity, "ভুল পাসওয়ার্ড!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val homeBtn = Button(this).apply {
            text = "হোম স্ক্রিনে ফিরে যান"
            setOnClickListener { goHome() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(passwordInput)
        root.addView(unlockBtn)
        root.addView(homeBtn)
        setContentView(root)
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
