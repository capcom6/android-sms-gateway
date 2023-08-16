package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageWithRecipients

@Dao
interface MessageDao {
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

    @Query("UPDATE messagerecipient SET state = :state WHERE messageId = :id AND phoneNumber = :phoneNumber")
    fun updateRecipientState(id: String, phoneNumber: String, state: Message.State)

    @Query("UPDATE messagerecipient SET state = :state WHERE messageId = :id")
    fun updateRecipientsState(id: String, state: Message.State)
}