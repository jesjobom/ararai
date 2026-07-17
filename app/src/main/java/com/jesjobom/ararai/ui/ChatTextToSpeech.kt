package com.jesjobom.ararai.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.MessageContent
import java.util.UUID

internal fun interface ChatTextToSpeechListener {
    fun onResult(result: ChatTextToSpeechResult)
}

internal sealed interface ChatTextToSpeechResult {
    data object Completed : ChatTextToSpeechResult
    data class Failed(val message: String) : ChatTextToSpeechResult
}

internal interface ChatTextToSpeechService : AutoCloseable {
    fun speak(text: String, listener: ChatTextToSpeechListener)
    fun stop()
    override fun close()
}

internal data class ChatTextToSpeechState(
    val activeMessageId: String? = null,
    val error: String? = null,
)

internal class ChatTextToSpeechController(
    private val service: ChatTextToSpeechService,
    private val onStateChanged: (ChatTextToSpeechState) -> Unit,
) : AutoCloseable {
    private var requestId = 0L
    var state = ChatTextToSpeechState()
        private set

    fun toggle(messageId: String, responseText: String) {
        if (state.activeMessageId == messageId) {
            stop()
            return
        }

        service.stop()
        val currentRequest = ++requestId
        updateState(ChatTextToSpeechState(activeMessageId = messageId))
        service.speak(responseText) { result ->
            if (currentRequest != requestId) return@speak
            when (result) {
                ChatTextToSpeechResult.Completed -> updateState(ChatTextToSpeechState())
                is ChatTextToSpeechResult.Failed -> updateState(ChatTextToSpeechState(error = result.message))
            }
        }
    }

    fun stop() {
        requestId++
        service.stop()
        updateState(ChatTextToSpeechState())
    }

    fun clearError() {
        if (state.error != null) updateState(state.copy(error = null))
    }

    override fun close() {
        requestId++
        service.close()
        updateState(ChatTextToSpeechState())
    }

    private fun updateState(newState: ChatTextToSpeechState) {
        state = newState
        onStateChanged(newState)
    }
}

internal fun ChatMessage.isEligibleForTextToSpeech(isStreaming: Boolean): Boolean =
    role == ChatRole.Assistant &&
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

    override fun speak(text: String, listener: ChatTextToSpeechListener) {
        if (closed) {
            notify(listener, ChatTextToSpeechResult.Failed("Text-to-speech is no longer available"))
            return
        }
        stop()
        pending = PendingSpeech(text, listener)
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
        initialized = true
        currentEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                finish(utteranceId, ChatTextToSpeechResult.Completed)
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                finish(utteranceId, ChatTextToSpeechResult.Failed("Unable to play this response"))
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finish(utteranceId, ChatTextToSpeechResult.Failed("Unable to play this response"))
            }
        })
        speakPending()
    }

    private fun speakPending() {
        val speech = pending ?: return
        pending = null
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId
        val result = engine?.speak(speech.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            activeUtteranceId = null
            notify(speech.listener, ChatTextToSpeechResult.Failed("Unable to start text-to-speech playback"))
            return
        }
        pending = speech.copy(utteranceId = utteranceId)
    }

    private fun finish(utteranceId: String?, result: ChatTextToSpeechResult) {
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

    private fun notify(listener: ChatTextToSpeechListener, result: ChatTextToSpeechResult) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onResult(result)
        } else {
            mainHandler.post { listener.onResult(result) }
        }
    }

    private data class PendingSpeech(
        val text: String,
        val listener: ChatTextToSpeechListener,
        val utteranceId: String? = null,
    )
}
