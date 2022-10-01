package me.capcom.smsgateway

import android.app.Application
import androidx.room.Room
import me.capcom.smsgateway.data.AppDatabase
import me.capcom.smsgateway.receivers.EventsReceiver

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "gateway"
            )
            .allowMainThreadQueries()
            .build()

        EventsReceiver.register(this)
    }

    companion object {
        lateinit var db: AppDatabase
    }
}