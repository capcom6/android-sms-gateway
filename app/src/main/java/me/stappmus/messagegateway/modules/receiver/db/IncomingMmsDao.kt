package me.stappmus.messagegateway.modules.receiver.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface IncomingMmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(message: IncomingMmsEntity)
}
