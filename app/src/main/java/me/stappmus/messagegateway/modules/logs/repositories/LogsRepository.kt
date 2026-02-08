package me.stappmus.messagegateway.modules.logs.repositories

import androidx.lifecycle.distinctUntilChanged
import me.stappmus.messagegateway.modules.logs.db.LogEntriesDao

class LogsRepository(
    private val dao: LogEntriesDao
) {
    val lastEntries = dao.selectLast().distinctUntilChanged()
}