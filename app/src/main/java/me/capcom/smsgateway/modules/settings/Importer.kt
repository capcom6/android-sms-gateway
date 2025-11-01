package me.capcom.smsgateway.modules.settings

interface Importer {
    fun import(data: Map<String, *>): Boolean
}