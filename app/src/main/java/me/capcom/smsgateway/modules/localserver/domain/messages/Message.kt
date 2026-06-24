package me.capcom.smsgateway.modules.localserver.domain.messages

import me.capcom.smsgateway.domain.ProcessingState
import java.util.Date

open class Message(
    val id: String,
    val deviceId: String,
    val state: ProcessingState,
    val isHashed: Boolean,
    val isEncrypted: Boolean,
    val scheduleAt: Date?,
    val textMessage: TextMessage?,
    val dataMessage: DataMessage?,
    val mmsMessage: MmsMessage?,
    val hashedMessage: HashedMessage?,
    val recipients: List<Recipient>,
    val states: Map<ProcessingState, Date>,
) {
    data class Recipient(
        val phoneNumber: String,
        val state: ProcessingState,
        val error: String?,
    )
}
