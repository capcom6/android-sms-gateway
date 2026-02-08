package me.stappmus.messagegateway.helpers

import android.os.Build
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeParser {
    fun parseIsoDateTime(input: String): Date? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            parseModern(input)
        } else {
            parseLegacy(input)
        }
    }

    @Suppress("NewApi")
    private fun parseModern(input: String): Date? {
        return try {
            // Pattern handles both with/without milliseconds
            val formatter = java.time.format.DateTimeFormatter.ofPattern(
                "yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX"
            )
            val offsetDateTime = java.time.OffsetDateTime.parse(input, formatter)
            Date.from(offsetDateTime.toInstant())
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLegacy(input: String): Date? {
        // Try patterns in order of specificity
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // With milliseconds
            "yyyy-MM-dd'T'HH:mm:ssXXX"       // Without milliseconds
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return sdf.parse(input)
            } catch (e: ParseException) {
                // Try next pattern
            }
        }
        return null
    }
}