package me.capcom.smsgateway.helpers

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

object DateHelper {
    val ISO_DATE_TIME = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .optionalStart()
        .appendOffsetId()
        .optionalStart()
        .appendLiteral('[')
        .parseCaseSensitive()
        .appendZoneRegionId()
        .appendLiteral(']')
        .toFormatter()
}