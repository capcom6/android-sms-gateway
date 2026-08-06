package me.capcom.smsgateway.modules.gateway

import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.encryption.IncomingMessageEncryptor
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.testutil.FakeKeyValueStorage
import me.capcom.smsgateway.testutil.InMemoryIncomingMessagesDao
import me.capcom.smsgateway.testutil.RecordingLogsDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * The A4 upload flow: encryption scope, provider re-read, attachment size
 * pre-check (10 MiB cap), chunking (A3 helper), granular per-item uploadedAt
 * marking, and the retry-vs-permanent error policy.
 */
internal class GatewayInboxUploaderTest {

    private fun row(
        id: String,
        type: IncomingMessageType = IncomingMessageType.SMS,
        createdAt: Long = 1000L + id.hashCode(),
    ) = IncomingMessage(
        id = id,
        type = type,
        sender = "+79261234567",
        recipient = null,
        simNumber = 1,
        subscriptionId = null,
        contentPreview = "preview-$id",
        createdAt = createdAt,
    )

    private fun part(partId: Long, bytes: ByteArray? = null, contentType: String = "image/jpeg") =
        SmsProviderReader.ProviderPart(partId, contentType, "name-$partId.jpg", bytes)

    private class FakeReader(
        private val results: Map<String, SmsProviderReader.ReadResult?> = mapOf(),
    ) : IncomingMessageReader {
        override fun read(message: IncomingMessage): SmsProviderReader.ReadResult? = results[message.id]
    }

    private class FakeStoredAttachments : StoredAttachmentReader {
        val files = mutableMapOf<String, File>()

        override fun find(messageId: String, partId: Long): File? = files["$messageId:$partId"]
    }

    private class Harness {
        var passphrase: String? = "uploader-test-pass"
        var token: String? = "device-token"
        var reader: IncomingMessageReader = FakeReader()
        val stored = FakeStoredAttachments()
        val logs = RecordingLogsDao()
        val dao = InMemoryIncomingMessagesDao()
        val chunks = mutableListOf<List<InboxUploadItem>>()
        var failOnCall: Int? = null
        var failure: Throwable? = null
        var classifyFailure: (Throwable) -> Boolean = { e -> GatewayInboxUploader.isPermanentError(e) }

        val downloader: GatewayInboxUploader
        val decryption: EncryptionService

