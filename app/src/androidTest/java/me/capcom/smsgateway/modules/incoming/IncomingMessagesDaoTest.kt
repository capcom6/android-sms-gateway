package me.capcom.smsgateway.modules.incoming

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.db.IncomingMessagesDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Room query used by the inbox observer's restart fallback. */
@RunWith(AndroidJUnit4::class)
class IncomingMessagesDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: IncomingMessagesDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.incomingMessagesDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insert(
        id: String,
        sender: String = "SENDER",
        text: String = "hello",
        at: Long = 10_000,
        sub: Int? = 1,
        type: IncomingMessageType = IncomingMessageType.SMS,
    ) = dao.insert(
        IncomingMessage(
            id = id,
            type = type,
            sender = sender,
            recipient = null,
            simNumber = null,
            subscriptionId = sub,
            contentPreview = text,
            createdAt = at,
        )
    )

    @Test
    fun findsTextSmsOfBothSubscriptionsAtTheExactTime() {
        insert("a", sub = 1)
        insert("b", sub = 3)

        val found = dao.selectSimilarSms("SENDER", "hello", 10_000, 10_000)

        assertEquals(setOf<Int?>(1, 3), found.map { it.subscriptionId }.toSet())
    }

    @Test
    fun timeWindowIsInclusiveAndExact() {
        insert("a", at = 10_000)

        assertEquals(1, dao.selectSimilarSms("SENDER", "hello", 7_000, 13_000).size)
        assertEquals(1, dao.selectSimilarSms("SENDER", "hello", 10_000, 10_000).size)
        assertTrue(dao.selectSimilarSms("SENDER", "hello", 10_001, 10_001).isEmpty())
        assertTrue(dao.selectSimilarSms("SENDER", "hello", 9_999, 9_999).isEmpty())
    }

    @Test
    fun senderIsMatchedCaseInsensitivelyAndTrimmed() {
        insert("a", sender = " Sender ")

        assertEquals(1, dao.selectSimilarSms("sender", "hello", 10_000, 10_000).size)
        assertTrue(dao.selectSimilarSms("other", "hello", 10_000, 10_000).isEmpty())
    }

    @Test
    fun onlyTextSmsWithTheSameBodyMatch() {
        insert("a", type = IncomingMessageType.DATA_SMS)
        insert("b", type = IncomingMessageType.MMS)
        insert("c", text = "hello!")

        assertTrue(dao.selectSimilarSms("SENDER", "hello", 10_000, 10_000).isEmpty())
    }
}
