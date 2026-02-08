package me.stappmus.messagegateway.modules.localserver.domain

import java.util.Date

data class Device(
    val id: String,
    val name: String,
    val createdAt: Date,
    val updatedAt: Date,
    val lastSeen: Date
)
