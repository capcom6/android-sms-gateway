package me.capcom.smsgateway.testutil

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageTotals
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.db.IncomingMessagesDao
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import java.lang.reflect.Type

/** In-memory KeyValueStorage (same shape as the encryption tests' FakeStorage). */
class FakeKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, Any?>()

    override fun <T> set(key: String, value: T) {
        values[key] = value
    }

    override fun <T> get(key: String, typeOfT: Type): T? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as T?
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

/**
 * In-memory [IncomingMessagesDao] that mirrors the Room SQL semantics of the
 * queries under test (uploadedAt IS NULL filter, type/period filters, per-id
 * uploadedAt UPDATE). LiveData surfaces are inert (never emitted).
 */
class InMemoryIncomingMessagesDao : IncomingMessagesDao {
    val rows = mutableMapOf<String, IncomingMessage>()

    override fun insert(message: IncomingMessage) {
        // INSERT OR IGNORE: a re-saved (deduped) message keeps its existing row.
        if (!rows.containsKey(message.id)) rows[message.id] = message
    }

    override fun selectLast(limit: Int): LiveData<List<IncomingMessage>> = MutableLiveData()

    override suspend fun count(type: IncomingMessageType?, from: Long, to: Long): Int =
        rows.values.count {
            (type == null || it.type == type) && it.createdAt in from..to
        }

    override suspend fun select(
        type: IncomingMessageType?,
        from: Long,
        to: Long,
        limit: Int,
        offset: Int
    ): List<IncomingMessage> = rows.values
        .filter { (type == null || it.type == type) && it.createdAt in from..to }
        .sortedWith(compareByDescending<IncomingMessage> { it.createdAt }.thenByDescending { it.id })
        .drop(offset)
        .take(limit)

    override fun selectById(id: String): IncomingMessage? = rows[id]

    override fun getStats(): LiveData<IncomingMessageTotals> = MutableLiveData()

    override fun delete(from: Long, to: Long, types: Set<IncomingMessageType>) {
        rows.entries.removeIf { it.value.type in types && it.value.createdAt in from..to }
    }

    override suspend fun truncate(until: Long) {
        rows.entries.removeIf { it.value.createdAt < until }
    }

    override suspend fun selectForUpload(
        types: Set<IncomingMessageType>?,
        from: Long?,
        until: Long?
    ): List<IncomingMessage> = rows.values
        .filter { row ->
            row.uploadedAt == null &&
                (types == null || row.type in types) &&
                (from == null || row.createdAt >= from) &&
                (until == null || row.createdAt <= until)
        }
        .sortedWith(compareBy<IncomingMessage> { it.createdAt }.thenBy { it.id })

    override suspend fun updateUploadedAt(id: String, uploadedAt: Long) {
        rows[id]?.let { rows[id] = it.copy(uploadedAt = uploadedAt) }
    }
}

/** Captures every LogEntriesDao insert for assertions. */
class RecordingLogsDao : LogEntriesDao {
    val entries = mutableListOf<LogEntry>()

    override suspend fun selectByPeriod(from: Long, to: Long): List<LogEntry> = emptyList()

    override fun selectLast(): LiveData<List<LogEntry>> = MutableLiveData()

    override fun insert(entry: LogEntry) {
        entries += entry
    }

    override suspend fun truncate(until: Long) = Unit

    val warns: List<LogEntry> get() = entries.filter { it.priority == LogEntry.Priority.WARN }
}
