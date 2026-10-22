package me.capcom.smsgateway.modules.localserver

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.localserver.events.IPReceivedEvent
import me.capcom.smsgateway.providers.LocalIPProvider
import me.capcom.smsgateway.providers.PublicIPProvider

class LocalServerService(
    private val settings: LocalServerSettings,
    private val events: EventBus,
) {

    private fun getDeviceId(context: Context): String {
        val firstInstallTime = context.packageManager.getPackageInfo(
            context.packageName,
            0
        ).firstInstallTime
        val deviceName = "${Build.MANUFACTURER}/${Build.PRODUCT}"

        val modelHash = deviceName.hashCode().toULong()
            .toString(16).padStart(MODEL_HASH_LENGTH, '0').take(MODEL_HASH_LENGTH)
        val installHash = firstInstallTime.toULong()
            .toString(16).padStart(INSTALL_HASH_LENGTH, '0').take(INSTALL_HASH_LENGTH)

        return modelHash + installHash
    }

    fun start(context: Context) {
        if (!settings.enabled) return
        settings.deviceId = settings.deviceId ?: getDeviceId(context)

        WebService.start(context)

        scope.launch(Dispatchers.IO) {
            val localIP = LocalIPProvider(context).getIP()
            val remoteIP = PublicIPProvider().getIP()

            events.emit(IPReceivedEvent(localIP, remoteIP))
        }
    }

    fun stop(context: Context) {
        WebService.stop(context)
    }

    fun isActiveLiveData(context: Context) = WebService.STATUS

    companion object {
        const val DEVICE_ID_LENGTH = 21

        private const val MODEL_HASH_LENGTH = 5
        private const val INSTALL_HASH_LENGTH = DEVICE_ID_LENGTH - MODEL_HASH_LENGTH

        private val job = SupervisorJob()
        private val scope = CoroutineScope(job)
    }
}