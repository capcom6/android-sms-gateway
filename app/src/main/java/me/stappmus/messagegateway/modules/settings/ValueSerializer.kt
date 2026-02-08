package me.stappmus.messagegateway.modules.settings

import com.google.gson.GsonBuilder
import java.lang.reflect.Type

class ValueSerializer {
    private val gson = GsonBuilder().create()

    fun <T>serialize(value: T): String {
        return gson.toJson(value)
    }

    fun <T>deserialize(value: String, typeOfT: Type): T {
        return gson.fromJson(value, typeOfT)
    }
}