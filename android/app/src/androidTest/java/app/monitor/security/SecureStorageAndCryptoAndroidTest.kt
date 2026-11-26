package app.monitor.core.network

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real device / AVD instrumentation tests verifying Android Keystore AES-256-GCM
 * via [SecureStorage] (`AUD-002`) and pure-Kotlin X25519 + Noise IK (`AUD-003`)
 * inside ART runtime.
 */
@RunWith(JUnit4::class)
class SecureStorageAndCryptoAndroidTest {
    @Test
    fun androidKeystoreSecretKeyEntryRoundtripAndReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenStorage = TokenStorage(context)
        tokenStorage.clearAll()

        tokenStorage.savePairing("10.0.2.2", 9480, "mon_dev_avd_keystore_test_12345")
        tokenStorage.setAppLockEnabled(true)
        tokenStorage.setDestructiveOpsBiometricGated(true)
        val noisePair = tokenStorage.getOrCreateNoiseStaticKeyPair()
        assertEquals(32, noisePair.privateKey.size)
        assertEquals(32, noisePair.publicKey.size)

        // Fresh instance must reload and decrypt using AndroidKeyStore SecretKeyEntry
        val reloaded = TokenStorage(context)
        assertEquals("10.0.2.2", reloaded.getServerHost())
        assertEquals(9480, reloaded.getServerPort())
        assertEquals("mon_dev_avd_keystore_test_12345", reloaded.getDeviceToken())
        assertTrue(reloaded.isAppLockEnabled())
        assertTrue(reloaded.isDestructiveOpsBiometricGated())
        assertEquals(
            noisePair.publicKey.toList(),
            reloaded.getOrCreateNoiseStaticKeyPair().publicKey.toList()
        )

        // Sign-out clears credentials but retains local lock preferences (NEW-10)
        reloaded.clearAccountData()
        assertNull(reloaded.getDeviceToken())
        assertEquals("", reloaded.getServerHost())
        assertTrue(reloaded.isAppLockEnabled())
        assertTrue(reloaded.isDestructiveOpsBiometricGated())

        reloaded.clearAll()
        assertFalse(reloaded.isAppLockEnabled())
    }

    @Test
    fun x25519AndNoiseIkWorkInAndroidRuntime() {
        val viewer = NoiseKeyMaterial.generate()
        val host = NoiseKeyMaterial.generate()
        val handshake = NoiseIkViewerHandshake(viewer, host.publicKey, NoiseIkViewerHandshake.PROLOGUE_LAN)
        val msg1 = handshake.writeMessage1()
        assertTrue(msg1.size >= 80)

        val sharedA = SecureSessionV2.computeSharedSecret(viewer.privateKey, host.publicKey)
        val sharedB = SecureSessionV2.computeSharedSecret(host.privateKey, viewer.publicKey)
        assertEquals(sharedA.toList(), sharedB.toList())
    }

    @Test
    fun monitorApiClientBlocksPublicCleartextViaLanEndpointPolicy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = TokenStorage(context)
        val apiClient = MonitorApiClient(storage)
        try {
            val res = apiClient.getHealth("example.com", 9480)
            assertTrue(res.isFailure)
            assertTrue(
                res.exceptionOrNull()?.message.orEmpty().contains("Blocked cleartext", ignoreCase = true)
            )
            LanEndpointPolicy.requireAllowedCleartextEndpoint("192.168.1.44", 9485)
        } finally {
            apiClient.close()
        }
    }
}
