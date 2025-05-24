package me.capcom.smsgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_settings")
data class ServerSettings(
    @PrimaryKey val id: Int = 1, // Singleton settings row
    val serverUrl: String,
    val agentId: String?, // Nullable if not yet registered
    val apiKey: String?   // Nullable if not yet registered or if cleared
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
