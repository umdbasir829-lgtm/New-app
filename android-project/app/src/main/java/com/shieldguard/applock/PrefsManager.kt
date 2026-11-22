package com.shieldguard.applock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.security.MessageDigest

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "shieldguard_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_VAULT_HASH = "vault_password_hash"
        private const val KEY_APPLOCK_HASH = "applock_password_hash"
        private const val KEY_SEPARATE_PASSWORDS = "use_separate_passwords"
        private const val KEY_LOCKED_APPS = "locked_apps_set"
        private const val KEY_LIMITS_JSON = "daily_limits_json"
        private const val KEY_UNLOCKED_UNTIL = "temp_unlocked_until_json"
        private const val KEY_OTA_URL = "ota_server_url"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isVaultPasswordSet(): Boolean = prefs.getString(KEY_VAULT_HASH, null) != null

    fun setVaultPassword(rawPassword: String) {
        prefs.edit().putString(KEY_VAULT_HASH, sha256(rawPassword)).apply()
    }

    fun verifyVaultPassword(rawPassword: String): Boolean {
        val saved = prefs.getString(KEY_VAULT_HASH, null) ?: return false
        return saved == sha256(rawPassword)
    }

    fun isSeparateAppLockEnabled(): Boolean = prefs.getBoolean(KEY_SEPARATE_PASSWORDS, true)

    fun setSeparateAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEPARATE_PASSWORDS, enabled).apply()
    }

    fun isAppLockPasswordSet(): Boolean = prefs.getString(KEY_APPLOCK_HASH, null) != null

    fun setAppLockPassword(rawPassword: String) {
        prefs.edit().putString(KEY_APPLOCK_HASH, sha256(rawPassword)).apply()
    }

    fun verifyAppLockPassword(rawPassword: String): Boolean {
        if (!isSeparateAppLockEnabled()) {
            return verifyVaultPassword(rawPassword)
        }
        val saved = prefs.getString(KEY_APPLOCK_HASH, null) ?: return verifyVaultPassword(rawPassword)
        return saved == sha256(rawPassword)
    }

    fun getLockedApps(): Set<String> {
        return prefs.getStringSet(KEY_LOCKED_APPS, emptySet())?.toSet() ?: emptySet()
    }

    fun setAppLocked(packageName: String, locked: Boolean) {
        val current = getLockedApps().toMutableSet()
        if (locked) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet(KEY_LOCKED_APPS, current).apply()
    }

    fun getDailyLimits(): MutableMap<String, Int> {
        val raw = prefs.getString(KEY_LIMITS_JSON, "{}") ?: "{}"
        val obj = JSONObject(raw)
        val map = mutableMapOf<String, Int>()
        obj.keys().forEach { key ->
            map[key] = obj.optInt(key, 0)
        }
        return map
    }

    fun setDailyLimit(packageName: String, minutes: Int) {
        val map = getDailyLimits()
        if (minutes <= 0) map.remove(packageName) else map[packageName] = minutes
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_LIMITS_JSON, obj.toString()).apply()
    }

    fun grantTemporaryUnlock(packageName: String, durationMillis: Long = 3 * 60 * 1000L) {
        val obj = JSONObject(prefs.getString(KEY_UNLOCKED_UNTIL, "{}") ?: "{}")
        obj.put(packageName, System.currentTimeMillis() + durationMillis)
        prefs.edit().putString(KEY_UNLOCKED_UNTIL, obj.toString()).apply()
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val obj = JSONObject(prefs.getString(KEY_UNLOCKED_UNTIL, "{}") ?: "{}")
        val expiry = obj.optLong(packageName, 0L)
        return System.currentTimeMillis() < expiry
    }

    fun getOtaServerUrl(): String {
        return prefs.getString(KEY_OTA_URL, BuildConfig.OTA_SERVER_URL) ?: BuildConfig.OTA_SERVER_URL
    }

    fun setOtaServerUrl(url: String) {
        prefs.edit().putString(KEY_OTA_URL, url.trim().trimEnd('/')).apply()
    }
}
