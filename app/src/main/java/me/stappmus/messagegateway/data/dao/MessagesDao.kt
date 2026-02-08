package me.stappmus.messagegateway.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.stappmus.messagegateway.data.entities.Message
import me.stappmus.messagegateway.data.entities.MessageRecipient
import me.stappmus.messagegateway.data.entities.MessageState
import me.stappmus.messagegateway.data.entities.MessageWithRecipients
import me.stappmus.messagegateway.data.entities.MessagesStats
import me.stappmus.messagegateway.data.entities.MessagesTotals
import me.stappmus.messagegateway.data.entities.RecipientState
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.ProcessingState

@Dao
interface MessagesDao {
    //#region Read
    @Query("SELECT COUNT(*) as count, MAX(processedAt) as lastTimestamp FROM message WHERE state <> 'Pending' AND state <> 'Failed' AND processedAt >= :timestamp")
    fun countProcessedFrom(timestamp: Long): MessagesStats

    @Query("SELECT COUNT(*) as count, MAX(processedAt) as lastTimestamp FROM message WHERE state = 'Failed' AND processedAt >= :timestamp")
    fun countFailedFrom(timestamp: Long): MessagesStats

    @Query(
        """
        SELECT
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN state = 'Pending' THEN 1 ELSE 0 END), 0) as pending,
            COALESCE(SUM(CASE WHEN state = 'Sent' THEN 1 ELSE 0 END), 0) as sent,
            COALESCE(SUM(CASE WHEN state = 'Delivered' THEN 1 ELSE 0 END), 0) as delivered,
            COALESCE(SUM(CASE WHEN state = 'Failed' THEN 1 ELSE 0 END), 0) as failed
        FROM message
    """
    )
    fun getMessagesStats(): LiveData<MessagesTotals>

    @Query("SELECT * FROM message ORDER BY createdAt DESC LIMIT :limit")
    fun selectLast(limit: Int): LiveData<List<Message>>

    /**
     * FIFO: oldest pending first (priority DESC, createdAt ASC)
     */
    @Transaction
    @Query("SELECT *, `rowid` FROM message WHERE state = 'Pending' ORDER BY priority DESC, createdAt ASC LIMIT 1")
    fun getPendingFifo(): MessageWithRecipients?

    /**
     * LIFO: newest pending first (priority DESC, createdAt DESC)
     */
    @Transaction
    @Query("SELECT *, `rowid` FROM message WHERE state = 'Pending' ORDER BY priority DESC, createdAt DESC LIMIT 1")
    fun getPendingLifo(): MessageWithRecipients?

    @Transaction
    @Query("SELECT *, `rowid` FROM message WHERE id = :id")
    fun get(id: String): MessageWithRecipients?

    /**
     * Count messages based on state and date range
     */
    @Query("SELECT COUNT(*) as count FROM message WHERE source = :source AND (:state IS NULL OR state = :state) AND createdAt BETWEEN :start AND :end")
    fun count(source: EntitySource, state: ProcessingState?, start: Long, end: Long): Int

    /**
     * Get messages with pagination and filtering
     */
    @Transaction
    @Query("SELECT *, `rowid` FROM message WHERE source = :source AND (:state IS NULL OR state = :state) AND createdAt BETWEEN :start AND :end ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun select(
        source: EntitySource,
        state: ProcessingState?,
        start: Long,
        end: Long,
        limit: Int,
        offset: Int
    ): List<MessageWithRecipients>
    //#endregion

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
        state: me.stappmus.messagegateway.domain.ProcessingState
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
    fun _updateMessageState(id: String, state: me.stappmus.messagegateway.domain.ProcessingState)

    fun updateMessageState(id: String, state: me.stappmus.messagegateway.domain.ProcessingState) {
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
                me.stappmus.messagegateway.domain.ProcessingState.Processed,
                System.currentTimeMillis()
            )
        )
    }

    @Query("UPDATE messagerecipient SET state = :state, error = :error WHERE messageId = :id AND phoneNumber = :phoneNumber AND state <> 'Failed'")
    fun _updateRecipientState(
        id: String,
        phoneNumber: String,
        state: me.stappmus.messagegateway.domain.ProcessingState,
        error: String?
    )

    @Transaction
    fun updateRecipientState(
        id: String,
        phoneNumber: String,
        state: me.stappmus.messagegateway.domain.ProcessingState,
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
        state: me.stappmus.messagegateway.domain.ProcessingState,
        error: String?
    )

    @Transaction
    fun updateRecipientsState(
        id: String,
        state: me.stappmus.messagegateway.domain.ProcessingState,
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

    @Query("UPDATE message SET partsCount = :partsCount WHERE id = :id")
    fun updatePartsCount(id: String, partsCount: Int)

    @Query("DELETE FROM message WHERE createdAt < :until AND state <> 'Pending'")
    suspend fun truncateLog(until: Long)
}