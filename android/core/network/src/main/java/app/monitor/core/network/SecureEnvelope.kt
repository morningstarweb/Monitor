package app.monitor.core.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Typed value stored inside the encrypted secure-store payload.
 * Kept deliberately small: strings, 32-bit ints and booleans cover every key
 * currently used by TokenStorage (host, port, tokens, Noise keys, flags).
 */
sealed interface StoredValue {
    val typeCode: Int

    data class StoredText(val value: String) : StoredValue {
        override val typeCode: Int = StoredValueCodec.TYPE_TEXT
    }

    data class StoredNumber(val value: Int) : StoredValue {
        override val typeCode: Int = StoredValueCodec.TYPE_NUMBER
    }

    data class StoredFlag(val value: Boolean) : StoredValue {
        override val typeCode: Int = StoredValueCodec.TYPE_FLAG
    }
}

/** Pure binary codecs for the secure store. No Android references: JVM-testable. */
object StoredValueCodec {
    const val TYPE_TEXT = 1
    const val TYPE_NUMBER = 2
    const val TYPE_FLAG = 3

    private const val MAP_VERSION = 1
    private const val MAX_ENTRIES = 512
    private const val MAX_FIELD_BYTES = 64 * 1024

    fun encode(values: Map<String, StoredValue>): ByteArray {
        var size = 8 // map version + entry count
        for ((key, value) in values) {
            size += 12 // type + key length + value length
            size += key.toByteArray(StandardCharsets.UTF_8).size
            size += when (value) {
                is StoredValue.StoredText -> value.value.toByteArray(StandardCharsets.UTF_8).size
                is StoredValue.StoredNumber -> 4
                is StoredValue.StoredFlag -> 1
            }
        }

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAP_VERSION)
        buffer.putInt(values.size)
        for ((key, value) in values) {
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            val valueBytes = when (value) {
                is StoredValue.StoredText -> value.value.toByteArray(StandardCharsets.UTF_8)
                is StoredValue.StoredNumber -> ByteBuffer.allocate(4).putInt(value.value).array()
                is StoredValue.StoredFlag -> byteArrayOf(if (value.value) 1 else 0)
            }
            buffer.putInt(value.typeCode)
            buffer.putInt(keyBytes.size)
            buffer.put(keyBytes)
            buffer.putInt(valueBytes.size)
            buffer.put(valueBytes)
        }
        return buffer.array()
    }

    /** Returns null for any malformed or truncated payload; never throws. */
    fun decode(bytes: ByteArray): Map<String, StoredValue>? {
        return try {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.int != MAP_VERSION) return null
        val count = buffer.int
        if (count < 0 || count > MAX_ENTRIES) return null

        val result = LinkedHashMap<String, StoredValue>(count)
        repeat(count) {
            val type = buffer.int
            val keyLength = buffer.int
            if (keyLength <= 0 || keyLength > MAX_FIELD_BYTES || keyLength > buffer.remaining()) return null
            val keyBytes = ByteArray(keyLength)
            buffer.get(keyBytes)
            val key = String(keyBytes, StandardCharsets.UTF_8)

            val valueLength = buffer.int
            if (valueLength < 0 || valueLength > MAX_FIELD_BYTES || valueLength > buffer.remaining()) return null
            val valueBytes = ByteArray(valueLength)
            buffer.get(valueBytes)

            val value = when (type) {
                StoredValueCodec.TYPE_TEXT -> StoredValue.StoredText(String(valueBytes, StandardCharsets.UTF_8))
                StoredValueCodec.TYPE_NUMBER -> {
                    if (valueLength != 4) return null
                    StoredValue.StoredNumber(ByteBuffer.wrap(valueBytes).order(ByteOrder.BIG_ENDIAN).int)
                }
                StoredValueCodec.TYPE_FLAG -> {
                    if (valueLength != 1) return null
                    StoredValue.StoredFlag(valueBytes[0] != 0.toByte())
                }
                else -> return null
            }
            result[key] = value
        }
        if (buffer.hasRemaining()) return null
        result
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * On-disk envelope: [magic(4)][version(1)][reserved(1)][nonce(12)][ciphertext+tag].
 *
 * The 12-byte nonce is freshly generated with SecureRandom for every write and is
 * stored in clear text (it is not secret). The AES-GCM authentication tag is part
 * of the ciphertext; any bit flip makes decryption after [unwrap] fail closed.
 */
object SecureEnvelope {
    const val VERSION: Int = 1
    const val NONCE_LENGTH: Int = 12
    const val GCM_TAG_BITS: Int = 128
    private const val MIN_CIPHERTEXT_BYTES = 16 // GCM tag length

    private val MAGIC = byteArrayOf(0x4D, 0x4F, 0x4E, 0x53) // "MONS"
    private const val MAGIC_LENGTH = 4
    private const val VERSION_INDEX = 4
    private const val RESERVED_INDEX = 5
    private const val NONCE_OFFSET = 6
    private const val HEADER_LENGTH = NONCE_OFFSET + NONCE_LENGTH

    data class Unwrapped(val nonce: ByteArray, val ciphertext: ByteArray)

    fun wrap(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        require(nonce.size == NONCE_LENGTH) { "AES-GCM nonce must be exactly $NONCE_LENGTH bytes" }
        require(ciphertext.size >= MIN_CIPHERTEXT_BYTES) { "ciphertext must include the GCM tag" }

        val out = ByteBuffer.allocate(HEADER_LENGTH + ciphertext.size)
        out.put(MAGIC)
        out.put(VERSION.toByte())
        out.put(0) // reserved, must be zero
        out.put(nonce)
        out.put(ciphertext)
        return out.array()
    }

    /** Returns null for any malformed header or truncated payload; never throws. */
    fun unwrap(bytes: ByteArray): Unwrapped? {
        if (bytes.size < HEADER_LENGTH + MIN_CIPHERTEXT_BYTES) return null
        if (!bytes.copyOfRange(0, MAGIC_LENGTH).contentEquals(MAGIC)) return null
        if (bytes[VERSION_INDEX] != VERSION.toByte()) return null
        if (bytes[RESERVED_INDEX] != 0.toByte()) return null

        return Unwrapped(
            nonce = bytes.copyOfRange(NONCE_OFFSET, HEADER_LENGTH),
            ciphertext = bytes.copyOfRange(HEADER_LENGTH, bytes.size)
        )
    }
}
