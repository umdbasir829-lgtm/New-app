package com.shieldguard.applock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LockCredential(
    val type: String, // TYPE_PIN | TYPE_PATTERN | TYPE_PASSWORD
    val hash: String,
    val allowBiometric: Boolean = false,
    val pinLength: Int = 0
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
        private const val KEY_VAULT_PIN_LEN = "vault_pin_length"
        private const val KEY_APPLOCK_HASH = "applock_password_hash"
        private const val KEY_APPLOCK_TYPE = "applock_lock_type"
        private const val KEY_APPLOCK_PIN_LEN = "applock_pin_length"
        private const val KEY_SEPARATE_PASSWORDS = "use_separate_passwords"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_ENROLLED_APP = "biometric_enrolled_in_app"
        private const val KEY_BIOMETRIC_FOR_VAULT = "biometric_for_vault"
        private const val KEY_BIOMETRIC_FOR_APPS = "biometric_for_apps"
        private const val KEY_BIOMETRIC_ENROLLED_AT = "biometric_enrolled_at"
        private const val KEY_RELOCK_MODE = "relock_mode"
        private const val KEY_LOCKED_APPS = "locked_apps_set"
        private const val KEY_PER_APP_LOCKS_JSON = "per_app_locks_json"
        private const val KEY_LIMITS_JSON = "daily_limits_json"
        private const val KEY_USAGE_DAY_DATE = "usage_tracking_date"
        private const val KEY_USAGE_SECONDS_JSON = "usage_seconds_today_json"
        private const val KEY_BONUS_LIMITS_DATE = "bonus_limits_date"
        private const val KEY_BONUS_LIMITS_JSON = "bonus_limits_today_json"
        private const val KEY_UNLOCKED_UNTIL = "temp_unlocked_until_json"
        private const val KEY_OTA_URL = "ota_server_url"
        private const val KEY_RUNTIME_CONFIG_JSON = "ota_runtime_config_json"
        private const val KEY_LAST_POLICY_REVISION = "last_synced_policy_revision"
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
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

    fun getLastSyncedPolicyRevision(): Int = prefs.getInt(KEY_LAST_POLICY_REVISION, 0)

    fun setLastSyncedPolicyRevision(rev: Int) {
        prefs.edit().putInt(KEY_LAST_POLICY_REVISION, rev).apply()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Vault Master Lock ---
    fun isVaultPasswordSet(): Boolean = prefs.getString(KEY_VAULT_HASH, null) != null

    fun getVaultLockType(): String = prefs.getString(KEY_VAULT_TYPE, TYPE_PIN) ?: TYPE_PIN

    fun getVaultPinLength(): Int {
        if (getVaultLockType() != TYPE_PIN) return 0
        return prefs.getInt(KEY_VAULT_PIN_LEN, 0)
    }

    fun setVaultCredential(rawSecret: String, type: String = TYPE_PIN) {
        val editor = prefs.edit()
            .putString(KEY_VAULT_HASH, sha256(rawSecret))
            .putString(KEY_VAULT_TYPE, type)
        if (type == TYPE_PIN) {
            editor.putInt(KEY_VAULT_PIN_LEN, rawSecret.length)
        } else {
            editor.remove(KEY_VAULT_PIN_LEN)
        }
        editor.apply()
    }

    fun setVaultPassword(rawPassword: String) = setVaultCredential(rawPassword, TYPE_PASSWORD)

    fun verifyVaultPassword(rawSecret: String): Boolean {
        val saved = prefs.getString(KEY_VAULT_HASH, null) ?: return false
        val ok = saved == sha256(rawSecret)
        if (ok && getVaultLockType() == TYPE_PIN && prefs.getInt(KEY_VAULT_PIN_LEN, 0) <= 0) {
            prefs.edit().putInt(KEY_VAULT_PIN_LEN, rawSecret.length).apply()
        }
        return ok
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

    fun getGlobalAppPinLength(): Int {
        if (!isSeparateAppLockEnabled()) return getVaultPinLength()
        if (getGlobalAppLockType() != TYPE_PIN) return 0
        val hasAppHash = prefs.getString(KEY_APPLOCK_HASH, null) != null
        return if (hasAppHash) prefs.getInt(KEY_APPLOCK_PIN_LEN, 0) else getVaultPinLength()
    }

    fun setAppLockCredential(rawSecret: String, type: String = TYPE_PIN) {
        val editor = prefs.edit()
            .putString(KEY_APPLOCK_HASH, sha256(rawSecret))
            .putString(KEY_APPLOCK_TYPE, type)
        if (type == TYPE_PIN) {
            editor.putInt(KEY_APPLOCK_PIN_LEN, rawSecret.length)
        } else {
            editor.remove(KEY_APPLOCK_PIN_LEN)
        }
        editor.apply()
    }

    fun setAppLockPassword(rawPassword: String) = setAppLockCredential(rawPassword, TYPE_PASSWORD)

    fun verifyGlobalAppLock(rawSecret: String): Boolean {
        if (!isSeparateAppLockEnabled()) return verifyVaultPassword(rawSecret)
        val saved = prefs.getString(KEY_APPLOCK_HASH, null) ?: return verifyVaultPassword(rawSecret)
        val ok = saved == sha256(rawSecret)
        if (ok && getGlobalAppLockType() == TYPE_PIN && prefs.getInt(KEY_APPLOCK_PIN_LEN, 0) <= 0) {
            prefs.edit().putInt(KEY_APPLOCK_PIN_LEN, rawSecret.length).apply()
        }
        return ok
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
        val pinLen = obj.optInt("pinLength", 0)
        if (type.isBlank() || hash.isBlank()) return null
        return LockCredential(type, hash, allowBio, pinLen)
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
            if (type == TYPE_PIN) put("pinLength", rawSecret.length)
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

    fun getEffectivePinLength(packageName: String): Int {
        val custom = getCustomAppLock(packageName)
        if (custom != null) {
            return if (custom.type == TYPE_PIN) custom.pinLength else 0
        }
        return getGlobalAppPinLength()
    }

    fun verifyAppUnlock(packageName: String, rawSecret: String): Boolean {
        val custom = getCustomAppLock(packageName)
        if (custom != null) {
            val ok = custom.hash == sha256(rawSecret)
            if (ok && custom.type == TYPE_PIN && custom.pinLength <= 0) {
                val root = getPerAppLocksObject()
                root.optJSONObject(packageName)?.let { obj ->
                    obj.put("pinLength", rawSecret.length)
                    root.put(packageName, obj)
                    prefs.edit().putString(KEY_PER_APP_LOCKS_JSON, root.toString()).apply()
                }
            }
            return ok
        }
        return verifyGlobalAppLock(rawSecret)
    }

    // --- Biometric (In-App Enrollment + Targets) & Relock Mode ---
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnrolledInApp(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENROLLED_APP, false)

    fun getBiometricEnrolledAt(): Long = prefs.getLong(KEY_BIOMETRIC_ENROLLED_AT, 0L)

    fun markBiometricEnrolledInApp(enrolled: Boolean) {
        val edit = prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENROLLED_APP, enrolled)
            .putBoolean(KEY_BIOMETRIC_ENABLED, enrolled)
        if (enrolled) {
            edit.putLong(KEY_BIOMETRIC_ENROLLED_AT, System.currentTimeMillis())
            if (!prefs.contains(KEY_BIOMETRIC_FOR_VAULT)) edit.putBoolean(KEY_BIOMETRIC_FOR_VAULT, true)
            if (!prefs.contains(KEY_BIOMETRIC_FOR_APPS)) edit.putBoolean(KEY_BIOMETRIC_FOR_APPS, true)
        } else {
            edit.remove(KEY_BIOMETRIC_ENROLLED_AT)
        }
        edit.apply()
    }

    fun isBiometricForVault(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_FOR_VAULT, true)

    fun setBiometricForVault(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_FOR_VAULT, enabled).apply()
    }

    fun isBiometricForApps(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_FOR_APPS, true)

    fun setBiometricForApps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_FOR_APPS, enabled).apply()
    }

    fun isBiometricAllowedForVault(): Boolean {
        return isBiometricEnabled() && isBiometricForVault()
    }

    fun isBiometricAllowedForApp(packageName: String): Boolean {
        if (!isBiometricEnabled() || !isBiometricForApps()) return false
        val custom = getCustomAppLock(packageName) ?: return true
        return custom.allowBiometric
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
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
        val map = mutableMapOf<String, Int>()
        obj.keys().forEach { key ->
            map[key] = obj.optInt(key, 0)
        }
        return map
    }

    fun setDailyLimit(packageName: String, minutes: Int) {
        val map = getDailyLimits()
        if (minutes <= 0) {
            map.remove(packageName)
            clearBonusMinutesToday(packageName)
        } else {
            map[packageName] = minutes
        }
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_LIMITS_JSON, obj.toString()).apply()
    }

    // --- Exact Per-Second Today Usage & Today-Only Bonus Limit ---
    private fun getTodayUsageSecondsObject(): JSONObject {
        val today = todayDateKey()
        val savedDate = prefs.getString(KEY_USAGE_DAY_DATE, "")
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_USAGE_DAY_DATE, today)
                .putString(KEY_USAGE_SECONDS_JSON, "{}")
                .apply()
            return JSONObject()
        }
        val raw = prefs.getString(KEY_USAGE_SECONDS_JSON, "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun getAllAppUsageSecondsToday(): MutableMap<String, Int> {
        val obj = getTodayUsageSecondsObject()
        val map = mutableMapOf<String, Int>()
        obj.keys().forEach { key ->
            val v = obj.optInt(key, 0)
            if (v > 0) map[key] = v
        }
        return map
    }

    fun getAppUsageSecondsToday(packageName: String): Int {
        return getTodayUsageSecondsObject().optInt(packageName, 0)
    }

    fun setAppUsageSecondsToday(packageName: String, seconds: Int) {
        val obj = getTodayUsageSecondsObject()
        obj.put(packageName, maxOf(0, seconds))
        prefs.edit()
            .putString(KEY_USAGE_DAY_DATE, todayDateKey())
            .putString(KEY_USAGE_SECONDS_JSON, obj.toString())
            .apply()
    }

    fun saveAllAppUsageSecondsToday(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            if (v > 0) obj.put(k, v)
        }
        prefs.edit()
            .putString(KEY_USAGE_DAY_DATE, todayDateKey())
            .putString(KEY_USAGE_SECONDS_JSON, obj.toString())
            .apply()
    }

    private fun getBonusLimitsObject(): JSONObject {
        val today = todayDateKey()
        val savedDate = prefs.getString(KEY_BONUS_LIMITS_DATE, "")
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_BONUS_LIMITS_DATE, today)
                .putString(KEY_BONUS_LIMITS_JSON, "{}")
                .apply()
            return JSONObject()
        }
        val raw = prefs.getString(KEY_BONUS_LIMITS_JSON, "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun getBonusMinutesToday(packageName: String): Int {
        return maxOf(0, getBonusLimitsObject().optInt(packageName, 0))
    }

    fun addBonusMinutesToday(packageName: String, extraMinutes: Int) {
        if (extraMinutes <= 0) return
        val obj = getBonusLimitsObject()
        val cur = maxOf(0, obj.optInt(packageName, 0))
        obj.put(packageName, cur + extraMinutes)
        prefs.edit()
            .putString(KEY_BONUS_LIMITS_DATE, todayDateKey())
            .putString(KEY_BONUS_LIMITS_JSON, obj.toString())
            .apply()
    }

    fun clearBonusMinutesToday(packageName: String) {
        val obj = getBonusLimitsObject()
        obj.remove(packageName)
        prefs.edit()
            .putString(KEY_BONUS_LIMITS_DATE, todayDateKey())
            .putString(KEY_BONUS_LIMITS_JSON, obj.toString())
            .apply()
    }

    fun getEffectiveDailyLimitMinutes(packageName: String): Int {
        val base = getDailyLimits()[packageName] ?: 0
        if (base <= 0) return 0
        return base + getBonusMinutesToday(packageName)
    }

    fun getEffectiveDailyLimitSeconds(packageName: String): Int {
        return getEffectiveDailyLimitMinutes(packageName) * 60
    }

    // --- Temporary & Session Unlocks ---
    fun grantTemporaryUnlock(packageName: String, durationMillis: Long = 3 * 60 * 1000L) {
        val obj = try {
            JSONObject(prefs.getString(KEY_UNLOCKED_UNTIL, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        obj.put(packageName, System.currentTimeMillis() + durationMillis)
        prefs.edit().putString(KEY_UNLOCKED_UNTIL, obj.toString()).apply()
    }

    fun clearAllSessionUnlocks() {
        prefs.edit().putString(KEY_UNLOCKED_UNTIL, "{}").apply()
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val obj = try {
            JSONObject(prefs.getString(KEY_UNLOCKED_UNTIL, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
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
