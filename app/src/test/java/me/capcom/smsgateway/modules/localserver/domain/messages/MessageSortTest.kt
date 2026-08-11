package me.capcom.smsgateway.modules.localserver.domain.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MessageSortTest {

    @Test
    fun parseValidValues() {
        val tests = mapOf(
            null to MessageSort.CreatedAtDesc,
            "created_at" to MessageSort.CreatedAtAsc,
            "-created_at" to MessageSort.CreatedAtDesc,
        )

        tests.forEach { (raw, expected) ->
            assertEquals("parse($raw)", expected, MessageSort.parse(raw))
        }
    }

    @Test
    fun parseInvalidValues() {
        val invalidValues = listOf(
            "",
            "createdAt",      // PascalCase
            "asc",            // not a wire value
            "order",          // different param concept (lifo/fifo)
            "CREATED_AT",     // uppercase
            "-created_at ",   // trailing space
        )

        invalidValues.forEach { raw ->
            try {
                MessageSort.parse(raw)
                fail("Expected IllegalArgumentException for sort=$raw")
            } catch (e: IllegalArgumentException) {
                assertEquals(
                    "sort must be one of: created_at, -created_at",
                    e.message
                )
            }
        }
    }
}
