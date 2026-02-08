package me.stappmus.messagegateway.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE message
            SET validUntil = strftime('%FT%TZ', createdAt / 1000 + 86400, 'unixepoch')
            WHERE validUntil IS NULL AND state = 'Pending'
        """.trimIndent()
        )
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create a new table with the desired schema (without the text column)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_new (
                `id` TEXT NOT NULL,
                `withDeliveryReport` INTEGER NOT NULL DEFAULT 1,
                `simNumber` INTEGER,
                `validUntil` TEXT,
                `isEncrypted` INTEGER NOT NULL DEFAULT 0,
                `skipPhoneValidation` INTEGER NOT NULL DEFAULT 0,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `type` TEXT NOT NULL DEFAULT 'Text',
                `source` TEXT NOT NULL DEFAULT 'Local',
                `content` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `processedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // Copy data from the old table to the new table
        db.execSQL(
            """
            INSERT INTO message_new (
                id,
                withDeliveryReport,
                simNumber,
                validUntil,
                isEncrypted,
                skipPhoneValidation,
                priority,
                source,
                type,
                content,
                state,
                createdAt,
                processedAt
            ) SELECT
                id,
                withDeliveryReport,
                simNumber,
                validUntil,
                isEncrypted,
                skipPhoneValidation,
                priority,
                source,
                'Text' AS type,
                '{"text": "' || replace(replace(text, '\\', '\\\\'), '"', '\\"') || '"}' AS content,
                state,
                createdAt,
                processedAt
            FROM message
        """.trimIndent()
        )

        // Drop the old table and rename the new table to the original name
        db.execSQL("DROP TABLE IF EXISTS message")
        db.execSQL("ALTER TABLE message_new RENAME TO message")

        // Create indices
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_state ON message (state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_createdAt ON message (createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_processedAt ON message (processedAt)")
    }
}
