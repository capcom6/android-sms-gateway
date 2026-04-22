package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.capcom.smsgateway.data.entities.Token
import me.capcom.smsgateway.data.entities.TokenUse

@Dao
interface TokensDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(token: Token): Long

    @Query("SELECT * FROM tokens WHERE id = :id LIMIT 1")
    suspend fun findByID(id: String): Token?

    @Query("UPDATE tokens SET revokedAt = strftime('%s', 'now') * 1000 WHERE id = :id")
    suspend fun revoke(id: String)

    @Query("UPDATE tokens SET revokedAt = strftime('%s', 'now') * 1000 WHERE (id = :id OR parentJti = :id) AND revokedAt IS NULL")
    suspend fun revokeWithChildren(id: String)

    @Query("UPDATE tokens SET revokedAt = strftime('%s', 'now') * 1000 WHERE id = :id AND revokedAt IS NULL")
    suspend fun revokeIfActive(id: String): Int

    @Query("SELECT EXISTS (SELECT 1 FROM tokens WHERE id = :id AND revokedAt IS NOT NULL)")
    suspend fun isRevoked(id: String): Boolean

    @Query("DELETE FROM tokens WHERE expiresAt < strftime('%s', 'now') * 1000")
    suspend fun cleanup()

    @Transaction
    suspend fun insertPair(access: Token, refresh: Token) {
        insert(access)
        insert(refresh)
    }

    @Transaction
    suspend fun rotateRefreshToken(currentRefreshJti: String, newAccess: Token, newRefresh: Token): RefreshRotationResult {
        val current = findByID(currentRefreshJti) ?: return RefreshRotationResult.NotFound
        if (current.tokenUse != TokenUse.Refresh.value) {
            return RefreshRotationResult.WrongTokenUse
        }
        if (current.revokedAt != null) {
            return RefreshRotationResult.AlreadyRevoked
        }

        val revokedRows = revokeIfActive(currentRefreshJti)
        if (revokedRows != 1) {
            return RefreshRotationResult.AlreadyRevoked
        }

        insert(newAccess)
        insert(newRefresh)

        return RefreshRotationResult.Rotated
    }
}

enum class RefreshRotationResult {
    Rotated,
    NotFound,
    AlreadyRevoked,
    WrongTokenUse,
}
