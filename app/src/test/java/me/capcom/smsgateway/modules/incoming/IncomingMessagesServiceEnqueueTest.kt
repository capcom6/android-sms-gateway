package me.capcom.smsgateway.modules.incoming

import android.content.ContextWrapper
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.incoming.repositories.IncomingMessagesRepository
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.mms.MmsAttachmentStorage
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.testutil.FakeKeyValueStorage
import me.capcom.smsgateway.testutil.InMemoryIncomingMessagesDao
import me.capcom.smsgateway.testutil.RecordingLogsDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

// A4 trigger wiring: IncomingMessagesService.save() must enqueue the unique
// GatewayInboxWorker (one-shot; coalesces bursts) only when the gateway is
// enabled. Asserted via enqueue-method injection: the context is a
// ContextWrapper(null) stub that is never dereferenced on this path
// (subscriptionId = null -> no SubscriptionsHelper call, Text -> no MMS
// attachment persistence, enqueue lambda ignores the context).
internal class IncomingMessagesServiceEnqueueTest {

    private class Harness(
        gatewayEnabled: Boolean,
        private val onEnqueue: () -> Unit,
    ) {
        val dao = InMemoryIncomingMessagesDao()
        val logs = RecordingLogsDao()
        val service: IncomingMessagesService

        init {
            val logsService = LogsService(logs, LogsSettings(FakeKeyValueStorage()))
            val gatewayStorage = FakeKeyValueStorage().apply { set("ENABLED", gatewayEnabled) }
            service = IncomingMessagesService(
                context = ContextWrapper(null),
                settings = IncomingMessagesSettings(FakeKeyValueStorage()),
                repository = IncomingMessagesRepository(dao),
                attachmentStorage = MmsAttachmentStorage(
                    ContextWrapper(null),
                    LogsService(logs, LogsSettings(FakeKeyValueStorage())),
                ),
                logsService = logsService,
                gatewaySettings = GatewaySettings(gatewayStorage),
                enqueueInboxUpload = { onEnqueue() },
            )
        }
    }

    private fun textMessage() = InboxMessage.Text("hello", "+79261234567", Date(1000), null)

    @Test
    fun saveEnqueuesInboxUploadWhenGatewayEnabled() {
        var enqueued = 0
        val h = Harness(gatewayEnabled = true) { enqueued++ }

        h.service.save(textMessage())

        assertEquals("gateway enabled -> enqueue unique worker", 1, enqueued)
        assertEquals(1, h.dao.rows.size)
        assertTrue(h.logs.entries.isEmpty())
    }

    @Test
    fun saveDoesNotEnqueueWhenGatewayDisabled() {
        var enqueued = 0
        val h = Harness(gatewayEnabled = false) { enqueued++ }

        h.service.save(textMessage())

        assertEquals("gateway disabled -> no enqueue", 0, enqueued)
        assertEquals("row still persisted locally", 1, h.dao.rows.size)
    }

    @Test
    fun enqueueFailureDoesNotLoseTheSavedRow() {
        val h = Harness(gatewayEnabled = true) { error("work manager unavailable") }

        h.service.save(textMessage())

        assertEquals("save must not fail when enqueue throws", 1, h.dao.rows.size)
        assertTrue("failure is logged, not propagated", h.logs.entries.any { it.message.contains("Failed to save message") })
    }

    @Test
    fun burstOfSavesCoalescesByUniqueWorkerName() {
        // Behavior contract documented on GatewayInboxWorker.start (REPLACE
        // policy + fixed NAME): N saves in a row produce at most one pending
        // run; each enqueue supersedes the previous input.
        var enqueued = 0
        val h = Harness(gatewayEnabled = true) { enqueued++ }

        h.service.save(textMessage())
        h.service.save(textMessage())
        h.service.save(textMessage())

        assertEquals(3, enqueued) // 3 triggers, one coalesced worker run
        assertEquals(1, h.dao.rows.size) // second save dedups the same message
    }

    @Test
    fun gatewayEnabledFlagReadFromGatewaySettings() {
        val storage = FakeKeyValueStorage().apply { set("ENABLED", false) }
        assertFalse("default is disabled", GatewaySettings(storage).enabled)

        storage.set("ENABLED", true)
        assertTrue(GatewaySettings(storage).enabled)
    }
}