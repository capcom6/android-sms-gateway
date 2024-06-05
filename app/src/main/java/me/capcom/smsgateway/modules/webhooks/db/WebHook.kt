package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent

@Entity
data class WebHook(
    @PrimaryKey
    val id: String,
    val url: String,
    val event: WebHookEvent,
    val source: EntitySource,
)