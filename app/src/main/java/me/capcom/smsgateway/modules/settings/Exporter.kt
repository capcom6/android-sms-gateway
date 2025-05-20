package me.capcom.smsgateway.modules.settings

interface Exporter {
    fun export(): Map<String, *>
}