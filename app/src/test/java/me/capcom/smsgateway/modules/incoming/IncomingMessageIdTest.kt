package me.capcom.smsgateway.modules.incoming

import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

// Locks the FROZEN ext_id scheme (IncomingMessagesService.buildId contract):
// "text:"/"data:"/"mms:"/"mms-header:" + InboxMessage.hashCode(). If the scheme
// changes (e.g. threading provider _id into the id), these tests fail.
internal class IncomingMessageIdTest {

    private val date = Date(1_700_000_000_000L)

    @Test
    fun textIdUsesTextPrefixWithHashCode() {
        val msg = InboxMessage.Text(
            text = "hello world",
            address = "+79001234567",
            date = date,
            subscriptionId = 0,
        )
        assertEquals("text:${msg.hashCode()}", buildIncomingMessageId(msg))
    }

    @Test
    fun dataIdUsesDataPrefixWithHashCode() {
        val msg = InboxMessage.Data(
            data = byteArrayOf(1, 2, 3),
            address = "+79001234567",
            date = date,
            subscriptionId = 0,
        )
        assertEquals("data:${msg.hashCode()}", buildIncomingMessageId(msg))
    }

    @Test
    fun mmsHeadersIdUsesMmsHeaderPrefix() {
        val msg = InboxMessage.MmsHeaders(
            messageId = "notif-1",
            transactionId = "t-123",
            subject = null,
            size = 1024,
            contentClass = null,
            address = "+79001234567",
            date = date,
            subscriptionId = 0,
        )
        assertEquals("mms-header:${msg.hashCode()}", buildIncomingMessageId(msg))
    }

    @Test
    fun mmsDownloadedIdUsesMmsPrefixAndNeverProviderId() {
        val msg = InboxMessage.MMS(
            messageId = "12345", // provider _id value must NOT become the id
            body = "body",
            subject = null,
            attachments = emptyList(),
            address = "+79001234567",
            date = date,
            subscriptionId = 0,
        )
        assertEquals("mms:${msg.hashCode()}", buildIncomingMessageId(msg))
        assertNotEquals("mms:12345", buildIncomingMessageId(msg))
    }

    @Test
    fun sameInputYieldsSameIdAndDedup() {
        val a = InboxMessage.Text("dup", "+79001234567", date, 0)
        val b = InboxMessage.Text("dup", "+79001234567", date, 0)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(buildIncomingMessageId(a), buildIncomingMessageId(b))
        assertTrue(buildIncomingMessageId(a).startsWith("text:"))
    }
}
