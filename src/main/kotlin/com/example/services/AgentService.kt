package com.example.services

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.RegisteredAgentsTable
import com.example.model.RegisteredAgent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

class AgentService {

    private fun ResultRow.toAgent(): RegisteredAgent = RegisteredAgent(
        id = this[RegisteredAgentsTable.id].value,
        name = this[RegisteredAgentsTable.name],
        apiKeyHash = this[RegisteredAgentsTable.apiKeyHash],
        isEnabled = this[RegisteredAgentsTable.isEnabled],
        dailySmsLimit = this[RegisteredAgentsTable.dailySmsLimit],
        currentDailySmsCount = this[RegisteredAgentsTable.currentDailySmsCount],
        lastSmsCountResetAt = this[RegisteredAgentsTable.lastSmsCountResetAt],
        lastHeartbeatAt = this[RegisteredAgentsTable.lastHeartbeatAt],
        createdAt = this[RegisteredAgentsTable.createdAt],
        updatedAt = this[RegisteredAgentsTable.updatedAt],
        smsPrefix = this[RegisteredAgentsTable.smsPrefix]
    )

    suspend fun createAgent(
        id: UUID = UUID.randomUUID(),
        name: String,
        apiKeyHash: String,
        dailySmsLimit: Int = -1,
        smsPrefix: String? = null
    ): RegisteredAgent? = dbQuery {
        val insertStatement = RegisteredAgentsTable.insert {
            it[RegisteredAgentsTable.id] = id
            it[RegisteredAgentsTable.name] = name
            it[RegisteredAgentsTable.apiKeyHash] = apiKeyHash
            it[RegisteredAgentsTable.dailySmsLimit] = dailySmsLimit
            it[RegisteredAgentsTable.lastSmsCountResetAt] = Instant.now()
            it[RegisteredAgentsTable.createdAt] = Instant.now()
            it[RegisteredAgentsTable.updatedAt] = Instant.now()
            if (smsPrefix != null) {
                it[RegisteredAgentsTable.smsPrefix] = smsPrefix
            }
        }
        insertStatement.resultedValues?.singleOrNull()?.toAgent()
    }

    suspend fun findByApiKeyHash(apiKeyHash: String): RegisteredAgent? = dbQuery {
        RegisteredAgentsTable
            .select { RegisteredAgentsTable.apiKeyHash eq apiKeyHash }
            .mapNotNull { it.toAgent() }
            .singleOrNull()
    }

    suspend fun findById(id: UUID): RegisteredAgent? = dbQuery {
        RegisteredAgentsTable
            .select { RegisteredAgentsTable.id eq id }
            .mapNotNull { it.toAgent() }
            .singleOrNull()
    }
    
    suspend fun getAllAgents(): List<RegisteredAgent> = dbQuery {
        RegisteredAgentsTable.selectAll().map { it.toAgent() }
    }

    suspend fun setEnabled(id: UUID, isEnabled: Boolean): Boolean = dbQuery {
        RegisteredAgentsTable.update({ RegisteredAgentsTable.id eq id }) {
            it[RegisteredAgentsTable.isEnabled] = isEnabled
            it[RegisteredAgentsTable.updatedAt] = Instant.now()
        } > 0
    }

    suspend fun updateConfig(
        id: UUID,
        name: String?,
        dailySmsLimit: Int?,
        smsPrefix: String? // Allow updating smsPrefix
    ): Boolean = dbQuery {
        RegisteredAgentsTable.update({ RegisteredAgentsTable.id eq id }) {
            if (name != null) it[RegisteredAgentsTable.name] = name
            if (dailySmsLimit != null) it[RegisteredAgentsTable.dailySmsLimit] = dailySmsLimit
            if (smsPrefix != null) it[RegisteredAgentsTable.smsPrefix] = smsPrefix // Handle null to clear if needed
            // Or, if you want to explicitly set null to clear:
            // it[RegisteredAgentsTable.smsPrefix] = smsPrefix 
            it[RegisteredAgentsTable.updatedAt] = Instant.now()
        } > 0
    }
    
    suspend fun updateHeartbeat(id: UUID): Boolean = dbQuery {
        RegisteredAgentsTable.update({ RegisteredAgentsTable.id eq id }) {
            it[lastHeartbeatAt] = Instant.now()
            // No change to updatedAt for heartbeat to distinguish from config changes
        } > 0
    }

    suspend fun incrementSmsCount(id: UUID): Boolean = dbQuery {
        RegisteredAgentsTable.update({ RegisteredAgentsTable.id eq id }) {
            with(SqlExpressionBuilder) {
                it.update(currentDailySmsCount, currentDailySmsCount + 1)
            }
            // No change to updatedAt for counter increments
        } > 0
    }

    suspend fun resetAllCounts(): Int = dbQuery {
        RegisteredAgentsTable.update {
            it[currentDailySmsCount] = 0
            it[lastSmsCountResetAt] = Instant.now()
            it[updatedAt] = Instant.now() // Indicate a mass update
        }
    }
}
