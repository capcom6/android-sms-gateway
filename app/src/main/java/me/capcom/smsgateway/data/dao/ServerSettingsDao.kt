package me.capcom.smsgateway.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.capcom.smsgateway.data.entities.ServerSettings

@Dao
interface ServerSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: ServerSettings)

    @Query("SELECT * FROM server_settings WHERE id = :id")
    fun getSettings(id: Int = ServerSettings.SINGLETON_ID): Flow<ServerSettings?>

    @Query("SELECT * FROM server_settings WHERE id = :id")
    suspend fun getSettingsDirect(id: Int = ServerSettings.SINGLETON_ID): ServerSettings?

    @Query("DELETE FROM server_settings WHERE id = :id")
    suspend fun clear(id: Int = ServerSettings.SINGLETON_ID)
}
