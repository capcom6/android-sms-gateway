package me.capcom.smsgateway.modules.receiver.parsers

import org.junit.Assert.assertThrows
import org.junit.Test

internal class MMSParserTest {
    private fun hexToBytes(hex: String) = hex
        .replace(Regex("\\s"), "")
        .chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    @Test
    fun parseMNotificationIndTest() {
        val tests = listOf(
            """
                8C 82 98 74 31 2D 50 5A 4A 4B 6E 44 6F
                37 72 00 8D 93 89 17 80 31 39 31 36 32
                32 35 35 38 38 37 2F 54 59 50 45 3D 50
                4C 4D 4E 00 8A 80 88 05 81 03 03 F4 7F
                8E 02 05 39 83 68 74 74 70 3A 2F 2F 78
                78 78 2E 78 78 78 78 78 78 78 78 2E 78
                78 78 2F 6D 6D 73 63 3F 78 78 2D 78 78
                78 78 78 78 78 78 78 00
            """.trimIndent(),
            """
                8C 82 98 6D 2D 37 2D 31 31 32 2D 33 2D
                63 65 66 37 65 66 2D 62 39 2D 35 31 2D
                32 00 8D 92 89 0F 80 0D 83 31 39 31 36
                32 32 35 35 38 38 37 00 8A 80 8E 02 02
                83 88 05 81 03 03 F4 80 83 68 74 74 70
                3A 2F 2F 78 78 2E 78 2D 78 78 78 78 78
                78 2E 78 78 78 2F 6D 6D 3F 54 3D 6D 2D
                78 2D 78 78 2D 78 2D 78 78 78 78 78 78
                2D 78 78 00 86 80
            """.trimIndent(),
            """
                8C 82 98 6D 2D 36 2D 34 63 2D 36 2D 61
                65 34 34 66 61 2D 39 66 2D 37 33 2D 31
                00 8D 92 89 0F 80 0D 83 31 39 31 36 32
                32 35 35 38 38 37 00 8A 80 8E 02 02 83
                88 05 81 03 03 F4 80 83 68 74 74 70 3A
                2F 2F 78 78 2E 78 2D 78 78 78 78 78 78
                2E 78 78 78 2F 6D 6D 3F 54 3D 78 2D 78
                2D 78 78 2D 78 2D 78 78 78 78 78 78 2D
                78 78 00 86 80
            """.trimIndent(),
            """
                8C 82 98 6D 2D 37 2D 34 66 2D 37 2D 38
                64 64 66 31 35 2D 37 64 2D 31 31 2D 33
                00 8D 92 89 0F 80 0D 83 31 39 31 36 32
                32 35 35 38 38 37 00 8A 80 8E 02 02 92
                88 05 81 03 03 F4 80 83 68 74 74 70 3A
                2F 2F 78 78 2E 78 2D 78 78 78 78 78 78
                2E 78 78 78 2F 6D 6D 3F 54 3D 6D 2D 78
                2D 78 78 2D 78 2D 78 78 78 78 78 78 2D
                78 78 00 86 80
            """.trimIndent(),
            """
                8C 82 98 6D 2D 31 2D 34 34 2D 38 2D 64
                30 65 32 64 38 2D 62 62 2D 36 2D 35 00
                8D 92 89 0F 80 0D 83 31 39 31 36 32 32
                35 35 38 38 37 00 8A 80 8E 02 02 9E 88
                05 81 03 03 F4 80 83 68 74 74 70 3A 2F
                2F 78 78 2E 78 2D 78 78 78 78 78 78 2E
                78 78 78 2F 6D 6D 3F 54 3D 6D 2D 78 2D
                78 2D 78 2D 78 78 78 78 78 78 2D 78 78
                00 86 80
            """.trimIndent(),
        )

        tests.forEach {
            val notification = MMSParser.parseMNotificationInd(hexToBytes(it))
            assert(notification.from.contains("19162255887"))
        }
    }

