package me.capcom.smsgateway.modules.receiver.parsers

import me.capcom.smsgateway.modules.mms.pdu.aosp.NotificationInd
import me.capcom.smsgateway.modules.mms.pdu.aosp.PduHeaders
import me.capcom.smsgateway.modules.mms.pdu.aosp.PduParser
import java.util.Date


object MMSParser {
    /**
     * X-Mms-Content-Class field types.
     */
    enum class ContentClass(val value: Int) {
        TEXT(PduHeaders.CONTENT_CLASS_TEXT),
        IMAGE_BASIC(PduHeaders.CONTENT_CLASS_IMAGE_BASIC),
        IMAGE_RICH(PduHeaders.CONTENT_CLASS_IMAGE_RICH),
        VIDEO_BASIC(PduHeaders.CONTENT_CLASS_VIDEO_BASIC),
        VIDEO_RICH(PduHeaders.CONTENT_CLASS_VIDEO_RICH),
        MEGAPIXEL(PduHeaders.CONTENT_CLASS_MEGAPIXEL),
        CONTENT_BASIC(PduHeaders.CONTENT_CLASS_CONTENT_BASIC),
        CONTENT_RICH(PduHeaders.CONTENT_CLASS_CONTENT_RICH);
    }

    data class MNotificationInd(
        val messageId: String?,
        val transactionId: String,
        val date: Date?,
        val from: String,
        val subject: String?,
        val messageSize: Long,
        val contentClass: ContentClass?,
        val contentLocation: String?,
        /**
         * Header fields that are not a part of the MMS specification or that carry a value
         * the parser is not able to decode. Their values are skipped, parsing continues.
         */
        val ignoredHeaders: List<Int>,
    )

    fun parseMNotificationInd(pdu: ByteArray): MNotificationInd {
        val parser = PduParser(pdu)
        val headers = requireNotNull(parser.parseHeaders()) { "Unparsable MMS notification" }
        val notification = NotificationInd(headers)

        require(notification.getMessageType() == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            "Invalid message type (expected m-notification-ind)"
        }

        return MNotificationInd(
            messageId = notification.getMessageId()?.let { String(it, Charsets.UTF_8) },
            transactionId = requireNotNull(notification.getTransactionId()) {
                "Missing transaction id"
            }.let { String(it, Charsets.UTF_8) },
            date = notification.getDate().takeIf { it >= 0 }?.let { Date(it * 1000L) },
            from = notification.getFrom()?.getString().orEmpty(),
            subject = notification.getSubject()?.getString(),
            messageSize = notification.getMessageSize().coerceAtLeast(0),
            contentClass = ContentClass.values()
                .firstOrNull { it.value == notification.getContentClass() },
            contentLocation = notification.getContentLocation()?.let { String(it, Charsets.UTF_8) },
            ignoredHeaders = parser.getIgnoredHeaders(),
        )
    }
}