package me.capcom.smsgateway.providers

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder


class LocalIPProvider(private val context: Context): IPProvider {
    override fun getIP(onResult: (String?) -> Unit) {
        var ipAddress = (context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress
        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress)
        }

        val ipByteArray: ByteArray = BigInteger.valueOf(0L+ipAddress).toByteArray()

        onResult(InetAddress.getByAddress(ipByteArray).hostAddress)
    }
}