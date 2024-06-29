package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.os.Build
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageState
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.gateway.workers.PullMessagesWorker
import me.capcom.smsgateway.modules.gateway.workers.SendStateWorker
import me.capcom.smsgateway.modules.gateway.workers.WebhooksUpdateWorker
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.SendRequest
import me.capcom.smsgateway.services.PushService
import java.util.Date

class GatewayService(
    private val messagesService: MessagesService,
    private val settings: GatewaySettings,
    private val events: EventBus,
) {
    private val eventsReceiver by lazy { EventsReceiver() }

    private var _api: GatewayApi? = null

    private val api
        get() = _api ?: GatewayApi(
            settings.privateUrl ?: GatewaySettings.PUBLIC_URL,
            settings.privateToken
        ).also { _api = it }

    fun start(context: Context) {
        if (!settings.enabled) return
        this._api = GatewayApi(
            settings.privateUrl ?: GatewaySettings.PUBLIC_URL,
            settings.privateToken
        )

        PushService.register(context)
        PullMessagesWorker.start(context)
        WebhooksUpdateWorker.start(context)
        eventsReceiver.start()
    }

    fun isActiveLiveData(context: Context) = PullMessagesWorker.getStateLiveData(context)

    fun stop(context: Context) {
        eventsReceiver.stop()
        WebhooksUpdateWorker.stop(context)
        PullMessagesWorker.stop(context)
        this._api = null
    }

    internal suspend fun getWebHooks(): List<GatewayApi.WebHook> {
        val settings = settings.registrationInfo
        return if (settings != null) {
            api.getWebHooks(settings.token)
        } else {
            emptyList()
        }
    }

    internal suspend fun sendState(
        message: MessageWithRecipients
    ) {
        val settings = settings.registrationInfo ?: return

        api.patchMessages(
            settings.token,
            listOf(
                GatewayApi.MessagePatchRequest(
                    message.message.id,
                    message.message.state.toApiState(),
                    message.recipients.map {
                        GatewayApi.RecipientState(
                            it.phoneNumber,
                            it.state.toApiState(),
                            it.error
                        )
                    },
                    message.states.associate { it.state.toApiState() to Date(it.updatedAt) }
                )
            )
        )
    }

    internal suspend fun registerFcmToken(pushToken: String) {
        if (!settings.enabled) return

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
                events.emit(
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

        events.emit(
            DeviceRegisteredEvent(
                api.hostname,
                response.login,
                response.password,
            )
        )
    }

    internal suspend fun getNewMessages(context: Context) {
        if (!settings.enabled) return
        val settings = settings.registrationInfo ?: return
        val messages = api.getMessages(settings.token)
        for (message in messages) {
            try {
                processMessage(context, message)
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }
    }

    private fun processMessage(context: Context, message: GatewayApi.Message) {
        val messageState = messagesService.getMessage(message.id)
        if (messageState != null) {
            SendStateWorker.start(context, message.id)
            return
        }

        val request = SendRequest(
            EntitySource.Cloud,
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
}