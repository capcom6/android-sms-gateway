package me.stappmus.messagegateway.data

import androidx.room.TypeConverter
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Converters {
    private val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create()

    @TypeConverter
    fun listToString(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun listFromString(value: String?): List<String>? {
        return value?.let { gson.fromJson(it, Array<String>::class.java).toList() }
    }

    @TypeConverter
    fun dateToString(value: Date?): String? {
        return value?.let { DATE_FORMAT.format(it) }
    }

    @TypeConverter
    fun dateFromString(value: String?): Date? {
        return value?.let { DATE_FORMAT.parse(it) }
    }

    @TypeConverter
    fun jsonToString(value: JsonElement?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun stringToJson(value: String?): JsonElement? {
        return value?.let { gson.fromJson(it, JsonElement::class.java) }
    }

    companion object {
        private val DATE_FORMAT =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
    }
}