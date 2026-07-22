package com.jesjobom.ararai.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.MessageContent
import java.util.Locale
import java.util.UUID

internal fun interface ChatTextToSpeechListener {
    fun onResult(result: ChatTextToSpeechResult)

    fun onRangeStart(
        start: Int,
        endExclusive: Int,
    ) = Unit
}

internal sealed interface ChatTextToSpeechResult {
    data object Completed : ChatTextToSpeechResult

    data class Failed(
        val message: String,
    ) : ChatTextToSpeechResult
}

internal interface ChatTextToSpeechService : AutoCloseable {
    fun speak(
        text: String,
        languageTag: String?,
        speechRate: Float = 1.0f,
        listener: ChatTextToSpeechListener,
    )

    fun stop()

    override fun close()
}

internal data class ChatTextToSpeechState(
    val activeMessageId: String? = null,
    val error: String? = null,
    val preparedLanguageTags: Map<String, String?> = emptyMap(),
)

internal class ChatTextToSpeechController(
    private val service: ChatTextToSpeechService,
    private val languageIdentifier: ChatLanguageIdentifier,
    private val onStateChanged: (ChatTextToSpeechState) -> Unit,
) : AutoCloseable {
    private var requestId = 0L
    private val preparationTexts = mutableMapOf<String, String>()
    var state = ChatTextToSpeechState()
        private set

    fun prepare(
        messageId: String,
        responseText: String,
    ) {
        if (preparationTexts[messageId] == responseText) return

        preparationTexts[messageId] = responseText
        updateState(state.copy(preparedLanguageTags = state.preparedLanguageTags - messageId))
        languageIdentifier.identify(responseText) { languageTag ->
            if (preparationTexts[messageId] != responseText) return@identify
            updateState(
                state.copy(
                    preparedLanguageTags = state.preparedLanguageTags + (messageId to languageTag),
                ),
            )
        }
    }

    fun isPrepared(messageId: String): Boolean = state.preparedLanguageTags.containsKey(messageId)

    fun toggle(
        messageId: String,
        responseText: String,
    ) {
        if (state.activeMessageId == messageId) {
            stop()
            return
        }
        if (!isPrepared(messageId)) return

        service.stop()
        val currentRequest = ++requestId
        updateState(state.copy(activeMessageId = messageId, error = null))
        service.speak(responseText, state.preparedLanguageTags[messageId]) { result ->
            if (currentRequest != requestId) return@speak
            when (result) {
                ChatTextToSpeechResult.Completed -> updateState(state.copy(activeMessageId = null, error = null))
                is ChatTextToSpeechResult.Failed ->
                    updateState(
                        state.copy(activeMessageId = null, error = result.message),
                    )
            }
        }
    }

    fun stop() {
        requestId++
        service.stop()
        updateState(state.copy(activeMessageId = null, error = null))
    }

    fun clearError() {
        if (state.error != null) updateState(state.copy(error = null))
    }

    override fun close() {
        requestId++
        preparationTexts.clear()
        languageIdentifier.close()
        service.close()
        updateState(ChatTextToSpeechState())
    }

    private fun updateState(newState: ChatTextToSpeechState) {
        state = newState
        onStateChanged(newState)
    }
}

internal fun ChatMessage.isEligibleForTextToSpeech(isStreaming: Boolean): Boolean = role == ChatRole.Assistant &&
    !isStreaming &&
    (content as? MessageContent.TextPrompt)?.text?.isNotBlank() == true

