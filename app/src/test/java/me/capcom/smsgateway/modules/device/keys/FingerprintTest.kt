package me.capcom.smsgateway.modules.device.keys

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FingerprintTest {

    // RSA-2048 SPKI DER (SubjectPublicKeyInfo) in base64 NO_WRAP, same
    // encoding as produced by KeyStore.encodePublicKey.
    private val key1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7aOC/3NuXQULVFdAYJBVSlZAMDTI7oPXYs0S8KS0k6zxAqtX25yC5wsq+S05e7OeG8J8vDD90Hz8hRhvcfx7ZkNPmVeeEpVTqSV3XrA1cWzRKueOwEAJmXR2hsPs1p5YL6MtIZn9AFF/WEV8KosLkpYBRTMs+hHQk+EKqoU5aFFwuN30KyGgMYGm2lEiJPACVsATPxxNOfQP9ZsatHZ7t2khN6cKPQ5/YVhWWKFsyVcKK2usZo9K3/Zp5xT8J+2o9AGCMBod6QQh75QlCdDAv6WUm/eYj/7i/x3J9RKpAuIFLToX4mZjgyAhs7MYCjGIG9XRVguESFdYlwjjLcT44wIDAQAB"
    private val key1Fingerprint =
        "1461:C5BD:FB5C:79A7:1A9C:1CA1:617C:F044:2FF4:A2FF:3989:40A6:73BE:4157:29A8:49E2"

    private val key2 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvYkJzsVxnjOYB52VrmVvDAyuI2Y55hlOf4ppuUQXasYs0UHO8x+l8DqRBWBL2jNgxJkH9x6szFJSfj083e5Oh7prXkoBJDxgpQ4JtHknCvXGh17Bpe/GEClijzFgZq0gnxkLmuNZ7gOsGqBX1+rfivbDas/HyJ21dIjm7H84S3y6pM3LaUEYoTUELrjKkx/gz0Esfqu9LZsihv5Xbfrz4IMhLr30cnEqqwTDe1jg7BpD0kRioTjsPXxS5TQ5B5TFoOjOYXcHVCl+sE5oLWzH2Z82doUhC+8TMTAVS1d9oNAI2PoKraLsAGleM0VrCMGS+iH8ZpQJYlIL31j1hznIEwIDAQAB"
    private val key2Fingerprint =
        "AC84:5367:ADB5:DD43:6FF4:DB87:9A27:5400:FBAA:1B4A:6B67:9308:AD04:23FE:F29E:B22E"

    @Test
    fun fingerprintOfKnownRsaKey() {
        assertEquals(
            key1Fingerprint,
            Fingerprint.format(Base64.getDecoder().decode(key1))
        )
    }

    @Test
    fun fingerprintOfAnotherRsaKey() {
        assertEquals(
            key2Fingerprint,
            Fingerprint.format(Base64.getDecoder().decode(key2))
        )
    }

    @Test
    fun fingerprintOfSmallInput() {
        // SHA-256("abc") formatted in 16 groups of 4 uppercase hex chars
        assertEquals(
            "BA78:16BF:8F01:CFEA:4141:40DE:5DAE:2223:B003:61A3:9617:7A9C:B410:FF61:F200:15AD",
            Fingerprint.format("abc".toByteArray())
        )
    }

    @Test
    fun fingerprintOfEmptyInput() {
        // SHA-256 of empty input
        assertEquals(
            "E3B0:C442:98FC:1C14:9AFB:F4C8:996F:B924:27AE:41E4:649B:934C:A495:991B:7852:B855",
            Fingerprint.format(ByteArray(0))
        )
    }

    @Test
    fun fingerprintFormatInvariants() {
        val fingerprints = listOf(
            key1Fingerprint,
            key2Fingerprint,
            requireNotNull(Fingerprint.format("abc".toByteArray())),
        )

        for (fingerprint in fingerprints) {
            assertEquals(16, fingerprint.split(":").size)
            assertEquals(64, fingerprint.replace(":", "").length)
            assertTrue(fingerprint.split(":").all { it.length == 4 })
            assertTrue(
                fingerprint.all { it == ':' || it in '0'..'9' || it in 'A'..'F' }
            )
        }
    }

    @Test
    fun nullInputYieldsNull() {
        assertNull(Fingerprint.format(null))
    }
}