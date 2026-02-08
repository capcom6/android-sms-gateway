package me.stappmus.messagegateway.modules.receiver.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "incoming_mms",
    indices = [
        Index(value = ["messageId"]),
        Index(value = ["phoneNumber", "receivedAt"]),
    ]
)
data class IncomingMmsEntity(
    @PrimaryKey
    val transactionId: String,
    val messageId: String?,
    val phoneNumber: String,
    val simNumber: Int?,
    val subject: String?,
    val size: Long,
    val contentClass: String?,
    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val attachments: String,
    val receivedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
)
