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
            putString("${prefix}.${key}", serializer.serialize(value))
        }
    }

    override fun <T> get(key: String, typeOfT: Type): T? {
        return preferences.getString("${prefix}.${key}", null)?.let {
            serializer.deserialize(it, typeOfT)
        }
    }
}