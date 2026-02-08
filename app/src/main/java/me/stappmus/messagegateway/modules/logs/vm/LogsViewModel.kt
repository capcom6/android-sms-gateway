package me.stappmus.messagegateway.modules.logs.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.logs.repositories.LogsRepository

class LogsViewModel(
    logs: LogsRepository
) : ViewModel() {
    val lastEntries: LiveData<List<LogEntry>> = logs.lastEntries
}