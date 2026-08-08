package me.capcom.smsgateway.modules.device.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DeviceKeysDao {
    @Insert
    suspend fun insert(deviceKey: DeviceKey): Long

    @Query("SELECT * FROM device_keys WHERE retired_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getCurrent(): DeviceKey?

    @Query("SELECT * FROM device_keys WHERE retired_at IS NULL ORDER BY id DESC")
    suspend fun getAllActive(): List<DeviceKey>

    @Query("SELECT * FROM device_keys ORDER BY id DESC")
    suspend fun getAll(): List<DeviceKey>

    @Query("SELECT * FROM device_keys WHERE key_version = :keyVersion LIMIT 1")
    suspend fun getByKeyVersion(keyVersion: Int): DeviceKey?

    @Query("UPDATE device_keys SET retired_at = :retiredAt WHERE key_version = :keyVersion")
    suspend fun retire(keyVersion: Int, retiredAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM device_keys WHERE retired_at IS NOT NULL AND retired_at < :cutoffTime")
    suspend fun getRetiredOlderThan(cutoffTime: Long): List<DeviceKey>

    @Query("DELETE FROM device_keys WHERE key_version = :keyVersion")
    suspend fun deleteByKeyVersion(keyVersion: Int)
}
