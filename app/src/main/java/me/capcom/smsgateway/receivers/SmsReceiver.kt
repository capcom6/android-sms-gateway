package me.capcom.smsgateway.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import me.capcom.smsgateway.data.remote.dto.IncomingSmsRequest
import me.capcom.smsgateway.modules.localsms.utils.Logger
import me.capcom.smsgateway.services.AgentService

class SmsReceiver : BroadcastReceiver() {

    private val logger = Logger.get(this.javaClass.simpleName)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        logger.info("SMS_RECEIVED_ACTION intent received.")

        val messages: Array<SmsMessage> = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            logger.warn("No SMS messages found in intent.")
            return
        }

        // For simplicity, process the first message if it's not multipart.
        // Multipart messages would require reassembly.
        // For MVP, we'll assume single-part messages or process parts individually if needed.
        val firstMessage = messages[0]
        val sender = firstMessage.originatingAddress
        val messageBody = StringBuilder()
        messages.forEach {
            messageBody.append(it.messageBody)
        }
        val timestamp = firstMessage.timestampMillis

        if (sender == null) {
            logger.warn("Sender address is null. Skipping SMS processing.")
            return
        }

        logger.info("Received SMS from: $sender, Timestamp: $timestamp")
        logger.debug("Message body: $messageBody")

        val incomingSmsRequest = IncomingSmsRequest(
            sender = sender,
            messageText = messageBody.toString(),
            timestamp = timestamp
        )

        // Start AgentService to report the incoming SMS
        val serviceIntent = Intent(context, AgentService::class.java).apply {
            action = AgentService.ACTION_REPORT_INCOMING_SMS // New action
            // Pass data via intent extras. For complex data, consider serializing or using a temporary store.
            putExtra(AgentService.EXTRA_INCOMING_SMS_REQUEST, toJson(incomingSmsRequest)) // Need a simple way to pass this
        }
        context.startService(serviceIntent)
        logger.info("Started AgentService with ACTION_REPORT_INCOMING_SMS for SMS from $sender")
    }

    // Helper to convert DTO to JSON string (requires a JSON library like Gson)
    // For MVP, if Gson is not yet in Android app, might pass individual fields.
    // This is a placeholder. A proper JSON lib should be used.
    private fun toJson(request: IncomingSmsRequest): String {
        // return Gson().toJson(request) // Example with Gson
        // Manual for MVP if no Gson:
        return """
            {
                "sender": "${request.sender.replace("\"", "\\\"")}",
                "messageText": "${request.messageText.replace("\"", "\\\"")}",
                "timestamp": ${request.timestamp}
            }
        """.trimIndent()
    }

    companion object {
        // This will be used in AgentService to deserialize
        fun fromJson(json: String?): IncomingSmsRequest? {
            if (json == null) return null
            // return Gson().fromJson(json, IncomingSmsRequest::class.java) // Example with Gson
            // Manual parsing for MVP (very basic, assumes structure from toJson):
            try {
                val senderMatch = """"sender":\s*"([^"]*)"""".toRegex().find(json)
                val textMatch = """"messageText":\s*"([^"]*)"""".toRegex().find(json)
                val timeMatch = """"timestamp":\s*(\d+)""".toRegex().find(json)

                if (senderMatch != null && textMatch != null && timeMatch != null) {
                    return IncomingSmsRequest(
                        sender = senderMatch.groupValues[1].replace("\\\"", "\""),
                        messageText = textMatch.groupValues[1].replace("\\\"", "\""),
                        timestamp = timeMatch.groupValues[1].toLong()
                    )
                }
            } catch (e: Exception) {
                Logger.get("SmsReceiver").error("Error parsing IncomingSmsRequest from JSON: $e", e)
            }
            return null
        }
    }
}
