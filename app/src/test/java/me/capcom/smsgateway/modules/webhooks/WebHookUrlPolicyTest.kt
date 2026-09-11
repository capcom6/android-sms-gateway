package me.capcom.smsgateway.modules.webhooks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure Kotlin, no Android dependencies, so it runs in `./gradlew test`. */
class WebHookUrlPolicyTest {

    @Test
    fun `ordinary public destinations are allowed`() {
        for (url in listOf(
            "https://example.com/hook",
            "https://api.example.co.uk:8443/webhooks/sms",
            "https://203.0.113.10/hook",
            "https://8.8.8.8/hook",
        )) {
            assertFalse(url, WebHookUrlPolicy.isForbiddenHost(url))
        }
    }

    @Test
    fun `private ranges are refused`() {
        for (url in listOf(
            "https://10.0.0.5/hook",
            "https://172.16.4.1/hook",
            "https://172.31.255.254/hook",
            "https://192.168.1.1/hook",
            "https://100.64.0.1/hook",
        )) {
            assertTrue(url, WebHookUrlPolicy.isForbiddenHost(url))
        }
    }

    @Test
    fun `the cloud metadata address is refused`() {
        assertTrue(WebHookUrlPolicy.isForbiddenHost("https://169.254.169.254/latest/meta-data/"))
    }

    @Test
    fun `loopback and unspecified are refused`() {
        for (url in listOf(
            "https://127.0.0.1/hook",
            "https://127.1.2.3/hook",
            "https://0.0.0.0/hook",
            "https://localhost/hook",
            "https://foo.localhost/hook",
        )) {
            assertTrue(url, WebHookUrlPolicy.isForbiddenHost(url))
        }
    }

    @Test
    fun `172 dot 32 is public and must not be caught by the 172 dot 16 slash 12 rule`() {
        assertFalse(WebHookUrlPolicy.isForbiddenHost("https://172.32.0.1/hook"))
        assertFalse(WebHookUrlPolicy.isForbiddenHost("https://172.15.0.1/hook"))
    }

    @Test
    fun `ipv6 loopback link-local and unique-local are refused`() {
        for (url in listOf(
            "https://[::1]/hook",
            "https://[fe80::1]/hook",
            "https://[fd00::1]/hook",
            "https://[::ffff:127.0.0.1]/hook",
        )) {
            assertTrue(url, WebHookUrlPolicy.isForbiddenHost(url))
        }
    }

    @Test
    fun `mdns and internal suffixes are refused`() {
        assertTrue(WebHookUrlPolicy.isForbiddenHost("https://printer.local/hook"))
        assertTrue(WebHookUrlPolicy.isForbiddenHost("https://db.internal/hook"))
    }

    @Test
    fun `an unparseable url is refused rather than guessed at`() {
        assertTrue(WebHookUrlPolicy.isForbiddenHost("not a url"))
        assertTrue(WebHookUrlPolicy.isForbiddenHost(""))
    }
}
