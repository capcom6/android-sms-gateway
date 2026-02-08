package me.stappmus.messagegateway.modules.receiver.parsers

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
        assert(notification.from == "+19162255887/TYPE=PLMN")
    }
}