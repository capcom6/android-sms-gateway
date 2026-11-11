package me.capcom.smsgateway.modules.messages.providers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.messages.FakeMessagesDao
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataSendProviderTest {

    /** buildPayload is protected; expose only its partsCount. */
    private class RecordingDataSendProvider : DataSendProvider(
        fakeContext(),
        FakeMessagesDao(),
        fakeMachine(FakeMessagesDao()),
        fakeLogsService(),
        fakeSimSelector(),
        fakePhoneNormalizer(),
        fakeEncryptionService(),
    ) {
        suspend fun partsCount(request: StoredSendRequest): Int =
            buildPayload(request, fakeSmsManager()).partsCount
    }

    private val provider = RecordingDataSendProvider()

    @Test
    fun supportsDataContentOnly() {
        assertTrue(provider.supports(MessageContent.Data("aGVsbG8=", 9001u)))
        assertFalse(provider.supports(MessageContent.Text("hello")))
        assertFalse(provider.supports(MessageContent.Mms(null, null, emptyList())))
    }

    @Test
    fun buildPayloadIsSinglePart() = runTest {
        val request = fakeStoredRequest(
            message = fakeMessage(content = MessageContent.Data("aGVsbG8=", 9001u)),
        )

        assertEquals(1, provider.partsCount(request))
    }
}
