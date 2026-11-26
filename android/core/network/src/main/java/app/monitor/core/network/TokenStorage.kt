package app.monitor.core.network

import android.content.Context
import android.util.Base64

/**
 * Public token/preference API is intentionally unchanged.
 *
 * Storage internals now delegate to [SecureKeyValueStore] (AES-256-GCM directly
 * over the Android Keystore, see [SecureStorage]). The deprecated
 * EncryptedSharedPreferences file is migrated once, atomically, on first use.
 */
class TokenStorage(context: Context) {
    val appContext: Context = context.applicationContext

    private val store: SecureKeyValueStore = SecureStorage(context)

    fun getDeviceToken(): String? =
        store.getString(KEY_DEVICE_TOKEN)?.takeIf { it.startsWith("mon_dev_") }

    fun isCloudVerified(): Boolean = store.getBoolean(KEY_CLOUD_VERIFIED, false)

    fun markCloudVerified() {
        store.putBoolean(KEY_CLOUD_VERIFIED, true)
    }

    fun isAuthenticated(): Boolean {
        return getDeviceToken() != null || isCloudVerified()
    }

    @Synchronized
    fun getOrCreateNoiseStaticKeyPair(): NoiseStaticKeyPair {
        val privateBytes = store.getString(KEY_NOISE_PRIVATE)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
        val publicBytes = store.getString(KEY_NOISE_PUBLIC)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
        if (privateBytes?.size == 32 && publicBytes?.size == 32) {
            return NoiseStaticKeyPair(privateBytes, publicBytes)
        }
        val generated = NoiseKeyMaterial.generate()
        // Both halves must be committed together: a partial write would leave a
        // valid-looking but mismatched static key pair.
        store.putAll(
            mapOf<String, StoredValue>(
                KEY_NOISE_PRIVATE to StoredValue.StoredText(
                    Base64.encodeToString(generated.privateKey, Base64.NO_WRAP)
                ),
                KEY_NOISE_PUBLIC to StoredValue.StoredText(
                    Base64.encodeToString(generated.publicKey, Base64.NO_WRAP)
                )
            )
        )
        return generated
    }

    @Synchronized
    fun getOrCreateCloudDeviceId(): String {
        store.getString(KEY_CLOUD_DEVICE_ID)?.let { return it }
        val id = "android-${java.util.UUID.randomUUID()}"
        store.putString(KEY_CLOUD_DEVICE_ID, id)
        return id
    }

    /** Publish the endpoint and its token together so reconnect never uses mixed credentials. */
    fun savePairing(host: String, port: Int, token: String) {
        store.putAll(
            mapOf<String, StoredValue>(
                KEY_HOST to StoredValue.StoredText(host),
                KEY_PORT to StoredValue.StoredNumber(port),
                KEY_DEVICE_TOKEN to StoredValue.StoredText(token)
            )
        )
    }

    fun saveDeviceToken(token: String) {
        if (token.isNotBlank()) {
            store.putString(KEY_DEVICE_TOKEN, token)
        }
    }

    fun saveServerEndpoint(host: String, port: Int) {
        store.putAll(
            mapOf<String, StoredValue>(
                KEY_HOST to StoredValue.StoredText(host),
                KEY_PORT to StoredValue.StoredNumber(port)
            )
        )
    }

    fun getServerHost(): String = store.getString(KEY_HOST).orEmpty()

    fun getServerPort(): Int = store.getInt(KEY_PORT, 9480)

    fun getCloudSessionToken(): String? = store.getString(KEY_CLOUD_TOKEN)

    fun getUserEmail(): String? = store.getString(KEY_USER_EMAIL)

    fun getUserName(): String? = store.getString(KEY_USER_NAME)

    fun getUserPicture(): String? = store.getString(KEY_USER_PICTURE)

    fun saveCloudHostDeviceId(hostId: String) {
        if (hostId.isNotBlank()) store.putString(KEY_CLOUD_HOST_ID, hostId)
    }

    fun getCloudHostDeviceId(): String? = store.getString(KEY_CLOUD_HOST_ID)

