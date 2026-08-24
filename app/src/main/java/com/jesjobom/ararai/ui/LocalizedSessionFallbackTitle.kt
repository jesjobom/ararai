package com.jesjobom.ararai.ui

import android.content.Context
import androidx.annotation.StringRes
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

internal fun localizedSessionFallbackTitle(
    context: Context,
    @StringRes titleResource: Int,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val locale = context.resources.configuration.locales[0]
    val formattedDate =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).run {
            this.timeZone = timeZone
            format(Date(nowMillis))
        }
    return context.getString(titleResource, formattedDate)
}
