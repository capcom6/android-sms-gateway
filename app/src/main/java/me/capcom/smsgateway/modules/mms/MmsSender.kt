package me.capcom.smsgateway.modules.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.BuildConfig
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.mms.pdu.aosp.CharacterSets
import me.capcom.smsgateway.modules.mms.pdu.aosp.EncodedStringValue
import me.capcom.smsgateway.modules.mms.pdu.aosp.PduBody
import me.capcom.smsgateway.modules.mms.pdu.aosp.PduComposer as AospPduComposer
import me.capcom.smsgateway.modules.mms.pdu.aosp.PduPart
import me.capcom.smsgateway.modules.mms.pdu.aosp.SendReq
import me.capcom.smsgateway.receivers.EventsReceiver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class MmsSender(
    private val context: Context,
) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun send(
        messageId: String,
        phoneNumbers: List<String>,
        mms: MessageContent.Mms,
        subscriptionId: Int?,
        fromMsisdn: String? = null,
    ) {
        val req = buildSendReq(phoneNumbers, mms, fromMsisdn)
        val pdu = AospPduComposer(context, req).make()
            ?: throw RuntimeException("AOSP PduComposer returned null bytes")

        val file = writePduFile(messageId, pdu)
        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file
        )

        context.grantUriPermission(
            "com.android.phone", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        context.grantUriPermission(
            "com.android.mms.service", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val sentIntent = PendingIntent.getBroadcast(
            context,
            messageId.hashCode(),
            Intent(
                EventsReceiver.ACTION_MMS_SENT,
                Uri.parse("mmsgw:$messageId"),
                context,
                EventsReceiver::class.java
            ).apply {
                putExtra(EventsReceiver.EXTRA_MESSAGE_ID, messageId)
                putExtra(EventsReceiver.EXTRA_PDU_PATH, file.absolutePath)
                flags = Intent.FLAG_RECEIVER_FOREGROUND
            },
            pendingIntentFlags()
        )

        val manager = getSmsManager(subscriptionId)
        try {
            manager.sendMultimediaMessage(
                context,
                uri,
                null,
                null,
                sentIntent
            )
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private suspend fun buildSendReq(
        phoneNumbers: List<String>,
        mms: MessageContent.Mms,
        fromMsisdn: String?,
    ): SendReq {
        val req = SendReq()

        // Recipients
        req.to = phoneNumbers.map { EncodedStringValue(it) }.toTypedArray()

        // Optional subject
        mms.subject?.takeIf { it.isNotBlank() }?.let {
            req.subject = EncodedStringValue(it)
        }

        // Optional explicit From (Verizon accepts both insert-address-token
        // default set by SendReq() and an explicit MSISDN).
        if (!fromMsisdn.isNullOrBlank()) {
            req.from = EncodedStringValue(fromMsisdn)
        }

        val body = PduBody()
        var index = 0
        mms.text?.takeIf { it.isNotBlank() }?.let { text ->
            val part = PduPart()
            part.contentType = "text/plain".toByteArray()
            part.charset = CharacterSets.UTF_8
            val name = "text_${index++}.txt"
            part.contentLocation = name.toByteArray()
            part.contentId = "<${name}>".toByteArray()
            part.name = name.toByteArray()
            part.data = text.toByteArray(Charsets.UTF_8)
            body.addPart(part)
        }

        mms.attachments.forEach { att ->
            val bytes = attachmentBytes(att)
            val fallbackName = "part_${index++}${extensionFor(att.contentType)}"
            val name = att.name?.takeIf { it.isNotBlank() } ?: fallbackName
            val part = PduPart()
            part.contentType = att.contentType.substringBefore(';').trim().toByteArray()
            part.contentLocation = name.toByteArray()
            part.contentId = "<${name}>".toByteArray()
            part.name = name.toByteArray()
            part.data = bytes
            body.addPart(part)
        }

        require(body.partsNum > 0) { "MMS must contain at least one part" }

        req.body = body
        // Message size hint for the MMSC (AOSP composes this field optionally).
        req.messageSize = (0 until body.partsNum)
            .sumOf { body.getPart(it).dataLength.toLong() }
        return req
    }

    private suspend fun attachmentBytes(att: MessageContent.Mms.Attachment): ByteArray {
        att.data?.let {
            return try {
                Base64.decode(it, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid base64 data for attachment", e)
            }
        }
        val url = att.url
            ?: throw IllegalArgumentException("Attachment must have data or url")
        return withContext(Dispatchers.IO) {
            val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
            resp.use {
                if (!it.isSuccessful) {
                    throw RuntimeException("Failed to fetch attachment $url: HTTP ${it.code}")
                }
                it.body?.bytes() ?: throw RuntimeException("Empty body from $url")
            }
        }
    }

    private fun extensionFor(contentType: String): String {
        return when (contentType.substringBefore(';').trim().lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            "video/3gpp" -> ".3gp"
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/amr" -> ".amr"
            "audio/ogg" -> ".ogg"
            "audio/wav", "audio/x-wav" -> ".wav"
            "text/plain" -> ".txt"
            "application/pdf" -> ".pdf"
            else -> ".bin"
        }
    }

    private fun writePduFile(messageId: String, bytes: ByteArray): File {
        val dir = File(context.filesDir, "mms-out").apply { mkdirs() }
        val safeId = messageId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val file = File(dir, "$safeId.pdu")
        file.writeBytes(bytes)
        return file
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(subscriptionId: Int?): SmsManager {
        return when {
            subscriptionId == null || subscriptionId < 0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)

            else -> SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    companion object {
        /** Clean up any stale PDU files for the given message. */
        fun cleanup(context: Context, messageId: String) {
            val safeId = messageId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            File(File(context.filesDir, "mms-out"), "$safeId.pdu").delete()
        }
    }
}
