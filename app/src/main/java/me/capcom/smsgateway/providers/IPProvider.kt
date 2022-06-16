package me.capcom.smsgateway.providers

interface IPProvider {
    fun getIP(onResult: (String?) -> Unit)
}