    @Test
    fun simpleTest() {
        val data = byteArrayOf(
            0x8C.toByte(),
            0x82.toByte(),
            0x98.toByte(),
            0x69,
            0x64,
            0x31,
            0x5F,
            0x31,
            0x35,
            0x36,
            0x33,
            0x39,
            0x34,
            0x38,
            0x38,
            0x34,
            0x30,
            0x00,
            0x8D.toByte(),
            0x92.toByte(),
            0x8B.toByte(),
            0x35,
            0x38,
            0x63,
            0x66,
            0x63,
            0x36,
            0x64,
            0x61,
            0x2D,
            0x32,
            0x30,
            0x32,
            0x35,
            0x30,
            0x38,
            0x32,
            0x32,
            0x30,
            0x34,
            0x35,
            0x31,
            0x34,
            0x34,
            0x40,
            0x6D,
            0x6D,
            0x73,
            0x63,
            0x2E,
            0x6D,
            0x74,
            0x73,
            0x2E,
            0x72,
            0x75,
            0x00,
            0x85.toByte(),
            0x04,
            0x68,
            0xA7.toByte(),
            0xCD.toByte(),
            0x31,
            0x89.toByte(),
            0x18,
            0x80.toByte(),
            0x2B,
            0x31,
            0x39,
            0x31,
            0x36,
            0x32,
            0x32,
            0x35,
            0x35,
            0x38,
            0x38,
            0x37,
            0x2F,
            0x54,
            0x59,
            0x50,
            0x45,
            0x3D,
            0x50,
            0x4C,
            0x4D,
            0x4E,
            0x00,
            0x8A.toByte(),
            0x80.toByte(),
            0x8E.toByte(),
            0x04,
            0x00,
            0x01,
            0x8B.toByte(),
            0xD8.toByte(),
            0x88.toByte(),
            0x05,
            0x81.toByte(),
            0x03,
            0x03,
            0xF4.toByte(),
            0x80.toByte(),
            0x83.toByte(),
            0x68,
            0x74,
            0x74,
            0x70,
            0x3A,
            0x2F,
            0x2F,
            0x31,
            0x30,
            0x2E,
            0x34,
            0x37,
            0x2E,
            0x31,
            0x33,
            0x33,
            0x2E,
            0x33,
            0x35,
            0x2F,
            0x3F,
            0x6D,
            0x65,
            0x73,
            0x73,
            0x61,
            0x67,
            0x65,
            0x2D,
            0x69,
            0x64,
            0x3D,
            0x31,
            0x35,
            0x36,
            0x33,
            0x39,
            0x34,
            0x38,
            0x38,
            0x34,
            0x30,
            0x26,
            0x69,
            0x64,
            0x3D,
            0x31
        )

        val notification = MMSParser.parseMNotificationInd(data)
        // the AOSP parser cuts the address at "/TYPE=", the receiver used to do it
        assert(notification.from == "+19162255887")
    }

