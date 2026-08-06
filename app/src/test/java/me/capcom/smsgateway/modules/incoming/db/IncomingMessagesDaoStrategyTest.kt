package me.capcom.smsgateway.modules.incoming.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Room annotations have CLASS retention and SQLite is unavailable in JVM unit tests,
// so the conflict strategy is verified against the Room-generated DAO implementation
// (build/generated/ksp/<variant>/java/.../IncomingMessagesDao_Impl.java), which the
// compiler regenerates from the @Insert annotation on every build.
internal class IncomingMessagesDaoStrategyTest {

    @Test
    fun insertUsesIgnoreConflictStrategy() {
        val impl = generatedImpl() ?: throw AssertionError(
            "IncomingMessagesDao_Impl.java not found (cwd=${File(".").absolutePath})"
        )
        assertTrue(
            "generated insert must be INSERT OR IGNORE (preserve row + future uploadedAt)",
            impl.contains("INSERT OR IGNORE INTO `incoming_messages`")
        )
        assertFalse(
            "generated insert must NOT be INSERT OR REPLACE (would wipe uploadedAt/providerId)",
            impl.contains("INSERT OR REPLACE INTO")
        )
    }

    @Test
    fun generatedInsertBindsNewColumns() {
        val impl = generatedImpl() ?: throw AssertionError("IncomingMessagesDao_Impl.java not found")
        val insertStmt = impl.lineSequence().first { it.contains("INSERT OR IGNORE INTO `incoming_messages`") }
        assertTrue("insert must bind uploadedAt", insertStmt.contains("`uploadedAt`"))
        assertTrue("insert must bind providerId", insertStmt.contains("`providerId`"))
    }

    @Test
    fun daoApiSurfaceUnchanged() {
        val insert = IncomingMessagesDao::class.java.getMethod("insert", IncomingMessage::class.java)
        assertNotNull(insert)
        // Dedup path used by ReceiverService/IncomingMessagesService.isMessageProcessed
        assertNotNull(IncomingMessagesDao::class.java.getMethod("selectById", String::class.java))
    }

    private fun generatedImpl(): String? {
        val kspDir = File("build/generated/ksp")
        if (!kspDir.exists()) return null
        val candidates = kspDir.walkTopDown()
            .filter { it.name == "IncomingMessagesDao_Impl.java" }
            .map { it.readText() }
            .toList()
        // Prefer the debug variant when multiple exist (testDebugUnitTest)
        return candidates.firstOrNull { it.contains("INSERT OR IGNORE INTO") } ?: candidates.firstOrNull()
    }
}
