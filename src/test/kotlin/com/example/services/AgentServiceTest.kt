package com.example.services

import com.example.db.DatabaseFactory // Potentially used for dbQuery, though direct transaction is also common in tests
import com.example.db.RegisteredAgentsTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.sql.Connection

class AgentServiceTest {

    @BeforeEach
    fun setup() {
        // Connect to H2 in-memory database for testing
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver", user = "root", password = "")
        transaction {
            // Ensure schema is created for each test
            SchemaUtils.create(RegisteredAgentsTable)
        }
    }

    @AfterEach
    fun teardown() {
        transaction {
            SchemaUtils.drop(RegisteredAgentsTable)
        }
        // Close the connection to H2 database
        // Exposed's TransactionManager can hold connections, ensure they are closed.
        // For simple tests, a new connection per test (as above) and then letting it go might be enough.
        // For more complex scenarios, consider explicit connection management or test utilities.
        TransactionManager.closeAndClearAll()
    }

    private val agentService = AgentService()

    @Test
    fun `test create and find agent by id`() = runBlocking {
        val agentId = UUID.randomUUID()
        val apiKeyHash = "hashedApiKey-${UUID.randomUUID()}" // Ensure unique hash for test
        val newAgent = agentService.createAgent(id = agentId, name = "Test Agent", apiKeyHash = apiKeyHash, dailySmsLimit = 100, smsPrefix = "TEST")
        
        assertNotNull(newAgent)
        assertEquals(agentId, newAgent?.id)
        assertEquals("Test Agent", newAgent?.name)
        assertEquals(apiKeyHash, newAgent?.apiKeyHash)
        assertEquals(100, newAgent?.dailySmsLimit)
        assertEquals("TEST", newAgent?.smsPrefix)

        val foundAgent = agentService.findById(agentId)
        assertEquals(newAgent, foundAgent)
    }

    @Test
    fun `test find agent by non-existent id`() = runBlocking {
        val foundAgent = agentService.findById(UUID.randomUUID())
        assertNull(foundAgent)
    }
    
    @Test
    fun `test find agent by api key hash`() = runBlocking {
        val agentId = UUID.randomUUID()
        val apiKeyHash = "anotherHashedApiKey-${UUID.randomUUID()}"
        agentService.createAgent(id = agentId, name = "API Key Agent", apiKeyHash = apiKeyHash)
        
        val foundAgent = agentService.findByApiKeyHash(apiKeyHash)
        assertNotNull(foundAgent)
        assertEquals(agentId, foundAgent?.id)
        assertEquals(apiKeyHash, foundAgent?.apiKeyHash)
    }

    @Test
    fun `test get all agents`() = runBlocking {
        val agent1 = agentService.createAgent(name = "Agent A", apiKeyHash = "hashA-${UUID.randomUUID()}")
        val agent2 = agentService.createAgent(name = "Agent B", apiKeyHash = "hashB-${UUID.randomUUID()}")

        val allAgents = agentService.getAllAgents()
        assertEquals(2, allAgents.size)
        assertTrue(allAgents.any { it.id == agent1?.id })
        assertTrue(allAgents.any { it.id == agent2?.id })
    }

    @Test
    fun `test set enabled`() = runBlocking {
        val agent = agentService.createAgent(name = "Enable Agent", apiKeyHash = "hashEnable-${UUID.randomUUID()}")!!
        assertTrue(agent.isEnabled) // Default

        agentService.setEnabled(agent.id, false)
        var updatedAgent = agentService.findById(agent.id)
        assertNotNull(updatedAgent)
        assertFalse(updatedAgent!!.isEnabled)

        agentService.setEnabled(agent.id, true)
        updatedAgent = agentService.findById(agent.id)
        assertNotNull(updatedAgent)
        assertTrue(updatedAgent!!.isEnabled)
    }
    
    @Test
    fun `test update config`() = runBlocking {
        val agent = agentService.createAgent(name = "Config Agent", apiKeyHash = "hashConfig-${UUID.randomUUID()}", dailySmsLimit = 50, smsPrefix = "OLD")!!
        
        agentService.updateConfig(agent.id, "Updated Config Agent", 150, "NEW")
        val updatedAgent = agentService.findById(agent.id)!!
        
        assertEquals("Updated Config Agent", updatedAgent.name)
        assertEquals(150, updatedAgent.dailySmsLimit)
        assertEquals("NEW", updatedAgent.smsPrefix)
    }

    @Test
    fun `test update heartbeat`() = runBlocking {
        val agent = agentService.createAgent(name = "Heartbeat Agent", apiKeyHash = "hashHeartbeat-${UUID.randomUUID()}")!!
        val initialHeartbeat = agent.lastHeartbeatAt
        
        // Ensure some time passes for the timestamp to be different
        Thread.sleep(10) // Small delay to ensure timestamp changes
        agentService.updateHeartbeat(agent.id)
        val updatedAgent = agentService.findById(agent.id)!!
        
        assertNotNull(updatedAgent.lastHeartbeatAt)
        if (initialHeartbeat != null) { // If it was null initially, this check is not needed
             assertTrue(updatedAgent.lastHeartbeatAt!! > initialHeartbeat)
        } else {
            assertNotNull(updatedAgent.lastHeartbeatAt) // Should be set now
        }
    }

    @Test
    fun `test increment sms count`() = runBlocking {
        val agent = agentService.createAgent(name = "Count Agent", apiKeyHash = "hashCount-${UUID.randomUUID()}")!!
        assertEquals(0, agent.currentDailySmsCount)

        agentService.incrementSmsCount(agent.id)
        var updatedAgent = agentService.findById(agent.id)!!
        assertEquals(1, updatedAgent.currentDailySmsCount)

        agentService.incrementSmsCount(agent.id)
        updatedAgent = agentService.findById(agent.id)!!
        assertEquals(2, updatedAgent.currentDailySmsCount)
    }
    
    @Test
    fun `test reset all counts`() = runBlocking {
        agentService.createAgent(name = "Reset1", apiKeyHash = "hashR1-${UUID.randomUUID()}", dailySmsLimit = 10)
        agentService.createAgent(name = "Reset2", apiKeyHash = "hashR2-${UUID.randomUUID()}", dailySmsLimit = 20)
        
        val agent1 = agentService.findByApiKeyHash("hashR1-${UUID.randomUUID().toString().substring(0,10)}") // Need actual hash
        val agent2 = agentService.findByApiKeyHash("hashR2-${UUID.randomUUID().toString().substring(0,10)}")
        
        // This test needs refinement as hashes are dynamic. Better to fetch all and update.
        // For now, let's assume we can increment their counts first.
        val allAgentsBeforeReset = agentService.getAllAgents()
        allAgentsBeforeReset.forEach { agentService.incrementSmsCount(it.id) }
        
        agentService.resetAllCounts()
        val allAgentsAfterReset = agentService.getAllAgents()
        
        allAgentsAfterReset.forEach {
            assertEquals(0, it.currentDailySmsCount)
            // Could also check lastSmsCountResetAt is recent
        }
    }
}
