package com.example.services

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.OutgoingSmsQueueTable
import com.example.model.OutgoingSmsTask
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import java.time.Instant
import java.util.UUID

class OutgoingSmsTaskService {

    private fun ResultRow.toOutgoingSmsTask(): OutgoingSmsTask = OutgoingSmsTask(
        id = this[OutgoingSmsQueueTable.id].value,
        agentId = this[OutgoingSmsQueueTable.agentId],
        messageContent = this[OutgoingSmsQueueTable.messageContent],
        recipient = this[OutgoingSmsQueueTable.recipient],
        status = this[OutgoingSmsQueueTable.status],
        retries = this[OutgoingSmsQueueTable.retries],
        maxRetries = this[OutgoingSmsQueueTable.maxRetries],
        failureReason = this[OutgoingSmsQueueTable.failureReason],
        receivedFromSmppAt = this[OutgoingSmsQueueTable.receivedFromSmppAt],
        assignedToAgentAt = this[OutgoingSmsQueueTable.assignedToAgentAt],
        sentByAgentAt = this[OutgoingSmsQueueTable.sentByAgentAt],
        completedAt = this[OutgoingSmsQueueTable.completedAt],
        createdAt = this[OutgoingSmsQueueTable.createdAt],
        updatedAt = this[OutgoingSmsQueueTable.updatedAt]
    )

    suspend fun createTask(
        messageContent: String,
        recipient: String,
        receivedFromSmppAt: Instant // This implies the task originated from an SMPP message
    ): OutgoingSmsTask? = dbQuery {
        val insertStatement = OutgoingSmsQueueTable.insert {
            it[OutgoingSmsQueueTable.messageContent] = messageContent
            it[OutgoingSmsQueueTable.recipient] = recipient
            it[OutgoingSmsQueueTable.status] = "PENDING"
            it[OutgoingSmsQueueTable.receivedFromSmppAt] = receivedFromSmppAt
            it[OutgoingSmsQueueTable.createdAt] = Instant.now()
            it[OutgoingSmsQueueTable.updatedAt] = Instant.now()
        }
        insertStatement.resultedValues?.singleOrNull()?.toOutgoingSmsTask()
    }

    suspend fun getTaskById(taskId: UUID): OutgoingSmsTask? = dbQuery {
        OutgoingSmsQueueTable
            .select { OutgoingSmsQueueTable.id eq taskId }
            .mapNotNull { it.toOutgoingSmsTask() }
            .singleOrNull()
    }

    suspend fun assignTaskToAgent(taskId: UUID, agentId: UUID): Boolean = dbQuery {
        OutgoingSmsQueueTable.update({ OutgoingSmsQueueTable.id eq taskId }) {
            it[OutgoingSmsQueueTable.agentId] = agentId
            it[status] = "ASSIGNED"
            it[assignedToAgentAt] = Instant.now()
            it[updatedAt] = Instant.now()
        } > 0
    }

    suspend fun findPendingTasksForAgent(agentId: UUID, limit: Int): List<OutgoingSmsTask> = dbQuery {
        OutgoingSmsQueueTable
            .select { (OutgoingSmsQueueTable.agentId eq agentId) and (OutgoingSmsQueueTable.status eq "ASSIGNED") }
            .orderBy(OutgoingSmsQueueTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toOutgoingSmsTask() }
    }
    
    suspend fun findUnassignedTasks(limit: Int): List<OutgoingSmsTask> = dbQuery {
        OutgoingSmsQueueTable
            .select { (OutgoingSmsQueueTable.status eq "PENDING") and (OutgoingSmsQueueTable.agentId.isNull()) }
            .orderBy(OutgoingSmsQueueTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it.toOutgoingSmsTask() }
    }

    suspend fun updateTaskStatus(
        taskId: UUID,
        newStatus: String,
        failureReason: String? = null,
        agentIdIfSent: UUID? = null // To confirm which agent sent it, or if it's an internal update
    ): Boolean = dbQuery {
        OutgoingSmsQueueTable.update({ OutgoingSmsQueueTable.id eq taskId }) {
            it[status] = newStatus
            it[OutgoingSmsQueueTable.failureReason] = failureReason
            it[completedAt] = Instant.now() // Mark as completed when status is final (SENT/FAILED)
            if (newStatus == "SENT") {
                it[sentByAgentAt] = Instant.now() // Assuming this is called when agent confirms sending
                if (agentIdIfSent != null) { // If agentId is not already set (e.g. task was PENDING and directly failed by system)
                   it[OutgoingSmsQueueTable.agentId] = agentIdIfSent
                }
            }
            it[updatedAt] = Instant.now()
        } > 0
    }

    suspend fun incrementTaskRetries(taskId: UUID): Boolean = dbQuery {
        OutgoingSmsQueueTable.update({ OutgoingSmsQueueTable.id eq taskId }) {
            with(SqlExpressionBuilder) {
                it.update(retries, retries + 1)
            }
            it[updatedAt] = Instant.now()
        } > 0
    }
    
    suspend fun getTasks(page: Int, pageSize: Int, statusFilter: String?): List<OutgoingSmsTask> = dbQuery {
        val offset = (page - 1) * pageSize.toLong()
        val query = OutgoingSmsQueueTable.selectAll()
        statusFilter?.let {
            query.adjustWhere { OutgoingSmsQueueTable.status eq statusFilter }
        }
        query.orderBy(OutgoingSmsQueueTable.createdAt, SortOrder.DESC)
            .limit(pageSize, offset)
            .map { it.toOutgoingSmsTask() }
    }
}
