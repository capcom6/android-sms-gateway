package me.capcom.smsgateway.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tokens",
    indices = [
        Index("expiresAt"),
        Index("tokenUse"),
        Index("parentJti"),
    ],
)
data class Token(
    @PrimaryKey
    val id: String,
    val expiresAt: Long,
    val revokedAt: Long? = null,
    @ColumnInfo(defaultValue = "access")
    val tokenUse: String = TokenUse.Access.value,
    val parentJti: String? = null,
)

enum class TokenUse(val value: String) {
    Access("access"),
    Refresh("refresh");

    companion object {
        fun isValid(value: String): Boolean {
            return values().any { it.value == value }
        }
    }
}
