package me.stappmus.messagegateway.modules.connection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.stappmus.messagegateway.modules.health.domain.CheckResult
import me.stappmus.messagegateway.modules.health.domain.Status
import me.stappmus.messagegateway.modules.logs.LogsService
import me.stappmus.messagegateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ConnectionService(
    private val context: Context
) : KoinComponent {
    private val _status = MutableLiveData(false)
    val status: LiveData<Boolean> = _status

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val logsService by inject<LogsService>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (_status.value == false) return

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

            if (_status.value == hasInternet) return

            logsService.insert(
                LogEntry.Priority.INFO,
                MODULE_NAME,
                "Internet connection status: $hasInternet"
            )

            _status.postValue(hasInternet)

            super.onCapabilitiesChanged(network, networkCapabilities)
        }
    }

    fun healthCheck(): Map<String, CheckResult> {
        val status = when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            true -> when (_status.value) {
                true -> Status.PASS
                else -> Status.FAIL
            }

            false -> Status.PASS
        }
        val transport = transportType
        val cellularType = cellularNetworkType

        return mapOf(
            "status" to CheckResult(
                status,
                when (status) {
                    Status.PASS -> 1L
                    else -> 0L
                },
                "boolean",
                "Internet connection status"
            ),
            "transport" to CheckResult(
                when (transport.isEmpty()) {
                    true -> Status.FAIL
                    false -> Status.PASS
                },
                transport.sumOf { it.value }.toLong(),
                "flags",
                "Network transport type"
            ),
            "cellular" to CheckResult(
                Status.PASS,
                cellularType.ordinal.toLong(),
                "index",
                "Cellular network type"
            )
        )
    }

    val transportType: Set<TransportType>
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return setOf(TransportType.Unknown)

            val result = mutableSetOf<TransportType>()

            val nw = connectivityManager.activeNetwork ?: return result
            val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return result

            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                result.add(TransportType.WiFi)
            }
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                result.add(TransportType.Ethernet)
            }
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                result.add(TransportType.Cellular)
            }

            return result;
        }

    val cellularNetworkType: CellularNetworkType
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return CellularNetworkType.Unknown

            val transport = transportType

            if (transport.contains(TransportType.Unknown)) {
                return CellularNetworkType.Unknown
            }
            if (!transport.contains(TransportType.Cellular)) {
                return CellularNetworkType.None
            }

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return CellularNetworkType.Unknown
            }
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_GSM -> return CellularNetworkType.Mobile2G

                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> return CellularNetworkType.Mobile3G

                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_IWLAN, 19 -> return CellularNetworkType.Mobile4G

                TelephonyManager.NETWORK_TYPE_NR -> return CellularNetworkType.Mobile5G
            }

            return CellularNetworkType.Unknown
        }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }
    }
}