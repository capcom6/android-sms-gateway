package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface WebHookDao {
    @Query("SELECT * FROM webHook")
    fun select(): List<WebHook>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(webHook: WebHook)

    @Query("DELETE FROM webHook WHERE id = :id")
    fun delete(id: String)
}