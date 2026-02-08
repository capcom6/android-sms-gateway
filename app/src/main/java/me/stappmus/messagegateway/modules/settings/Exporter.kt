package me.stappmus.messagegateway.modules.settings

interface Exporter {
    fun export(): Map<String, *>
}