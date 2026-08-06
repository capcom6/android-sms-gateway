package me.capcom.smsgateway.modules.encryption

import android.util.Base64
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Type
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class IncomingMessageEncryptorTest {

    private fun service(passphrase: String?): EncryptionService =
        EncryptionService(EncryptionSettings(FakeStorage(passphrase)))

    private fun decryptor(passphrase: String): EncryptionService = service(passphrase)

    private class CountingKeyFactory {
        var derivations = 0
            private set

        fun factory(): (String, ByteArray, Int) -> SecretKey = { p, s, i ->
            derivations++
            deriveBatchKey(p, s, i)
        }
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    // region: message field roundtrip

    @Test
    fun roundtripMessageFieldsUnicodeAndNullRecipient() {
        val passphrase = "a2-roundtrip-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val dec = decryptor(passphrase)
        val sender = "Bob +48 600 100 200 🚀"
        val preview = "Привет! こんにちは世界 line1\nline2\ttabbed"

        val result = encryptor.encryptMessage(passphrase, sender, null, preview, iterationCount = 1000)
        assertNotNull(result)
        assertNull(result!!.recipient)
        assertEquals(sender, dec.decrypt(result.sender))
        assertEquals(preview, dec.decrypt(result.contentPreview))
    }

    @Test
    fun roundtripMessageFieldsWithRecipientEmptyAndBlank() {
        val passphrase = "a2-recipient-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val dec = decryptor(passphrase)
        val sender = "sender"
        val preview = "preview"

        val withRecipient = encryptor.encryptMessage(passphrase, sender, "recipient-1", preview, iterationCount = 1000)
        assertEquals("recipient-1", dec.decrypt(withRecipient!!.recipient!!))

        val emptyRecipient = encryptor.encryptMessage(passphrase, sender, "", preview, iterationCount = 1000)
        assertNull(emptyRecipient!!.recipient)

        val blankRecipient = encryptor.encryptMessage(passphrase, sender, "   ", preview, iterationCount = 1000)
        assertEquals("   ", dec.decrypt(blankRecipient!!.recipient!!))
    }

    @Test
    fun roundtripEmptyMessageContentAndSender() {
        val passphrase = "a2-empty-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val result = encryptor.encryptMessage(passphrase, "", null, "", iterationCount = 1000)
        assertNotNull(result)
        assertEquals("", decryptor(passphrase).decrypt(result!!.sender))
        assertNull(result.recipient)
        assertEquals("", decryptor(passphrase).decrypt(result.contentPreview))
    }

    // endregion

    // region: attachment roundtrip

    @Test
    fun roundtripAttachmentNameAndBinaryData() {
        val passphrase = "a2-attach-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val fullRangeBytes = ByteArray(256) { (it - 128).toByte() }
        val utcBytes = "hello текст utf-8".toByteArray(Charsets.UTF_8)
        val attachments = listOf(
            AttachmentInput("photo-1.jpg 🖼", fullRangeBytes),
            AttachmentInput("empty.bin", ByteArray(0)),
            AttachmentInput("text.txt", utcBytes),
        )

        val encrypted = encryptor.encryptMessageWithAttachments(
            passphrase, "sender", "recipient", "preview", attachments, iterationCount = 1000
        )
        assertNotNull(encrypted)
        assertEquals(attachments.size, encrypted!!.attachments.size)

        encrypted.attachments.forEachIndexed { index, enc ->
            assertEquals("name roundtrip $index", attachments[index].name, decryptor(passphrase).decrypt(enc.name))
        }

        assertArrayEquals(fullRangeBytes, decryptAttachmentBytes(passphrase, encrypted.attachments[0].data))
        assertArrayEquals(ByteArray(0), decryptAttachmentBytes(passphrase, encrypted.attachments[1].data))
        assertArrayEquals(utcBytes, decryptAttachmentBytes(passphrase, encrypted.attachments[2].data))
    }

    @Test
    fun attachmentDataIsBase64CiphertextOfRawBytes() {
        val passphrase = "a2-b64-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val data = "data".toByteArray(Charsets.UTF_8)
        val enc = encryptor.encryptAttachments(
            passphrase, listOf(AttachmentInput("n", data)), iterationCount = 1000
        )

        assertNotNull(enc)
        val chunks = enc!!.single().data.split('$')
        assertEquals(6, chunks.size)
        assertEquals("aes-256-cbc/pbkdf2-sha1", chunks[1])
        // chunk[5] is base64 of the raw AES ciphertext bytes (encrypt bytes -> base64).
        java.util.Base64.getDecoder().decode(chunks[5])
        assertArrayEquals(data, decryptAttachmentBytes(passphrase, enc.single().data))
    }

    // endregion

    // region: one derivation per batch (AC-A2c)

    @Test
    fun singleDerivationForOneMessageFieldsAndAttachments() {
        val counter = CountingKeyFactory()
        val encryptor = IncomingMessageEncryptor(service("a2-single-pass"), warn = { })
        val attachments = listOf(
            AttachmentInput("a.bin", bytes(1, 2, 3)),
            AttachmentInput("b.bin", bytes(4, 5, 6)),
        )

        val result = encryptor.encryptMessageWithAttachments(
            "a2-single-pass", "sender", "recipient", "preview", attachments,
            iterationCount = 1000, keyFactory = counter.factory()
        )

        assertNotNull(result)
        assertEquals(1, counter.derivations)
        val salts = buildList {
            add(result!!.sender.split('$')[3])
            add(result.recipient!!.split('$')[3])
            add(result.contentPreview.split('$')[3])
            result.attachments.forEach {
                add(it.name.split('$')[3])
                add(it.data.split('$')[3])
            }
        }
        assertEquals(1, salts.toSet().size)
    }

    @Test
    fun singleDerivationForWholeUploadBatchManyMessages() {
        val passphrase = "a2-batch-pass"
        val counter = CountingKeyFactory()
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val scope = encryptor.openScope(passphrase, iterationCount = 1000, keyFactory = counter.factory())
        assertNotNull(scope)

        scope!!.addMessage("sender-1", "recipient-1", "preview-1", listOf(AttachmentInput("n1", bytes(1)), AttachmentInput("n2", bytes(2))))
        scope.addMessage("sender-2", null, "preview-2", listOf(AttachmentInput("n3", bytes(3))))
        scope.addMessage("sender-3", "recipient-3", "preview-3")

        val encrypted = scope.finish()
        assertEquals(3, encrypted.size)
        assertEquals("ONE derivation serves the entire upload batch", 1, counter.derivations)

        val dec = decryptor(passphrase)
        assertEquals("sender-1", dec.decrypt(encrypted[0].sender))
        assertEquals("recipient-1", dec.decrypt(encrypted[0].recipient!!))
        assertEquals("preview-1", dec.decrypt(encrypted[0].contentPreview))
        assertEquals(2, encrypted[0].attachments.size)
        assertEquals("n1", dec.decrypt(encrypted[0].attachments[0].name))
        assertArrayEquals(bytes(1), decryptAttachmentBytes(passphrase, encrypted[0].attachments[0].data))

        assertNull(encrypted[1].recipient)
        assertEquals("sender-2", dec.decrypt(encrypted[1].sender))
        assertEquals("preview-2", dec.decrypt(encrypted[1].contentPreview))
        assertEquals("n3", dec.decrypt(encrypted[1].attachments[0].name))

        assertEquals("sender-3", dec.decrypt(encrypted[2].sender))
        assertEquals(0, encrypted[2].attachments.size)

        val salts = encrypted.flatMap { m ->
            buildList {
                add(m.sender.split('$')[3])
                m.recipient?.let { add(it.split('$')[3]) }
                add(m.contentPreview.split('$')[3])
                m.attachments.forEach {
                    add(it.name.split('$')[3])
                    add(it.data.split('$')[3])
                }
            }
        }
        assertEquals("all fields share one salt (one derivation)", 1, salts.toSet().size)
    }

    @Test
    fun emptyBatchDerivesNothing() {
        val counter = CountingKeyFactory()
        val encryptor = IncomingMessageEncryptor(service("a2-empty-batch-pass"), warn = { })
        val scope = encryptor.openScope("a2-empty-batch-pass", iterationCount = 1000, keyFactory = counter.factory())
        assertNotNull(scope)
        assertTrue(scope!!.finish().isEmpty())
        assertEquals(0, counter.derivations)
    }

    // endregion

    // region: missing passphrase (AC-A2b)

    @Test
    fun nullPassphraseReturnsNullAndWarnsNoEncryptAttempt() {
        val warnings = mutableListOf<String>()
        val counter = CountingKeyFactory()
        val encryptor = IncomingMessageEncryptor(service(null), warn = { warnings += it })

        assertNull(encryptor.encryptMessage(null, "sender", null, "preview", iterationCount = 1000, keyFactory = counter.factory()))
        assertNull(encryptor.encryptMessageWithAttachments(null, "sender", null, "preview", emptyList(), iterationCount = 1000, keyFactory = counter.factory()))
        assertNull(encryptor.encryptAttachments(null, emptyList(), iterationCount = 1000, keyFactory = counter.factory()))
        assertNull(encryptor.openScope(null, iterationCount = 1000, keyFactory = counter.factory()))

        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.all { it.contains("assphrase") })
        // No encryption attempted: the derivation counter must never be touched.
        assertEquals(0, counter.derivations)
    }

    // endregion

    // region: random IV (per-field)

    @Test
    fun samePlaintextTwiceDifferentCiphertext() {
        val passphrase = "a2-rand-iv-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val first = encryptor.encryptMessage(passphrase, "same sender", "same recipient", "same preview", iterationCount = 1000)!!
        val second = encryptor.encryptMessage(passphrase, "same sender", "same recipient", "same value", iterationCount = 1000)!!

        assertNotEquals(first.sender, second.sender)
        assertNotEquals(first.recipient, second.recipient)
        assertNotEquals(first.contentPreview, second.contentPreview)
        assertNotEquals(first.sender.split('$')[3], second.sender.split('$')[3])
        assertNotEquals(first.sender.split('$')[4], second.sender.split('$')[4])
        assertNotEquals(first.sender.split('$')[5], second.sender.split('$')[5])
    }

    @Test
    fun sameAttachmentEncryptedTwiceDifferentCiphertext() {
        val passphrase = "a2-rand-att-pass"
        val encryptor = IncomingMessageEncryptor(service(passphrase), warn = { })
        val attachment = AttachmentInput("same.bin", bytes(1, 2, 3))

        val first = encryptor.encryptAttachments(passphrase, listOf(attachment), iterationCount = 1000)!!.single()
        val second = encryptor.encryptAttachments(passphrase, listOf(attachment), iterationCount = 1000)!!.single()

        assertNotEquals(first.data, second.data)
        assertNotEquals(first.data.split('$')[4], second.data.split('$')[4])
        assertNotEquals(first.data.split('$')[5], second.data.split('$')[5])
    }

    // endregion

    // region: encrypt-at-upload decision (AC-A2a / AC-A2d)

    @Test
    fun encryptAtUploadDecisionRoomAndLocalServerUntouched() {
        // The helper must not import the Room entity/DAO layer or the local server.
        val helperSource = findSource("me/capcom/smsgateway/modules/encryption/IncomingMessageEncryptor.kt").readText()
        assertTrue(helperSource.isNotEmpty())
        val helperImports = helperSource.lines().filter { it.startsWith("import ") }
        assertFalse("helper must not import the Room layer", helperImports.any { it.contains("incoming.db") })
        assertFalse("helper must not import androidx.room", helperImports.any { it.contains("androidx.room") })
        assertFalse("helper must not import the local server", helperImports.any { it.contains("localserver") })

        val inboxRoutes = findSource("me/capcom/smsgateway/modules/localserver/routes/InboxRoutes.kt").readText()
        assertTrue(inboxRoutes.isNotEmpty())
        assertFalse("InboxRoutes.kt must stay encryption-free", inboxRoutes.contains("encrypt", ignoreCase = true))
        assertFalse("InboxRoutes.kt must stay encryption-free", inboxRoutes.contains("cipher", ignoreCase = true))
        assertFalse("InboxRoutes.kt must stay encryption-free", inboxRoutes.contains("derivation", ignoreCase = true))
    }

    // endregion

    private fun findSource(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "src/main/java/$relative")
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw AssertionError("Could not find src/main/java/$relative under ${System.getProperty("user.dir")}")
    }

    private fun decryptAttachmentBytes(passphrase: String, encryptedData: String): ByteArray {
        val chunks = encryptedData.split('$')
        assertEquals(6, chunks.size)
        assertEquals("aes-256-cbc/pbkdf2-sha1", chunks[1])
        val iterations = chunks[2].substringAfter("i=").toInt()
        val salt = Base64.decode(chunks[3], Base64.NO_WRAP)
        val iv = Base64.decode(chunks[4], Base64.NO_WRAP)
        val ciphertext = Base64.decode(chunks[5], Base64.NO_WRAP)

        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    private class FakeStorage(private var passphrase: String?) : KeyValueStorage {
        private val values = mutableMapOf<String, Any?>()

        override fun <T> set(key: String, value: T) {
            values[key] = value
        }

        override fun <T> get(key: String, typeOfT: Type): T? {
            @Suppress("UNCHECKED_CAST")
            return (if (key == "passphrase") passphrase else values[key]) as T?
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }
}