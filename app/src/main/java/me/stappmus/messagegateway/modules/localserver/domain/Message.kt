package me.stappmus.messagegateway.modules.localserver.domain

import me.stappmus.messagegateway.domain.ProcessingState
import java.util.Date

open class Message(
    val id: String,
    val deviceId: String,
    val state: ProcessingState,
    val isHashed: Boolean,
    val isEncrypted: Boolean,
    val recipients: List<Recipient>,
    val states: Map<ProcessingState, Date>,
) {
    data class Recipient(
        val phoneNumber: String,
        val state: ProcessingState,
        val error: String?,
    )
}
