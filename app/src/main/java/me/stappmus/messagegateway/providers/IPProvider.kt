package me.stappmus.messagegateway.providers

interface IPProvider {
    suspend fun getIP(): String?
}