    @Test
    fun parseSubjectUtf8() {
        // Minimal MMS notification-ind PDU with UTF-8 subject "테스트" (Korean for "test")
        // FROM uses Text-string format (no charset prefix) — first byte of address >= 0x20
        // Subject header: 0x96 (HEADER_SUBJECT) + Value-length(0x0B=11) + Char-set(0xEA=UTF-8 Short-int) + text + 0x00
        // Value-length = 1 (charset) + 9 (text bytes) + 1 (null terminator) = 11
        val pdu = hexToBytes(
            """
                8C 82
                98 74 65 73 74 2D 74 78 6E 2D 30 30 31 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                96 0B EA ED 85 8C EC 8A A4 ED 8A B8 00
                8E 02 03 E8
                85 04 63 B7 9F 80
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.subject == "테스트") { "Expected '테스트' but got '${notification.subject}'" }
        assert(notification.transactionId == "test-txn-001")
        assert(notification.messageSize == 1000L)
    }

    @Test
    fun parseSubjectAscii() {
        // Minimal MMS notification-ind PDU with ASCII subject "Hello"
        // FROM uses Text-string format (no charset prefix)
        // Subject header: 0x96 + Value-length(0x07=7) + Char-set(0xEA=UTF-8 Short-int) + text + 0x00
        // Value-length = 1 (charset) + 5 (text bytes) + 1 (null terminator) = 7
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                96 07 EA 48 65 6C 6C 6F 00
                8E 02 03 E8
                85 04 63 B7 9F 80
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.subject == "Hello") { "Expected 'Hello' but got '${notification.subject}'" }
    }

    @Test
    fun parseSubjectWithLongCharset() {
        // The charset of an Encoded-string-value is an Integer-value: a charset that does not
        // fit into a Short-integer (windows-1251 = 2251) is encoded as a Long-integer
        // Value-length(0x05=5) + Char-set(0x02 0x08 0xCB) + Text-string(0xE9) + End-of-string
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                96 05 02 08 CB E9 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.subject == "й") { "Expected 'й' but got '${notification.subject}'" }
    }

    @Test
    fun parseSubjectIso2022Jp() {
        // ISO-2022-JP (MIB 39) subjects carry escape bytes 0x1B that must survive parsing:
        // ESC $ B (JIS X 0208) + 0x2422 ("あ") + ESC ( B (back to ASCII)
        // Subject header: 0x96 + Value-length(0x0A=10) + Char-set(0xA7=39 Short-int) + text + 0x00
        // Value-length = 1 (charset) + 8 (text bytes) + 1 (null terminator) = 10
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                96 0A A7 1B 24 42 24 22 1B 28 42 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.subject == "あ") { "Expected 'あ' but got '${notification.subject}'" }
    }

    @Test
    fun parseBccHeader() {
        // #457: carriers may send the Bcc (0x81) header, it must not break the parsing
        // Bcc header: 0x81 + Value-length(0x0D=13) + Char-set(0xEA=UTF-8 Short-int) + text + 0x00
        // Value-length = 1 (charset) + 11 (text bytes) + 1 (null terminator) = 13
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 93
                81 0D EA 31 39 31 36 32 32 35 35 38 38 37 00
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.transactionId == "abc")
        assert(notification.messageSize == 1000L)
        assert(notification.ignoredHeaders.isEmpty()) { "Expected no ignored headers" }
    }

    @Test
    fun parseAddressHeaders() {
        // Cc (0x82) and To (0x97) carry Encoded-string-value in Text-string format
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                82 31 32 33 34 35 36 37 38 39 30 00
                97 31 32 33 34 35 36 37 38 39 30 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.messageSize == 1000L)
        assert(notification.ignoredHeaders.isEmpty()) { "Expected no ignored headers" }
    }

    @Test
    fun parseUnknownHeader() {
        // An unknown header field must not drop the whole notification
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
                FF
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.transactionId == "abc")
        assert(notification.messageSize == 1000L)
        assert(notification.ignoredHeaders == listOf(0xFF))
    }

    @Test
    fun parseUnknownHeaderWithValue() {
        // The value of an unknown header field cannot be decoded, the parsing stops there
        // instead of reading the value bytes as the next header field
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                FF 02 85 FF
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.transactionId == "abc")
        assert(notification.ignoredHeaders == listOf(0xFF))
        assert(notification.from == "") { "Expected an empty sender but got '${notification.from}'" }
        assert(notification.messageSize == 0L)
    }

    @Test
    fun parseInvalidOctetHeaderIsReported() {
        // A recognized header with an invalid value is dropped, the field id is reported
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                86 82
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.messageSize == 1000L)
        assert(notification.ignoredHeaders == listOf(0x86))
    }

    @Test
    fun parseTextHeader() {
        // WSP text-header: the field name and its value are text-strings
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                58 2D 74 65 73 74 2D 68 65 61 64 65 72 3A 31 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.messageSize == 1000L)
    }

    @Test
    fun parseElementDescriptor() {
        // X-Mms-Element-Descriptor (0xB2) carries a Content-type-value
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                B2 0B 69 6D 61 67 65 2F 6A 70 65 67 00
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.messageSize == 1000L)
        assert(notification.ignoredHeaders.isEmpty()) { "Expected no ignored headers" }
    }

    @Test
    fun parseElementDescriptorWithParameters() {
        // X-Mms-Element-Descriptor (0xB2) may carry a Content-type-value with parameters
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                B2 08 83 81 75 74 66 2D 38 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.messageSize == 1000L)
        assert(notification.ignoredHeaders.isEmpty()) { "Expected no ignored headers" }
    }

    @Test
    fun parseExpiryWithAbsoluteToken() {
        // X-Mms-Expiry (0x88) with an absolute token, the date value follows the token anyway
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                88 05 80 03 03 F4 80
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "1234567890")
        assert(notification.messageSize == 1000L)
    }

    @Test
    fun parseFromWithInsertAddressToken() {
        // The MMS proxy inserts the address, the PDU carries a token instead of a value
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                89 01 81
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "insert-address-token")
        assert(notification.messageSize == 1000L)
    }

    @Test
    fun parseFromWithIso88591Charset() {
        // The address is encoded in ISO-8859-1 (MIB 4), the byte 0xE9 is "é" there
        // From-value(0x05=5) + Address-present-token(0x80) + Encoded-string-value
        // Encoded-string-value = Value-length(0x03=3) + Char-set(0x84) + Text-string(0xE9)
        val pdu = hexToBytes(
            """
                8C 82
                98 61 62 63 00
                8D 92
                89 05 80 03 84 E9 00
                8E 02 03 E8
            """.trimIndent()
        )

        val notification = MMSParser.parseMNotificationInd(pdu)
        assert(notification.from == "é") { "Expected 'é' but got '${notification.from}'" }
    }

    @Test
    fun parseWithoutTransactionId() {
        val pdu = hexToBytes(
            """
                8C 82
                8D 92
                89 0C 80 31 32 33 34 35 36 37 38 39 30 00
                8E 02 03 E8
            """.trimIndent()
        )

        assertThrows(IllegalArgumentException::class.java) {
            MMSParser.parseMNotificationInd(pdu)
        }
    }
}