package me.capcom.smsgateway.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.data.entities.ServerSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ServerSettingsDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var serverSettingsDao: ServerSettingsDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // For testing simplicity
            .build()
        serverSettingsDao = db.serverSettingsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetSettings() = runBlocking {
        val settings = ServerSettings(serverUrl = "http://localhost:8080", agentId = "agent1", apiKey = "key1")
        serverSettingsDao.insert(settings)

        val retrievedFlow = serverSettingsDao.getSettings().first()
        assertNotNull(retrievedFlow)
        assertEquals(settings.serverUrl, retrievedFlow?.serverUrl)
        assertEquals(settings.agentId, retrievedFlow?.agentId)
        assertEquals(settings.apiKey, retrievedFlow?.apiKey)

        val retrievedDirect = serverSettingsDao.getSettingsDirect()
        assertNotNull(retrievedDirect)
        assertEquals(settings.serverUrl, retrievedDirect?.serverUrl)
    }

    @Test
    @Throws(Exception::class)
    fun getSettingsWhenNoneExist() = runBlocking {
        val retrievedFlow = serverSettingsDao.getSettings().first()
        assertNull(retrievedFlow)

        val retrievedDirect = serverSettingsDao.getSettingsDirect()
        assertNull(retrievedDirect)
    }

    @Test
    @Throws(Exception::class)
    fun updateSettings() = runBlocking {
        val initialSettings = ServerSettings(serverUrl = "http://initial.url", agentId = "initialAgent", apiKey = "initialKey")
        serverSettingsDao.insert(initialSettings)

        val updatedSettings = ServerSettings(serverUrl = "http://updated.url", agentId = "updatedAgent", apiKey = "updatedKey")
        serverSettingsDao.insert(updatedSettings) // Insert with same ID replaces

        val retrieved = serverSettingsDao.getSettings().first()
        assertNotNull(retrieved)
        assertEquals(updatedSettings.serverUrl, retrieved?.serverUrl)
        assertEquals(updatedSettings.agentId, retrieved?.agentId)
        assertEquals(updatedSettings.apiKey, retrieved?.apiKey)
    }

    @Test
    @Throws(Exception::class)
    fun clearSettings() = runBlocking {
        val settings = ServerSettings(serverUrl = "http://localhost:8080", agentId = "agent1", apiKey = "key1")
        serverSettingsDao.insert(settings)

        var retrieved = serverSettingsDao.getSettings().first()
        assertNotNull(retrieved)

        serverSettingsDao.clear()
        retrieved = serverSettingsDao.getSettings().first()
        assertNull(retrieved)
    }
}
