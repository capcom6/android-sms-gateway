package me.stappmus.messagegateway.modules.receiver.parsers

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date


object MMSParser {
    private const val HEADER_MESSAGE_TYPE = 0x8C
    private const val HEADER_TRANSACTION_ID = 0x98
    private const val HEADER_MMS_VERSION = 0x8D
    private const val HEADER_FROM = 0x89
    private const val HEADER_SUBJECT = 0x96
    private const val HEADER_DELIVERY_REPORT = 0x86
    private const val HEADER_STORED = 0xA7
    private const val HEADER_MESSAGE_CLASS = 0x8A
    private const val HEADER_PRIORITY = 0x8F
    private const val HEADER_MESSAGE_SIZE = 0x8E
    private const val HEADER_EXPIRY = 0x88
    private const val HEADER_REPLY_CHARGING = 0x9C
    private const val HEADER_REPLY_CHARGING_DEADLINE = 0x9D
    private const val HEADER_REPLY_CHARGING_SIZE = 0x9F
    private const val HEADER_REPLY_CHARGING_ID = 0x9E
    private const val HEADER_DISTRIBUTION_INDICATOR = 0xB1
    private const val HEADER_ELEMENT_DESCRIPTOR = 0xB2
    private const val HEADER_RECOMMENDED_RETRIEVAL_MODE = 0xB4
    private const val HEADER_RECOMMENDED_RETRIEVAL_MODE_TEXT = 0xB5
    private const val HEADER_APPLIC_ID = 0xB7
    private const val HEADER_REPLY_APPLIC_ID = 0xB8
    private const val HEADER_AUX_APPLIC_INFO = 0xB9
    private const val HEADER_CONTENT_CLASS = 0xBA
    private const val HEADER_DRM_CONTENT = 0xBB
    private const val HEADER_REPLACE_ID = 0xBD
    private const val HEADER_CONTENT_LOCATION = 0x83
    private const val HEADER_MESSAGE_ID = 0x8B
    private const val HEADER_DATE = 0x85


    // Message type values from 7.3.30
    private const val MESSAGE_TYPE_NOTIFICATION_IND = 130

    private const val FROM_ADDRESS_PRESENT_TOKEN = 128

    /**
     * X-Mms-Content-Class field types.
     */
    enum class ContentClass(val value: Int) {
        TEXT(0x80),
        IMAGE_BASIC(0x81),
        IMAGE_RICH(0x82),
        VIDEO_BASIC(0x83),
        VIDEO_RICH(0x84),
        MEGAPIXEL(0x85),
        CONTENT_BASIC(0x86),
        CONTENT_RICH(0x87);
    }

    data class MNotificationInd(
        val messageId: String?,
        val transactionId: String,
        val contentLocation: String?,
        val date: Date?,
        val from: String,
        val subject: String?,
        val messageSize: Long,
        val contentClass: ContentClass?
    )

    fun parseMNotificationInd(pdu: ByteArray): MNotificationInd {
        val buffer = ByteBuffer.wrap(pdu).order(ByteOrder.BIG_ENDIAN)
        val headers = mutableMapOf<Int, Any?>()

        // Parse headers until buffer is exhausted
        while (buffer.hasRemaining()) {
            val headerType = buffer.get().toInt() and 0xFF
            val value = when (headerType) {
                HEADER_MESSAGE_TYPE -> parseShortInteger(buffer)
                HEADER_TRANSACTION_ID -> parseTextString(buffer)
                HEADER_MMS_VERSION -> parseShortInteger(buffer)
                HEADER_FROM -> parseFrom(buffer)
                HEADER_SUBJECT -> parseEncodedString(buffer)
                HEADER_DELIVERY_REPORT -> parseShortInteger(buffer)
                HEADER_STORED -> parseShortInteger(buffer)
                HEADER_MESSAGE_CLASS -> parseShortInteger(buffer)
                HEADER_PRIORITY -> parseShortInteger(buffer)
                HEADER_MESSAGE_SIZE -> parseLongInteger(buffer)
                HEADER_EXPIRY -> parseExpiry(buffer)
                HEADER_REPLY_CHARGING -> parseShortInteger(buffer)
                HEADER_REPLY_CHARGING_DEADLINE -> parseShortInteger(buffer)
                HEADER_REPLY_CHARGING_SIZE -> parseLongInteger(buffer)
                HEADER_REPLY_CHARGING_ID -> parseTextString(buffer)
                HEADER_DISTRIBUTION_INDICATOR -> parseShortInteger(buffer)
//                HEADER_ELEMENT_DESCRIPTOR -> parseShortInteger(buffer)
                HEADER_RECOMMENDED_RETRIEVAL_MODE -> parseShortInteger(buffer)
                HEADER_RECOMMENDED_RETRIEVAL_MODE_TEXT -> parseEncodedString(buffer)
                HEADER_APPLIC_ID -> parseTextString(buffer)
                HEADER_REPLY_APPLIC_ID -> parseTextString(buffer)
                HEADER_AUX_APPLIC_INFO -> parseTextString(buffer)
                HEADER_CONTENT_CLASS -> parseContentClass(buffer)
                HEADER_DRM_CONTENT -> parseShortInteger(buffer)
                HEADER_REPLACE_ID -> parseTextString(buffer)
                HEADER_CONTENT_LOCATION -> parseTextString(buffer)
                HEADER_MESSAGE_ID -> parseTextString(buffer)
                HEADER_DATE -> parseLongInteger(buffer)

                else -> throw IllegalArgumentException("Unknown header type: $headerType")
            }
            headers[headerType] = value
        }

        // Validate mandatory fields
        require(headers[HEADER_MESSAGE_TYPE] == MESSAGE_TYPE_NOTIFICATION_IND) {
            "Invalid message type (expected m-notification-ind)"
        }

        return MNotificationInd(
            messageId = headers[HEADER_MESSAGE_ID] as String?,
            transactionId = headers[HEADER_TRANSACTION_ID] as String,
            contentLocation = headers[HEADER_CONTENT_LOCATION] as String?,
            date = (headers[HEADER_DATE] as? Long)?.let { Date(it * 1000L) },
            from = headers[HEADER_FROM] as String,
            subject = headers[HEADER_SUBJECT] as String?,
            messageSize = headers[HEADER_MESSAGE_SIZE] as Long,
            contentClass = headers[HEADER_CONTENT_CLASS] as ContentClass?
        )
    }

