package com.example.services

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.IncomingSmsLogTable
import com.example.model.IncomingSms
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import java.time.Instant
import java.util.UUID

class IncomingSmsLogService {

    private fun ResultRow.toIncomingSms(): IncomingSms = IncomingSms(
        id = this[IncomingSmsLogTable.id].value,
        agentId = this[IncomingSmsLogTable.agentId],
        originalSender = this[IncomingSmsLogTable.originalSender],
        messageContent = this[IncomingSmsLogTable.messageContent],
        receivedAtAgentAt = this[IncomingSmsLogTable.receivedAtAgentAt],
        createdAt = this[IncomingSmsLogTable.createdAt]
    )

    suspend fun logMessage(
        agentId: UUID,
        originalSender: String,
        messageContent: String,
        receivedAtAgentAt: Instant
    ): IncomingSms? = dbQuery {
        val insertStatement = IncomingSmsLogTable.insert {
            it[IncomingSmsLogTable.agentId] = agentId
            it[IncomingSmsLogTable.originalSender] = originalSender
            it[IncomingSmsLogTable.messageContent] = messageContent
            it[IncomingSmsLogTable.receivedAtAgentAt] = receivedAtAgentAt
            it[IncomingSmsLogTable.createdAt] = Instant.now()
        }
        insertStatement.resultedValues?.singleOrNull()?.toIncomingSms()
    }
}
