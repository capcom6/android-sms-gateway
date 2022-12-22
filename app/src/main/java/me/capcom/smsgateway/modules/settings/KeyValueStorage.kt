package me.capcom.smsgateway.modules.settings

import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

interface KeyValueStorage {
    fun <T>set(key: String, value: T)
    fun <T>get(key: String, typeOfT: Type): T?
}

inline fun <reified T> KeyValueStorage.get(key: String): T? {
    return get<T>(key, object : TypeToken<T>(){}.type)
}