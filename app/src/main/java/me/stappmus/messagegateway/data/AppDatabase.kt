package me.stappmus.messagegateway.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.stappmus.messagegateway.data.dao.MessagesDao
import me.stappmus.messagegateway.data.entities.Message
import me.stappmus.messagegateway.data.entities.MessageRecipient
import me.stappmus.messagegateway.data.entities.MessageState
import me.stappmus.messagegateway.data.entities.RecipientState
import me.stappmus.messagegateway.modules.logs.db.LogEntriesDao
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.receiver.db.IncomingMmsDao
import me.stappmus.messagegateway.modules.receiver.db.IncomingMmsEntity
import me.stappmus.messagegateway.modules.webhooks.db.WebHook
import me.stappmus.messagegateway.modules.webhooks.db.WebHooksDao
import me.stappmus.messagegateway.modules.webhooks.db.WebhookQueueDao
import me.stappmus.messagegateway.modules.webhooks.db.WebhookQueueEntity

@Database(
    entities = [
        Message::class,
        MessageRecipient::class,
        RecipientState::class,
        MessageState::class,
        WebHook::class,
        WebhookQueueEntity::class,
        IncomingMmsEntity::class,
        LogEntry::class,
    ],
    version = 18,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        // AutoMigration(from = 7, to = 8),  // manual migration
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        // AutoMigration(from = 13, to = 14),   // manual migration
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messagesDao(): MessagesDao
    abstract fun webhooksDao(): WebHooksDao
    abstract fun webhookQueueDao(): WebhookQueueDao
    abstract fun incomingMmsDao(): IncomingMmsDao
    abstract fun logDao(): LogEntriesDao

    companion object {
        fun getDatabase(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "gateway"
            )
                .addMigrations(
                    MIGRATION_7_8,
                    MIGRATION_13_14,
                )
                .allowMainThreadQueries()
                .build()
        }
    }
}
