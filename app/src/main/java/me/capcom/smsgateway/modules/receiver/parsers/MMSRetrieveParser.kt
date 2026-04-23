package me.capcom.smsgateway.modules.receiver.parsers

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

/**
 * Minimal parser for the MMS M-Retrieve.conf PDU (message type 0x84 /
 * 132 decimal). Extracts the headers we care about and the multipart
 * body parts so we can persist and forward them via webhook.
 *
 * Reference: OMA-TS-MMS_ENC-V1_3 (WAP-209 encapsulation).
 * This is NOT a full implementation — unknown headers are skipped
 * defensively.
 */
object MMSRetrieveParser {

    data class MRetrieveConf(
        val messageId: String?,
        val transactionId: String?,
        val from: String?,
        val to: List<String>,
        val subject: String?,
        val date: Date?,
        val parts: List<Part>,
    )

    data class Part(
        val partId: Long,
        val contentType: String,
        val name: String?,
        val contentLocation: String?,
        val contentId: String?,
        val charset: String?,
        val data: ByteArray,
    )

    // Header field codes (with high bit set).
    private const val F_BCC = 0x81
    private const val F_CC = 0x82
    private const val F_CONTENT_LOCATION = 0x83
    private const val F_CONTENT_TYPE = 0x84
    private const val F_DATE = 0x85
    private const val F_DELIVERY_REPORT = 0x86
    private const val F_DELIVERY_TIME = 0x87
    private const val F_EXPIRY = 0x88
    private const val F_FROM = 0x89
    private const val F_MESSAGE_CLASS = 0x8A
    private const val F_MESSAGE_ID = 0x8B
    private const val F_MESSAGE_TYPE = 0x8C
    private const val F_MMS_VERSION = 0x8D
    private const val F_MESSAGE_SIZE = 0x8E
    private const val F_PRIORITY = 0x8F
    private const val F_READ_REPORT = 0x90
    private const val F_REPORT_ALLOWED = 0x91
    private const val F_RESPONSE_STATUS = 0x92
    private const val F_RESPONSE_TEXT = 0x93
    private const val F_SENDER_VISIBILITY = 0x94
    private const val F_STATUS = 0x95
    private const val F_SUBJECT = 0x96
    private const val F_TO = 0x97
    private const val F_TRANSACTION_ID = 0x98
    private const val F_RETRIEVE_STATUS = 0x99
    private const val F_RETRIEVE_TEXT = 0x9A
    private const val F_READ_STATUS = 0x9B
    private const val F_REPLY_CHARGING = 0x9C
    private const val F_REPLY_CHARGING_DEADLINE = 0x9D
    private const val F_REPLY_CHARGING_ID = 0x9E
    private const val F_REPLY_CHARGING_SIZE = 0x9F
    private const val F_PREVIOUSLY_SENT_BY = 0xA0
    private const val F_PREVIOUSLY_SENT_DATE = 0xA1

    private val WELL_KNOWN_CONTENT_TYPES = mapOf(
        0x02 to "text/html",
        0x03 to "text/plain",
        0x0C to "multipart/mixed",
        0x0F to "multipart/alternative",
        0x1D to "image/gif",
        0x1E to "image/jpeg",
        0x1F to "image/tiff",
        0x20 to "image/png",
        0x21 to "image/vnd.wap.wbmp",
        0x23 to "application/vnd.wap.multipart.mixed",
        0x27 to "application/xml",
        0x28 to "text/xml",
        0x33 to "application/vnd.wap.multipart.related",
        0x3B to "application/xhtml+xml",
        0x3D to "text/css",
        0x5B to "application/smil",
    )

