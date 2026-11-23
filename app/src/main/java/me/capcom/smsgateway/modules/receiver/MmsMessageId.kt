package me.capcom.smsgateway.modules.receiver

import java.security.MessageDigest

object MmsMessageId {
    fun from(message: MmsContentReader.MmsMessage): String {
        val parts = message.attachments
            .asSequence()
            .map { encode(it.contentType, it.name, it.size?.toString()) }
            .sorted()
            .joinToString("")

        val canonical = encode(
            message.sender,
            message.date.time.toString(),
            message.subject,
            message.body,
            parts,
        )

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return PREFIX + digest.joinToString("") { "%02x".format(it) }
    }

    private fun encode(vararg values: String?): String = values.joinToString("") { value ->
        val text = value.orEmpty()
        "${text.length}:$text"
    }

    private const val PREFIX = "mms_"
}
