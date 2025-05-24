package me.capcom.smsgateway.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.data.entities.SmppServerConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SmppServerConfigDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var smppServerConfigDao: SmppServerConfigDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // Allowing main thread queries, just for testing.
            .allowMainThreadQueries()
            .build()
        smppServerConfigDao = db.smppServerConfigDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetById() = runBlocking {
        val config = SmppServerConfig("test_id", "Test Server", "localhost", 2775, "test_sys", "password", false, "default")
        smppServerConfigDao.insert(config)
        val retrieved = smppServerConfigDao.getById("test_id")
        assertEquals(config, retrieved)
    }

    @Test
    @Throws(Exception::class)
    fun getAllWhenEmpty() = runBlocking {
        val allConfigs = smppServerConfigDao.getAll().first()
        assertTrue(allConfigs.isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun getAllAfterInsert() = runBlocking {
        val config1 = SmppServerConfig("id1", "Server1", "host1", 1234, "sys1", "pass1")
        val config2 = SmppServerConfig("id2", "Server2", "host2", 5678, "sys2", "pass2")
        smppServerConfigDao.insert(config1)
        smppServerConfigDao.insert(config2)
        val allConfigs = smppServerConfigDao.getAll().first()
        assertEquals(2, allConfigs.size)
        assertTrue(allConfigs.contains(config1))
        assertTrue(allConfigs.contains(config2))
    }

    @Test
    @Throws(Exception::class)
    fun updateConfig() = runBlocking {
        val config = SmppServerConfig("update_id", "Initial Name", "host", 1111, "sys", "pass")
        smppServerConfigDao.insert(config)
        val updatedConfig = config.copy(name = "Updated Name", port = 2222)
        smppServerConfigDao.update(updatedConfig)
        val retrieved = smppServerConfigDao.getById("update_id")
        assertEquals("Updated Name", retrieved?.name)
        assertEquals(2222, retrieved?.port)
    }

    @Test
    @Throws(Exception::class)
    fun deleteConfig() = runBlocking {
        val config = SmppServerConfig("delete_id", "To Delete", "host", 123, "sys", "pass")
        smppServerConfigDao.insert(config)
        assertNotNull(smppServerConfigDao.getById("delete_id"))
        smppServerConfigDao.delete(config)
        assertNull(smppServerConfigDao.getById("delete_id"))
    }

    @Test
    @Throws(Exception::class)
    fun getByIdWhenNotExists() = runBlocking {
        val retrieved = smppServerConfigDao.getById("non_existent_id")
        assertNull(retrieved)
    }
}
