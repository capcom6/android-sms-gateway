package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.domain.MessageState
import me.capcom.smsgateway.modules.messages.MessageStateChangedEvent
import me.capcom.smsgateway.modules.messages.MessagesModule
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class GatewayModule(
    private val messagesModule: MessagesModule,
    private val storage: KeyValueStorage,
) {
    private val api = GatewayApi()

    init {
        scope.launch {
            messagesModule.events.events.collect { event ->
                val event = event as? MessageStateChangedEvent ?: return@collect
                try {
                    sendState(event)
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
            }
        }
    }

    fun start(context: Context) {
        PullMessagesWorker.start(context)
    }

    fun stop(context: Context) {
        PullMessagesWorker.stop(context)
    }

    private suspend fun sendState(
        event: MessageStateChangedEvent
    ) {
        val settings = storage.get<GatewayApi.DeviceRegisterResponse>(REGISTRATION_INFO)
            ?: return
        withContext(Dispatchers.IO) {
            api.patchMessages(
                settings.token,
                listOf(
                    GatewayApi.MessagePatchRequest(
                        event.id,
                        event.state.toApiState(),
                        event.recipients.entries.map {
                            GatewayApi.RecipientState(
                                it.key.removePrefix("+"),
                                it.value.toApiState()
                            )
                        }
                    )
                )
            )
        }
    }

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
    }

    suspend fun getNewMessages() {
        val settings = storage.get<GatewayApi.DeviceRegisterResponse>(REGISTRATION_INFO) ?: return
        withContext(Dispatchers.IO) {
            val messages = api.getMessages(settings.token)
            messages.forEach {
                try {
                    messagesModule.getState(it.id)
//                        ?.takeIf { it.message.state != Message.State.Pending }
                        ?.also {
                        sendState(
                                MessageStateChangedEvent(
                                it.message.id,
                                it.message.state,
                                it.recipients.associate { it.phoneNumber to it.state }
                            )
                        )
                    }
                        ?: messagesModule.sendMessage(
                            it.id,
                            it.message,
                            it.phoneNumbers,
                            Message.Source.Gateway
                        )
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
            }
        }
    }

    private fun Message.State.toApiState(): MessageState = when (this) {
        Message.State.Pending -> MessageState.Pending
        Message.State.Sent -> MessageState.Sent
        Message.State.Delivered -> MessageState.Delivered
        Message.State.Failed -> MessageState.Failed
    }

    companion object {
        private val job = SupervisorJob()
        private val scope = CoroutineScope(job)

        private const val REGISTRATION_INFO = "REGISTRATION_INFO"
    }
}