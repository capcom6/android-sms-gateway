package me.capcom.smsgateway.modules.encryption.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encryption_keys",
    indices = [
        Index("retired_at"),
        Index("key_version", unique = true)
    ],
)
data class EncryptionKey(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "key_version")
    val keyVersion: Int,

    @ColumnInfo(name = "private_key_blob")
    val privateKeyBlob: ByteArray,

    @ColumnInfo(name = "public_key_base64")
    val publicKeyBase64: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retired_at")
    val retiredAt: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptionKey
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