        init {
            val storage = FakeKeyValueStorage().apply { set("passphrase", passphrase) }
            val encryptionService = EncryptionService(EncryptionSettings(storage))
            val encryptor = IncomingMessageEncryptor(
                service = encryptionService,
                warn = { message -> logs.entries += LogEntry(LogEntry.Priority.WARN, "gateway", message) },
            )
            val logsService = LogsService(logs, LogsSettings(FakeKeyValueStorage()))
            decryption = encryptionService

            var call = 0
            downloader = GatewayInboxUploader(
                passphrase = { this.passphrase },
                deviceToken = { this.token },
                // Bind to the mutable property so tests can swap the reader AFTER
                // the harness is constructed (the uploader reads via lambda).
                reader = IncomingMessageReader { message -> this@Harness.reader.read(message) },
                storedAttachments = stored,
                repository = IncomingMessagesRepository(dao),
                encryptor = encryptor,
                logsService = logsService,
                uploadChunk = { _, chunk ->
                    call++
                    if (failOnCall == call) failure?.let { throw it }
                    chunks += chunk
                },
                classifyFailure = { e -> this@Harness.classifyFailure(e) },
            )
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun uploadsAllPendingMarkingEachId() = runBlocking {
        val h = Harness()
        h.dao.rows["a"] = row("a")
        h.dao.rows["b"] = row("b", type = IncomingMessageType.MMS)
        h.reader = FakeReader(
            mapOf(
                "a" to SmsProviderReader.ReadResult("body-a", emptyList()),
                "b" to SmsProviderReader.ReadResult("body-b", emptyList()),
            ),
        )

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        assertEquals(1, h.chunks.size)
        assertEquals(listOf("a", "b"), h.chunks.single().map { it.id })
        assertTrue(h.chunks.single().all { it.isEncrypted })
        assertTrue(h.chunks.single().all { it.content.startsWith("\$aes-256-cbc/pbkdf2-sha1") })
        assertEquals(
            listOf("a", "b"),
            h.dao.rows.filterValues { it.uploadedAt != null }.keys.sorted(),
        )
    }

    @Test
    fun passphraseNullSkipsAllUploadsAndWarns() = runBlocking {
        val h = Harness().apply {
            passphrase = null
            dao.rows["a"] = row("a")
            reader = FakeReader(mapOf("a" to SmsProviderReader.ReadResult("body", emptyList())))
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.MISSING_PASSPHRASE, outcome)
        assertTrue(h.chunks.isEmpty())
        assertNull("never upload, never mark", h.dao.rows["a"]?.uploadedAt)
        assertTrue(h.logs.warns.any { it.message.contains("passphrase") })
    }

    @Test
    fun oversizedPartSkippedBeforeEncryption() = runBlocking {
        val oversize = ByteArray(MAX_INBOX_PART_BYTES.toInt() + 1)
        val small = "small".toByteArray()
        val h = Harness().apply {
            dao.rows["m"] = row("m", type = IncomingMessageType.MMS)
            reader = FakeReader(
                mapOf(
                    "m" to SmsProviderReader.ReadResult(
                        "mms-body",
                        listOf(part(1, oversize, "video/mp4"), part(2, null)),
                    ),
                ),
            )
            // second, in-size part comes from a stored blob so the message still uploads with one attachment
            val blob = File.createTempFile("blob", ".bin").apply { writeBytes(small) }
            stored.files["m:2"] = blob
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        val ups = h.chunks.single().single()
        assertEquals(1, ups.attachments!!.size)
        assertEquals(2L, ups.attachments!!.single().partId)
        assertEquals(small.size.toLong(), ups.attachments!!.single().size)
        assertTrue(h.logs.warns.any { it.message.contains("cap") })
    }

    @Test
    fun oversizedStoredBlobSkippedBeforeRead() = runBlocking {
        val h = Harness().apply {
            dao.rows["m"] = row("m", type = IncomingMessageType.MMS)
            reader = FakeReader(
                mapOf("m" to SmsProviderReader.ReadResult("c", listOf(part(1, null)))),
            )
            // Sparse oversized file: length-fake without allocating the bytes.
            val blob = File.createTempFile("blob", ".bin")
            RandomAccessFile(blob, "rw").use { it.setLength(MAX_INBOX_PART_BYTES + 1) }
            stored.files["m:1"] = blob
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        val ups = h.chunks.single().single()
        assertTrue(
            "oversized blob must never reach the upload chunk",
            ups.attachments.isNullOrEmpty(),
        )
        assertTrue(h.logs.warns.any { it.message.contains("cap") })
    }

    @Test
    fun unresolvedRowSkippedAndNotMarked() = runBlocking {
        val h = Harness().apply {
            dao.rows["ok"] = row("ok")
            dao.rows["gone"] = row("gone", type = IncomingMessageType.MMS)
            reader = FakeReader(
                mapOf("ok" to SmsProviderReader.ReadResult("c", emptyList()), "gone" to null),
            )
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        assertEquals(listOf("ok"), h.chunks.single().map { it.id })
        assertNull(h.dao.rows["gone"]?.uploadedAt)
        assertTrue(h.logs.warns.any { it.message.contains("cannot re-read") })
    }

    @Test
    fun storedBlobWinsOverProviderPart() = runBlocking {
        val h = Harness().apply {
            dao.rows["m"] = row("m", type = IncomingMessageType.MMS)
            reader = FakeReader(
                mapOf("m" to SmsProviderReader.ReadResult("c", listOf(part(7, "provider-bytes".toByteArray())))),
            )
            val blob = File.createTempFile("blob", ".bin").apply { writeBytes("stored-blob".toByteArray()) }
            stored.files["m:7"] = blob
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        val data = h.chunks.single().single().attachments!!.single().data
        assertEquals("upload must use the persisted blob bytes", "stored-blob", h.decryption.decrypt(data))
    }

    @Test
    fun partWithBothSourcesGoneIsSkipped() = runBlocking {
        val h = Harness().apply {
            dao.rows["m"] = row("m", type = IncomingMessageType.MMS)
            reader = FakeReader(
                mapOf("m" to SmsProviderReader.ReadResult("c", listOf(part(9, null)))),
            )
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        assertTrue(h.chunks.single().single().attachments.isNullOrEmpty())
        assertTrue(h.logs.warns.any { it.message.contains("no data") })
    }

    @Test
    fun chunkingMatchesA3BatchSize() = runBlocking {
        val h = Harness()
        (1..1200).forEach { h.dao.rows["r$it"] = row("r$it", createdAt = it.toLong()) }
        h.reader = FakeReader(
            (1..1200).associate { "r$it" to SmsProviderReader.ReadResult("c-r$it", emptyList()) },
        )

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        assertEquals("A3 helper chunks <=500", listOf(500, 500, 200), h.chunks.map { it.size })
        assertTrue(h.chunks.all { it.size <= INBOX_UPLOAD_BATCH_SIZE })
        assertEquals(1200, h.dao.rows.values.count { it.uploadedAt != null })
    }

    @Test
    fun retryErrorKeepsFailingChunkPendingButMarksEarlierChunks() = runBlocking {
        val h = Harness()
        (1..1200).forEach { h.dao.rows["r$it"] = row("r$it", createdAt = it.toLong()) }
        h.reader = FakeReader(
            (1..1200).associate { "r$it" to SmsProviderReader.ReadResult("c", emptyList()) },
        )
        h.failOnCall = 3
        h.failure = java.io.IOException("connection reset")

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.RETRY_REQUIRED, outcome)
        assertEquals("chunk 3 never starts uploading", 2, h.chunks.size)
        assertEquals((1..1000).toList(), h.chunks.flatten().map { it.id.removePrefix("r").toInt() })
        assertEquals(1000, h.dao.rows.values.count { it.uploadedAt != null })
        assertEquals(200, h.dao.rows.values.count { it.uploadedAt == null })
    }

    @Test
    fun permanent413KeepsRowPendingAndWarns() = runBlocking {
        val h = Harness().apply {
            dao.rows["m"] = row("m")
            reader = FakeReader(mapOf("m" to SmsProviderReader.ReadResult("c", emptyList())))
            failOnCall = 1
            // Ktor's ClientRequestException ctor reads response.call, which needs
            // a live call; classify via the injectable seam instead.
            failure = RuntimeException("413")
            classifyFailure = { (it as? RuntimeException)?.message == "413" }
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.PERMANENT_FAILURE, outcome)
        assertNull(h.dao.rows["m"]?.uploadedAt)
        assertTrue(h.logs.warns.any { it.message.contains("permanently rejected") })
        assertTrue(h.logs.warns.any { it.message.contains("413") })
    }

    @Test
    fun permanent400IsAlsoNoRetry() = runBlocking {
        val h = Harness().apply {
            dao.rows["m"] = row("m")
            reader = FakeReader(mapOf("m" to SmsProviderReader.ReadResult("c", emptyList())))
            failOnCall = 1
            failure = RuntimeException("400")
            classifyFailure = { (it as? RuntimeException)?.message == "400" }
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.PERMANENT_FAILURE, outcome)
        assertNull(h.dao.rows["m"]?.uploadedAt)
    }

    @Test
    fun missingTokenStopsBeforeEncryption() = runBlocking {
        val h = Harness().apply {
            token = null
            dao.rows["m"] = row("m")
            reader = FakeReader(mapOf("m" to SmsProviderReader.ReadResult("c", emptyList())))
        }

        val outcome = h.downloader.upload()

        assertEquals(GatewayInboxUploadOutcome.MISSING_TOKEN, outcome)
        assertTrue(h.chunks.isEmpty())
        assertTrue(h.logs.warns.any { it.message.contains("not registered") })
    }

    @Test
    fun filtersArePassedToPendingSelection() = runBlocking {
        val h = Harness()
        h.dao.rows["a"] = row("a", type = IncomingMessageType.SMS, createdAt = 10)
        h.dao.rows["b"] = row("b", type = IncomingMessageType.MMS, createdAt = 20)
        h.dao.rows["c"] = row("c", type = IncomingMessageType.SMS, createdAt = 40)
        h.reader = FakeReader(
            mapOf(
                "a" to SmsProviderReader.ReadResult("c", emptyList()),
                "b" to SmsProviderReader.ReadResult("c", emptyList()),
                "c" to SmsProviderReader.ReadResult("c", emptyList()),
            ),
        )

        val outcome = h.downloader.upload(InboxUploadFilter(types = setOf(IncomingMessageType.SMS), since = 5, until = 25))

        assertEquals(GatewayInboxUploadOutcome.ALL_UPLOADED, outcome)
        assertEquals(listOf("a"), h.chunks.single().map { it.id })
        assertNull(h.dao.rows["b"]?.uploadedAt)
        assertNull(h.dao.rows["c"]?.uploadedAt)
    }
}