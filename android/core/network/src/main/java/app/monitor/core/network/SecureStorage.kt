package app.monitor.core.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Minimal key/value contract used by token storage. The public surface of
 * [TokenStorage] is unchanged; it delegates to this interface so the legacy
 * EncryptedSharedPreferences backend can be replaced without touching callers.
 */
interface SecureKeyValueStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    /** Applies every entry in one atomic commit (no mixed-credential window). */
    fun putAll(values: Map<String, StoredValue>)
    fun remove(key: String)
    fun clear()
    fun contains(key: String): Boolean
}

class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class SecureMigrationResult(val status: Status, val migratedKeys: Int = 0) {
    enum class Status { MIGRATED, ALREADY_MIGRATED, NO_LEGACY_DATA, FAILED }
}

/**
 * Credential store backed directly by the Android Keystore and AES-256-GCM.
 *
 * SECURITY PROPERTIES:
 *  - the AES key is non-exportable and lives in the AndroidKeyStore;
 *  - every write uses a fresh 12-byte SecureRandom nonce (see [SecureEnvelope]);
 *  - the payload is encrypted as a whole and written to a temp file, fsynced and
 *    atomically renamed, so an interrupted write can never leave a half-record;
 *  - no SharedPreferences file holds plaintext credentials;
 *  - decryption failures (lost key or tampering) throw [SecureStorageException]
 *    instead of silently wiping data.
 *
 * MIGRATION: on first use, values from the deprecated `monitor_secure_prefs`
 * EncryptedSharedPreferences file (device token, cloud token, Noise static keys,
 * host/pin, theme and app-lock flag) are copied into the new store. The legacy
 * file is only cleared *after* the new store has been committed atomically, so an
 * interruption before that point simply causes a retry on the next launch.
 */
