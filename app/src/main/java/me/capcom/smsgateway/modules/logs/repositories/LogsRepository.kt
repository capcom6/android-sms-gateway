package me.capcom.smsgateway.modules.logs.repositories

import androidx.lifecycle.distinctUntilChanged
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao

class LogsRepository(
    private val dao: LogEntriesDao
) {
    val lastEntries = dao.selectLast().distinctUntilChanged()
}