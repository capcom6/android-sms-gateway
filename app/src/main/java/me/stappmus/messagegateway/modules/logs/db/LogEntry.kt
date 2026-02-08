package me.stappmus.messagegateway.modules.logs.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonElement


@Entity(tableName = "logs_entries", indices = [androidx.room.Index(value = ["createdAt"])])
data class LogEntry(
    val priority: Priority,
    val module: String,
    val message: String,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val context: JsonElement? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Priority {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}