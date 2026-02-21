package me.capcom.smsgateway.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "revoked_token")
data class RevokedToken(
    @PrimaryKey
    val id: String,
    val revokedAt: Long = System.currentTimeMillis(),
)
