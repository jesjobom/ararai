package com.jesjobom.ararai.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun formatMessageHeader(
    sender: String,
    createdAtMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val timestamp = formatMessageTimestamp(createdAtMillis, timeZone) ?: return sender
    return "$sender · $timestamp"
}

internal fun formatMessageTimestamp(
    createdAtMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String? {
    if (createdAtMillis <= 0L) return null
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).run {
        this.timeZone = timeZone
        format(Date(createdAtMillis))
    }
}
