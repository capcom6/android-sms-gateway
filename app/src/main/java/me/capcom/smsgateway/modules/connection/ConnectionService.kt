package me.capcom.smsgateway.modules.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ConnectionService(context: Context) : KoinComponent {
    private val _status = MutableLiveData(false)
    val status: LiveData<Boolean> = _status

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val logsService by inject<LogsService>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Internet connection lost"
            )
            _status.postValue(false)

            super.onLost(network)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ))
            logsService.insert(
                LogEntry.Priority.INFO,
                MODULE_NAME,
                "Internet connection status: $hasInternet"
            )

            _status.postValue(hasInternet)

            super.onCapabilitiesChanged(network, networkCapabilities)
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }
}