package app.monitor.core.network

import java.security.SecureRandom

/**
 * Pure Kotlin RFC 7748 Curve25519 (X25519) key generation and scalar multiplication.
 *
 * Replaces `KeyPairGenerator.getInstance("XDH")` / `KeyAgreement.getInstance("XDH")`,
 * which is unavailable on Android API 30-32 (`minSdk = 30`). Constant-shape
 * 255-step Montgomery ladder over 16-bit limbs mod 2^255 - 19 (`AUD-003`).
 */
internal object X25519 {
    private const val KEY_SIZE = 32
    private val BASE_POINT = ByteArray(KEY_SIZE).apply { this[0] = 9 }
    private val CURVE_121665 = gf(0xDB41, 1)
    private val rng = SecureRandom()

    fun generatePrivateKey(): ByteArray {
        val priv = ByteArray(KEY_SIZE)
        rng.nextBytes(priv)
        clampScalar(priv)
        return priv
    }

    fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE) { "X25519 private key must be exactly 32 bytes" }
        return scalarMult(privateKey, BASE_POINT)
    }

    fun computeSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE) { "X25519 private key must be exactly 32 bytes" }
        require(publicKey.size == KEY_SIZE) { "X25519 public key must be exactly 32 bytes" }
        val shared = scalarMult(privateKey, publicKey)
        // RFC 7748 section 6.1: reject low-order points producing the all-zero secret.
        var nonZero = 0
        for (b in shared) nonZero = nonZero or (b.toInt() and 0xFF)
        if (nonZero == 0) {
            throw SecurityException("Low-order X25519 peer public key rejected")
        }
        return shared
    }

    fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        val z = scalar.copyOf(KEY_SIZE)
        clampScalar(z)

        val x = unpack25519(point)
        val a = gf()
        val b = gf()
        val c = gf()
        val d = gf()
        val e = gf()
        val f = gf()
        a[0] = 1L
        b.copyFrom(x)
        c[0] = 0L
        d[0] = 1L

        for (i in 254 downTo 0) {
            val r = (z[i ushr 3].toInt() ushr (i and 7)) and 1
            cswap(a, b, r)
            cswap(c, d, r)
            add(e, a, c)
            sub(a, a, c)
            add(c, b, d)
            sub(b, b, d)
            square(d, e)
            square(f, a)
            mul(a, c, a)
            mul(c, b, e)
            add(e, a, c)
            sub(a, a, c)
            square(b, a)
            sub(c, d, f)
            mul(a, c, CURVE_121665)
            add(a, a, d)
            mul(c, c, a)
            mul(a, d, f)
            mul(d, b, x)
            square(b, e)
            cswap(a, b, r)
            cswap(c, d, r)
        }

        val invC = gf()
        inv25519(invC, c)
        mul(a, a, invC)
        return pack25519(a)
    }

    private fun clampScalar(z: ByteArray) {
        z[0] = (z[0].toInt() and 248).toByte()
        z[31] = ((z[31].toInt() and 127) or 64).toByte()
    }

    private fun gf(v0: Long = 0L, v1: Long = 0L): LongArray =
        LongArray(16).apply {
            this[0] = v0
            this[1] = v1
        }

    private fun LongArray.copyFrom(src: LongArray) {
        src.copyInto(this)
    }

    private fun unpack25519(n: ByteArray): LongArray {
        val o = gf()
        for (i in 0 until 16) {
            o[i] = ((n[2 * i].toInt() and 0xFF) + ((n[2 * i + 1].toInt() and 0xFF) shl 8)).toLong()
        }
        // RFC 7748 section 5: clear bit 255 before field operations.
        o[15] = o[15] and 0x7FFFL
        return o
    }

    private fun pack25519(n: LongArray): ByteArray {
        val m = gf()
        val t = n.copyOf()
        car25519(t)
        car25519(t)
        car25519(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xFFEDL
            for (i in 1 until 15) {
                m[i] = t[i] - 0xFFFFL - ((m[i - 1] shr 16) and 1L)
                m[i - 1] = m[i - 1] and 0xFFFFL
            }
            m[15] = t[15] - 0x7FFFL - ((m[14] shr 16) and 1L)
            val b = ((m[15] shr 16) and 1L).toInt()
            m[14] = m[14] and 0xFFFFL
            cswap(t, m, 1 - b)
        }
        val out = ByteArray(KEY_SIZE)
        for (i in 0 until 16) {
            out[2 * i] = (t[i] and 0xFFL).toByte()
            out[2 * i + 1] = ((t[i] ushr 8) and 0xFFL).toByte()
        }
        return out
    }

    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) {
                o[i + 1] += c - 1L
            } else {
                o[0] += 38L * (c - 1L)
            }
            o[i] -= c shl 16
        }
    }

    private fun cswap(p: LongArray, q: LongArray, b: Int) {
        val c = ((b - 1).inv()).toLong()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                t[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38L * t[i + 16]
        }
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    private fun square(o: LongArray, a: LongArray) = mul(o, a, a)

    private fun inv25519(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            square(c, c)
            if (a != 2 && a != 4) mul(c, c, i)
        }
        o.copyFrom(c)
    }
}
