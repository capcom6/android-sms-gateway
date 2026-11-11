package me.capcom.smsgateway.modules.messages.providers

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.messages.FakeMessagesDao
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import me.capcom.smsgateway.modules.mms.MmsSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MmsSendProviderTest {

    private data class SenderCall(
        val id: String,
        val recipients: List<String>,
        val content: MessageContent.Mms,
        val subscriptionId: Int?,
        val fromMsisdn: String?,
    )

    private class Harness(
        val dao: FakeMessagesDao,
        val provider: RecordingMmsProvider,
        val events: MutableList<MessageStateChangedEvent>,
    )

    /** Records the sender-seam call; MmsSender.send is device-bound. */
    private class RecordingMmsProvider(
        context: Context,
        dao: MessagesDao,
        machine: MessageStateService,
        simSelector: SimSelector,
        phoneNormalizer: PhoneNormalizer,
        mmsSender: MmsSender,
        encryptionService: EncryptionService,
    ) : MmsSendProvider(
        context,
        dao,
        machine,
        simSelector,
        phoneNormalizer,
        mmsSender,
        encryptionService,
    ) {
        val senderCalls = mutableListOf<SenderCall>()

        override fun sendViaMmsSender(
            id: String,
            recipients: List<String>,
            content: MessageContent.Mms,
            subscriptionId: Int?,
            fromMsisdn: String?,
        ) {
            senderCalls += SenderCall(id, recipients, content, subscriptionId, fromMsisdn)
        }
    }

    private fun provider(
        dao: FakeMessagesDao = FakeMessagesDao(),
        simSelectionMode: MessagesSettings.SimSelectionMode = MessagesSettings.SimSelectionMode.RoundRobin,
        slots: Set<Int>? = setOf(0, 1),
    ): RecordingMmsProvider = RecordingMmsProvider(
        fakeContext(),
        dao,
        fakeMachine(dao),
        fakeSimSelector(slots, simSelectionMode),
        fakePhoneNormalizer(),
        MmsSender(fakeContext()),
        fakeEncryptionService(),
    )

    private fun TestScope.harness(
        simSelectionMode: MessagesSettings.SimSelectionMode = MessagesSettings.SimSelectionMode.RoundRobin,
        slots: Set<Int>? = setOf(0, 1),
    ): Harness {
        val dao = FakeMessagesDao()
        val bus = EventBus()
        val machine = MessageStateService(dao, bus)
        val provider = RecordingMmsProvider(
            fakeContext(),
            dao,
            machine,
            fakeSimSelector(slots, simSelectionMode),
            fakePhoneNormalizer(),
            MmsSender(fakeContext()),
            fakeEncryptionService(),
        )
        val events = mutableListOf<MessageStateChangedEvent>()
        backgroundScope.launch { bus.collect<MessageStateChangedEvent> { events += it } }
        runCurrent()
        return Harness(dao, provider, events)
    }

    private fun mmsContent(withAttachment: Boolean = true) = MessageContent.Mms(
        subject = "Subject",
        text = "Body",
        attachments = if (withAttachment) {
            listOf(
                MessageContent.Mms.Attachment(
                    contentType = "text/plain",
                    name = "a.txt",
                    data = "aGVsbG8=",
                )
            )
        } else {
            emptyList()
        },
    )

    private fun Harness.request(
        content: MessageContent = mmsContent(),
        simNumber: Int? = null,
        isEncrypted: Boolean = false,
    ): StoredSendRequest {
        val recipients = dao.recipients.values.filter { it.messageId == "m1" }.toList()
        return fakeStoredRequest(
            id = 1L,
            message = fakeMessage(
                id = "m1",
                content = content,
                phoneNumbers = recipients.map { it.phoneNumber },
                isEncrypted = isEncrypted,
            ),
            recipients = recipients,
            params = fakeSendParams(simNumber = simNumber),
        )
    }

    private fun Harness.seed(vararg recipients: Pair<String, ProcessingState>) {
        dao.seed("m1", seedRecipients = recipients.toList())
    }

    @Test
    fun supportsMmsContentOnly() {
        val provider = provider()

        assertTrue(provider.supports(mmsContent()))
        assertFalse(provider.supports(MessageContent.Text("hello")))
        assertFalse(provider.supports(MessageContent.Data("aGVsbG8=", 1u)))
    }

    @Test
    fun sendNotifiesSenderPersistsPartsAndSim() = runTest {
        val h = harness()
        h.seed("+79001234567" to ProcessingState.Pending, "+79001234568" to ProcessingState.Pending)

        h.provider.send(h.request())

        val call = h.provider.senderCalls.single()
        assertEquals("m1", call.id)
        assertEquals(listOf("+79001234567", "+79001234568"), call.recipients)
        assertEquals(mmsContent(), call.content)
        // 1 + 1 attachment
        assertEquals(2, h.dao.storedMessage("m1")!!.partsCount)
        // RoundRobin: request id 1 over [0, 1] -> zero-based 1, persisted 1-based
        assertEquals(2, h.dao.storedMessage("m1")!!.simNumber)
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234567"))
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234568"))
        // MMS asymmetry: ONE bulk event with both phones, not per-recipient
        val event = h.events.single()
        assertEquals(setOf("+79001234567", "+79001234568"), event.phoneNumbers)
        assertEquals(ProcessingState.Processed, event.state)
    }

    @Test
    fun explicitSimNumberSkipsDaoSimWrite() = runTest {
        val h = harness()
        h.seed("+79001234567" to ProcessingState.Pending)

        h.provider.send(h.request(simNumber = 3))

        assertEquals(1, h.provider.senderCalls.size)
        assertNull(h.dao.storedMessage("m1")!!.simNumber)
        assertEquals(ProcessingState.Processed, h.dao.recipientState("m1", "+79001234567"))
    }

    @Test
    fun invalidRecipientFailsBeforeSender() = runTest {
        val h = harness()
        h.seed("123456789" to ProcessingState.Pending)

        val ex = try {
            h.provider.send(h.request())
            null
        } catch (e: Exception) {
            e
        }

        assertTrue("expected RuntimeException but was: $ex", ex is RuntimeException)
        assertEquals("Invalid phone number", ex!!.message)
        assertTrue(h.provider.senderCalls.isEmpty())
        assertNull(h.dao.storedMessage("m1")!!.partsCount)
        assertEquals(ProcessingState.Pending, h.dao.recipientState("m1", "123456789"))
    }

    @Test
    fun terminalRecipientIseIsSwallowed() = runTest {
        val h = harness()
        // Failed recipient cannot reach Processed -> bulk transition throws ISE,
        // which send() swallows (benign race); zero partial writes.
        h.seed(
            "+79001234567" to ProcessingState.Pending,
            "+79001234568" to ProcessingState.Failed,
        )

        h.provider.send(h.request())

        assertEquals(1, h.provider.senderCalls.size)
        assertEquals(ProcessingState.Pending, h.dao.recipientState("m1", "+79001234567"))
        assertEquals(ProcessingState.Failed, h.dao.recipientState("m1", "+79001234568"))
    }

    @Test
    fun encryptedContentRoutesThroughDecrypt() = runTest {
        val h = harness()
        h.seed("+79001234567" to ProcessingState.Pending)

        val ex = try {
            h.provider.send(
                h.request(
                    content = MessageContent.Mms(
                        subject = "\$bad",
                        text = null,
                        attachments = emptyList(),
                    ),
                    isEncrypted = true,
                ),
            )
            null
        } catch (e: Exception) {
            e
        }

        // "$bad" has < 3 '$' chunks -> EncryptionService.decrypt fails fast
        // (before any Keystore access), proving the isEncrypted branch routes.
        assertTrue("expected RuntimeException but was: $ex", ex is RuntimeException)
        assertEquals("Invalid encrypted data format", ex!!.message)
        assertTrue(h.provider.senderCalls.isEmpty())
        assertEquals(ProcessingState.Pending, h.dao.recipientState("m1", "+79001234567"))
    }
}
