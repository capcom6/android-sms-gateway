package me.capcom.smsgateway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudServerAddressTest {
    private val fragment = HomeFragment()
    @Test
    fun `invalid saved port falls back instead of crashing`() {
        assertNull(fragment.cloudServerAddress("https://example.com:bad/mobile/v1"))
    }

    @Test
    fun `blank saved address falls back`() {
        assertNull(fragment.cloudServerAddress(""))
    }

    @Test
    fun `valid address displays host without credentials or path`() {
        assertEquals("example.com:443", fragment.cloudServerAddress("https://user:password@example.com/mobile/v1"))
    }

    @Test
    fun `custom port is preserved`() {
        assertEquals("example.com:8443", fragment.cloudServerAddress("https://example.com:8443/mobile/v1"))
    }
}
