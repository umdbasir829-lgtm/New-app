package com.shieldguard.applock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val latestVersionName: String,
    val latestVersionCode: Int,
    val changelog: String,
    val apkDownloadUrl: String?
)

object UpdateManager {

    suspend fun checkForUpdate(serverBaseUrl: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val endpoint = "$serverBaseUrl/api/updates/latest"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val latestCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
        val latestName = json.optString("versionName", BuildConfig.VERSION_NAME)
        val changelog = json.optString("changelog", "")
        val apkUrl = json.optString("apkUrl", "").takeIf { it.isNotBlank() }

        UpdateCheckResult(
            hasUpdate = latestCode > BuildConfig.VERSION_CODE,
            latestVersionName = latestName,
            latestVersionCode = latestCode,
            changelog = changelog,
            apkDownloadUrl = apkUrl
        )
    }

    suspend fun syncRemotePolicies(serverBaseUrl: String, prefs: PrefsManager): Int = withContext(Dispatchers.IO) {
        val endpoint = "$serverBaseUrl/api/state"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val apps = json.optJSONArray("apps") ?: return@withContext 0
        var syncedCount = 0
        for (i in 0 until apps.length()) {
            val item = apps.getJSONObject(i)
            val pkg = item.optString("packageName")
            if (pkg.isBlank()) continue
            prefs.setAppLocked(pkg, item.optBoolean("isLocked", false))
            prefs.setDailyLimit(pkg, item.optInt("dailyLimitMinutes", 0))
            syncedCount++
        }
        syncedCount
    }

    suspend fun downloadAndInstallApk(context: Context, apkUrl: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
            }
            return@withContext
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "ShieldGuard-update.apk")

        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 30000
        }
        conn.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                input.copyTo(output)
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        withContext(Dispatchers.Main) {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
    }
}
