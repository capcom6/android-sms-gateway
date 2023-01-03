package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class GatewayModule constructor(
    private val context: Context,
    private val storage: KeyValueStorage,
) {
    private val api = GatewayApi()

    suspend fun registerFcmToken(token: String) {
        val settings = storage.get<GatewayApi.DeviceRegisterResponse>(REGISTRATION_INFO)
        settings?.token?.let {
            withContext(Dispatchers.IO) {
                api.devicePatch(
                    it, GatewayApi.DevicePatchRequest(
                        settings.id,
                        token
                    )
                )
            }
        } ?: kotlin.run {
            val response = withContext(Dispatchers.IO) {
                api.deviceRegister(
                    GatewayApi.DeviceRegisterRequest(
                        Build.MANUFACTURER + Build.PRODUCT,
                        token
                    )
                )
            }
            storage.set(REGISTRATION_INFO, response)
        }

        storage.get<GatewayApi.DeviceRegisterResponse>(REGISTRATION_INFO)?.let {
            Log.d(this.javaClass.name, it.toString())
        }
    }

    companion object {
        private const val REGISTRATION_INFO = "REGISTRATION_INFO"
    }
}