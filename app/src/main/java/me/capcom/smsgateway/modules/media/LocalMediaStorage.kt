package me.capcom.smsgateway.modules.media

import android.content.Context
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.google.gson.GsonBuilder
import me.capcom.smsgateway.extensions.configure
import java.io.File

class LocalMediaStorage(
    context: Context,
) : MediaStorage {
    private val gson = GsonBuilder().configure().serializeNulls().create()
    private val rootDir = File(context.filesDir, "media-cache").apply {
        mkdirs()
    }

    override fun store(request: StoreMediaRequest): StoredMedia {
        val id = request.id ?: NanoIdUtils.randomNanoId()
        val blobFile = blobFile(id)

        blobFile.writeBytes(request.bytes)

        val stored = StoredMedia(
            id = id,
            mimeType = request.mimeType,
            filename = request.filename,
            size = request.size,
            width = request.width,
            height = request.height,
            durationMs = request.durationMs,
            sha256 = request.sha256,
            createdAt = System.currentTimeMillis(),
            blobFile = blobFile.name,
        )

        descriptorFile(id).writeText(gson.toJson(stored))

        return stored
    }

    override fun get(id: String): StoredMedia? {
        val descriptor = descriptorFile(id)
        if (!descriptor.exists()) {
            return null
        }

        return try {
            val stored = gson.fromJson(descriptor.readText(), StoredMedia::class.java)
            if (!blobFile(stored.id, stored.blobFile).exists()) {
                null
            } else {
                stored
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun readBytes(id: String): ByteArray? {
        val stored = get(id) ?: return null
        val file = blobFile(stored.id, stored.blobFile)
        if (!file.exists()) {
            return null
        }

        return try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    override fun cleanup(retentionDays: Int): Int {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        var deleted = 0

        rootDir.listFiles { file -> file.extension == "json" }
            ?.forEach { descriptor ->
                val stored = try {
                    gson.fromJson(descriptor.readText(), StoredMedia::class.java)
                } catch (_: Exception) {
                    null
                }

                if (stored == null || stored.createdAt < cutoff) {
                    if (stored != null) {
                        blobFile(stored.id, stored.blobFile).delete()
                    }
                    if (descriptor.delete()) {
                        deleted += 1
                    }
                }
            }

        return deleted
    }

    private fun descriptorFile(id: String): File {
        return File(rootDir, "$id.json")
    }

    private fun blobFile(id: String, fileName: String = "$id.blob"): File {
        return File(rootDir, fileName)
    }
}
