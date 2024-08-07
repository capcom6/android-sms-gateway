package me.capcom.smsgateway.modules.logs.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.logs.repositories.LogsRepository

class LogsViewModel(
    logs: LogsRepository
) : ViewModel() {
    val lastEntries: LiveData<List<LogEntry>> = logs.lastEntries
}