    fun parse(pdu: ByteArray): MRetrieveConf {
        val buf = ByteBuffer.wrap(pdu).order(ByteOrder.BIG_ENDIAN)

        val to = mutableListOf<String>()
        var from: String? = null
        var messageId: String? = null
        var transactionId: String? = null
        var subject: String? = null
        var date: Date? = null
        var contentTypeParsed: ContentTypeInfo? = null

        // Parse headers until Content-Type is consumed; the body follows.
        while (buf.hasRemaining()) {
            val code = buf.get().toInt() and 0xFF
            when (code) {
                F_MESSAGE_TYPE -> readShortInt(buf)
                F_TRANSACTION_ID -> transactionId = readTextString(buf)
                F_MMS_VERSION -> readShortInt(buf)
                F_DATE -> date = Date(readLongInt(buf) * 1000L)
                F_FROM -> from = readFrom(buf)
                F_TO -> to.add(stripAddrType(readEncodedString(buf)))
                F_CC -> to.add(stripAddrType(readEncodedString(buf)))
                F_BCC -> to.add(stripAddrType(readEncodedString(buf)))
                F_SUBJECT -> subject = readEncodedString(buf)
                F_MESSAGE_ID -> messageId = readTextString(buf)
                F_MESSAGE_CLASS -> {
                    // Either short-integer or token-text. Peek first byte.
                    val mark = buf.position()
                    val b = buf.get().toInt() and 0xFF
                    buf.position(mark)
                    if (b >= 0x80) buf.get() else readTextString(buf)
                }
                F_CONTENT_TYPE -> {
                    contentTypeParsed = readContentType(buf)
                    break // body follows
                }
                F_DELIVERY_REPORT, F_READ_REPORT, F_PRIORITY, F_STATUS,
                F_SENDER_VISIBILITY, F_REPORT_ALLOWED, F_RESPONSE_STATUS,
                F_RETRIEVE_STATUS, F_READ_STATUS, F_REPLY_CHARGING,
                F_REPLY_CHARGING_DEADLINE -> readShortInt(buf)
                F_DELIVERY_TIME, F_EXPIRY -> readExpiry(buf)
                F_MESSAGE_SIZE, F_REPLY_CHARGING_SIZE -> readLongInt(buf)
                F_CONTENT_LOCATION, F_RESPONSE_TEXT, F_RETRIEVE_TEXT,
                F_REPLY_CHARGING_ID, F_PREVIOUSLY_SENT_BY -> readTextString(buf)
                F_PREVIOUSLY_SENT_DATE -> readLongInt(buf)
                else -> {
                    // Unknown header: best-effort skip as text-string.
                    readTextString(buf)
                }
            }
        }

        val parts = if (contentTypeParsed != null && buf.hasRemaining()) {
            readMultipart(buf)
        } else {
            emptyList()
        }

        return MRetrieveConf(
            messageId = messageId,
            transactionId = transactionId,
            from = from?.let { stripAddrType(it) },
            to = to,
            subject = subject,
            date = date,
            parts = parts,
        )
    }

    // --- WSP primitives ---

    private fun readShortInt(buf: ByteBuffer): Int = buf.get().toInt() and 0xFF

    private fun readLongInt(buf: ByteBuffer): Long {
        val length = readShortInt(buf)
        var v = 0L
        repeat(length.coerceAtMost(buf.remaining())) {
            v = (v shl 8) or (buf.get().toLong() and 0xFF)
        }
        return v
    }

    private fun readUintvar(buf: ByteBuffer): Long {
        var v = 0L
        while (true) {
            val b = buf.get().toInt() and 0xFF
            v = (v shl 7) or (b and 0x7F).toLong()
            if ((b and 0x80) == 0) break
        }
        return v
    }

