package me.capcom.smsgateway.modules.encryption

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Attachment bytes waiting to be encrypted. [data] is the RAW bytes: they are
 * AES-encrypted first and the ciphertext is base64-encoded afterwards
 * (encrypt bytes -> base64 ciphertext), see [EncryptedAttachment.data].
 */
data class AttachmentInput(
    val name: String,
    val data: ByteArray,
)

/**
 * Encrypted form of one incoming message: [sender], optional [recipient] and
 * [contentPreview] are 6-part strings; [attachments] hold the encrypted names
 * and data. A null [recipient] means the original recipient was null/empty and
 * is NOT represented as ciphertext (nothing was encrypted for it).
 */
data class EncryptedMessage(
    val sender: String,
    val recipient: String?,
    val contentPreview: String,
    val attachments: List<EncryptedAttachment>,
)

/**
 * Encrypted attachment. [name] is a 6-part string. [data] is a 6-part
 * cipher string whose plaintext were the RAW attachment bytes, i.e. its fifth
 * chunk is base64(AES(bytes)); it is self-contained (salt/iv/iters embedded).
 */
data class EncryptedAttachment(
    val name: String,
    val data: String,
)

/**
 * Encrypt-at-upload helpers (plan A-A2).
 *
 * BINDING DECISION (encrypt-at-upload): Room keeps incoming messages as
 * plaintext (no IncomingMessage column change) and the local server
 * (modules/localserver/routes/InboxRoutes.kt) keeps serving plaintext to LAN
 * clients. Encryption is applied ONLY in the cloud upload path (worker A4),
 * which calls the helpers in this file. This file must never be referenced by
 * the Room entity/DAO layer or the local server layer (asserted by
 * IncomingMessageEncryptorTest.encryptAtUploadDecisionRoomAndLocalServerUntouched).
 *
 * Key derivation is shared with [EncryptionService.encryptBatch]: string/byte
 * fields of one "batch" get ONE PBKDF2 derivation and per-field random 16-byte
 * IVs in the 6-part format "$aes-256-cbc/pbkdf2-sha1$i=<iters>$<salt>$<iv>$<cipher>".
 * For an entire upload batch (many messages, plan A4) use [openScope], collect
 * messages with [UploadEncryptorScope.addMessage],then call
 * [UploadEncryptorScope.finish] once: that single finish() performs exactly ONE
 * key derivation for ALL fields and attachments of ALL messages.
 *
 * Missing passphrase (null) NEVER produces plaintext output: every call returns
 * null and invokes [warn]; the caller must skip the batch. An empty-string
 * passphrase remains usable (EncryptionService supports it).
 *
 * The [passphrase] argument of the helpers must equal
 * [EncryptionSettings.passphrase] (A4 passes it through); [EncryptionService]
 * reads the configured passphrase when deriving the batch key.
 */
