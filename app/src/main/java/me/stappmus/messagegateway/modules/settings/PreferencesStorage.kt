package me.stappmus.messagegateway.modules.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import java.lang.reflect.Type

class PreferencesStorage(
    private val preferences: SharedPreferences,
    private val prefix: String,
    serializer: ValueSerializer? = null
): KeyValueStorage {
    private val serializer = serializer ?: ValueSerializer()

    override fun <T> set(key: String, value: T) {
        preferences.edit {
            if (value == null) {
                remove("${prefix}.${key}")
                return@edit
            }

            when (value) {
                is Long -> putLong("${prefix}.${key}", value)
                is Int -> putInt("${prefix}.${key}", value)
                is String -> putString("${prefix}.${key}", value)
                is Boolean -> putBoolean("${prefix}.${key}", value)
                is Float -> putFloat("${prefix}.${key}", value)
                else -> putString("${prefix}.${key}", serializer.serialize(value))
            }
        }
    }

    override fun <T> get(key: String, typeOfT: Type): T? {
        if (!preferences.contains("${prefix}.${key}")) {
            return null
        }

        return try {
            preferences.getString("${prefix}.${key}", null)?.let {
                serializer.deserialize(it, typeOfT)
            }
        } catch (th: ClassCastException) {
            getFallback<T>(typeOfT, key)
        } catch (th: com.google.gson.JsonParseException) {
            getFallback<T>(typeOfT, key)
        }
    }

    override fun remove(key: String) {
        preferences.edit {
            remove("${prefix}.${key}")
        }
    }

    private fun <T> getFallback(typeOfT: Type, key: String) = when (typeOfT) {
        java.lang.Long::class.java -> preferences.getLong("${prefix}.${key}", 0) as T
        Integer::class.java -> preferences.getInt("${prefix}.${key}", 0) as T
        java.lang.String::class.java -> preferences.getString("${prefix}.${key}", "") as T
        java.lang.Boolean::class.java -> preferences.getBoolean(
            "${prefix}.${key}",
            false
        ) as T

        java.lang.Float::class.java -> preferences.getFloat("${prefix}.${key}", 0.0f) as T
        else -> throw RuntimeException("Unknown type for key $key")
    }
}