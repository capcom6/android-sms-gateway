package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.capcom.smsgateway.domain.EntitySource


@Dao
interface WebHooksDao {
    @Query("SELECT * FROM webHook WHERE source = :source")
    fun selectBySource(source: EntitySource): List<WebHook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replace(webHook: WebHook)

    @Query("DELETE FROM webHook WHERE id = :id AND source = :source")
    fun delete(source: EntitySource, id: String)
}