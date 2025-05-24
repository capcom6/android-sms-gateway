package me.capcom.smsgateway.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.data.entities.SmppServerConfig
import me.capcom.smsgateway.data.entities.VirtualPhoneConfig
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.IOException
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class VirtualPhoneConfigDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var virtualPhoneConfigDao: VirtualPhoneConfigDao
    private lateinit var smppServerConfigDao: SmppServerConfigDao // For inserting prerequisite

    private val dummySmppServer = SmppServerConfig("default-smpp", "Default", "localhost", 2775, "sys", "pass")

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        virtualPhoneConfigDao = db.virtualPhoneConfigDao()
        smppServerConfigDao = db.smppServerConfigDao()

        runBlocking {
            smppServerConfigDao.insert(dummySmppServer)
        }
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetVirtualPhone() = runBlocking {
        val phone = VirtualPhoneConfig("vp1", "Test Phone", smppServerId = dummySmppServer.id, simSlot = 1)
        virtualPhoneConfigDao.insert(phone)
        val retrieved = virtualPhoneConfigDao.getById("vp1")
        assertEquals(phone.id, retrieved?.id)
        assertEquals(phone.name, retrieved?.name)
        assertEquals(dummySmppServer.id, retrieved?.smppServerId)
        assertEquals(1, retrieved?.simSlot)
        assertEquals(0, retrieved?.currentDailySmsCount)
        assertEquals(-1, retrieved?.dailySmsLimit)
        assertTrue(retrieved?.isEnabled ?: false)
    }

    @Test
    @Throws(Exception::class)
    fun getAllWhenEmpty() = runBlocking {
        val allPhones = virtualPhoneConfigDao.getAll().first()
        assertTrue(allPhones.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun getAllAfterInsert() = runBlocking {
        val phone1 = VirtualPhoneConfig("vp1", "Phone 1", smppServerId = dummySmppServer.id, simSlot = null)
        val phone2 = VirtualPhoneConfig("vp2", "Phone 2", smppServerId = null, simSlot = 1)
        virtualPhoneConfigDao.insert(phone1)
        virtualPhoneConfigDao.insert(phone2)
        val allPhones = virtualPhoneConfigDao.getAll().first()
        assertEquals(2, allPhones.size)
        assertTrue(allPhones.any { it.id == "vp1" })
        assertTrue(allPhones.any { it.id == "vp2" })
    }

    @Test
    @Throws(Exception::class)
    fun updateVirtualPhone() = runBlocking {
        val phone = VirtualPhoneConfig("vp_update", "Initial Name", smppServerId = null, dailySmsLimit = 50)
        virtualPhoneConfigDao.insert(phone)
        val updatedPhone = phone.copy(name = "Updated Name", dailySmsLimit = 100, isEnabled = false)
        virtualPhoneConfigDao.update(updatedPhone)
        val retrieved = virtualPhoneConfigDao.getById("vp_update")
        assertEquals("Updated Name", retrieved?.name)
        assertEquals(100, retrieved?.dailySmsLimit)
        assertFalse(retrieved?.isEnabled ?: true)
    }

    @Test
    @Throws(Exception::class)
    fun deleteVirtualPhone() = runBlocking {
        val phone = VirtualPhoneConfig("vp_delete", "To Delete", smppServerId = null)
        virtualPhoneConfigDao.insert(phone)
        assertNotNull(virtualPhoneConfigDao.getById("vp_delete"))
        virtualPhoneConfigDao.delete(phone)
        assertNull(virtualPhoneConfigDao.getById("vp_delete"))
    }
    
    @Test
    @Throws(Exception::class)
    fun incrementSmsCountTest() = runBlocking {
        val phone = VirtualPhoneConfig("vp_count", "Counter Phone", dailySmsLimit = 10, smppServerId = dummySmppServer.id)
        virtualPhoneConfigDao.insert(phone)
        
        virtualPhoneConfigDao.incrementSmsCount("vp_count")
        var retrieved = virtualPhoneConfigDao.getById("vp_count")
        assertEquals(1, retrieved?.currentDailySmsCount)

        virtualPhoneConfigDao.incrementSmsCount("vp_count")
        retrieved = virtualPhoneConfigDao.getById("vp_count")
        assertEquals(2, retrieved?.currentDailySmsCount)
    }

    @Test
    @Throws(Exception::class)
    fun resetSmsCountTest() = runBlocking {
        val initialTime = System.currentTimeMillis()
        val phone = VirtualPhoneConfig("vp_reset", "Reset Phone", currentDailySmsCount = 5, lastResetTime = initialTime - 10000, smppServerId = null)
        virtualPhoneConfigDao.insert(phone)

        val newResetTime = System.currentTimeMillis()
        virtualPhoneConfigDao.resetSmsCount("vp_reset", newResetTime)
        
        val retrieved = virtualPhoneConfigDao.getById("vp_reset")
        assertEquals(0, retrieved?.currentDailySmsCount)
        assertTrue((retrieved?.lastResetTime ?: 0) >= newResetTime) // Check it's updated, allow for slight diff
    }

    @Test
    @Throws(Exception::class)
    fun setEnabledAndGetEnabledPhonesTest() = runBlocking {
        val phone1 = VirtualPhoneConfig("vp_enabled1", "Enabled Phone 1", isEnabled = true, smppServerId = null)
        val phone2 = VirtualPhoneConfig("vp_disabled1", "Disabled Phone 1", isEnabled = false, smppServerId = dummySmppServer.id)
        val phone3 = VirtualPhoneConfig("vp_enabled2", "Enabled Phone 2", isEnabled = true, simSlot = 1)
        
        virtualPhoneConfigDao.insert(phone1)
        virtualPhoneConfigDao.insert(phone2)
        virtualPhoneConfigDao.insert(phone3)

        var enabledPhones = virtualPhoneConfigDao.getEnabledPhones().first()
        assertEquals(2, enabledPhones.size)
        assertTrue(enabledPhones.any {it.id == "vp_enabled1"})
        assertTrue(enabledPhones.any {it.id == "vp_enabled2"})
        assertFalse(enabledPhones.any {it.id == "vp_disabled1"})

        virtualPhoneConfigDao.setEnabled("vp_enabled1", false)
        enabledPhones = virtualPhoneConfigDao.getEnabledPhones().first()
        assertEquals(1, enabledPhones.size)
        assertTrue(enabledPhones.any {it.id == "vp_enabled2"})
        assertFalse(enabledPhones.any {it.id == "vp_enabled1"})

        virtualPhoneConfigDao.setEnabled("vp_disabled1", true)
        enabledPhones = virtualPhoneConfigDao.getEnabledPhones().first()
        assertEquals(2, enabledPhones.size)
        assertTrue(enabledPhones.any {it.id == "vp_disabled1"})
    }

    @Test
    @Throws(Exception::class)
    fun getByIdWhenNotExists() = runBlocking {
        val retrieved = virtualPhoneConfigDao.getById("non_existent_vp_id")
        assertNull(retrieved)
    }
}
