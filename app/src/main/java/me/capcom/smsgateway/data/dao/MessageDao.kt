package me.capcom.smsgateway.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.data.entities.ProcessedStats

@Dao
interface MessageDao {
    @Query("SELECT COUNT(*) as count, MAX(processedAt) as lastTimestamp FROM message WHERE state <> 'Pending' AND state <> 'Failed' AND processedAt >= :timestamp")
    fun countProcessedFrom(timestamp: Long): ProcessedStats

    @Query("SELECT * FROM message ORDER BY createdAt DESC LIMIT 50")
    fun selectLast(): LiveData<List<Message>>

    @Transaction
    @Query("SELECT * FROM message WHERE state = 'Pending' ORDER BY createdAt")
    fun selectPending(): List<MessageWithRecipients>

    @Transaction
    @Query("SELECT * FROM message WHERE id = :id")
    fun get(id: String): MessageWithRecipients?

    @Insert
    fun insert(message: Message)

    @Insert
    fun insert(recipient: MessageRecipient)

    @Transaction
    fun insert(message: MessageWithRecipients) {
        insert(message.message)
        message.recipients.forEach {
            insert(it)
        }
    }

    @Query("UPDATE message SET state = :state WHERE id = :id")
    fun updateMessageState(id: String, state: Message.State)

    @Query("UPDATE message SET state = 'Processed', processedAt = strftime('%s', 'now') * 1000 WHERE id = :id")
    fun setMessageProcessed(id: String)

    @Query("UPDATE messagerecipient SET state = :state, error = :error WHERE messageId = :id AND phoneNumber = :phoneNumber")
    fun updateRecipientState(id: String, phoneNumber: String, state: Message.State, error: String?)

    @Query("UPDATE messagerecipient SET state = :state, error = :error WHERE messageId = :id")
    fun updateRecipientsState(id: String, state: Message.State, error: String?)

    @Query("DELETE FROM message WHERE createdAt < :until AND state <> 'Pending'")
    suspend fun truncateLog(until: Long)
}