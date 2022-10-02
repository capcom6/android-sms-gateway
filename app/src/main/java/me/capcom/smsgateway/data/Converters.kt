package me.capcom.smsgateway.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun listFromString(value: String?): List<String>? {
        return value?.split("|")
    }

    @TypeConverter
    fun stringFromList(value: List<String>?): String? {
        return value?.joinToString("|")
    }

}