class IncomingMessageEncryptor(
    private val service: EncryptionService,
    private val warn: (String) -> Unit,
) {

    /**
     * Encrypts one message's three fields (sender, optional recipient,
     * contentPreview) with ONE key derivation. Returns null + warns when
     * [passphrase] is null. A null or empty [recipient] stays null in the
     * result (nothing is encrypted for it).
     */
    fun encryptMessage(
        passphrase: String?,
        sender: String,
        recipient: String?,
        contentPreview: String,
        iterationCount: Int = EncryptionService.DEFAULT_ITERATION_COUNT,
        keyFactory: (String, ByteArray, Int) -> SecretKey = ::deriveBatchKey,
    ): EncryptedMessage? = openScope(passphrase, iterationCount, keyFactory)
        ?.addMessage(sender, recipient, contentPreview)
        ?.finish()
        ?.singleOrNull()

    /**
     * Encrypts one message's fields AND all its attachment names + data under
     * ONE key derivation. Pass [passphrase] from EncryptionSettings.
     */
    fun encryptMessageWithAttachments(
        passphrase: String?,
        sender: String,
        recipient: String?,
        contentPreview: String,
        attachments: List<AttachmentInput>,
        iterationCount: Int = EncryptionService.DEFAULT_ITERATION_COUNT,
        keyFactory: (String, ByteArray, Int) -> SecretKey = ::deriveBatchKey,
    ): EncryptedMessage? = openScope(passphrase, iterationCount, keyFactory)
        ?.addMessage(sender, recipient, contentPreview, attachments)
        ?.finish()
        ?.singleOrNull()

    /**
     * Encrypts [attachments] (names as strings, data as bytes) with ONE key
     * derivation. Returns null + warns when [passphrase] is null.
     */
    fun encryptAttachments(
        passphrase: String?,
        attachments: List<AttachmentInput>,
        iterationCount: Int = EncryptionService.DEFAULT_ITERATION_COUNT,
        keyFactory: (String, ByteArray, Int) -> SecretKey = ::deriveBatchKey,
    ): List<EncryptedAttachment>? {
        if (passphrase == null) {
            warn(MISSING_PASSPHRASE_MESSAGE)
            return null
        }
        if (attachments.isEmpty()) return emptyList()

        val salt = randomBytes(EncryptionService.SALT_SIZE)
        val key = keyFactory(passphrase, salt, iterationCount)
        return attachments.map { encryptAttachment(it, salt, key, iterationCount) }
    }

    /**
     * Opens an upload-batch scope. Returns null and warns when [passphrase] is
     * null. The returned scope captures the passphrase/iteration/factory ONCE;
     * A4 adds every message of the upload batch and finishes with a single
     * key derivation for the whole batch. A scope with no messages derives
     * nothing, so an upload run short-circuit is free.
     */
    fun openScope(
        passphrase: String?,
        iterationCount: Int = EncryptionService.DEFAULT_ITERATION_COUNT,
        keyFactory: (String, ByteArray, Int) -> SecretKey = ::deriveBatchKey,
    ): UploadEncryptorScope? {
        if (passphrase == null) {
            warn(MISSING_PASSPHRASE_MESSAGE)
            return null
        }
        return UploadEncryptorScope(service, passphrase, iterationCount, keyFactory)
    }

    private fun encryptAttachment(
        attachment: AttachmentInput,
        salt: ByteArray,
        key: SecretKey,
        iterationCount: Int,
    ): EncryptedAttachment = EncryptedAttachment(
        name = encryptText(attachment.name, salt, key, iterationCount),
        data = encryptBytes(attachment.data, salt, key, iterationCount),
    )

    /** 6-part string cipher of [text] under the batch key with a fresh IV. */
    private fun encryptText(
        text: String,
        salt: ByteArray,
        key: SecretKey,
        iterationCount: Int,
    ): String = encryptBytes(text.toByteArray(Charsets.UTF_8), salt, key, iterationCount)

    /**
     * 6-part cipher string whose plaintext are the raw [data] bytes: the final
     * chunk is base64(AES/CBC/PKCS5(data)) - i.e. encrypt bytes, then base64.
     * Shares [salt]/[key] so it belongs to the same single derivation as every
     * other field in the batch.
     */
    private fun encryptBytes(
        data: ByteArray,
        salt: ByteArray,
        key: SecretKey,
        iterationCount: Int,
    ): String {
        val iv = randomBytes(EncryptionService.IV_SIZE)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(data)
        return formatEncryptedChunks(salt, iv, ciphertext, iterationCount)
    }

    companion object {
        const val MISSING_PASSPHRASE_MESSAGE =
            "Encryption passphrase is not set; skipping encrypted upload (plaintext is never uploaded)."
    }
}

/**
 * Aligns the 6-part format of [EncryptionService.encryptBatch] for a value
 * encrypted with the batch [salt]/[key]: the ciphertext (raw bytes) is base64
 * encoded as the final chunk. Used for attachment data whose plaintext are
 * raw bytes; encryption-before-base64 is applied by the caller.
 */