    fun saveCloudAuth(
        cloudToken: String,
        email: String,
        name: String,
        host: String? = null,
        port: Int? = null,
        picture: String? = null,
        desktopHostId: String? = null,
        verified: Boolean = false
    ) {
        val batch = linkedMapOf<String, StoredValue>(
            KEY_CLOUD_TOKEN to StoredValue.StoredText(cloudToken),
            KEY_USER_EMAIL to StoredValue.StoredText(email),
            KEY_USER_NAME to StoredValue.StoredText(name),
            KEY_CLOUD_VERIFIED to StoredValue.StoredFlag(verified)
        )
        if (!picture.isNullOrBlank()) batch[KEY_USER_PICTURE] = StoredValue.StoredText(picture)
        if (!host.isNullOrBlank()) batch[KEY_HOST] = StoredValue.StoredText(host)
        if (port != null && port > 0) batch[KEY_PORT] = StoredValue.StoredNumber(port)
        if (!desktopHostId.isNullOrBlank()) batch[KEY_CLOUD_HOST_ID] = StoredValue.StoredText(desktopHostId)
        store.putAll(batch)
    }

    fun getThemeMode(): String = store.getString(KEY_THEME_MODE) ?: "dark"

    fun saveThemeMode(mode: String) = store.putString(KEY_THEME_MODE, mode)

    /** NEW-08: persistent binding between a paired LAN host and its static key. */
    fun getHostNoiseKey(host: String, port: Int): String? =
        store.getString("${KEY_HOST_NOISE_KEY}_${host}:${port}")

    fun saveHostNoiseKey(host: String, port: Int, keyHex: String) =
        store.putString("${KEY_HOST_NOISE_KEY}_${host}:${port}", keyHex)

    fun clearHostNoiseKey(host: String, port: Int) =
        store.remove("${KEY_HOST_NOISE_KEY}_${host}:${port}")

    fun isAppLockEnabled(): Boolean = store.getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(enabled: Boolean) = store.putBoolean(KEY_APP_LOCK, enabled)

    fun isDestructiveOpsBiometricGated(): Boolean = store.getBoolean(KEY_DESTRUCTIVE_BIO_GATE, true)

    fun setDestructiveOpsBiometricGated(enabled: Boolean) =
        store.putBoolean(KEY_DESTRUCTIVE_BIO_GATE, enabled)

    /**
     * Clears account credentials (token, host, cloud identity) on sign-out or
     * unpair while preserving local security preferences. App lock is a device
     * setting, not an account credential; wiping it on sign-out would silently
     * disable the lock (NEW-10).
     */
    fun clearAccountData() {
        val appLockEnabled = isAppLockEnabled()
        val destructiveGated = isDestructiveOpsBiometricGated()
        val themeMode = getThemeMode()
        store.clear()
        store.putAll(
            mapOf<String, StoredValue>(
                KEY_APP_LOCK to StoredValue.StoredFlag(appLockEnabled),
                KEY_DESTRUCTIVE_BIO_GATE to StoredValue.StoredFlag(destructiveGated),
                KEY_THEME_MODE to StoredValue.StoredText(themeMode)
            )
        )
    }

    /** Full wipe of every local preference. Use only for an explicit factory reset. */
    fun clearAll() = store.clear()

    companion object {
        private const val KEY_APP_LOCK = "pref_app_lock_enabled"
        private const val KEY_DESTRUCTIVE_BIO_GATE = "pref_destructive_bio_gate"
        private const val KEY_HOST_NOISE_KEY = "host_noise_key"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
        private const val KEY_CLOUD_TOKEN = "cloud_session_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PICTURE = "user_picture"
        private const val KEY_NOISE_PRIVATE = "noise_static_private"
        private const val KEY_NOISE_PUBLIC = "noise_static_public"
        private const val KEY_CLOUD_DEVICE_ID = "cloud_device_id"
        private const val KEY_CLOUD_VERIFIED = "cloud_device_verified"
        private const val KEY_CLOUD_HOST_ID = "cloud_host_device_id"
    }
}