    private fun parseFrom(buffer: ByteBuffer): String {
        val length = parseShortInteger(buffer)
        if (length <= 0 || buffer.remaining() < length) return ""

        val addressType = parseShortInteger(buffer) // 128 == address-present
        require(addressType == FROM_ADDRESS_PRESENT_TOKEN) { "Unexpected address type: $addressType" }

        return parseEncodedString(buffer)
    }

    private fun parseShortInteger(buffer: ByteBuffer): Int {
        return buffer.get().toInt() and 0xFF
    }

    private fun parseLongInteger(buffer: ByteBuffer): Long {
        val length = parseShortInteger(buffer) // number of octets to read
        var result = 0L
        repeat(length.coerceAtMost(buffer.remaining())) {
            result = (result shl 8) or (buffer.get().toLong() and 0xFF)
        }
        return result
    }

    private fun parseTextString(buffer: ByteBuffer): String {
        val bytes = mutableListOf<Byte>()
        while (buffer.hasRemaining()) {
            val byte = buffer.get()
            if (byte == 0x00.toByte()) break
            bytes.add(byte)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun parseEncodedString(buffer: ByteBuffer): String {
        if (!buffer.hasRemaining()) return ""
        val mark = buffer.position()
        val lenOrToken = buffer.get().toInt() and 0xFF

        buffer.position(mark)

        if (lenOrToken < 32) {
            parseValueLength(buffer)
            parseShortInteger(buffer)
        }

        return parseTextString(buffer)
    }

    private fun parseContentClass(buffer: ByteBuffer): ContentClass? {
        val value = parseShortInteger(buffer)
        return ContentClass.values().firstOrNull { it.value == value }
    }

    private fun parseExpiry(buffer: ByteBuffer): Long {
        parseValueLength(buffer) // skip length

        return when (parseShortInteger(buffer)) {
            128 -> parseLongInteger(buffer)  // Absolute time
            129 -> parseLongInteger(buffer)  // Relative time
            else -> 0L
        }
    }

    ///
    private const val LENGTH_QUOTE = 31
    private const val SHORT_LENGTH_MAX = 30

    private fun parseValueLength(buffer: ByteBuffer): Int {
        val temp = buffer.get()
        val first = temp.toInt() and 0xFF
        if (first <= SHORT_LENGTH_MAX) {
            return first
        } else if (first == LENGTH_QUOTE) {
            return parseUnsignedInt(buffer)
        }
        throw RuntimeException("Value length > LENGTH_QUOTE!")
    }

    private fun parseUnsignedInt(buffer: ByteBuffer): Int {
        var result = 0
        var temp = buffer.get()
        while (temp.toInt() and 0x80 != 0) {
            result = result shl 7
            result = result or (temp.toInt() and 0x7F)
            temp = buffer.get()
        }

        result = result shl 7
        result = result or (temp.toInt() and 0x7F)

        return result
    }
}
