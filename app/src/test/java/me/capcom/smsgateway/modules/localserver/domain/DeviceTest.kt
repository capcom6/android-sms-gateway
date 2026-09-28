package me.capcom.smsgateway.modules.localserver.domain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.TimeZone

/**
 * Pins the wire contract of the local server's device listing against the
 * server's `smsgateway.Device` (`client-go/smsgateway/domain_devices.go`),
 * which is what `GET /devices` and `GET /device` must stay compatible with:
 * the field names, their values, and the omission of the key fields when the
 * device has no E2E key.
 *
 * JSON key order is not asserted, deliberately: objects are unordered per
 * RFC 8259, and Gson emits `getDeclaredFields()` order, which the JVM does not
 * guarantee. Comparisons are therefore structural.
 *
 * The Gson instance mirrors the API >= 24 branch of the local server's
 * `configure()` (extensions/GsonBuilder.kt) so date rendering is deterministic.
 * The production config never enables `serializeNulls()`, which is what makes
 * nullable fields behave like the Go `omitempty` pointers.
 */
class DeviceTest {

    private lateinit var gson: Gson
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun pinTimeZone() {
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Built after the timezone is pinned: Gson's date adapter binds the
        // default zone when the adapter is constructed, not when it formats.
        gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .create()
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(
            JsonParser.parseString(expected),
            JsonParser.parseString(actual)
        )
    }

    private fun device(
        publicKey: String? = null,
        keyVersion: Int? = null,
        simCards: List<SimCard> = emptyList(),
    ) = Device(
        id = "PyDmBQZZXYmyxMwED8Fzy",
        name = "Google/sargo",
        createdAt = Date(0),
        updatedAt = Date(10_000),
        lastSeen = Date(20_000),
        simCards = simCards,
        publicKey = publicKey,
        keyVersion = keyVersion,
    )

    @Test
    fun serializesKeyFields() {
        val json = gson.toJson(
            device(
                publicKey = "MIIBIjANBgkqh",
                keyVersion = 1,
                simCards = listOf(
                    SimCard(0, 1, "+79990001234", "MTS", "897010112233445")
                ),
            )
        )

        assertJsonEquals(
            """{
                "publicKey": "MIIBIjANBgkqh",
                "keyVersion": 1,
                "id": "PyDmBQZZXYmyxMwED8Fzy",
                "name": "Google/sargo",
                "createdAt": "1970-01-01T00:00:00.000Z",
                "updatedAt": "1970-01-01T00:00:10.000Z",
                "lastSeen": "1970-01-01T00:00:20.000Z",
                "simCards": [{
                    "slotIndex": 0,
                    "simNumber": 1,
                    "phoneNumber": "+79990001234",
                    "carrierName": "MTS",
                    "iccid": "897010112233445"
                }]
            }""",
            json
        )
    }

    @Test
    fun omitsKeyFieldsWhenDeviceHasNoKey() {
        val json = gson.toJson(device())

        assertJsonEquals(
            """{
                "id": "PyDmBQZZXYmyxMwED8Fzy",
                "name": "Google/sargo",
                "createdAt": "1970-01-01T00:00:00.000Z",
                "updatedAt": "1970-01-01T00:00:10.000Z",
                "lastSeen": "1970-01-01T00:00:20.000Z",
                "simCards": []
            }""",
            json
        )
    }

    @Test
    fun neverEmitsDeletedAt() {
        val json = gson.toJson(device(publicKey = "MIIBIjANBgkqh", keyVersion = 2))

        assertEquals(
            setOf(
                "publicKey", "keyVersion", "id", "name",
                "createdAt", "updatedAt", "lastSeen", "simCards"
            ),
            JsonParser.parseString(json).asJsonObject.keySet()
        )
    }
}
