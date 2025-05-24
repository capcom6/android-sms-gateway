package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.capcom.smsgateway.data.entities.OutgoingSmsStatus
import me.capcom.smsgateway.data.entities.QueuedOutgoingSms

@Dao
interface QueuedOutgoingSmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: QueuedOutgoingSms)

    @Update
    suspend fun update(sms: QueuedOutgoingSms)

    @Delete
    suspend fun delete(sms: QueuedOutgoingSms)

    @Query("SELECT * FROM queued_outgoing_sms WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): QueuedOutgoingSms?

    @Query("SELECT * FROM queued_outgoing_sms WHERE status = :status ORDER BY nextSendAttemptAt ASC")
    suspend fun getPendingMessages(status: OutgoingSmsStatus = OutgoingSmsStatus.PENDING): List<QueuedOutgoingSms>

    @Query("SELECT * FROM queued_outgoing_sms ORDER BY createdAt DESC")
    fun getAllMessagesFlow(): Flow<List<QueuedOutgoingSms>>
}
