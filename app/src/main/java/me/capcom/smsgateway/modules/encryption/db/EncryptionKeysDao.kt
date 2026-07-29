package me.capcom.smsgateway.modules.encryption.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EncryptionKeysDao {
    @Insert
    suspend fun insert(encryptionKey: EncryptionKey): Long

    @Query("SELECT * FROM encryption_keys WHERE retired_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getCurrent(): EncryptionKey?

    @Query("SELECT * FROM encryption_keys WHERE retired_at IS NULL ORDER BY id DESC")
    suspend fun getAllActive(): List<EncryptionKey>

    @Query("SELECT * FROM encryption_keys ORDER BY id DESC")
    suspend fun getAll(): List<EncryptionKey>

    @Query("SELECT * FROM encryption_keys WHERE key_version = :keyVersion LIMIT 1")
    suspend fun getByKeyVersion(keyVersion: Int): EncryptionKey?

    @Query("UPDATE encryption_keys SET retired_at = :retiredAt WHERE key_version = :keyVersion")
    suspend fun retire(keyVersion: Int, retiredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM encryption_keys WHERE retired_at IS NOT NULL AND retired_at < :cutoffTime")
    suspend fun deleteOld(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM encryption_keys WHERE retired_at IS NULL")
    suspend fun countActive(): Int
}
