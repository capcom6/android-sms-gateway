package me.capcom.smsgateway.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.capcom.smsgateway.data.dao.MessageDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageState
import me.capcom.smsgateway.data.entities.RecipientState
import me.capcom.smsgateway.modules.webhooks.db.WebHook

@Database(
    entities = [Message::class, MessageRecipient::class, RecipientState::class, MessageState::class, WebHook::class],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
//        AutoMigration(from = 7, to = 8),  // manual migration
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        fun getDatabase(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "gateway"
            )
                .addMigrations(MIGRATION_7_8)
                .allowMainThreadQueries()
                .build()
        }
    }
}