internal fun formatEncryptedChunks(
    salt: ByteArray,
    iv: ByteArray,
    ciphertext: ByteArray,
    iterationCount: Int,
): String = buildString {
    append('$').append(EncryptionService.ALGORITHM)
    append("\$i=$iterationCount")
    append('$').append(encode(salt))
    append('$').append(encode(iv))
    append('$').append(encode(ciphertext))
}

/**
 * Collects the plaintext messages of one cloud upload batch and encrypts them
 * all with exactly ONE key derivation at [finish] using
 * [EncryptionService.encryptBatch] (one derivation, per-field random IVs).
 * Created via [IncomingMessageEncryptor.openScope].
 */
class UploadEncryptorScope internal constructor(
    private val service: EncryptionService,
    private val passphrase: String,
    private val iterationCount: Int,
    private val keyFactory: (String, ByteArray, Int) -> SecretKey,
) {
    private data class PlannedMessage(
        val sender: String,
        val encryptRecipient: Boolean,
        val recipient: String?,
        val contentPreview: String,
        val attachments: List<AttachmentInput>,
    )

    private val messages = mutableListOf<PlannedMessage>()

    /** Adds one message (fields + optional attachments) to the batch. */
    fun addMessage(
        sender: String,
        recipient: String?,
        contentPreview: String,
        attachments: List<AttachmentInput> = emptyList(),
    ): UploadEncryptorScope {
        messages += PlannedMessage(
            sender = sender,
            encryptRecipient = !recipient.isNullOrEmpty(),
            recipient = recipient,
            contentPreview = contentPreview,
            attachments = attachments,
        )
        return this
    }

    /**
     * Encrypts every added message with exactly ONE key derivation. Fields are
     * laid out per message as [sender, (recipient if present), contentPreview,
     * each attachment name]; every attachment's raw bytes share the same
     * derived key + salt. Returns an empty list (and derives nothing) when no
     * messages were added.
     */
    fun finish(): List<EncryptedMessage> {
        if (messages.isEmpty()) return emptyList()

        val stringFields = messages.flatMap { m ->
            buildList {
                add(m.sender)
                if (m.encryptRecipient) add(m.recipient!!)
                add(m.contentPreview)
                m.attachments.forEach { add(it.name) }
            }
        }

        val salt: ByteArray
        val key: SecretKey
        val stringCiphertexts: List<String>
        if (stringFields.isNotEmpty()) {
            var capturedSalt: ByteArray? = null
            var capturedKey: SecretKey? = null
            stringCiphertexts = service.encryptBatch(
                stringFields,
                iterationCount = iterationCount,
                keyFactory = { _, currentSalt, currentIterations ->
                    val derived = keyFactory(passphrase, currentSalt, currentIterations)
                    capturedSalt = currentSalt
                    capturedKey = derived
                    derived
                },
            )
            salt = checkNotNull(capturedSalt)
            key = checkNotNull(capturedKey)
        } else {
            salt = randomBytes(EncryptionService.SALT_SIZE)
            key = keyFactory(passphrase, salt, iterationCount)
            stringCiphertexts = emptyList()
        }

        var stringIndex = 0
        var dataIndex = 0
        return messages.map { message ->
            val sender = stringCiphertexts[stringIndex++]
            val recipient = if (message.encryptRecipient) stringCiphertexts[stringIndex++] else null
            val contentPreview = stringCiphertexts[stringIndex++]
            val attachments = message.attachments.map { attachment ->
                val iv = randomBytes(EncryptionService.IV_SIZE)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
                val ciphertext = cipher.doFinal(attachment.data)
                EncryptedAttachment(
                    name = stringCiphertexts[stringIndex++],
                    data = formatEncryptedChunks(salt, iv, ciphertext, iterationCount),
                )
            }
            EncryptedMessage(sender, recipient, contentPreview, attachments)
        }
    }
}