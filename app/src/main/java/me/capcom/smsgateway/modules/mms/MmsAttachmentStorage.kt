package me.capcom.smsgateway.modules.mms

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/**
 * Persists MMS attachment bytes under the app's private files directory so
 * they can be served back to webhook consumers and survive beyond
 * the lifetime of the Android MMS provider entry.
 *
 * Layout: `<filesDir>/mms-in/<sha256(messageId)>/<partId>`
 * Metadata: `<filesDir>/mms-in/<sha256(messageId)>/<partId>.metadata` (JSON)
 */
class MmsAttachmentStorage(private val context: Context) {

    private val gson = Gson()

    private val root: File
        get() = File(context.filesDir, "mms-in").apply { mkdirs() }

    fun store(
        messageId: String,
        partId: Long,
        name: String?,
        contentType: String,
        bytes: ByteArray,
    ): StoredAttachment {
        val dir = messageDir(messageId).apply { mkdirs() }
        val file = File(dir, "$partId")
        file.writeBytes(bytes)
        val meta = AttachmentMetadata(
            name = name,
            contentType = contentType.ifBlank { DEFAULT_CONTENT_TYPE },
        )
        metadataFile(file).writeText(gson.toJson(meta))
        return StoredAttachment(partId, meta.displayName, meta.contentType, file)
    }

    fun find(messageId: String, partId: Long): StoredAttachment? {
        val dir = messageDir(messageId)
        if (!dir.isDirectory) return null
        val file = File(dir, "$partId")
        if (!file.isFile) return null
        val meta = readMetadata(file)
        return StoredAttachment(partId, meta.displayName, meta.contentType, file)
    }

    fun list(messageId: String): List<StoredAttachment> {
        val dir = messageDir(messageId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(METADATA_SUFFIX) }
            ?.mapNotNull { file ->
                val partId = file.name.toLongOrNull() ?: return@mapNotNull null
                val meta = readMetadata(file)
                StoredAttachment(partId, meta.displayName, meta.contentType, file)
            }
            .orEmpty()
    }

    fun remove(messageId: String) {
        val dir = messageDir(messageId)
        if (!dir.exists()) return
        val removed = dir.deleteRecursively()
        check(removed) { "Failed to remove MMS attachments directory: ${dir.absolutePath}" }
    }

    private fun messageDir(messageId: String): File = File(root, digest(messageId))

    private fun digest(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    private fun readMetadata(file: File): AttachmentMetadata {
        val metaFile = metadataFile(file)
        if (!metaFile.isFile) return AttachmentMetadata()
        return try {
            gson.fromJson(metaFile.readText(), AttachmentMetadata::class.java)
                ?: AttachmentMetadata()
        } catch (_: Exception) {
            AttachmentMetadata()
        }
    }

    private fun metadataFile(file: File): File = File(file.parentFile, file.name + METADATA_SUFFIX)

    private data class AttachmentMetadata(
        val name: String? = null,
        val contentType: String = DEFAULT_CONTENT_TYPE,
    ) {
        val displayName: String
            get() = name?.takeIf { it.isNotBlank() } ?: "attachment"
    }

    data class StoredAttachment(
        val partId: Long,
        val displayName: String,
        val contentType: String,
        val file: File,
    )

    companion object {
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
        private const val METADATA_SUFFIX = ".metadata"
    }
}
