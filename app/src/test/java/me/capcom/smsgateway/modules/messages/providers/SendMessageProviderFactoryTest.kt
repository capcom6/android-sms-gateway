package me.capcom.smsgateway.modules.messages.providers

import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.modules.messages.FakeMessagesDao
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageProviderFactoryTest {

    private fun textProvider() = TextSendProvider(
        fakeContext(),
        FakeMessagesDao(),
        fakeMachine(FakeMessagesDao()),
        fakeLogsService(),
        fakeSimSelector(),
        fakePhoneNormalizer(),
        fakeEncryptionService(),
    )

    private fun dataProvider() = DataSendProvider(
        fakeContext(),
        FakeMessagesDao(),
        fakeMachine(FakeMessagesDao()),
        fakeLogsService(),
        fakeSimSelector(),
        fakePhoneNormalizer(),
        fakeEncryptionService(),
    )

    private val factory = SendMessageProviderFactory(listOf(textProvider(), dataProvider()))

    @Test
    fun resolvesTextProvider() {
        assertTrue(factory.resolve(MessageContent.Text("hello")) is TextSendProvider)
    }

    @Test
    fun resolvesDataProvider() {
        assertTrue(factory.resolve(MessageContent.Data("aGVsbG8=", 9001u)) is DataSendProvider)
    }

    @Test
    fun mmsContentIsUnsupported() {
        val ex = runCatching {
            factory.resolve(MessageContent.Mms(null, null, emptyList()))
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was: $ex", ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("Mms"))
    }

    @Test
    fun emptyFactoryThrows() {
        val empty = SendMessageProviderFactory(emptyList())

        val ex = runCatching { empty.resolve(MessageContent.Text("hello")) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was: $ex", ex is IllegalArgumentException)
    }

    @Test
    fun supportsChecks() {
        val text = textProvider()
        val data = dataProvider()

        assertTrue(text.supports(MessageContent.Text("hello")))
        assertFalse(text.supports(MessageContent.Data("aGVsbG8=", 9001u)))
        assertFalse(text.supports(MessageContent.Mms(null, null, emptyList())))

        assertTrue(data.supports(MessageContent.Data("aGVsbG8=", 9001u)))
        assertFalse(data.supports(MessageContent.Text("hello")))
        assertFalse(data.supports(MessageContent.Mms(null, null, emptyList())))
    }
}
