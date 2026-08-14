package me.capcom.smsgateway.modules.gateway.inbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InboxUploadDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: InboxUploadEntity)

    @Query(
        """
        SELECT * FROM gateway__inbox_upload
        ORDER BY id ASC
        LIMIT :limit
    """
    )
    suspend fun getPending(
        limit: Int = 100,
    ): List<InboxUploadEntity>

    @Query("DELETE FROM gateway__inbox_upload WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
