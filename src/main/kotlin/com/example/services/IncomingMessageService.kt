package com.example.services

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class IncomingMessageService(
    private val incomingSmsLogService: IncomingSmsLogService
) {
    private val logger = LoggerFactory.getLogger(IncomingMessageService::class.java)

    suspend fun handleIncomingAgentSms(
        agentId: UUID,
        originalSender: String,
        messageContent: String,
        receivedAtAgentAt: Instant
    ) {
        logger.info("Handling incoming SMS from agent $agentId: Sender=$originalSender, Message='${messageContent.take(30)}...'")
        try {
            val loggedSms = incomingSmsLogService.logMessage(
                agentId = agentId,
                originalSender = originalSender,
                messageContent = messageContent,
                receivedAtAgentAt = receivedAtAgentAt
            )
            if (loggedSms != null) {
                logger.info("Incoming SMS from agent $agentId logged with ID ${loggedSms.id}")
                // TODO: Trigger webhooks or other processing for the incoming message.
                // Example: eventBus.post(IncomingSmsEvent(loggedSms))
            } else {
                logger.error("Failed to log incoming SMS from agent $agentId for sender $originalSender")
            }
        } catch (e: Exception) {
            logger.error("Error handling incoming SMS from agent $agentId: ${e.message}", e)
            // Depending on requirements, might rethrow or handle differently
        }
    }
}
