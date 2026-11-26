package com.jesjobom.ararai.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

internal sealed interface ChatShareRequest {
    class Text(val value: String) : ChatShareRequest

    class Image(val uri: Uri) : ChatShareRequest
}

internal fun Intent.toChatShareRequest(): ChatShareRequest? {
    val mimeType = type
    if (action != Intent.ACTION_SEND || mimeType == null) return null
    return when {
        mimeType == "text/plain" -> getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()?.takeIf(String::isNotBlank)?.let(ChatShareRequest::Text)
        mimeType.startsWith("image/") -> {
            val uri = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            uri?.takeIf { it.scheme == "content" }?.let(ChatShareRequest::Image)
        }
        else -> null
    }
}
