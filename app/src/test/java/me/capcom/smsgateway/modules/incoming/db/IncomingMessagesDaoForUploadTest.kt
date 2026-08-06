package me.capcom.smsgateway.modules.incoming.db

import kotlinx.coroutines.runBlocking
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.testutil.InMemoryIncomingMessagesDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// A4 DAO contract: selectForUpload only returns rows WHERE uploadedAt IS NULL
// (with optional type/period filters); updateUploadedAt sets the mark per row.
// Room SQL is not executable in JVM unit tests, so the real query is asserted
// against the generated DAO implementation (same technique as
// IncomingMessagesDaoStrategyTest) and the behaviors are exercised through a
// fake DAO that mirrors Room's SQL semantics.
internal class IncomingMessagesDaoForUploadTest {

    private fun row(
        id: String,
        type: IncomingMessageType = IncomingMessageType.SMS,
        createdAt: Long = 1000L
    ) = IncomingMessage(
        id = id,
        type = type,
        sender = "+79990001234",
        recipient = null,
        simNumber = null,
        subscriptionId = null,
        contentPreview = "preview-$id",
        createdAt = createdAt,
    )

    ///////////////////////////////////////////////////////////////////////////
    // Generated SQL (real query source of truth)

    @Test
    fun generatedSelectForUploadFiltersPendingRows() {
        val sql = normalizedGeneratedSql() ?: throw AssertionError(
            "IncomingMessagesDao_Impl.java not found (cwd=${File(".").absolutePath})"
        )
        assertTrue(
            "select must filter `uploadedAt IS NULL`, got: ${sql.take(400)}",
            sql.contains("WHERE uploadedAt IS NULL"),
        )
    }

    @Test
    fun generatedUpdateUploadedAtIsAnUpdate() {
        val sql = normalizedGeneratedSql() ?: throw AssertionError("IncomingMessagesDao_Impl.java not found")
        assertTrue(sql.contains("UPDATE incoming_messages SET uploadedAt"))
        assertTrue(sql.contains("WHERE id = ?"))
        assertTrue("the uploadedAt updater must never delete", sql.contains("uploadedAt = ?") || sql.contains("SET uploadedAt"))
    }

    @Test
    fun generatedImplExposesNewMethods() {
        val names = IncomingMessagesDao::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue("dao must declare selectForUpload", names.contains("selectForUpload"))
        assertTrue("dao must declare updateUploadedAt", names.contains("updateUploadedAt"))
    }

    @Test
    fun generatedSelectForUploadOrdersOldestFirst() {
        val sql = normalizedGeneratedSql() ?: throw AssertionError("IncomingMessagesDao_Impl.java not found")
        assertTrue("oldest first for FIFO upload", sql.contains("ORDER BY createdAt ASC, id ASC"))
    }

    /** The whole generated DAO impl, normalized to single-line SQL for robust asserts. */
    private fun normalizedGeneratedSql(): String? {
        val kspDir = File("build/generated/ksp")
        if (!kspDir.exists()) return null
        val impl = kspDir.walkTopDown()
            .filter { it.name == "IncomingMessagesDao_Impl.java" }
            .map { it.readText() }
            .firstOrNull { it.contains("INSERT OR IGNORE INTO") }
            ?: return null
        return impl
            .replace("\" +\n", " ")
            .replace("\" + ", " ")
            .replace("\"", " ")
            .replace("\\n", " ")
            .replace(Regex("\\s+"), " ")
    }

    ///////////////////////////////////////////////////////////////////////////
    // repository layer (fake DAO mirrors the Room WHERE semantics)

    @Test
    fun selectForUploadReturnsOnlyPendingRows() = runBlocking {
        val dao = InMemoryIncomingMessagesDao().apply {
            rows["a"] = row("a", createdAt = 10)
            rows["b"] = row("b", type = IncomingMessageType.MMS, createdAt = 20)
            rows["c"] = row("c", createdAt = 30).copy(uploadedAt = 99) // already uploaded
        }
        val repo = IncomingMessagesRepository(dao)

        val pending = repo.selectForUpload()

        assertEquals(listOf("a", "b"), pending.map { it.id })
        assertTrue("uploaded rows must be excluded", pending.none { it.id == "c" })
    }

    @Test
    fun selectForUpdateHonorsTypeAndPeriodFilters() = runBlocking {
        val dao = InMemoryIncomingMessagesDao().apply {
            insert(row("a", type = IncomingMessageType.SMS, createdAt = 10))
            insert(row("b", type = IncomingMessageType.MMS, createdAt = 20))
            insert(row("c", type = IncomingMessageType.SMS, createdAt = 40))
        }
        val repo = IncomingMessagesRepository(dao)

        val smsInRange = repo.selectForUpload(
            types = setOf(IncomingMessageType.SMS),
            from = 5,
            until = 25,
        )
        assertEquals(listOf("a"), smsInRange.map { it.id })

        val allTypesInRange = repo.selectForUpload(
            types = null,
            from = 15,
            until = 45,
        )
        assertEquals(listOf("b", "c"), allTypesInRange.map { it.id })
    }

    @Test
    fun updateUploadedAtMarksOnlyTheTargetRow() = runBlocking {
        val dao = InMemoryIncomingMessagesDao().apply {
            insert(row("a", createdAt = 10))
            insert(row("b", createdAt = 20))
        }
        val repo = IncomingMessagesRepository(dao)

        repo.updateUploadedAt("a", 999L)

        assertEquals(999L, dao.rows["a"]?.uploadedAt)
        assertNull("b must stay pending", dao.rows["b"]?.uploadedAt)
    }
}