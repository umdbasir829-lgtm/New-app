package com.shieldguard.applock

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsManager(this)
        val url = prefs.getOtaServerUrl()

        window.statusBarColor = UiStyleHelper.BG_CANVAS
        window.navigationBarColor = UiStyleHelper.BG_CANVAS

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiStyleHelper.BG_CANVAS)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(UiStyleHelper.BG_CARD)
            setPadding(
                UiStyleHelper.dp(this@WebViewActivity, 16),
                UiStyleHelper.dp(this@WebViewActivity, 12),
                UiStyleHelper.dp(this@WebViewActivity, 16),
                UiStyleHelper.dp(this@WebViewActivity, 12)
            )
        }

        val backBtn = Button(this).apply {
            text = "← ফিরুন"
            isAllCaps = false
            textSize = 13f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(this@WebViewActivity, UiStyleHelper.BG_ELEVATED, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                UiStyleHelper.dp(this@WebViewActivity, 38)
            ).apply { rightMargin = UiStyleHelper.dp(this@WebViewActivity, 12) }
            setOnClickListener { finish() }
        }

        val titleTv = TextView(this).apply {
            text = "ShieldGuard লাইভ ওয়েব (অটো সিঙ্ক)"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val reloadBtn = Button(this).apply {
            text = "রিফ্রেশ"
            isAllCaps = false
            textSize = 12f
            setTextColor(UiStyleHelper.TEXT_PRIMARY)
            background = UiStyleHelper.roundedBox(this@WebViewActivity, UiStyleHelper.ACCENT_BLUE, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                UiStyleHelper.dp(this@WebViewActivity, 38)
            )
            setOnClickListener { webView.reload() }
        }

        topBar.addView(backBtn)
        topBar.addView(titleTv)
        topBar.addView(reloadBtn)

        root.addView(topBar)
        root.addView(webView)
        setContentView(root)

        webView.loadUrl(url)
    }
}
