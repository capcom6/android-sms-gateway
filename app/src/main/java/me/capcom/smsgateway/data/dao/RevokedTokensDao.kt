package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.capcom.smsgateway.data.entities.RevokedToken

@Dao
interface RevokedTokensDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(token: RevokedToken)

    @Query("SELECT EXISTS (SELECT 1 FROM revoked_token WHERE id = :id)")
    fun exists(id: String): Boolean
}
