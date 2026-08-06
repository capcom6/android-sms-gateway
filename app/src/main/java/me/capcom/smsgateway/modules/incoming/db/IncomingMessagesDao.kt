package me.capcom.smsgateway.modules.incoming.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IncomingMessagesDao {
    // PK is the caller-supplied deterministic id (prefix + InboxMessage.hashCode), so
    // re-saving the same message targets the same row. REPLACE would delete+reinsert
    // and wipe a future uploadedAt (set by A4). IGNORE keeps the existing row intact
    // while preserving dedup (ReceiverService dedups via selectById/isMessageProcessed).
    // All changed fields are hash-derived, so the kept row equals what would be inserted.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    @Query(
        """
            DELETE FROM incoming_messages
            WHERE type IN (:types) AND createdAt BETWEEN :from and :to 
        """
    )
    fun delete(from: Long, to: Long, types: Set<IncomingMessageType>)

    @Query("DELETE FROM incoming_messages WHERE createdAt < :until")
    suspend fun truncate(until: Long)

    @Query(
        """
        SELECT *
        FROM incoming_messages
        WHERE uploadedAt IS NULL
          AND (:types IS NULL OR type IN (:types))
          AND (:from IS NULL OR createdAt >= :from)
          AND (:until IS NULL OR createdAt <= :until)
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun selectForUpload(
        types: Set<IncomingMessageType>?,
        from: Long?,
        until: Long?
    ): List<IncomingMessage>

    @Query("UPDATE incoming_messages SET uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun updateUploadedAt(id: String, uploadedAt: Long)
}
