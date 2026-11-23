package com.shieldguard.applock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.security.MessageDigest

data class LockCredential(
    val type: String, // TYPE_PIN | TYPE_PATTERN | TYPE_PASSWORD
    val hash: String,
    val allowBiometric: Boolean = false
)

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
        const val TYPE_PIN = "PIN"
        const val TYPE_PATTERN = "PATTERN"
        const val TYPE_PASSWORD = "PASSWORD"

        const val RELOCK_IMMEDIATE = "IMMEDIATE"
        const val RELOCK_SCREEN_OFF = "SCREEN_OFF"
        const val RELOCK_GRACE_3M = "GRACE_3M"

        private const val KEY_VAULT_HASH = "vault_password_hash"
        private const val KEY_VAULT_TYPE = "vault_lock_type"
        private const val KEY_APPLOCK_HASH = "applock_password_hash"
        private const val KEY_APPLOCK_TYPE = "applock_lock_type"
        private const val KEY_SEPARATE_PASSWORDS = "use_separate_passwords"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_RELOCK_MODE = "relock_mode"
        private const val KEY_LOCKED_APPS = "locked_apps_set"
        private const val KEY_PER_APP_LOCKS_JSON = "per_app_locks_json"
        private const val KEY_LIMITS_JSON = "daily_limits_json"
        private const val KEY_UNLOCKED_UNTIL = "temp_unlocked_until_json"
        private const val KEY_OTA_URL = "ota_server_url"
        private const val KEY_RUNTIME_CONFIG_JSON = "ota_runtime_config_json"
    }

    private fun getRuntimeConfigObject(): JSONObject {
        val raw = prefs.getString(KEY_RUNTIME_CONFIG_JSON, "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun saveRuntimeConfig(json: JSONObject): Boolean {
        val prevRaw = prefs.getString(KEY_RUNTIME_CONFIG_JSON, "{}") ?: "{}"
        val nextRaw = json.toString()
        prefs.edit().putString(KEY_RUNTIME_CONFIG_JSON, nextRaw).apply()
        return prevRaw != nextRaw
    }

    fun getHomeAnnouncement(): String = getRuntimeConfigObject().optString("homeAnnouncement", "")

    fun getLockSubtitle(): String {
        val v = getRuntimeConfigObject().optString("lockSubtitle", "")
        return if (v.isNotBlank()) v else "প্রবেশ করতে আপনার লক যাচাই করুন"
    }

    fun getHeroActiveTitle(): String {
        val v = getRuntimeConfigObject().optString("heroActiveTitle", "")
        return if (v.isNotBlank()) v else "সুরক্ষা সম্পূর্ণ সক্রিয়"
    }

    fun getRemoteAccentBlue(): String = getRuntimeConfigObject().optString("accentBlue", "#3B82F6")

    fun getRemoteAccentGreen(): String = getRuntimeConfigObject().optString("accentGreen", "#10B981")

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Vault Master Lock ---
    fun isVaultPasswordSet(): Boolean = prefs.getString(KEY_VAULT_HASH, null) != null

    fun getVaultLockType(): String = prefs.getString(KEY_VAULT_TYPE, TYPE_PIN) ?: TYPE_PIN

    fun setVaultCredential(rawSecret: String, type: String = TYPE_PIN) {
        prefs.edit()
            .putString(KEY_VAULT_HASH, sha256(rawSecret))
            .putString(KEY_VAULT_TYPE, type)
            .apply()
    }

    fun setVaultPassword(rawPassword: String) = setVaultCredential(rawPassword, TYPE_PASSWORD)

    fun verifyVaultPassword(rawSecret: String): Boolean {
        val saved = prefs.getString(KEY_VAULT_HASH, null) ?: return false
        return saved == sha256(rawSecret)
    }

    // --- Global App-Lock (Separate from Vault) ---
    fun isSeparateAppLockEnabled(): Boolean = prefs.getBoolean(KEY_SEPARATE_PASSWORDS, true)

    fun setSeparateAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEPARATE_PASSWORDS, enabled).apply()
    }

    fun getGlobalAppLockType(): String {
        if (!isSeparateAppLockEnabled()) return getVaultLockType()
        return prefs.getString(KEY_APPLOCK_TYPE, TYPE_PIN) ?: TYPE_PIN
    }

    fun setAppLockCredential(rawSecret: String, type: String = TYPE_PIN) {
        prefs.edit()
            .putString(KEY_APPLOCK_HASH, sha256(rawSecret))
            .putString(KEY_APPLOCK_TYPE, type)
            .apply()
    }

    fun setAppLockPassword(rawPassword: String) = setAppLockCredential(rawPassword, TYPE_PASSWORD)

    fun verifyGlobalAppLock(rawSecret: String): Boolean {
        if (!isSeparateAppLockEnabled()) return verifyVaultPassword(rawSecret)
        val saved = prefs.getString(KEY_APPLOCK_HASH, null) ?: return verifyVaultPassword(rawSecret)
        return saved == sha256(rawSecret)
    }

    // --- Per-App Custom Credential ---
    private fun getPerAppLocksObject(): JSONObject {
        val raw = prefs.getString(KEY_PER_APP_LOCKS_JSON, "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun getCustomAppLock(packageName: String): LockCredential? {
        val root = getPerAppLocksObject()
        val obj = root.optJSONObject(packageName) ?: return null
        val type = obj.optString("type", "")
        val hash = obj.optString("hash", "")
        val allowBio = obj.optBoolean("allowBiometric", false)
        if (type.isBlank() || hash.isBlank()) return null
        return LockCredential(type, hash, allowBio)
    }

    fun hasCustomAppLock(packageName: String): Boolean = getCustomAppLock(packageName) != null

    fun setCustomAppLock(
        packageName: String,
        rawSecret: String,
        type: String,
        allowBiometric: Boolean = false
    ) {
        val root = getPerAppLocksObject()
        val entry = JSONObject().apply {
            put("type", type)
            put("hash", sha256(rawSecret))
            put("allowBiometric", allowBiometric)
        }
        root.put(packageName, entry)
        prefs.edit().putString(KEY_PER_APP_LOCKS_JSON, root.toString()).apply()
        setAppLocked(packageName, true)
    }

    fun setCustomAppBiometric(packageName: String, allowBiometric: Boolean) {
        val root = getPerAppLocksObject()
        val obj = root.optJSONObject(packageName) ?: return
        obj.put("allowBiometric", allowBiometric)
        root.put(packageName, obj)
        prefs.edit().putString(KEY_PER_APP_LOCKS_JSON, root.toString()).apply()
    }

    fun removeCustomAppLock(packageName: String) {
        val root = getPerAppLocksObject()
        root.remove(packageName)
        prefs.edit().putString(KEY_PER_APP_LOCKS_JSON, root.toString()).apply()
    }

    fun getEffectiveLockType(packageName: String): String {
        return getCustomAppLock(packageName)?.type ?: getGlobalAppLockType()
    }

    fun isBiometricAllowedForApp(packageName: String): Boolean {
        if (!isBiometricEnabled()) return false
        val custom = getCustomAppLock(packageName) ?: return true
        return custom.allowBiometric
    }

    fun verifyAppUnlock(packageName: String, rawSecret: String): Boolean {
        val custom = getCustomAppLock(packageName)
        if (custom != null) {
            return custom.hash == sha256(rawSecret)
        }
        return verifyGlobalAppLock(rawSecret)
    }

    // --- Biometric (Explicit Opt-In: Defaults to FALSE) & Relock Mode ---
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getRelockMode(): String = prefs.getString(KEY_RELOCK_MODE, RELOCK_IMMEDIATE) ?: RELOCK_IMMEDIATE

    fun setRelockMode(mode: String) {
        prefs.edit().putString(KEY_RELOCK_MODE, mode).apply()
    }

    // --- Locked Apps & Daily Limits ---
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

    // --- Temporary & Session Unlocks ---
    fun grantTemporaryUnlock(packageName: String, durationMillis: Long = 3 * 60 * 1000L) {
        val obj = JSONObject(prefs.getString(KEY_UNLOCKED_UNTIL, "{}") ?: "{}")
        obj.put(packageName, System.currentTimeMillis() + durationMillis)
        prefs.edit().putString(KEY_UNLOCKED_UNTIL, obj.toString()).apply()
    }

    fun clearAllSessionUnlocks() {
        prefs.edit().putString(KEY_UNLOCKED_UNTIL, "{}").apply()
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