internal class AndroidChatTextToSpeechService(
    context: Context,
) : ChatTextToSpeechService {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var closed = false
    private var pending: PendingSpeech? = null
    private var activeUtteranceId: String? = null
    private var defaultVoice: Voice? = null

    override fun speak(
        text: String,
        languageTag: String?,
        speechRate: Float,
        listener: ChatTextToSpeechListener,
    ) {
        if (closed) {
            notify(listener, ChatTextToSpeechResult.Failed("Text-to-speech is no longer available"))
            return
        }
        stop()
        pending = PendingSpeech(text, languageTag, speechRate, listener)
        if (initialized) {
            speakPending()
        } else if (engine == null) {
            engine = TextToSpeech(appContext) { status -> onInitialized(status) }
        }
    }

    override fun stop() {
        pending = null
        activeUtteranceId = null
        engine?.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        pending = null
        activeUtteranceId = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        initialized = false
        defaultVoice = null
    }

    private fun onInitialized(status: Int) {
        if (closed) return
        if (status != TextToSpeech.SUCCESS) {
            failPending("Unable to initialize text-to-speech")
            engine?.shutdown()
            engine = null
            return
        }
        val currentEngine = engine ?: return
        if (currentEngine.voice == null) {
            failPending("The default text-to-speech language or voice is unavailable")
            currentEngine.shutdown()
            engine = null
            return
        }
        defaultVoice = currentEngine.voice
        initialized = true
        currentEngine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int,
                ) {
                    val speech = pending ?: return
                    if (utteranceId == activeUtteranceId && speech.utteranceId == utteranceId) {
                        mainHandler.post { speech.listener.onRangeStart(start, end) }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    finish(utteranceId, ChatTextToSpeechResult.Completed)
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    finish(utteranceId, ChatTextToSpeechResult.Failed("Unable to play this response"))
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    finish(utteranceId, ChatTextToSpeechResult.Failed("Unable to play this response"))
                }
            },
        )
        speakPending()
    }

    private fun speakPending() {
        val speech = pending ?: return
        pending = null
        if (!configureVoice(speech.languageTag)) {
            notify(speech.listener, ChatTextToSpeechResult.Failed("The default text-to-speech voice is unavailable"))
            return
        }
        if (engine?.setSpeechRate(speech.speechRate) != TextToSpeech.SUCCESS) {
            notify(speech.listener, ChatTextToSpeechResult.Failed("Unable to apply the selected speech rate"))
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        pending = speech.copy(utteranceId = utteranceId)
        val result = engine?.speak(speech.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            activeUtteranceId = null
            pending = null
            notify(speech.listener, ChatTextToSpeechResult.Failed("Unable to start text-to-speech playback"))
            return
        }
    }

    private fun configureVoice(languageTag: String?): Boolean {
        val currentEngine = engine ?: return false
        if (languageTag != null) {
            val locale = Locale.forLanguageTag(languageTag)
            if (locale.language.isNotBlank()) {
                compatibleVoices(
                    voices = currentEngine.voices,
                    languageTag = languageTag,
                    currentVoice = currentEngine.voice,
                ).forEach { voice ->
                    if (
                        currentEngine.setVoice(voice) == TextToSpeech.SUCCESS &&
                        currentEngine.voice
                            ?.locale
                            ?.language
                            .equals(locale.language, ignoreCase = true)
                    ) {
                        Log.d(LOG_TAG, "Selected TTS voice ${voice.name} (${voice.locale.toLanguageTag()}) for $languageTag")
                        return true
                    }
                }

                if (
                    currentEngine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE &&
                    currentEngine.voice
                        ?.locale
                        ?.language
                        .equals(locale.language, ignoreCase = true)
                ) {
                    Log.d(LOG_TAG, "TTS engine selected ${currentEngine.voice?.name} for $languageTag")
                    return true
                }
                Log.w(LOG_TAG, "No installed TTS voice could be activated for $languageTag; using default")
            }
        } else {
            Log.d(LOG_TAG, "No detected language; using default TTS voice")
        }
        val voice = defaultVoice ?: return false
        return currentEngine.setVoice(voice) == TextToSpeech.SUCCESS
    }

    private fun finish(
        utteranceId: String?,
        result: ChatTextToSpeechResult,
    ) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        activeUtteranceId = null
        val listener = pending?.takeIf { it.utteranceId == utteranceId }?.listener
        pending = null
        listener?.let { notify(it, result) }
    }

    private fun failPending(message: String) {
        val listener = pending?.listener
        pending = null
        activeUtteranceId = null
        listener?.let { notify(it, ChatTextToSpeechResult.Failed(message)) }
    }

    private fun notify(
        listener: ChatTextToSpeechListener,
        result: ChatTextToSpeechResult,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onResult(result)
        } else {
            mainHandler.post { listener.onResult(result) }
        }
    }

    private data class PendingSpeech(
        val text: String,
        val languageTag: String?,
        val speechRate: Float,
        val listener: ChatTextToSpeechListener,
        val utteranceId: String? = null,
    )

    private companion object {
        const val LOG_TAG = "ArarAI.TTS"
    }
}

internal fun compatibleVoices(
    voices: Set<Voice>?,
    languageTag: String,
    currentVoice: Voice?,
): List<Voice> {
    val requestedLanguage = Locale.forLanguageTag(languageTag).language
    if (requestedLanguage.isBlank()) return emptyList()

    return voices
        .orEmpty()
        .asSequence()
        .filter { it.locale.language.equals(requestedLanguage, ignoreCase = true) }
        .filterNot { it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        .sortedWith(
            compareBy<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it == currentVoice }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
                .thenBy { it.locale.toLanguageTag() }
                .thenBy { it.name },
        ).toList()
}
