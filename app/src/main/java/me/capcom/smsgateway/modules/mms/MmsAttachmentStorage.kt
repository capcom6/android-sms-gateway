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

    fun store(messageId: String, partId: Long, name: String?, bytes: ByteArray): File {
        val dir = messageDir(messageId).apply { mkdirs() }
        val safeName = sanitize(name ?: "part")
        val file = File(dir, "$partId-$safeName")
        file.writeBytes(bytes)
        return file
    }

    fun find(messageId: String, partId: Long): File? {
        val dir = messageDir(messageId)
        if (!dir.isDirectory) return null
        val prefix = "$partId-"
        return dir.listFiles()?.firstOrNull { it.name.startsWith(prefix) }
    }

    fun list(messageId: String): List<File> {
        val dir = messageDir(messageId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.toList().orEmpty()
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
}
