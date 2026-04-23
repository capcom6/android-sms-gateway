package me.capcom.smsgateway.modules.mms

import android.content.Context
import java.io.File

/**
 * Persists MMS attachment bytes under the app's private files directory so
 * they can be served back to webhook consumers by URL and survive beyond
 * the lifetime of the Android MMS provider entry.
 *
 * Layout: `<filesDir>/mms-in/<messageId>/<partId>-<safeName>`
 */
class MmsAttachmentStorage(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "mms-in").apply { mkdirs() }

    fun store(messageId: String, partId: Long, name: String?, bytes: ByteArray): File {
        val dir = File(root, sanitize(messageId)).apply { mkdirs() }
        val safeName = sanitize(name ?: "part")
        val file = File(dir, "$partId-$safeName")
        file.writeBytes(bytes)
        return file
    }

    fun find(messageId: String, partId: Long): File? {
        val dir = File(root, sanitize(messageId))
        if (!dir.isDirectory) return null
        val prefix = "$partId-"
        return dir.listFiles()?.firstOrNull { it.name.startsWith(prefix) }
    }

    fun remove(messageId: String) {
        val dir = File(root, sanitize(messageId))
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
