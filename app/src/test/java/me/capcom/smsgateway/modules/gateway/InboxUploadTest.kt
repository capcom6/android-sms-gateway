package me.capcom.smsgateway.modules.gateway

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.capcom.smsgateway.extensions.configure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// Locks the FROZEN wire contract (client-go requests_mobile.go
// MobilePostInboxRequest): JSON array of items, null/empty omitted,
// isEncrypted true, RFC3339 createdAt, base64-string attachment data.
class InboxUploadTest {

    private val savedTimeZone = TimeZone.getDefault()

    private lateinit var gson: com.google.gson.Gson

    @Before
    fun setUp() {
        // Deterministic offset so golden JSON is stable across machines.
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        // Build AFTER the TZ change: Gson captures the default TZ at build time.
        gson = GsonBuilder().apply { configure() }.create()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(savedTimeZone)
    }

    // 2026-08-07T12:34:56.123+03:00 (epoch fixed, timezone injected via setUp)
    private fun fixedCreatedAt(): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 7)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 34)
            set(Calendar.SECOND, 56)
            set(Calendar.MILLISECOND, 123)
        }
        return cal.time
    }

    private fun sampleItem(
        type: String = InboxMessageType.SMS,
        attachments: List<InboxUploadAttachment>? = null,
        recipient: String? = "+79000000000",
        simNumber: Int? = 1,
        isEncrypted: Boolean = true,
    ): InboxUploadItem = InboxUploadItem(
        id = "text:719144410",
        type = type,
        sender = "+79990001234",
        recipient = recipient,
        simNumber = simNumber,
        content = "7407:4:2:base64payload:opaque:signature",
        isEncrypted = isEncrypted,
        createdAt = fixedCreatedAt(),
        attachments = attachments,
    )

    ///////////////////////////////////////////////////////////////////////////
    // Golden JSON: exact serialized body (field order is declaration order)

    @Test
    fun goldenJsonMatchesFrozenContract() {
        val item = sampleItem(
            attachments = listOf(
                InboxUploadAttachment(
                    partId = 9007199254740993L, // 2^53+1: proves Long precision
                    contentType = "image/jpeg",
                    name = "image.jpg",
                    size = 1024L,
                    data = "iVBORw0KGgoAAAANSUhEUg==",
                ),
            ),
        )

        val json = gson.toJson(listOf(item).prepareInboxUpload())

        val expected = (
            "[{\"id\":\"text:719144410\",\"type\":\"SMS\",\"sender\":\"+79990001234\"," +
                "\"recipient\":\"+79000000000\",\"simNumber\":1," +
                "\"content\":\"7407:4:2:base64payload:opaque:signature\",\"isEncrypted\":true," +
                "\"createdAt\":\"2026-08-07T12:34:56.123+03:00\"," +
                "\"attachments\":[{\"partId\":9007199254740993,\"contentType\":\"image/jpeg\"," +
                "\"name\":\"image.jpg\",\"size\":1024,\"data\":\"iVBORw0KGgoAAAANSUhEUg==\"}]}]"
            )

        assertEquals(expected, json)
    }

    ///////////////////////////////////////////////////////////////////////////
    // RFC3339 createdAt

    @Test
    fun createdAtIsRfc3339WithTimezoneOffset() {
        val json = gson.toJson(listOf(sampleItem()).prepareInboxUpload())
        val createdAt = JsonParser.parseString(json).asJsonArray[0].asJsonObject["createdAt"].asString

        assertTrue(
            "expected RFC3339 with offset, got: $createdAt",
            createdAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}")),
        )
        assertEquals("2026-08-07T12:34:56.123+03:00", createdAt)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Optional-field omission (Go omitempty)

    @Test
    fun nullOptionalFieldsAreOmitted() {
        val item = sampleItem(recipient = null, simNumber = null, attachments = null)
        val json = gson.toJson(listOf(item).prepareInboxUpload())
        val obj = JsonParser.parseString(json).asJsonArray[0].asJsonObject

        assertFalse(obj.has("recipient"))
        assertFalse(obj.has("simNumber"))
        assertFalse(obj.has("attachments"))
    }

    @Test
    fun emptyAttachmentsAreOmitted() {
        val item = sampleItem(attachments = emptyList())
        val json = gson.toJson(listOf(item).prepareInboxUpload())
        val obj = JsonParser.parseString(json).asJsonArray[0].asJsonObject

        assertFalse(obj.has("attachments"))
    }

    @Test
    fun isEncryptedIsAlwaysTrueByDefault() {
        val json = gson.toJson(listOf(sampleItem()).prepareInboxUpload())
        val isEncrypted = JsonParser.parseString(json).asJsonArray[0].asJsonObject["isEncrypted"].asBoolean
        assertTrue(isEncrypted)
    }

    @Test
    fun isEncryptedTrueEvenIfCallerTriesFalse() {
        val json = gson.toJson(listOf(sampleItem(isEncrypted = false)).prepareInboxUpload())
        val isEncrypted = JsonParser.parseString(json).asJsonArray[0].asJsonObject["isEncrypted"].asBoolean
        assertTrue("server rejects isEncrypted=false", isEncrypted)
    }

    ///////////////////////////////////////////////////////////////////////////
    // No extra fields (strict parser: exact key sets)

    @Test
    fun fullItemHasExactlyContractKeys() {
        val json = gson.toJson(
            listOf(
                sampleItem(
                    attachments = listOf(
                        InboxUploadAttachment(
                            partId = 1L,
                            contentType = "image/png",
                            name = "p.png",
                            size = null,
                            data = "cGhvdG8=",
                        ),
                    ),
                ),
            ).prepareInboxUpload(),
        )
        val itemObj = JsonParser.parseString(json).asJsonArray[0].asJsonObject
        val attObj = itemObj.getAsJsonArray("attachments")[0].asJsonObject

        assertEquals(
            setOf("id", "type", "sender", "recipient", "simNumber", "content", "isEncrypted", "createdAt", "attachments"),
            itemObj.keySet(),
        )
        assertEquals(setOf("partId", "contentType", "name", "data"), attObj.keySet()) // size null -> omitted
    }

    @Test
    fun minimalItemHasOnlyRequiredKeys() {
        val json = gson.toJson(
            listOf(
                InboxUploadItem(
                    id = "mms:2078971183",
                    type = InboxMessageType.MMS,
                    sender = "+79990001234",
                    content = "7407:4:2:base64payload:opaque:signature",
                    createdAt = fixedCreatedAt(),
                ),
            ).prepareInboxUpload(),
        )
        val itemObj = JsonParser.parseString(json).asJsonArray[0].asJsonObject

        assertEquals(
            setOf("id", "type", "sender", "content", "isEncrypted", "createdAt"),
            itemObj.keySet(),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // All four frozen type constants

    @Test
    fun allTypeConstantsSerializeVerbatim() {
        val types = listOf(
            InboxMessageType.SMS to "SMS",
            InboxMessageType.DATA_SMS to "DATA_SMS",
            InboxMessageType.MMS to "MMS",
            InboxMessageType.MMS_DOWNLOADED to "MMS_DOWNLOADED",
        )
        for ((constant, wireValue) in types) {
            val json = gson.toJson(listOf(sampleItem(type = constant)).prepareInboxUpload())
            val type = JsonParser.parseString(json).asJsonArray[0].asJsonObject["type"].asString
            assertEquals("type constant $constant must serialize as $wireValue", wireValue, type)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Chunking (worker uploads batches; chunk helper is the unit under test)

    @Test
    fun chunk1200ItemsInto3ChunksOfAtMost500() {
        val items = List(1200) { sampleItem() }
        val chunks = chunkInboxUpload(items)

        assertEquals(3, chunks.size)
        assertEquals(listOf(500, 500, 200), chunks.map { it.size })
        assertTrue(chunks.all { it.size <= INBOX_UPLOAD_BATCH_SIZE })
        assertEquals(1200, chunks.sumOf { it.size })
    }

    @Test
    fun chunkBoundaries() {
        assertEquals(0, chunkInboxUpload(emptyList()).size)
        assertEquals(1, chunkInboxUpload(List(1) { sampleItem() }).size)
        assertEquals(1, chunkInboxUpload(List(500) { sampleItem() }).size)
        assertEquals(2, chunkInboxUpload(List(501) { sampleItem() }).size)
        assertEquals(2, chunkInboxUpload(List(1000) { sampleItem() }).size)
    }

    @Test
    fun chunkKeepsItemOrder() {
        val items = List(502) { sampleItem() }
        val chunks = chunkInboxUpload(items)
        assertEquals(items, chunks.flatten())
    }

    ///////////////////////////////////////////////////////////////////////////
    // Input variation: attachment data stays a plain base64 string

    @Test
    fun attachmentDataIsBase64StringNotArray() {
        val item = sampleItem(
            attachments = listOf(
                InboxUploadAttachment(
                    partId = 7L,
                    contentType = "application/octet-stream",
                    name = "raw.bin",
                    data = "AAECAwQFBgc=",
                ),
            ),
        )
        val json = gson.toJson(listOf(item).prepareInboxUpload())
        val data = JsonParser.parseString(json)
            .asJsonArray[0]
            .asJsonObject
            .getAsJsonArray("attachments")[0]
            .asJsonObject["data"]

        assertTrue(data.isJsonPrimitive)
        assertEquals("AAECAwQFBgc=", data.asString)
    }
}