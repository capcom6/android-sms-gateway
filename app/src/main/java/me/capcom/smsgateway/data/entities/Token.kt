package me.capcom.smsgateway.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tokens",
    indices = [
        Index("expiresAt")
    ],
)
data class Token(
    @PrimaryKey
    val id: String,
    val expiresAt: Long,
    val revokedAt: Long? = null,
)
