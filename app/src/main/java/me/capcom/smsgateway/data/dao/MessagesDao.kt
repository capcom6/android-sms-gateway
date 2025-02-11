package me.capcom.smsgateway.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageState
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.data.entities.MessagesStats
import me.capcom.smsgateway.data.entities.RecipientState

@Dao
interface MessagesDao {
    @Query("SELECT COUNT(*) as count, MAX(processedAt) as lastTimestamp FROM message WHERE state <> 'Pending' AND state <> 'Failed' AND processedAt >= :timestamp")
    fun countProcessedFrom(timestamp: Long): MessagesStats

    @Query("SELECT COUNT(*) as count, MAX(createdAt) as lastTimestamp FROM message WHERE state = 'Failed' AND createdAt >= :timestamp")
    fun countFailedFrom(timestamp: Long): MessagesStats

    @Query("SELECT * FROM message ORDER BY createdAt DESC LIMIT 50")
    fun selectLast(): LiveData<List<Message>>

    @Transaction
    @Query("SELECT *, `rowid` FROM message WHERE state = 'Pending' ORDER BY createdAt DESC LIMIT 100")
    fun selectPending(): List<MessageWithRecipients>

    @Transaction
    @Query("SELECT *, `rowid` FROM message WHERE id = :id")
    fun get(id: String): MessageWithRecipients?

    @Insert
    fun _insert(message: Message)

    @Insert
    fun _insertRecipients(recipient: List<MessageRecipient>)


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun _insertMessageState(state: MessageState)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun _insertRecipientStates(state: List<RecipientState>)

    @Query(
        "INSERT INTO recipientstate(messageId, phoneNumber, state, updatedAt) " +
                "SELECT :messageId, phoneNumber, :state, strftime('%s', 'now') * 1000 " +
                "FROM messagerecipient " +
                "WHERE messageId = :messageId"
    )
    fun _insertRecipientStatesByMessage(
        messageId: String,
        state: me.capcom.smsgateway.domain.ProcessingState
    )

    @Transaction
    fun insert(message: MessageWithRecipients) {
        _insert(message.message)
        _insertMessageState(
            MessageState(
                message.message.id,
                message.message.state,
                System.currentTimeMillis()
            )
        )
        _insertRecipients(message.recipients)
        _insertRecipientStates(message.recipients.map {
            RecipientState(
                message.message.id,
                it.phoneNumber,
                it.state,
                System.currentTimeMillis()
            )
        })
    }

    @Query("UPDATE message SET state = :state WHERE id = :id AND state <> 'Failed'")
    fun _updateMessageState(id: String, state: me.capcom.smsgateway.domain.ProcessingState)

    fun updateMessageState(id: String, state: me.capcom.smsgateway.domain.ProcessingState) {
        _updateMessageState(id, state)
        _insertMessageState(
            MessageState(
                id,
                state,
                System.currentTimeMillis()
            )
        )
    }

    @Query("UPDATE message SET state = 'Processed', processedAt = strftime('%s', 'now') * 1000 WHERE id = :id")
    fun _setMessageProcessed(id: String)
    fun setMessageProcessed(id: String) {
        _setMessageProcessed(id)
        _insertMessageState(
            MessageState(
                id,
                me.capcom.smsgateway.domain.ProcessingState.Processed,
                System.currentTimeMillis()
            )
        )
    }

    @Query("UPDATE messagerecipient SET state = :state, error = :error WHERE messageId = :id AND phoneNumber = :phoneNumber AND state <> 'Failed'")
    fun _updateRecipientState(
        id: String,
        phoneNumber: String,
        state: me.capcom.smsgateway.domain.ProcessingState,
        error: String?
    )

    @Transaction
    fun updateRecipientState(
        id: String,
        phoneNumber: String,
        state: me.capcom.smsgateway.domain.ProcessingState,
        error: String?
    ) {
        _updateRecipientState(id, phoneNumber, state, error)
        _insertRecipientStates(
            listOf(
                RecipientState(id, phoneNumber, state, System.currentTimeMillis())
            )
        )
    }

    @Query("UPDATE messagerecipient SET state = :state, error = :error WHERE messageId = :id AND state <> 'Failed'")
    fun _updateRecipientsState(
        id: String,
        state: me.capcom.smsgateway.domain.ProcessingState,
        error: String?
    )

    @Transaction
    fun updateRecipientsState(
        id: String,
        state: me.capcom.smsgateway.domain.ProcessingState,
        error: String?
    ) {
        _updateRecipientsState(id, state, error)
        _insertRecipientStatesByMessage(id, state)
    }

    @Query("UPDATE message SET simNumber = :simNumber WHERE id = :id")
    fun updateSimNumber(
        id: String,
        simNumber: Int
    )

    @Query("DELETE FROM message WHERE createdAt < :until AND state <> 'Pending'")
    suspend fun truncateLog(until: Long)
}