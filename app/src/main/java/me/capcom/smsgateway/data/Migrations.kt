package me.capcom.smsgateway.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            UPDATE message 
            SET validUntil = strftime('%FT%TZ', createdAt / 1000 + 86400, 'unixepoch') 
            WHERE validUntil IS NULL AND state = 'Pending'
        """.trimIndent()
        )
    }
}