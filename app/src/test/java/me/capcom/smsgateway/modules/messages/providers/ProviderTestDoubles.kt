package me.capcom.smsgateway.modules.messages.providers

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.telephony.SmsManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.GsonBuilder
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.messages.FakeMessagesDao
import me.capcom.smsgateway.modules.messages.MessageStateService
import me.capcom.smsgateway.modules.messages.MessagesRepository
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.messages.data.Message
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import java.lang.reflect.Type
import java.util.Date

internal class FakeKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String?>()
    private val gson = GsonBuilder().serializeNulls().create()

    override fun <T> set(key: String, value: T) {
        values[key] = gson.toJson(value)
    }

    override fun <T> get(key: String, typeOfT: Type): T? {
        val raw = values[key] ?: return null
        return gson.fromJson(raw, typeOfT)
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

internal class FakeLogEntriesDao : LogEntriesDao {
    val entries = mutableListOf<LogEntry>()

    override suspend fun selectByPeriod(from: Long, to: Long): List<LogEntry> = entries

    override fun selectLast(): LiveData<List<LogEntry>> = MutableLiveData()

    override fun insert(entry: LogEntry) {
        entries += entry
    }

    override suspend fun truncate(until: Long) = Unit
}

internal fun fakeMessagesSettings(
    simSelectionMode: MessagesSettings.SimSelectionMode = MessagesSettings.SimSelectionMode.OSDefault,
): MessagesSettings {
    val storage = FakeKeyValueStorage()
    // Key must match MessagesSettings companion (private there).
    storage.set("sim_selection_mode", simSelectionMode.name)
    return MessagesSettings(storage)
}

internal fun fakeSendParams(
    withDeliveryReport: Boolean = true,
    skipPhoneValidation: Boolean = false,
    simNumber: Int? = null,
) = SendParams(
    withDeliveryReport = withDeliveryReport,
    skipPhoneValidation = skipPhoneValidation,
    simNumber = simNumber,
    validUntil = null,
    scheduleAt = null,
    priority = null,
)

internal fun fakeMessage(
    id: String = "m1",
    content: MessageContent = MessageContent.Text("test message"),
    phoneNumbers: List<String> = emptyList(),
    isEncrypted: Boolean = false,
): Message = Message(
    id = id,
    content = content,
    phoneNumbers = phoneNumbers,
    isEncrypted = isEncrypted,
    createdAt = Date(1000L),
)

internal fun fakeStoredRequest(
    id: Long = 1L,
    message: Message = fakeMessage(),
    recipients: List<MessageRecipient> = emptyList(),
    params: SendParams = fakeSendParams(),
) = StoredSendRequest(
    id,
    ProcessingState.Pending,
    recipients,
    EntitySource.Local,
    message,
    params,
)

/**
 * JVM stand-in for the platform-typed context the providers store but never
 * touch on the extracted paths: ContextWrapper delegates to a null base, and
 * the stub-jar methods the send loop reaches (PendingIntent.getBroadcast,
 * Uri.parse) return defaults without consulting it.
 */
internal fun fakeContext(): Context = ContextWrapper(null)

/**
 * SmsManager's constructor is package-private; fabricate an instance without
 * running it. Used only as a non-null token - tests never invoke its methods
 * (TextSendProvider.buildPayload's divideMessage path is device-gated).
 */
internal fun fakeSmsManager(): SmsManager {
    val ctor = SmsManager::class.java.getDeclaredConstructor()
    ctor.isAccessible = true
    return ctor.newInstance()
}

/**
 * PendingIntent is abstract with a package-private constructor, and the stub
 * jar's PendingIntent.getBroadcast returns null; fabricate a non-null token
 * without running any constructor (same sun.misc.Unsafe technique the
 * pre-refactor MessagesServiceTest used). Tests only pass it through to a
 * recording sendFn - no PendingIntent method is ever invoked.
 */
internal fun fakePendingIntent(): PendingIntent {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass
        .getDeclaredField("theUnsafe")
        .also { it.isAccessible = true }
        .get(null)
    return unsafeClass
        .getMethod("allocateInstance", Class::class.java)
        .invoke(unsafe, PendingIntent::class.java) as PendingIntent
}

internal fun fakeEncryptionService() = EncryptionService()

internal fun fakeSimSelector(
    slots: Set<Int>? = null,
    mode: MessagesSettings.SimSelectionMode = MessagesSettings.SimSelectionMode.OSDefault,
) = SimSelector(fakeContext(), fakeMessagesSettings(mode)) { slots }

internal fun fakePhoneNormalizer(countryCode: String = "RU") =
    PhoneNormalizer(fakeContext(), fakeEncryptionService()) { countryCode }

internal fun fakeMachine(dao: FakeMessagesDao) = MessageStateService(dao, EventBus())

internal fun fakeLogsService() =
    LogsService(FakeLogEntriesDao(), LogsSettings(FakeKeyValueStorage()))

internal fun fakeRepository(dao: FakeMessagesDao, machine: MessageStateService) =
    MessagesRepository(dao, machine)
