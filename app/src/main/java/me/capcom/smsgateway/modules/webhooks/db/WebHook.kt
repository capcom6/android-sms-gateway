package me.capcom.smsgateway.modules.webhooks.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.capcom.smsgateway.domain.EntitySource

@Entity
data class WebHook(
    @PrimaryKey
    val id: String,
    val url: String,
    val source: EntitySource,
)