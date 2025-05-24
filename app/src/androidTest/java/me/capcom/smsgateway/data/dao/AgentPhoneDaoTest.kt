package me.capcom.smsgateway.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.data.entities.AgentPhone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AgentPhoneDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var agentPhoneDao: AgentPhoneDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        agentPhoneDao = db.agentPhoneDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetById() = runBlocking {
        val phone = AgentPhone("agent1", "Test Agent", apiKey = "key1")
        agentPhoneDao.insert(phone)
        val retrieved = agentPhoneDao.getById("agent1")
        assertEquals(phone, retrieved)
    }

    @Test
    @Throws(Exception::class)
    fun getByApiKey() = runBlocking {
        val phone = AgentPhone("agent2", "API Key Agent", apiKey = "unique_api_key")
        agentPhoneDao.insert(phone)
        val retrieved = agentPhoneDao.getByApiKey("unique_api_key")
        assertEquals(phone, retrieved)
    }

    @Test
    @Throws(Exception::class)
    fun getAllWhenEmpty() = runBlocking {
        val allPhones = agentPhoneDao.getAll().first()
        assertTrue(allPhones.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun getAllAfterInsert() = runBlocking {
        val phone1 = AgentPhone("a1", "Agent 1", apiKey = "k1")
        val phone2 = AgentPhone("a2", "Agent 2", apiKey = "k2")
        agentPhoneDao.insert(phone1)
        agentPhoneDao.insert(phone2)
        val allPhones = agentPhoneDao.getAll().first()
        assertEquals(2, allPhones.size)
        assertTrue(allPhones.contains(phone1))
        assertTrue(allPhones.contains(phone2))
    }

    @Test
    @Throws(Exception::class)
    fun updateAgentPhone() = runBlocking {
        val phone = AgentPhone("agent_update", "Initial Name", apiKey = "key_update", dailySmsLimit = 10)
        agentPhoneDao.insert(phone)
        val updatedPhone = phone.copy(name = "Updated Agent Name", dailySmsLimit = 20, isEnabled = false)
        agentPhoneDao.update(updatedPhone)
        val retrieved = agentPhoneDao.getById("agent_update")
        assertEquals("Updated Agent Name", retrieved?.name)
        assertEquals(20, retrieved?.dailySmsLimit)
        assertFalse(retrieved?.isEnabled ?: true)
    }

    @Test
    @Throws(Exception::class)
    fun deleteAgentPhone() = runBlocking {
        val phone = AgentPhone("agent_delete", "To Delete", apiKey = "key_delete")
        agentPhoneDao.insert(phone)
        assertNotNull(agentPhoneDao.getById("agent_delete"))
        agentPhoneDao.delete(phone)
        assertNull(agentPhoneDao.getById("agent_delete"))
    }

    @Test
    @Throws(Exception::class)
    fun incrementSmsCount() = runBlocking {
        val phone = AgentPhone("agent_count", "Counter", apiKey = "key_count", dailySmsLimit = 5)
        agentPhoneDao.insert(phone)
        assertEquals(0, agentPhoneDao.getById("agent_count")?.currentDailySmsCount)
        agentPhoneDao.incrementSmsCount("agent_count")
        assertEquals(1, agentPhoneDao.getById("agent_count")?.currentDailySmsCount)
        agentPhoneDao.incrementSmsCount("agent_count")
        assertEquals(2, agentPhoneDao.getById("agent_count")?.currentDailySmsCount)
    }

    @Test
    @Throws(Exception::class)
    fun resetAllCounts() = runBlocking {
        val phone1 = AgentPhone("agent_reset1", "Reset1", apiKey = "key_r1", currentDailySmsCount = 10)
        val phone2 = AgentPhone("agent_reset2", "Reset2", apiKey = "key_r2", currentDailySmsCount = 5)
        agentPhoneDao.insert(phone1)
        agentPhoneDao.insert(phone2)

        val resetTime = System.currentTimeMillis()
        agentPhoneDao.resetAllCounts(resetTime)

        val p1 = agentPhoneDao.getById("agent_reset1")
        val p2 = agentPhoneDao.getById("agent_reset2")
        assertEquals(0, p1?.currentDailySmsCount)
        assertEquals(0, p2?.currentDailySmsCount)
        assertTrue((p1?.lastResetTime ?: 0) >= resetTime)
        assertTrue((p2?.lastResetTime ?: 0) >= resetTime)
    }
    
    @Test
    @Throws(Exception::class)
    fun updateLastSeen() = runBlocking {
        val phone = AgentPhone("agent_seen", "LastSeen Agent", apiKey = "key_seen", lastSeenAt = 0)
        agentPhoneDao.insert(phone)
        val firstSeen = agentPhoneDao.getById("agent_seen")?.lastSeenAt ?: 0
        assertTrue(firstSeen <= System.currentTimeMillis()) // Should be close to 0 or default

        val newSeenTime = System.currentTimeMillis() + 1000 // Ensure it's different
        agentPhoneDao.updateLastSeen("agent_seen", newSeenTime)
        val retrieved = agentPhoneDao.getById("agent_seen")
        assertEquals(newSeenTime, retrieved?.lastSeenAt)
    }

    @Test
    @Throws(Exception::class)
    fun setEnabledAndGetEnabledPhones() = runBlocking {
        val phone1 = AgentPhone("ae1", "Enabled Agent 1", isEnabled = true, apiKey = "ake1")
        val phone2 = AgentPhone("ad1", "Disabled Agent 1", isEnabled = false, apiKey = "akd1")
        val phone3 = AgentPhone("ae2", "Enabled Agent 2", isEnabled = true, apiKey = "ake2")
        
        agentPhoneDao.insert(phone1)
        agentPhoneDao.insert(phone2)
        agentPhoneDao.insert(phone3)

        var enabledPhones = agentPhoneDao.getEnabledAgentPhones().first()
        assertEquals(2, enabledPhones.size)
        assertTrue(enabledPhones.any {it.id == "ae1"})
        assertTrue(enabledPhones.any {it.id == "ae2"})
        assertFalse(enabledPhones.any {it.id == "ad1"})

        agentPhoneDao.setEnabled("ae1", false)
        enabledPhones = agentPhoneDao.getEnabledAgentPhones().first()
        assertEquals(1, enabledPhones.size)
        assertTrue(enabledPhones.any {it.id == "ae2"})

        agentPhoneDao.setEnabled("ad1", true)
        enabledPhones = agentPhoneDao.getEnabledAgentPhones().first()
        assertEquals(2, enabledPhones.size)
        assertTrue(enabledPhones.any {it.id == "ad1"})
    }
    
    @Test
    @Throws(Exception::class)
    fun getByIdWhenNotExists() = runBlocking {
        val retrieved = agentPhoneDao.getById("non_existent_agent_id")
        assertNull(retrieved)
    }

    @Test
    @Throws(Exception::class)
    fun getByApiKeyWhenNotExists() = runBlocking {
        val retrieved = agentPhoneDao.getByApiKey("non_existent_api_key")
        assertNull(retrieved)
    }
}
