package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.domain.MessageState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.messages.MessageStateChangedEvent
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.services.PushService

class GatewayModule(
    private val messagesService: MessagesService,
    private val settings: GatewaySettings,
) {
    private val api = GatewayApi()

    val events = EventBus()
    var enabled: Boolean
        get() = settings.enabled
        set(value) {
            settings.enabled = value
        }

    fun start(context: Context) {
        if (!enabled) return
        PushService.register(context)
        PullMessagesWorker.start(context)

        scope.launch {
            withContext(Dispatchers.IO) {
                messagesService.events.events.collect { event ->
                    val event = event as? MessageStateChangedEvent ?: return@collect
                    if (event.source != Message.Source.Gateway) return@collect

                    try {
                        sendState(event)
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
            }
        }
    }

    fun isActiveLiveData(context: Context) = PullMessagesWorker.getStateLiveData(context)

    fun stop(context: Context) {
        scope.cancel()
        PullMessagesWorker.stop(context)
    }

    private suspend fun sendState(
        event: MessageStateChangedEvent
    ) {
        val settings = settings.registrationInfo ?: return

        withContext(Dispatchers.IO) {
            api.patchMessages(
                settings.token,
                listOf(
                    GatewayApi.MessagePatchRequest(
                        event.id,
                        event.state.toApiState(),
                        event.recipients.map {
                            GatewayApi.RecipientState(
                                it.phoneNumber,
                                it.state.toApiState(),
                                it.error
                            )
                        }
                    )
                )
            )
        }
    }

    suspend fun registerFcmToken(token: String) {
        if (!enabled) return

        val settings = settings.registrationInfo
        settings?.token?.let {
            withContext(Dispatchers.IO) {
                api.devicePatch(
                    it,
                    GatewayApi.DevicePatchRequest(
                        settings.id,
                        token
                    )
                )
            }

            events.emitEvent(
                DeviceRegisteredEvent(
                    settings.login,
                    settings.password,
                )
            )
        }
            ?: kotlin.run {
                val response = withContext(Dispatchers.IO) {
                    api.deviceRegister(
                        GatewayApi.DeviceRegisterRequest(
                            "${Build.MANUFACTURER}/${Build.PRODUCT}",
                            token
                        )
                    )
                }
                this.settings.registrationInfo = response

                events.emitEvent(
                    DeviceRegisteredEvent(
                        response.login,
                        response.password,
                    )
                )
            }
    }

    internal suspend fun getNewMessages() {
        val settings = settings.registrationInfo ?: return
        withContext(Dispatchers.IO) {
            api.getMessages(settings.token)
                .forEach {
                    try {
                        messagesService.getMessage(it.id)
                            ?.also {
                                sendState(
                                    MessageStateChangedEvent(
                                        it.message.id,
                                        it.message.state,
                                        it.message.source,
                                        it.recipients.map { rcp ->
                                            MessageStateChangedEvent.Recipient(
                                                rcp.phoneNumber,
                                                rcp.state,
                                                rcp.error
                                            )
                                        }
                                    )
                                )
                            }
                            ?: messagesService.sendMessage(
                                it.id,
                                it.message,
                                it.phoneNumbers,
                                Message.Source.Gateway,
                                it.simNumber?.let { it - 1 },
                                it.withDeliveryReport,
                                it.isEncrypted ?: false,
                            )
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                }
        }
    }

    private fun Message.State.toApiState(): MessageState = when (this) {
        Message.State.Pending -> MessageState.Pending
        Message.State.Processed -> MessageState.Processed
        Message.State.Sent -> MessageState.Sent
        Message.State.Delivered -> MessageState.Delivered
        Message.State.Failed -> MessageState.Failed
    }

    companion object {
        private val job = SupervisorJob()
        private val scope = CoroutineScope(job)
    }
}