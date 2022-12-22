package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.modules.events.AppEvent
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class GatewayModule(
    private val context: Context,
    private val storage: KeyValueStorage
) {
    suspend fun register(eventBus: EventBus) {
        val api = GatewayApi(null)
        eventBus.events
            .filter { it is AppEvent.FcmTokenUpdated }
            .collectLatest {
                Log.d(this.javaClass.name, (it as AppEvent.FcmTokenUpdated).token)
                val response = withContext(Dispatchers.IO) {
                    api.register(
                        GatewayApi.RegisterRequest(
                            null,
                            android.os.Build.MANUFACTURER + android.os.Build.PRODUCT,
                            it.token
                        )
                    )
                }
                storage.set(REGISTRATION_INFO, response)

                storage.get<GatewayApi.RegisterResponse>(REGISTRATION_INFO)?.let {
                    Log.d(this.javaClass.name, it.toString())
                }
            }
    }

    companion object {
        private const val REGISTRATION_INFO = "REGISTRATION_INFO"
    }
}