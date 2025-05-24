package me.capcom.smsgateway.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.data.entities.OutgoingSmsStatus
import me.capcom.smsgateway.data.entities.QueuedOutgoingSms
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class QueuedOutgoingSmsDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var queuedOutgoingSmsDao: QueuedOutgoingSmsDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queuedOutgoingSmsDao = db.queuedOutgoingSmsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetByTaskId() = runBlocking {
        val sms = QueuedOutgoingSms(recipient = "12345", messageContent = "Hello")
        queuedOutgoingSmsDao.insert(sms)

        val retrieved = queuedOutgoingSmsDao.getByTaskId(sms.taskId)
        assertNotNull(retrieved)
        assertEquals(sms.taskId, retrieved?.taskId)
        assertEquals("12345", retrieved?.recipient)
        assertEquals("Hello", retrieved?.messageContent)
    }

    @Test
    @Throws(Exception::class)
    fun updateSms() = runBlocking {
        val sms = QueuedOutgoingSms(recipient = "12345", messageContent = "Initial")
        queuedOutgoingSmsDao.insert(sms)

        val retrieved = queuedOutgoingSmsDao.getByTaskId(sms.taskId)
        assertNotNull(retrieved)

        retrieved!!.status = OutgoingSmsStatus.SENT
        retrieved.retries = 1
        queuedOutgoingSmsDao.update(retrieved)

        val updated = queuedOutgoingSmsDao.getByTaskId(sms.taskId)
        assertNotNull(updated)
        assertEquals(OutgoingSmsStatus.SENT, updated?.status)
        assertEquals(1, updated?.retries)
    }

    @Test
    @Throws(Exception::class)
    fun deleteSms() = runBlocking {
        val sms = QueuedOutgoingSms(recipient = "54321", messageContent = "To Delete")
        queuedOutgoingSmsDao.insert(sms)

        assertNotNull(queuedOutgoingSmsDao.getByTaskId(sms.taskId))
        queuedOutgoingSmsDao.delete(sms)
        assertNull(queuedOutgoingSmsDao.getByTaskId(sms.taskId))
    }

    @Test
    @Throws(Exception::class)
    fun getPendingMessages() = runBlocking {
        val smsPending1 = QueuedOutgoingSms(recipient = "p1", messageContent = "m1", status = OutgoingSmsStatus.PENDING, nextSendAttemptAt = System.currentTimeMillis())
        val smsSent = QueuedOutgoingSms(recipient = "s1", messageContent = "m2", status = OutgoingSmsStatus.SENT)
        val smsPending2 = QueuedOutgoingSms(recipient = "p2", messageContent = "m3", status = OutgoingSmsStatus.PENDING, nextSendAttemptAt = System.currentTimeMillis() - 1000) // older
        val smsFailed = QueuedOutgoingSms(recipient = "f1", messageContent = "m4", status = OutgoingSmsStatus.FAILED)

        queuedOutgoingSmsDao.insert(smsPending1)
        queuedOutgoingSmsDao.insert(smsSent)
        queuedOutgoingSmsDao.insert(smsPending2)
        queuedOutgoingSmsDao.insert(smsFailed)

        val pendingMessages = queuedOutgoingSmsDao.getPendingMessages() // Default status is PENDING
        assertEquals(2, pendingMessages.size)
        // Should be ordered by nextSendAttemptAt ASC
        assertEquals(smsPending2.taskId, pendingMessages[0].taskId)
        assertEquals(smsPending1.taskId, pendingMessages[1].taskId)
        assertTrue(pendingMessages.all { it.status == OutgoingSmsStatus.PENDING })
    }
    
    @Test
    @Throws(Exception::class)
    fun getAllMessagesFlow() = runBlocking {
        val sms1 = QueuedOutgoingSms(recipient = "a1", messageContent = "msg1", createdAt = System.currentTimeMillis())
        val sms2 = QueuedOutgoingSms(recipient = "a2", messageContent = "msg2", createdAt = System.currentTimeMillis() - 1000) // older
        
        queuedOutgoingSmsDao.insert(sms1)
        queuedOutgoingSmsDao.insert(sms2)
        
        val allMessages = queuedOutgoingSmsDao.getAllMessagesFlow().first()
        assertEquals(2, allMessages.size)
        // Should be ordered by createdAt DESC
        assertEquals(sms1.taskId, allMessages[0].taskId)
        assertEquals(sms2.taskId, allMessages[1].taskId)
    }

    @Test
    @Throws(Exception::class)
    fun getByTaskIdWhenNotExists() = runBlocking {
        val retrieved = queuedOutgoingSmsDao.getByTaskId("non-existent-id")
        assertNull(retrieved)
    }
}
