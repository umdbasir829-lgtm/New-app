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

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/umdbasir829-lgtm/New-app/releases/latest"

    suspend fun checkForUpdate(serverBaseUrl: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        // 1. Check OTA server manifest
        var otaResult: UpdateCheckResult? = null
        try {
            val endpoint = "$serverBaseUrl/api/updates/latest"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val latestCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
                val latestName = json.optString("versionName", BuildConfig.VERSION_NAME)
                val changelog = json.optString("changelog", "")
                val apkUrl = json.optString("apkUrl", "").takeIf { it.isNotBlank() }
                otaResult = UpdateCheckResult(
                    hasUpdate = latestCode > BuildConfig.VERSION_CODE,
                    latestVersionName = latestName,
                    latestVersionCode = latestCode,
                    changelog = changelog,
                    apkDownloadUrl = apkUrl
                )
            }
        } catch (_: Exception) {}

        if (otaResult != null && otaResult.hasUpdate) {
            return@withContext otaResult
        }

        // 2. Fallback: Check GitHub Releases API directly (works automatically when repo is Public)
        try {
            val ghConn = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ShieldGuardAppLock")
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (ghConn.responseCode in 200..299) {
                val body = ghConn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val notes = json.optString("body", "নতুন GitHub রিলিজ পাওয়া গেছে।")
                val assets = json.optJSONArray("assets")
                var apkAssetUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkAssetUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                val parsedBuild = if (tag.contains("-b")) {
                    tag.substringAfter("-b").substringBefore(".").toIntOrNull() ?: 0
                } else {
                    0
                }
                if (apkAssetUrl != null && parsedBuild > BuildConfig.VERSION_CODE) {
                    return@withContext UpdateCheckResult(
                        hasUpdate = true,
                        latestVersionName = tag.substringBefore("-b").ifBlank { "New Release" },
                        latestVersionCode = parsedBuild,
                        changelog = notes,
                        apkDownloadUrl = apkAssetUrl
                    )
                }
            }
        } catch (_: Exception) {}

        otaResult ?: UpdateCheckResult(
            hasUpdate = false,
            latestVersionName = BuildConfig.VERSION_NAME,
            latestVersionCode = BuildConfig.VERSION_CODE,
            changelog = "",
            apkDownloadUrl = null
        )
    }

    suspend fun syncRemotePolicies(serverBaseUrl: String, prefs: PrefsManager): Int = withContext(Dispatchers.IO) {
        var syncedCount = 0
        try {
            val stateEndpoint = "$serverBaseUrl/api/state"
            val conn = (URL(stateEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val manifest = json.optJSONObject("releaseManifest")
                val serverPolicyRev = manifest?.optInt("policyRevision", 0) ?: 0
                val lastRev = prefs.getLastSyncedPolicyRevision()
                val apps = json.optJSONArray("apps")
                if (apps != null && serverPolicyRev > lastRev) {
                    for (i in 0 until apps.length()) {
                        val item = apps.getJSONObject(i)
                        val pkg = item.optString("packageName")
                        if (pkg.isBlank()) continue
                        prefs.setAppLocked(pkg, item.optBoolean("isLocked", false))
                        prefs.setDailyLimit(pkg, item.optInt("dailyLimitMinutes", 0))
                        syncedCount++
                    }
                    prefs.setLastSyncedPolicyRevision(serverPolicyRev)
                }
                val rc = json.optJSONObject("runtimeConfig")
                if (rc != null && prefs.saveRuntimeConfig(rc)) {
                    syncedCount++
                }
            }
        } catch (_: Exception) {}

        try {
            val cfgEndpoint = "$serverBaseUrl/api/ota/runtime-config"
            val cfgConn = (URL(cfgEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (cfgConn.responseCode in 200..299) {
                val body = cfgConn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (prefs.saveRuntimeConfig(json)) {
                    syncedCount++
                }
            }
        } catch (_: Exception) {}

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

        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "ShieldGuard-update.apk")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
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
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
        }
    }
}