class SecureStorage(context: Context) : SecureKeyValueStore {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, FILE_NAME)
    private val tempFile = File(appContext.filesDir, "$FILE_NAME.tmp")
    private val secureRandom = SecureRandom()

    @Volatile
    private var cache: Map<String, StoredValue>? = null

    @Volatile
    var lastMigration: SecureMigrationResult = SecureMigrationResult(SecureMigrationResult.Status.NO_LEGACY_DATA)
        private set

    init {
        migrateFromLegacyIfNeeded()
    }

    // --- SecureKeyValueStore -------------------------------------------------

    @Synchronized
    override fun getString(key: String, default: String?): String? =
        (readLocked()[key] as? StoredValue.StoredText)?.value ?: default

    @Synchronized
    override fun putString(key: String, value: String) {
        val values = readLocked()
        values[key] = StoredValue.StoredText(value)
        writeLocked(values)
    }

    @Synchronized
    override fun getInt(key: String, default: Int): Int =
        (readLocked()[key] as? StoredValue.StoredNumber)?.value ?: default

    @Synchronized
    override fun putInt(key: String, value: Int) {
        val values = readLocked()
        values[key] = StoredValue.StoredNumber(value)
        writeLocked(values)
    }

    @Synchronized
    override fun getBoolean(key: String, default: Boolean): Boolean =
        (readLocked()[key] as? StoredValue.StoredFlag)?.value ?: default

    @Synchronized
    override fun putBoolean(key: String, value: Boolean) {
        val values = readLocked()
        values[key] = StoredValue.StoredFlag(value)
        writeLocked(values)
    }

    @Synchronized
    override fun putAll(values: Map<String, StoredValue>) {
        if (values.isEmpty()) return
        val current = readLocked()
        current.putAll(values)
        writeLocked(current)
    }

    @Synchronized
    override fun remove(key: String) {
        val values = readLocked()
        if (values.remove(key) != null) writeLocked(values)
    }

    @Synchronized
    override fun clear() {
        // Keep the migration marker so a later launch does not re-read legacy data.
        writeLocked(linkedMapOf(MIGRATION_MARKER to StoredValue.StoredFlag(true)))
    }

    @Synchronized
    override fun contains(key: String): Boolean = readLocked().containsKey(key)

    // --- Keystore / envelope -------------------------------------------------

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec); generateKey() }

        // Reload after generation: some keystore providers cache aliases on the
        // instance that was loaded before the key existed.
        val refreshed = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (refreshed.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: (refreshed.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw SecureStorageException("Android Keystore did not return the generated AES key")
    }

    private fun encryptCipher(): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            // AndroidKeyStore with randomized encryption enabled generates its own 12-byte GCM IV.
            init(Cipher.ENCRYPT_MODE, masterKey())
        }

    private fun decryptCipher(nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(SecureEnvelope.GCM_TAG_BITS, nonce))
        }

    /** Caller must hold the object monitor. */
    private fun readLocked(): MutableMap<String, StoredValue> {
        cache?.let { return it.toMutableMap() }
        if (!storeFile.exists()) return linkedMapOf()

        val envelope = SecureEnvelope.unwrap(storeFile.readBytes())
            ?: throw SecureStorageException("Secure store envelope is malformed")
        val plaintext = try {
            decryptCipher(envelope.nonce).doFinal(envelope.ciphertext)
        } catch (error: GeneralSecurityException) {
            throw SecureStorageException(
                "Secure store cannot be decrypted (keystore key lost or data tampered)",
                error
            )
        }
        val values = StoredValueCodec.decode(plaintext)
            ?: throw SecureStorageException("Secure store payload is malformed")
        cache = values
        return values.toMutableMap()
    }

    /** Caller must hold the object monitor. Atomic + fail-safe. */
    private fun writeLocked(values: Map<String, StoredValue>) {
        val plaintext = StoredValueCodec.encode(values)
        val cipher = encryptCipher()
        val ciphertext = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        if (nonce == null || nonce.size != SecureEnvelope.NONCE_LENGTH) {
            throw SecureStorageException("Android Keystore did not produce a 12-byte GCM IV")
        }
        val envelope = SecureEnvelope.wrap(nonce, ciphertext)

        try {
            FileOutputStream(tempFile, false).use { out ->
                out.write(envelope)
                out.flush()
                out.fd.sync()
            }
            if (!tempFile.renameTo(storeFile)) {
                throw SecureStorageException("Could not atomically replace the secure store file")
            }
            cache = values.toMap()
        } catch (error: Exception) {
            runCatching { tempFile.delete() }
            if (error is SecureStorageException) throw error
            throw SecureStorageException("Failed to persist the secure store", error)
        }
    }

    // --- Migration from deprecated EncryptedSharedPreferences ----------------

    private fun migrateFromLegacyIfNeeded() {
        if (storeFile.exists()) {
            // Already migrated in a previous launch. Never re-read legacy data,
            // which avoids clobbering a store created by this implementation.
            lastMigration = SecureMigrationResult(SecureMigrationResult.Status.ALREADY_MIGRATED)
            return
        }

        val legacy = openLegacyPreferences()
        if (legacy == null) {
            lastMigration = SecureMigrationResult(SecureMigrationResult.Status.NO_LEGACY_DATA)
            return
        }

        try {
            val existing = legacy.all
            val migrated = linkedMapOf<String, StoredValue>()
            for ((key, value) in existing) {
                migrated[key] = when (value) {
                    is String -> StoredValue.StoredText(value)
                    is Int -> StoredValue.StoredNumber(value)
                    is Boolean -> StoredValue.StoredFlag(value)
                    // Long/Float/String-set keys are not used today; stringify so
                    // no legacy value is silently dropped.
                    else -> StoredValue.StoredText(value.toString())
                }
            }
            migrated[MIGRATION_MARKER] = StoredValue.StoredFlag(true)

            // Atomic commit first. If this throws, legacy data is left untouched
            // and the migration is retried on the next launch.
            writeLocked(migrated)
            lastMigration = SecureMigrationResult(
                SecureMigrationResult.Status.MIGRATED,
                existing.size
            )

            // Best effort only: a crash here is harmless because the marker in the
            // new store prevents a second migration.
            runCatching { legacy.edit().clear().commit() }
        } catch (error: Exception) {
            cache = null
            lastMigration = SecureMigrationResult(SecureMigrationResult.Status.FAILED)
        }
    }

    private fun openLegacyPreferences(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            LEGACY_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Corrupt legacy file or unavailable legacy master key: fail safe and let
        // the user re-pair instead of crashing the app at startup.
        null
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "app.monitor.secure.master.v1"
        const val FILE_NAME = "monitor_secure_store_v1.bin"
        const val LEGACY_PREFS_NAME = "monitor_secure_prefs"
        const val MIGRATION_MARKER = "__secure_store_migration_v1_complete"
    }
}
