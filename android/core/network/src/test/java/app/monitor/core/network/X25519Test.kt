package app.monitor.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class X25519Test {
    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun rfc7748Section6_1TestVectorMatches() {
        val scalar = hexToBytes("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val uCoord = hexToBytes("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        val expected = "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"
        val out = X25519.scalarMult(scalar, uCoord)
        assertEquals(expected, bytesToHex(out))
    }

    @Test
    fun rfc7748IteratedScalarMultVectorsMatch() {
        var k = ByteArray(32).apply { this[0] = 9 }
        var u = ByteArray(32).apply { this[0] = 9 }

        repeat(1) {
            val next = X25519.scalarMult(k, u)
            u = k
            k = next
        }
        assertEquals(
            "422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079",
            bytesToHex(k)
        )

        repeat(999) {
            val next = X25519.scalarMult(k, u)
            u = k
            k = next
        }
        assertEquals(
            "684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51",
            bytesToHex(k)
        )
    }

    @Test
    fun matchesJdkXdhAcrossRandomKeyPairs() {
        val kpg = KeyPairGenerator.getInstance("XDH")
        val kf = KeyFactory.getInstance("XDH")
        val x509Header = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00
        )

        repeat(25) {
            val kotlinPair = NoiseKeyMaterial.generate()
            val jdkPair = kpg.generateKeyPair()
            val jdkPubRaw = jdkPair.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }

            val kotlinShared = X25519.computeSharedSecret(kotlinPair.privateKey, jdkPubRaw)

            val kotlinPublicForJdk = kf.generatePublic(X509EncodedKeySpec(x509Header + kotlinPair.publicKey))
            val ka = KeyAgreement.getInstance("XDH")
            ka.init(jdkPair.private)
            ka.doPhase(kotlinPublicForJdk, true)
            val jdkShared = ka.generateSecret()

            assertArrayEquals(jdkShared, kotlinShared)
        }
    }

    @Test(expected = SecurityException::class)
    fun lowOrderAllZeroPointIsRejected() {
        val pair = NoiseKeyMaterial.generate()
        X25519.computeSharedSecret(pair.privateKey, ByteArray(32))
    }

    @Test
    fun allRfc7748SmallOrderAndNonCanonicalEdgeCasesAreHandled() {
        val pair = NoiseKeyMaterial.generate()

        // All known Curve25519 low-order points (RFC 7748 section 6.1):
        val lowOrderHex = listOf(
            "0000000000000000000000000000000000000000000000000000000000000000", // 0
            "0100000000000000000000000000000000000000000000000000000000000000", // 1
            "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800", // order 8 (y^2>0)
            "5f9c95bca3508c24b1d0b1559c83ef5b04445cc4581c8e86d8224eddd09f1157", // order 8
            "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f", // p - 1
            "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f", // p (= 0 mod p)
            "eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"  // p + 1 (= 1 mod p)
        )
        for (hex in lowOrderHex) {
            val badKey = hexToBytes(hex)
            val thrown = runCatching {
                X25519.computeSharedSecret(pair.privateKey, badKey)
            }.exceptionOrNull()
            assertTrue("Expected SecurityException for low-order point $hex", thrown is SecurityException)
        }

        // RFC 7748 section 5: bit 255 of u-coordinate must be masked out cleanly.
        val canonicalBase = ByteArray(32).apply { this[0] = 9 }
        val msbSetBase = canonicalBase.copyOf().apply { this[31] = (this[31].toInt() or 0x80).toByte() }
        assertArrayEquals(
            X25519.computeSharedSecret(pair.privateKey, canonicalBase),
            X25519.computeSharedSecret(pair.privateKey, msbSetBase)
        )

        // Non-canonical u = 9 + p (0xed + 9 = 0xf6 at LSB, 0xff...7f top) must match u = 9 mod p.
        val ninePlusP = hexToBytes("f6ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f")
        assertArrayEquals(
            X25519.computeSharedSecret(pair.privateKey, canonicalBase),
            X25519.computeSharedSecret(pair.privateKey, ninePlusP)
        )
    }

    @Test
    fun rfc7748Section6_1SecondTestVectorMatches() {
        val scalar = hexToBytes("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d")
        val uCoord = hexToBytes("e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493")
        val expected = "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957"
        assertEquals(expected, bytesToHex(X25519.scalarMult(scalar, uCoord)))
    }

    @Test
    fun noiseIkMessage1SerializesWithoutJdkXdh() {
        val viewer = NoiseKeyMaterial.generate()
        val host = NoiseKeyMaterial.generate()
        val handshake = NoiseIkViewerHandshake(viewer, host.publicKey, NoiseIkViewerHandshake.PROLOGUE_LAN)
        val msg1 = handshake.writeMessage1()
        assertTrue(msg1.size >= 80)
    }
}
