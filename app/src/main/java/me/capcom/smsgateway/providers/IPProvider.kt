package me.capcom.smsgateway.providers

interface IPProvider {
    suspend fun getIP(): String?
}