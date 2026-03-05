package me.capcom.smsgateway.modules.receiver

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.util.Date

object MmsContentReader {

    data class MmsMessage(
        val id: Long,
        val sender: String?,
        val subject: String?,
        val body: String?,
        val date: Date,
        val attachments: List<MmsAttachment>,
        val subscriptionId: Int?,
    )

    data class MmsAttachment(
        val partId: Long,
        val contentType: String,
        val name: String?,
        val size: Long,
        val data: String?,
    )

    fun read(context: Context, mmsId: Long): MmsMessage? {
        val resolver = context.contentResolver

        // 1. Message-level fields
        val mmsUri = Uri.parse("content://mms/$mmsId")
        val projection = mutableListOf("sub", "date")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projection += "sub_id"
        }

        val mmsCursor = resolver.query(
            mmsUri,
            projection.toTypedArray(),
            null, null, null
        ) ?: return null

        val subject: String?
        val date: Date
        val subscriptionId: Int?
        mmsCursor.use { c ->
            if (!c.moveToFirst()) return null
            subject = c.getString(0)
            // MMS dates are in seconds, not milliseconds
            date = Date(c.getLong(1) * 1000)
            subscriptionId = when {
                projection.size > 2 -> c.getInt(2).takeIf { it >= 0 }
                else -> null
            }
        }

        // 2. Sender address (type 137 = FROM)
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        val sender = resolver.query(
            addrUri,
            arrayOf("address"),
            "type = 137",
            null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
        }

        // 3. Parts (body text + attachment metadata)
        val partUri = Uri.parse("content://mms/$mmsId/part")
        val partCursor = resolver.query(
            partUri,
            arrayOf("_id", "ct", "text", "name", "fn", "cl", "_data"),
            null, null, null
        )

        val bodyParts = mutableListOf<String>()
        val attachments = mutableListOf<MmsAttachment>()

        partCursor?.use { c ->
            while (c.moveToNext()) {
                val partId = c.getLong(0)
                val contentType = c.getString(1) ?: continue

                if (contentType == "text/plain") {
                    val text = c.getString(2)
                    if (!text.isNullOrBlank()) {
                        bodyParts.add(text)
                    }
                } else if (contentType != "application/smil") {
                    val name = c.getString(3)
                        ?: c.getString(4)
                        ?: c.getString(5)
                    val dataPath = c.getString(6)
                    val size = readPartSize(resolver, partId, dataPath)

                    attachments.add(
                        MmsAttachment(
                            partId = partId,
                            contentType = contentType,
                            name = name,
                            size = size,
                            data = readPartData(resolver, partId),
                        )
                    )
                }
            }
        }

        return MmsMessage(
            id = mmsId,
            sender = sender,
            subject = subject,
            body = bodyParts.joinToString("\n").takeIf { it.isNotEmpty() },
            date = date,
            attachments = attachments,
            subscriptionId = subscriptionId,
        )
    }

    private fun readPartSize(resolver: ContentResolver, partId: Long, dataPath: String?): Long {
        val sizeFromProvider = try {
            resolver.openFileDescriptor(Uri.parse("content://mms/part/$partId"), "r")?.use { pfd ->
                pfd.statSize
            }
        } catch (_: Exception) {
            null
        }

        if (sizeFromProvider != null && sizeFromProvider >= 0L) {
            return sizeFromProvider
        }

        return dataPath?.let {
            try {
                java.io.File(it).length()
            } catch (_: Exception) {
                0L
            }
        } ?: 0L
    }


    private fun readPartData(resolver: ContentResolver, partId: Long): String? {
        return try {
            resolver.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isNotEmpty()) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
