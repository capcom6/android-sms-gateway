package me.stappmus.messagegateway.modules.settings

interface Importer {
    fun import(data: Map<String, *>): Boolean
}