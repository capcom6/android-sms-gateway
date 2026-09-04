package me.capcom.smsgateway.modules.gateway.inbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gateway__inbox_upload",
    indices = [
        Index(value = ["type", "message_id"], unique = true),
    ]
)
data class InboxUploadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "recipient")
    val recipient: String?,

    @ColumnInfo(name = "sim_number")
    val simNumber: Int?,

    @ColumnInfo(name = "message_created_at")
    val messageCreatedAt: Long,

    @ColumnInfo(name = "content_encrypted", typeAffinity = ColumnInfo.TEXT)
    val contentEncrypted: String,

    @ColumnInfo(name = "attachments_encrypted", typeAffinity = ColumnInfo.TEXT)
    val attachmentsEncrypted: String?,
)
