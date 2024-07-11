package me.capcom.smsgateway.modules.logs.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogEntriesDao {
    @Query("SELECT * FROM logs_entries WHERE createdAt BETWEEN :from and :to")
    suspend fun selectByPeriod(from: Long, to: Long): List<LogEntry>

    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("DELETE FROM logs_entries WHERE createdAt < :until")
    suspend fun truncate(until: Long)
}