package me.capcom.smsgateway.modules.settings

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

            if (value is Long) {
                putLong("${prefix}.${key}", value)
                return@edit
            }
            if (value is Int) {
                putInt("${prefix}.${key}", value)
                return@edit
            }

            putString("${prefix}.${key}", serializer.serialize(value))
        }
    }

    override fun <T> get(key: String, typeOfT: Type): T? {
        if (!preferences.contains("${prefix}.${key}")) {
            return null
        }

        if (typeOfT == java.lang.Long::class.java) {
            return preferences.getLong("${prefix}.${key}", 0) as T
        }
        if (typeOfT == java.lang.Integer::class.java) {
            return preferences.getInt("${prefix}.${key}", 0) as T
        }

        return preferences.getString("${prefix}.${key}", null)?.let {
            serializer.deserialize(it, typeOfT)
        }
    }
}