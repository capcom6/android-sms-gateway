package me.capcom.smsgateway.modules.localserver.domain.messages

import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.localserver.routes.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date
import me.capcom.smsgateway.data.entities.Message as EntityMessage

class MessageCreatedAtTest {

    private fun message(createdAt: Date): Message = Message(
        id = "1",
        deviceId = "device-1",
        state = ProcessingState.Pending,
        isHashed = false,
        isEncrypted = false,
        scheduleAt = null,
        createdAt = createdAt,
        textMessage = null,
        dataMessage = null,
        mmsMessage = null,
        hashedMessage = null,
        recipients = emptyList(),
        states = emptyMap(),
    )

    @Test
    fun preservesCreatedAt() {
        val expected = Date(1700000000000L)

        val actual = message(expected).createdAt

        assertEquals(expected, actual)
    }

    @Test
    fun convertsFromEpochMillisLikeToDomain() {
        // epoch start, typical (2023), year 2100 - matches Date(Long) used in toDomain
        val values = listOf(0L, 1700000000000L, 4102444800000L)

        values.forEach { epochMillis ->
            val actual = message(Date(epochMillis)).createdAt

            assertEquals("epochMillis=$epochMillis", epochMillis, actual.time)
        }
    }

    @Test
    fun createdAtIsIndependentFromScheduleAt() {
        val createdAt = Date(1700000000000L)

        val actual = message(createdAt)

        assertEquals(createdAt, actual.createdAt)
        assertNull("scheduleAt must remain null", actual.scheduleAt)
    }

    @Test
    fun toDomainMapsEpochMillisToDate() {
        val entity = EntityMessage(
            id = "1",
            withDeliveryReport = false,
            simNumber = null,
            validUntil = null,
            scheduleAt = null,
            isEncrypted = false,
            skipPhoneValidation = false,
            priority = 0,
            source = EntitySource.Local,
            content = "",
            createdAt = 1700000000000L,
        )
        val mwr = MessageWithRecipients(
            message = entity,
            recipients = emptyList(),
            states = emptyList(),
        )

        val result = mwr.toDomain(deviceId = "device-1", includeContent = false)

        assertEquals(1700000000000L, result.createdAt.time)
        assertEquals(Date(1700000000000L), result.createdAt)
    }
}
