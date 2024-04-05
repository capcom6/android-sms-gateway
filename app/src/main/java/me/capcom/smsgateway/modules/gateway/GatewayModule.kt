package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.os.Build
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.domain.MessageState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.MessageSource
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.modules.messages.events.MessageStateChangedEvent
import me.capcom.smsgateway.services.PushService

class GatewayModule(
    private val messagesService: MessagesService,
    private val settings: GatewaySettings,
) {
    private var _api: GatewayApi? = null
    private var _job: Job? = null

    private val api
        get() = _api ?: GatewayApi(
            settings.privateUrl ?: GatewaySettings.PUBLIC_URL,
            settings.privateToken
        ).also { _api = it }

    val events = EventBus()
    var enabled: Boolean
        get() = settings.enabled
        set(value) {
            settings.enabled = value
        }

    fun start(context: Context) {
        if (!enabled) return
        this._api = GatewayApi(
            settings.privateUrl ?: GatewaySettings.PUBLIC_URL,
            settings.privateToken
        )

        PushService.register(context)
        PullMessagesWorker.start(context)

        _job = scope.launch {
            messagesService.events.events.collect { event ->
                val event = event as? MessageStateChangedEvent ?: return@collect
                if (event.source != MessageSource.Gateway) return@collect

                try {
                    sendState(event)
                } catch (th: Throwable) {
                    th.printStackTrace()
                }
            }
        }
    }

    fun isActiveLiveData(context: Context) = PullMessagesWorker.getStateLiveData(context)

    fun stop(context: Context) {
        _job?.cancel()
        _job = null
        PullMessagesWorker.stop(context)
        this._api = null
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

    suspend fun registerFcmToken(pushToken: String) {
        if (!enabled) return

        val settings = settings.registrationInfo
        val accessToken = settings?.token

        if (accessToken != null) {
            // if there's an access token, try to update push token
            try {
                api.devicePatch(
                    accessToken,
                    GatewayApi.DevicePatchRequest(
                        settings.id,
                        pushToken
                    )
                )
                events.emitEvent(
                    DeviceRegisteredEvent(
                        api.hostname,
                        settings.login,
                        settings.password,
                    )
                )
                return
            } catch (e: ClientRequestException) {
                // if token is invalid, try to register new one
                if (e.response.status != HttpStatusCode.Unauthorized) {
                    throw e
                }
            }
        }

        val response = api.deviceRegister(
            GatewayApi.DeviceRegisterRequest(
                "${Build.MANUFACTURER}/${Build.PRODUCT}",
                pushToken
            )
        )
        this.settings.registrationInfo = response

        events.emitEvent(
            DeviceRegisteredEvent(
                api.hostname,
                response.login,
                response.password,
            )
        )
    }

    internal suspend fun getNewMessages() {
        val settings = settings.registrationInfo ?: return
        val messages = api.getMessages(settings.token)
        for (message in messages) {
            try {
                processMessage(message)
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }

    private suspend fun processMessage(message: GatewayApi.Message) {
        val messageState = messagesService.getMessage(message.id)
        if (messageState != null) {
            sendState(
                MessageStateChangedEvent(
                    messageState.message.id,
                    messageState.message.state,
                    messageState.message.source,
                    messageState.recipients.map { rcp ->
                        MessageStateChangedEvent.Recipient(
                            rcp.phoneNumber,
                            rcp.state,
                            rcp.error
                        )
                    }
                )
            )
            return
        }

        val request = SendRequest(
            MessageSource.Gateway,
            me.capcom.smsgateway.modules.messages.data.Message(
                message.id,
                message.message,
                message.phoneNumbers,
                message.isEncrypted ?: false
            ),
            SendParams(
                message.withDeliveryReport ?: true,
                skipPhoneValidation = true,
                simNumber = message.simNumber,
                validUntil = message.validUntil
            )
        )
        messagesService.enqueueMessage(request)
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
        private val scope = CoroutineScope(job + Dispatchers.IO)
    }
}