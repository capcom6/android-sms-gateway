package me.capcom.smsgateway.modules.incoming.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IncomingMessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: IncomingMessage)

    @Query("SELECT * FROM incoming_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun selectLast(limit: Int): LiveData<List<IncomingMessage>>

    @Query(
        """
        SELECT COUNT(*)
        FROM incoming_messages
        WHERE (:type IS NULL OR type = :type)
          AND createdAt BETWEEN :from AND :to
        """
    )
    suspend fun count(type: IncomingMessageType?, from: Long, to: Long): Int

    @Query(
        """
        SELECT *
        FROM incoming_messages
        WHERE (:type IS NULL OR type = :type)
          AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun select(
        type: IncomingMessageType?,
        from: Long,
        to: Long,
        limit: Int,
        offset: Int
    ): List<IncomingMessage>

    @Query("SELECT * FROM incoming_messages WHERE id = :id LIMIT 1")
    fun selectById(id: String): IncomingMessage?

    @Query(
        """
        SELECT
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN type = 'SMS' THEN 1 ELSE 0 END), 0) as sms,
            COALESCE(SUM(CASE WHEN type = 'DATA_SMS' THEN 1 ELSE 0 END), 0) as dataSms,
            COALESCE(SUM(CASE WHEN type = 'MMS' OR type = 'MMS_DOWNLOADED' THEN 1 ELSE 0 END), 0) as mms
        FROM incoming_messages
        """
    )
    fun getStats(): LiveData<IncomingMessageTotals>
}
