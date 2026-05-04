package me.capcom.smsgateway.modules.mms

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Persists MMS attachment bytes under the app's private files directory so
 * they can be served back to webhook consumers by URL and survive beyond
 * the lifetime of the Android MMS provider entry.
 *
 * Layout: `<filesDir>/mms-in/<sha256(messageId)>/<partId>-<safeName>`
 */
class MmsAttachmentStorage(private val context: Context) {

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
        val safeName = sanitize(name ?: "part")
        val file = File(dir, "$partId-$safeName")
        file.writeBytes(bytes)
        metadataFile(file).writeText(contentType.ifBlank { DEFAULT_CONTENT_TYPE })
        return StoredAttachment(partId, safeName, storedContentType(file), file)
    }

    fun find(messageId: String, partId: Long): StoredAttachment? {
        val dir = messageDir(messageId)
        if (!dir.isDirectory) return null
        val prefix = "$partId-"
        return dir.listFiles()
            ?.firstOrNull { it.isAttachmentFile && it.name.startsWith(prefix) }
            ?.toStoredAttachment()
    }

    fun list(messageId: String): List<StoredAttachment> {
        val dir = messageDir(messageId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isAttachmentFile }
            ?.mapNotNull { it.toStoredAttachment() }
            .orEmpty()
    }

    fun remove(messageId: String) {
        val dir = messageDir(messageId)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    /**
     * sha256 of the messageId keeps the mapping one-to-one — distinct ids
     * like `a/b` and `a?b` no longer collapse to the same directory and
     * overwrite each other's attachments. The hex output is also
     * traversal-safe by construction.
     */
    private fun messageDir(messageId: String): File = File(root, digest(messageId))

    private fun digest(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private val File.isAttachmentFile: Boolean
        get() = isFile && !name.endsWith(METADATA_SUFFIX)

    private fun File.toStoredAttachment(): StoredAttachment? {
        val dashIdx = name.indexOf('-').takeIf { it > 0 } ?: return null
        val partId = name.substring(0, dashIdx).toLongOrNull() ?: return null
        val displayName = name.substring(dashIdx + 1)
        return StoredAttachment(partId, displayName, storedContentType(this), this)
    }

    private fun storedContentType(file: File): String {
        return metadataFile(file)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CONTENT_TYPE
    }

    private fun metadataFile(file: File): File = File(file.parentFile, file.name + METADATA_SUFFIX)

    data class StoredAttachment(
        val partId: Long,
        val displayName: String,
        val contentType: String,
        val file: File,
    )

    companion object {
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
        private const val METADATA_SUFFIX = ".content-type"
    }
}
