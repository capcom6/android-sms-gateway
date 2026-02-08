package me.stappmus.messagegateway.modules.webhooks.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.modules.webhooks.domain.WebHookEvent


@Dao
interface WebHooksDao {

    @Query("SELECT * FROM webHook")
    fun select(): List<WebHook>

    @Query("SELECT * FROM webHook WHERE event = :event")
    fun selectByEvent(event: WebHookEvent): List<WebHook>

    @Query("SELECT * FROM webHook WHERE source = :source")
    fun selectBySource(source: EntitySource): List<WebHook>

    @Query("SELECT EXISTS (SELECT 1 FROM webHook WHERE id = :id AND source = :source)")
    fun exists(source: EntitySource, id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(webHook: WebHook)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(webHooks: List<WebHook>)

    @Transaction
    fun replaceAll(source: EntitySource, webHooks: List<WebHook>) {
        deleteBySource(source)
        upsertAll(webHooks)
    }

    @Query("DELETE FROM webHook WHERE id = :id AND source = :source")
    fun delete(source: EntitySource, id: String)

    @Query("DELETE FROM webHook WHERE source = :source")
    fun deleteBySource(source: EntitySource)
}