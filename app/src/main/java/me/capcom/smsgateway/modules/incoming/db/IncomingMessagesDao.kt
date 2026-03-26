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
