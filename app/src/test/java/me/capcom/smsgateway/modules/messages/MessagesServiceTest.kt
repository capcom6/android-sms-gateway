package me.capcom.smsgateway.modules.messages

import androidx.lifecycle.LiveData
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageState
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.data.entities.MessagesStats
import me.capcom.smsgateway.data.entities.MessagesTotals
import me.capcom.smsgateway.data.entities.RecipientState
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.messages.data.MessageSort
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Field
import java.util.Date

class MessagesServiceTest {

    /**
     * Hand-written fake (no mocking library). Records select/selectAscending
     * calls; every other DAO method is out of scope for the dispatch test.
     */
    class RecordingMessagesDao : MessagesDao {
        data class SelectArgs(
            val source: EntitySource,
            val state: ProcessingState?,
            val start: Long,
            val end: Long,
            val limit: Int,
            val offset: Int,
        )

        val selectCalls = mutableListOf<SelectArgs>()
        val selectAscendingCalls = mutableListOf<SelectArgs>()

        override fun selectDescending(
            source: EntitySource,
            state: ProcessingState?,
            start: Long,
            end: Long,
            limit: Int,
            offset: Int,
        ): List<MessageWithRecipients> {
            selectCalls.add(SelectArgs(source, state, start, end, limit, offset))
            return emptyList()
        }

        override fun selectAscending(
            source: EntitySource,
            state: ProcessingState?,
            start: Long,
            end: Long,
            limit: Int,
            offset: Int,
        ): List<MessageWithRecipients> {
            selectAscendingCalls.add(SelectArgs(source, state, start, end, limit, offset))
            return emptyList()
        }

        override fun countProcessedFrom(timestamp: Long): MessagesStats =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun countFailedFrom(timestamp: Long): MessagesStats =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun getMessagesStats(): LiveData<MessagesTotals> =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun selectLast(limit: Int): LiveData<List<Message>> =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun getPendingFifo(now: Date): MessageWithRecipients? =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun getPendingLifo(now: Date): MessageWithRecipients? =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun nextScheduledTime(): Long? =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun get(id: String): MessageWithRecipients? =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun count(
            source: EntitySource,
            state: ProcessingState?,
            start: Long,
            end: Long,
        ): Int = throw UnsupportedOperationException("not used in dispatch test")

        override fun _insert(message: Message) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _insertRecipients(recipient: List<MessageRecipient>) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _insertMessageState(state: MessageState) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _insertRecipientStates(state: List<RecipientState>) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _insertRecipientStatesByMessage(messageId: String, state: ProcessingState) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _updateMessageState(id: String, state: ProcessingState) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _cancelMessage(id: String): Int =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _setMessageProcessed(id: String) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun _updateRecipientState(
            id: String,
            phoneNumber: String,
            state: ProcessingState,
            error: String?,
        ) = throw UnsupportedOperationException("not used in dispatch test")

        override fun _updateRecipientsState(
            id: String,
            state: ProcessingState,
            error: String?,
        ) = throw UnsupportedOperationException("not used in dispatch test")

        override fun updateSimNumber(id: String, simNumber: Int) =
            throw UnsupportedOperationException("not used in dispatch test")

        override fun updatePartsCount(id: String, partsCount: Int) =
            throw UnsupportedOperationException("not used in dispatch test")

        override suspend fun truncateLog(until: Long) =
            throw UnsupportedOperationException("not used in dispatch test")
    }

    private fun messagesServiceWith(fake: MessagesDao): MessagesService {
        // MessagesService's constructor touches Android APIs (countryCode field
        // reads the TelephonyManager), so it cannot run in a plain JVM test.
        // Allocate the instance without the constructor and inject the dao
        // field - the only dependency selectMessages uses. No mocking library.
        // sun.misc.Unsafe is reached via reflection: the Kotlin unit-test
        // classpath cannot resolve it at compile time.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass
            .getDeclaredField("theUnsafe")
            .also { it.isAccessible = true }
            .get(null)

        val service = unsafeClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, MessagesService::class.java) as MessagesService

        val daoField = MessagesService::class.java.getDeclaredField("dao")
        val offset = unsafeClass
            .getMethod("objectFieldOffset", Field::class.java)
            .invoke(unsafe, daoField)
        unsafeClass
            .getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
            .invoke(unsafe, service, offset, fake)
        return service
    }

    @Test
    fun selectMessagesCreatedAtDescDelegatesToSelect() {
        val fake = RecordingMessagesDao()
        val service = messagesServiceWith(fake)

        val result = service.selectMessages(
            source = EntitySource.Local,
            state = ProcessingState.Pending,
            start = 100L,
            end = 200L,
            limit = 50,
            offset = 10,
            sort = MessageSort.CreatedAtDesc,
        )

        assertEquals(1, fake.selectCalls.size)
        assertEquals(0, fake.selectAscendingCalls.size)
        assertEquals(
            RecordingMessagesDao.SelectArgs(
                EntitySource.Local,
                ProcessingState.Pending,
                100L,
                200L,
                50,
                10
            ),
            fake.selectCalls[0],
        )
        assertEquals(emptyList<MessageWithRecipients>(), result)
    }

    @Test
    fun selectMessagesCreatedAtAscDelegatesToSelectAscending() {
        val fake = RecordingMessagesDao()
        val service = messagesServiceWith(fake)

        val result = service.selectMessages(
            source = EntitySource.Cloud,
            state = ProcessingState.Processed,
            start = 1000L,
            end = 2000L,
            limit = 25,
            offset = 0,
            sort = MessageSort.CreatedAtAsc,
        )

        assertEquals(0, fake.selectCalls.size)
        assertEquals(1, fake.selectAscendingCalls.size)
        assertEquals(
            RecordingMessagesDao.SelectArgs(
                EntitySource.Cloud,
                ProcessingState.Processed,
                1000L,
                2000L,
                25,
                0
            ),
            fake.selectAscendingCalls[0],
        )
        assertEquals(emptyList<MessageWithRecipients>(), result)
    }

    @Test
    fun selectMessagesDefaultSortResolvesToCreatedAtDesc() {
        // Regression guard: omitting the trailing sort param must behave
        // exactly like explicit CreatedAtDesc (select, NOT selectAscending).
        val fake = RecordingMessagesDao()
        val service = messagesServiceWith(fake)

        val args = RecordingMessagesDao.SelectArgs(EntitySource.Local, null, 111L, 222L, 33, 7)

        service.selectMessages(
            args.source,
            args.state,
            args.start,
            args.end,
            args.limit,
            args.offset
        )
        service.selectMessages(
            args.source,
            args.state,
            args.start,
            args.end,
            args.limit,
            args.offset,
            MessageSort.CreatedAtDesc
        )

        assertEquals(2, fake.selectCalls.size)
        assertEquals(0, fake.selectAscendingCalls.size)
        assertEquals(args, fake.selectCalls[0])
        assertEquals(args, fake.selectCalls[1])
    }
}
