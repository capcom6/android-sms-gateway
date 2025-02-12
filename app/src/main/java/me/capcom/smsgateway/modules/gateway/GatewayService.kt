package me.capcom.smsgateway.modules.gateway

import android.content.Context
import android.os.Build
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
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
            settings.serverUrl,
            settings.privateToken
        ).also { _api = it }

    suspend fun getPublicIP(): String {
        return GatewayApi(
            settings.serverUrl,
            settings.privateToken
        )
            .getDevice(settings.registrationInfo?.token)
            .externalIp
    }

    fun start(context: Context) {
        if (!settings.enabled) return

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

    suspend fun changePassword(current: String, new: String) {
        val info = settings.registrationInfo
            ?: throw IllegalStateException("The device is not registered on the server")

        this.api.changePassword(
            info.token,
            GatewayApi.PasswordChangeRequest(current, new)
        )

        settings.registrationInfo = info.copy(password = new)

        events.emit(
            DeviceRegisteredEvent.Success(
                api.hostname,
                info.login,
                new,
            )
        )
    }

    ///////////////////////////////////////////////////////////////////////////
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
                    message.message.state,
                    message.recipients.map {
                        GatewayApi.RecipientState(
                            it.phoneNumber,
                            it.state,
                            it.error
                        )
                    },
                    message.states.associate { it.state to Date(it.updatedAt) }
                )
            )
        )
    }

    internal suspend fun registerFcmToken(pushToken: String?) {
        if (!settings.enabled) return

        val settings = settings.registrationInfo
        val accessToken = settings?.token

        if (accessToken != null) {
            // if there's an access token, try to update push token
            try {
                pushToken?.let {
                    api.devicePatch(
                        accessToken,
                        GatewayApi.DevicePatchRequest(
                            settings.id,
                            it
                        )
                    )
                }
                events.emit(
                    DeviceRegisteredEvent.Success(
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

        try {
            val response = api.deviceRegister(
                GatewayApi.DeviceRegisterRequest(
                    "${Build.MANUFACTURER}/${Build.PRODUCT}",
                    pushToken
                )
            )
            this.settings.registrationInfo = response

            events.emit(
                DeviceRegisteredEvent.Success(
                    api.hostname,
                    response.login,
                    response.password,
                )
            )
        } catch (th: Throwable) {
            events.emit(
                DeviceRegisteredEvent.Failure(
                    api.hostname,
                    th.localizedMessage ?: th.message ?: th.toString()
                )
            )

            throw th
        }
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
}