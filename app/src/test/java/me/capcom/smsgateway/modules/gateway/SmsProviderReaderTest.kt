package me.capcom.smsgateway.modules.gateway

import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// A4 fallback path: when providerId is null the SMS re-read must locate the
// stored row by the same key tuple (address, date, subscriptionId, type) the
// receiver records. The selection builder is pure and independently testable.
internal class SmsProviderReaderTest {

    private fun row(
        sender: String = "+79001234567",
        type: IncomingMessageType = IncomingMessageType.SMS,
        createdAt: Long = 1700000000000L,
        subscriptionId: Int? = null,
    ) = IncomingMessage(
        id = "text:1",
        type = type,
        sender = sender,
        recipient = null,
        simNumber = null,
        subscriptionId = subscriptionId,
        contentPreview = "preview",
        createdAt = createdAt,
    )

    @Test
    fun selectionIncludesAddressDateAndInboxType() {
        val (selection, args) = SmsProviderReader.smsLookupSelection(row(), includeSubscriptionId = false)!!

        assertEquals("address = ? AND date = ? AND type = ?", selection)
        assertEquals(listOf("+79001234567", "1700000000000", "1"), args.toList())
    }

    @Test
    fun selectionAddsSubIdWhenAvailable() {
        val (selection, args) = SmsProviderReader.smsLookupSelection(
            row(subscriptionId = 5),
            includeSubscriptionId = true,
        )!!

        assertEquals("address = ? AND date = ? AND sub_id = ? AND type = ?", selection)
        assertEquals(listOf("+79001234567", "1700000000000", "5", "1"), args.toList())
    }

    @Test
    fun selectionUsesStoredCreatedAtAsTheDateKey() {
        val (selection, args) = SmsProviderReader.smsLookupSelection(
            row(createdAt = 123456789L),
            includeSubscriptionId = false,
        )!!

        assertEquals("address = ? AND date = ? AND type = ?", selection)
        assertEquals("123456789", args[1])
    }

    @Test
    fun selectionKeepsDataSmsOnInboxType() {
        // DATA_SMS rows (incoming) share the type=INBOX providerbitmap.
        val (_, args) = SmsProviderReader.smsLookupSelection(
            row(type = IncomingMessageType.DATA_SMS),
            includeSubscriptionId = false,
        )!!
        assertEquals("1", args[2])
    }

    @Test
    fun blankSenderIsUnresolvable() {
        assertNull(SmsProviderReader.smsLookupSelection(row(sender = " "), includeSubscriptionId = false))
    }
}