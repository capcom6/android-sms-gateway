package me.stappmus.messagegateway.modules.webhooks.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.modules.webhooks.domain.WebHookEvent

@Entity
data class WebHook(
    @PrimaryKey
    val id: String,
    val url: String,
    val event: WebHookEvent,
    val source: EntitySource,
)