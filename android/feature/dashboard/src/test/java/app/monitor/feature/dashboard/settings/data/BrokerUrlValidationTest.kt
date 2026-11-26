package app.monitor.feature.dashboard.settings.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEW-07: a user-configured broker must never receive a Bearer token over a
 * plaintext or deceptive endpoint. These are behavioural tests of the real
 * parser used by ping and logout.
 */
class BrokerUrlValidationTest {

    private fun isRejected(url: String): Boolean =
        runCatching { validateBrokerUrl(url) }.exceptionOrNull() != null

    @Test
    fun acceptsHttpsBroker() {
        assertEquals(
            "https://broker.example.com",
            validateBrokerUrl(" https://broker.example.com/ ")
        )
    }

    @Test
    fun acceptsHttpsBrokerWithPort() {
        assertEquals(
            "https://broker.example.com:8787",
            validateBrokerUrl("https://broker.example.com:8787/")
        )
    }

    @Test
    fun rejectsPlaintextHttp() {
        assertTrue(isRejected("http://broker.example.com"))
    }

    @Test
    fun rejectsUserinfoInjection() {
        assertTrue(isRejected("https://user:pass@broker.example.com"))
        assertTrue(isRejected("https://broker.example.com@evil.example"))
    }

    @Test
    fun rejectsMalformedAndDeceptiveUrls() {
        assertTrue(isRejected(""))
        assertTrue(isRejected("not-a-url"))
        assertTrue(isRejected("https:///missing-host"))
        assertTrue(isRejected("https://broker.example.com:99999"))
        assertTrue(isRejected("https://broker.example.com/#fragment"))
    }
}
