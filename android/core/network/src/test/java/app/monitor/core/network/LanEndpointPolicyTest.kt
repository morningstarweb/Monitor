package app.monitor.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AUD-03 regression: cleartext is limited to LAN / loopback destinations. */
class LanEndpointPolicyTest {

    @Test
    fun allowsLoopbackAndEmulatorHostsOnApplicationPorts() {
        for (host in listOf("127.0.0.1", "localhost", "::1", "[::1]", "10.0.2.2")) {
            assertTrue(host, LanEndpointPolicy.isAllowedCleartextEndpoint(host, 9480))
            assertTrue(host, LanEndpointPolicy.isAllowedCleartextEndpoint(host, 9481))
        }
    }

    @Test
    fun allowsPrivateRfc1918AndLinkLocalAddresses() {
        for (host in listOf(
            "10.1.2.3",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.50",
            "169.254.1.1",
            "printer.local"
        )) {
            assertTrue(host, LanEndpointPolicy.isAllowedCleartextEndpoint(host, 9480))
        }
    }

    @Test
    fun rejectsPublicAddressesAndUnexpectedPorts() {
        for (host in listOf(
            "8.8.8.8",
            "1.1.1.1",
            "monitor-cloud-broker.workers.dev",
            "172.32.0.1",
            "192.169.1.1"
        )) {
            assertFalse(host, LanEndpointPolicy.isAllowedCleartextEndpoint(host, 9480))
        }
        assertFalse(LanEndpointPolicy.isAllowedCleartextEndpoint("192.168.1.5", 443))
        assertFalse(LanEndpointPolicy.isAllowedCleartextEndpoint("127.0.0.1", 8080))
    }

    @Test
    fun rejectsMalformedHosts() {
        for (host in listOf("", "999.1.1.1", "192.168.1", "example.com.evil.tld")) {
            assertFalse(host, LanEndpointPolicy.isAllowedCleartextEndpoint(host, 9480))
        }
    }
}