    private fun readTextString(buf: ByteBuffer): String {
        if (!buf.hasRemaining()) return ""
        val mark = buf.position()
        val first = buf.get().toInt() and 0xFF
        val start = if (first == 0x7F) buf.position() else {
            buf.position(mark); buf.position()
        }
        val bytes = ArrayList<Byte>()
        while (buf.hasRemaining()) {
            val b = buf.get()
            if (b == 0x00.toByte()) break
            bytes.add(b)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun readValueLength(buf: ByteBuffer): Int {
        val b = readShortInt(buf)
        return when {
            b <= 30 -> b
            b == 31 -> readUintvar(buf).toInt()
            else -> {
                // It was a short-integer itself — backtrack is impossible, caller should not call this for shorts.
                b
            }
        }
    }

    private fun readEncodedString(buf: ByteBuffer): String {
        if (!buf.hasRemaining()) return ""
        val mark = buf.position()
        val b = buf.get().toInt() and 0xFF
        buf.position(mark)
        if (b <= 30 || b == 31) {
            // Value-length + charset + text-string
            val len = readValueLength(buf)
            val bodyStart = buf.position()
            // Read charset (short-integer or long-integer).
            val charsetFirst = buf.get().toInt() and 0xFF
            if (charsetFirst < 0x80) {
                // long-integer: first byte is length
                repeat(charsetFirst) { buf.get() }
            }
            // Then text-string up to NUL.
            val s = readTextString(buf)
            val consumed = buf.position() - bodyStart
            if (consumed < len) buf.position(bodyStart + len)
            return s
        }
        return readTextString(buf)
    }

    private fun readFrom(buf: ByteBuffer): String {
        val mark = buf.position()
        val valueLen = readValueLength(buf)
        val start = buf.position()
        val addrType = readShortInt(buf)
        val addr = if (addrType == 0x80) readEncodedString(buf) else ""
        // Ensure we don't overrun.
        val end = start + valueLen
        if (buf.position() < end) buf.position(end)
        return addr
    }

    private fun readExpiry(buf: ByteBuffer) {
        val len = readValueLength(buf)
        repeat(len) { if (buf.hasRemaining()) buf.get() }
    }

    private data class ContentTypeInfo(
        val type: String,
        val params: Map<String, String>,
    )

    private fun readContentType(buf: ByteBuffer): ContentTypeInfo {
        val mark = buf.position()
        val first = buf.get().toInt() and 0xFF
        buf.position(mark)
        if (first >= 0x80) {
            // Constrained-media: single short-integer
            val code = buf.get().toInt() and 0x7F
            val name = WELL_KNOWN_CONTENT_TYPES[code] ?: "application/octet-stream"
            return ContentTypeInfo(name, emptyMap())
        }
        // Value-length + (well-known-media|extension-media) + *(Parameter)
        val len = readValueLength(buf)
        val innerStart = buf.position()
        val innerEnd = innerStart + len

        val second = buf.get().toInt() and 0xFF
        val typeName = if (second >= 0x80) {
            WELL_KNOWN_CONTENT_TYPES[second and 0x7F] ?: "application/octet-stream"
        } else {
            // Backtrack and read as text-string.
            buf.position(buf.position() - 1)
            readTextString(buf)
        }

        val params = mutableMapOf<String, String>()
        while (buf.position() < innerEnd) {
            val pToken = buf.get().toInt() and 0xFF
            if (pToken >= 0x80) {
                val pCode = pToken and 0x7F
                val name = paramName(pCode)
                val value = readParameterValue(buf, pCode)
                if (name != null) params[name] = value
            } else {
                // Untyped-parameter (token-text + untyped-value)
                buf.position(buf.position() - 1)
                val name = readTextString(buf)
                val value = readEncodedString(buf)
                params[name] = value
            }
        }
        buf.position(innerEnd)
        return ContentTypeInfo(typeName, params)
    }

    private fun paramName(code: Int): String? = when (code) {
        0x01 -> "charset"
        0x05 -> "name"
        0x06 -> "filename"
        0x09 -> "type"
        0x0A -> "start"
        0x0E -> "start-info"
        else -> null
    }

    private fun readParameterValue(buf: ByteBuffer, pCode: Int): String {
        // Mostly text-string for what we care about; charset is short-integer.
        if (pCode == 0x01) {
            val b = buf.get().toInt() and 0xFF
            return if (b >= 0x80) "charset-${b and 0x7F}" else {
                // long-integer for larger charset codes
                val bytes = ByteArray(b)
                buf.get(bytes)
                "charset-${bytes.joinToString(":") { "%02x".format(it) }}"
            }
        }
        if (pCode == 0x09) {
            val mark = buf.position()
            val b = buf.get().toInt() and 0xFF
            buf.position(mark)
            return if (b >= 0x80) {
                val code = buf.get().toInt() and 0x7F
                WELL_KNOWN_CONTENT_TYPES[code] ?: "application/octet-stream"
            } else readTextString(buf)
        }
        return readTextString(buf)
    }

    private fun readMultipart(buf: ByteBuffer): List<Part> {
        val entries = readUintvar(buf).toInt()
        val out = mutableListOf<Part>()
        repeat(entries) {
            val headersLen = readUintvar(buf).toInt()
            val dataLen = readUintvar(buf).toInt()
            val headersEnd = buf.position() + headersLen

            val ct = readContentType(buf)
            var contentLocation: String? = null
            var contentId: String? = null
            while (buf.position() < headersEnd) {
                val headerCode = buf.get().toInt() and 0xFF
                when (headerCode) {
                    0x8E -> contentLocation = readTextString(buf) // Content-Location
                    0xC0 -> contentId = readTextString(buf)       // Content-ID
                    else -> {
                        // Unknown, try skipping as text-string.
                        buf.position(buf.position() - 1)
                        readTextString(buf) // name
                        readTextString(buf) // value
                    }
                }
            }
            buf.position(headersEnd)

            val bytes = ByteArray(dataLen)
            if (dataLen > 0) buf.get(bytes)

            out.add(
                Part(
                    partId = (out.size + 1).toLong(),
                    contentType = ct.type,
                    name = ct.params["name"] ?: ct.params["filename"],
                    contentLocation = contentLocation,
                    contentId = contentId,
                    charset = ct.params["charset"],
                    data = bytes,
                )
            )
        }
        return out
    }

    private fun stripAddrType(s: String): String {
        return s.substringBefore('/')
    }
}
