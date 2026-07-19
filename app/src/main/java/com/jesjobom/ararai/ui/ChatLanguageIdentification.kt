package com.jesjobom.ararai.ui

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions

internal fun interface ChatLanguageIdentificationListener {
    fun onIdentified(languageTag: String?)
}

internal interface ChatLanguageIdentifier : AutoCloseable {
    fun identify(
        text: String,
        listener: ChatLanguageIdentificationListener,
    )

    override fun close()
}

internal class MlKitChatLanguageIdentifier : ChatLanguageIdentifier {
    private val client =
        LanguageIdentification.getClient(
            LanguageIdentificationOptions
                .Builder()
                .setConfidenceThreshold(MINIMUM_CONFIDENCE)
                .build(),
        )
    private var closed = false

    override fun identify(
        text: String,
        listener: ChatLanguageIdentificationListener,
    ) {
        if (closed) return
        client
            .identifyLanguage(text)
            .addOnSuccessListener { languageTag ->
                if (!closed) {
                    val detectedTag = languageTag.takeUnless { it == UNDETERMINED_LANGUAGE }
                    Log.d(LOG_TAG, "Language identification completed: ${detectedTag ?: "undetermined"}")
                    listener.onIdentified(detectedTag)
                }
            }.addOnFailureListener { error ->
                if (!closed) {
                    Log.w(LOG_TAG, "Language identification failed; using default TTS voice", error)
                    listener.onIdentified(null)
                }
            }
    }

    override fun close() {
        if (closed) return
        closed = true
        client.close()
    }

    private companion object {
        const val MINIMUM_CONFIDENCE = 0.5f
        const val UNDETERMINED_LANGUAGE = "und"
        const val LOG_TAG = "ArarAI.TTS"
    }
}
