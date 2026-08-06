package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.receiver.MmsContentReader

/**
 * Re-reads FULL message content from the Android content provider for the A4
 * encrypted cloud upload. Room only stores `contentPreview`, so the full body
 * (SMS text / MMS body) and the MMS attachment bytes must be fetched fresh at
 * upload time.
 *
 * - SMS/DATA_SMS: `content://sms/<_id>` when [IncomingMessage.providerId] is
 *   set, otherwise a lookup by (address, date, subscriptionId, type) - the
 *   same key tuple the receiver stored - and the first match is used. A
 *   missing row falls back to the stored preview (for SMS the preview IS the
 *   full text; for DATA_SMS it is the hex preview).
 * - MMS/MMS_DOWNLOADED: `content://mms/<id>` via the existing
 *   [MmsContentReader]; the plain-text parts become the body, every other
 *   part (skipping smil) is returned as [ProviderPart] with its RAW bytes.
 *   Unresolvable MMS rows (no provider id or provider row gone) return null;
 *   the worker skips and logs them.
 *
 * Part data is capped by [GatewayInboxUploader.MAX_INBOX_PART_BYTES]: MMS
 * parts exceeding the cap are skipped during the provider read (null data)
 * so oversized parts never materialize in heap; the uploader additionally
 * re-checks the cap before base64/encryption.
 */
object SmsProviderReader {

    const val SMS_INBOX_TYPE = 1

    data class ProviderPart(
        val partId: Long,
        val contentType: String,
        val name: String?,
        /** RAW bytes from the provider; null when the part data could not be read. */
        val data: ByteArray?,
        /** Part size from the provider stat; null when it could not be determined. */
        val size: Long? = null,
    )

    data class ReadResult(
        /** Full plain-text body; falls back to the stored preview when the provider body is gone. */
        val content: String,
        val parts: List<ProviderPart>,
    )

    fun read(context: Context, message: IncomingMessage): ReadResult? = when (message.type) {
        IncomingMessageType.SMS, IncomingMessageType.DATA_SMS -> readSms(context, message)
        IncomingMessageType.MMS, IncomingMessageType.MMS_DOWNLOADED -> readMms(context, message)
    }

    ///////////////////////////////////////////////////////////////////////////

    private fun readSms(context: Context, message: IncomingMessage): ReadResult {
        val resolver = context.contentResolver

        val body: String? = message.providerId
            ?.let { readSmsByProviderId(resolver, it) }
            ?: readSmsByLookup(resolver, message)

        // SMS previews are the full text; DATA_SMS previews are the hex data preview.
        return ReadResult(content = body ?: message.contentPreview, parts = emptyList())
    }

    private fun readSmsByProviderId(resolver: android.content.ContentResolver, providerId: Long): String? {
        return try {
            resolver.query(
                Uri.parse("content://sms/$providerId"),
                arrayOf("body"),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readSmsByLookup(
        resolver: android.content.ContentResolver,
        message: IncomingMessage
    ): String? {
        val includeSubscriptionId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
        val (selection, args) = smsLookupSelection(message, includeSubscriptionId) ?: return null

        return try {
            val providerId = resolver.query(
                Uri.parse("content://sms"),
                arrayOf("_id"),
                selection,
                args,
                "date DESC LIMIT 1"
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            } ?: return null
            readSmsByProviderId(resolver, providerId)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pure selection builder for the SMS fallback lookup. Filtering by
     * (address, date, subscriptionId, type=INBOX) reproduces the exact row the
     * receiver stored when the provider id was not persisted.
     */
    internal fun smsLookupSelection(
        message: IncomingMessage,
        includeSubscriptionId: Boolean,
    ): Pair<String, Array<String>>? {
        if (message.sender.isBlank()) return null

        val selection = buildString {
            append("address = ?")
            append(" AND date = ?")
            if (includeSubscriptionId) append(" AND sub_id = ?")
            append(" AND type = ?")
        }
        val args = buildList {
            add(message.sender)
            add(message.createdAt.toString())
            if (includeSubscriptionId) {
                add((message.subscriptionId ?: -1).toString())
            }
            add(SMS_INBOX_TYPE.toString())
        }.toTypedArray()

        return selection to args
    }

    ///////////////////////////////////////////////////////////////////////////

    private fun readMms(context: Context, message: IncomingMessage): ReadResult? {
        val mmsId = message.providerId ?: return null
        // Cap enforcement happens in MmsContentReader BEFORE the part is read:
        // oversized parts yield null data instead of being materialized in heap.
        val mms = MmsContentReader.read(context, mmsId, MAX_INBOX_PART_BYTES)
            ?: return null

        val parts = mms.attachments.map { attachment ->
            ProviderPart(
                partId = attachment.partId,
                contentType = attachment.contentType,
                name = attachment.name,
                data = attachment.data?.let { decodePartData(it) },
                size = attachment.size,
            )
        }

        return ReadResult(content = mms.body ?: message.contentPreview, parts = parts)
    }

    private fun decodePartData(base64: String): ByteArray? = try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }
}
