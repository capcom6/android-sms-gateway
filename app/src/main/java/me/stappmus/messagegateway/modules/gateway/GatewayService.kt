package me.stappmus.messagegateway.modules.gateway

import android.content.Context
import android.os.Build
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import me.stappmus.messagegateway.data.entities.MessageWithRecipients
import me.stappmus.messagegateway.domain.EntitySource
import me.stappmus.messagegateway.domain.MessageContent
import me.stappmus.messagegateway.modules.events.EventBus
import me.stappmus.messagegateway.modules.gateway.events.DeviceRegisteredEvent
import me.stappmus.messagegateway.modules.gateway.services.SSEForegroundService
import me.stappmus.messagegateway.modules.gateway.workers.PullMessagesWorker
import me.stappmus.messagegateway.modules.gateway.workers.SendStateWorker
import me.stappmus.messagegateway.modules.gateway.workers.SettingsUpdateWorker
import me.stappmus.messagegateway.modules.gateway.workers.WebhooksUpdateWorker
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import me.stappmus.messagegateway.modules.messages.MessagesService
import me.stappmus.messagegateway.modules.messages.MessagesSettings
import me.stappmus.messagegateway.modules.messages.data.SendParams
import me.stappmus.messagegateway.modules.messages.data.SendRequest
import me.stappmus.messagegateway.services.PushService
import java.util.Date

class GatewayService(
    private val messagesService: MessagesService,
    private val settings: GatewaySettings,
    private val events: EventBus,
    private val logsService: LogsService,
) {
    private val eventsReceiver by lazy { EventsReceiver() }

    private var _api: GatewayApi? = null

    private val api
        get() = _api ?: GatewayApi(
            settings.serverUrl,
            settings.privateToken
        ).also { _api = it }

    //region Start, stop, etc...
    fun start(context: Context) {
        if (!settings.enabled) return

        PushService.register(context)
        PullMessagesWorker.start(context)
        WebhooksUpdateWorker.start(context)
        SettingsUpdateWorker.start(context)

        eventsReceiver.start()
    }

    fun stop(context: Context) {
        eventsReceiver.stop()

        SSEForegroundService.stop(context)
        SettingsUpdateWorker.stop(context)
        WebhooksUpdateWorker.stop(context)
        PullMessagesWorker.stop(context)

        this._api = null
    }

    fun isActiveLiveData(context: Context) = PullMessagesWorker.getStateLiveData(context)
    //endregion

    //region Account
    suspend fun getLoginCode(): GatewayApi.GetUserCodeResponse {
        val username = settings.username
            ?: throw IllegalStateException("Username is not set")
        val password = settings.password
            ?: throw IllegalStateException("Password is not set")

        return api.getUserCode(username to password)
    }

    suspend fun changePassword(current: String, new: String) {
        val info = settings.registrationInfo
            ?: throw IllegalStateException("The device is not registered on the server")

        this.api.changeUserPassword(
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
    //endregion

    //region Device
    internal suspend fun registerDevice(
        pushToken: String?,
        registerMode: RegistrationMode
    ) {
        if (!settings.enabled) return

        val settings = settings.registrationInfo
        val accessToken = settings?.token

        if (accessToken != null) {
            // if there's an access token, try to update push token
            try {
                updateDevice(pushToken)
                return
            } catch (e: ClientRequestException) {
                // if token is invalid, try to register new one
                if (e.response.status != HttpStatusCode.Unauthorized) {
                    throw e
                }
            }
        }

        try {
            val deviceName = "${Build.MANUFACTURER}/${Build.PRODUCT}"
            val request = GatewayApi.DeviceRegisterRequest(
                deviceName,
                pushToken
            )
            val response = when (registerMode) {
                RegistrationMode.Anonymous -> api.deviceRegister(request, null)
                is RegistrationMode.WithCode -> api.deviceRegister(request, registerMode.code)
                is RegistrationMode.WithCredentials -> api.deviceRegister(
                    request,
                    registerMode.login to registerMode.password
                )
            }

            this.settings.fcmToken = pushToken
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

    internal suspend fun updateDevice(pushToken: String?) {
        if (!settings.enabled) return

        val settings = settings.registrationInfo ?: return
        val accessToken = settings.token

        pushToken?.let {
            api.devicePatch(
                accessToken,
                GatewayApi.DevicePatchRequest(
                    settings.id,
                    it
                )
            )
        }

        this.settings.fcmToken = pushToken

        events.emit(
            DeviceRegisteredEvent.Success(
                api.hostname,
                settings.login,
                settings.password,
            )
        )
    }

    sealed class RegistrationMode {
        object Anonymous : RegistrationMode()
        class WithCredentials(val login: String, val password: String) : RegistrationMode()
        class WithCode(val code: String) : RegistrationMode()
    }
    //endregion

    //region Messages
    internal suspend fun getNewMessages(context: Context) {
        if (!settings.enabled) return
        val settings = settings.registrationInfo ?: return
        val processingOrder = when (messagesService.processingOrder) {
            MessagesSettings.ProcessingOrder.LIFO -> GatewayApi.ProcessingOrder.LIFO
            MessagesSettings.ProcessingOrder.FIFO -> GatewayApi.ProcessingOrder.FIFO
        }
        val messages = api.getMessages(settings.token, processingOrder)
        for (message in messages) {
            try {
                processMessage(context, message)
            } catch (th: Throwable) {
                logsService.insert(
                    LogEntry.Priority.ERROR,
                    MODULE_NAME,
                    "Failed to process message",
                    mapOf(
                        "message" to message,
                        "exception" to th.stackTraceToString(),
                    )
                )
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
            me.stappmus.messagegateway.modules.messages.data.Message(
                message.id,
                when (val content = message.content) {
                    is GatewayApi.MessageContent.Text -> MessageContent.Text(content.text)
                    is GatewayApi.MessageContent.Data -> MessageContent.Data(
                        content.data,
                        content.port
                    )
                },
                message.phoneNumbers,
                message.isEncrypted ?: false,
                message.createdAt ?: Date(),
            ),
            SendParams(
                message.withDeliveryReport ?: true,
                skipPhoneValidation = true,
                simNumber = message.simNumber,
                validUntil = message.validUntil,
                priority = message.priority,
            )
        )
        messagesService.enqueueMessage(request)
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
    //endregion

    //region Webhooks
    internal suspend fun getWebHooks(): List<GatewayApi.WebHook> {
        val settings = settings.registrationInfo
        return if (settings != null) {
            api.getWebHooks(settings.token)
        } else {
            emptyList()
        }
    }
    //endregion

    //region Settings
    internal suspend fun getSettings(): Map<String, *>? {
        val settings = settings.registrationInfo ?: return null

        return api.getSettings(settings.token)
    }
    //endregion

    //region Utility
    suspend fun getPublicIP(): String {
        return GatewayApi(
            settings.serverUrl,
            settings.privateToken
        )
            .getDevice(settings.registrationInfo?.token)
            .externalIp
    }
    //endregion
}