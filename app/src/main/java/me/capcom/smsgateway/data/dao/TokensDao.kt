package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.capcom.smsgateway.data.entities.Token

@Dao
interface TokensDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(token: Token): Long

    @Query("UPDATE tokens SET revokedAt = strftime('%s', 'now') * 1000 WHERE id = :id")
    suspend fun revoke(id: String)

    @Query("SELECT EXISTS (SELECT 1 FROM tokens WHERE id = :id AND revokedAt IS NOT NULL)")
    suspend fun isRevoked(id: String): Boolean

    @Query("DELETE FROM tokens WHERE expiresAt < strftime('%s', 'now') * 1000")
    suspend fun cleanup()
}
