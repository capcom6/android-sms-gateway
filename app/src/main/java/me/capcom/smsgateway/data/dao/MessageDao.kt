package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.capcom.smsgateway.data.entities.Message

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE id = :id")
    fun get(id: String): Message?
    
    @Insert
    fun insert(message: Message)

    @Query("UPDATE message SET state = :state WHERE id = :id")
    fun updateState(id: String, state: Message.State)
}