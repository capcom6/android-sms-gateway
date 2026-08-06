package me.capcom.smsgateway.extensions

import android.os.Build
import com.google.gson.GsonBuilder
import java.util.TimeZone

fun GsonBuilder.configure(): GsonBuilder {
    // Disable HTML escaping to mirror Go encoding/json (no \uXXXX for
    // <>&= etc). Critical for base64 attachment data ("=" must stay "=").
    return this.setDateFormatISO8601().disableHtmlEscaping()
}

private fun GsonBuilder.setDateFormatISO8601(): GsonBuilder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.setDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
    } else {
        //get device timezone
        val timeZone = TimeZone.getDefault()
        this.setDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS" + when (timeZone.rawOffset) {
                0 -> "Z"
                else -> "+" + (timeZone.rawOffset / 3600000).toString().padStart(
                    2,
                    '0'
                ) + ":" + ((timeZone.rawOffset % 3600000) / 60000).toString()
                    .padStart(2, '0')
            }
        )
    }

    return this
}