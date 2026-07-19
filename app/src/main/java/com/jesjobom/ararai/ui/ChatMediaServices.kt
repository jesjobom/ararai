package com.jesjobom.ararai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.ChatMediaRepository
import java.io.File

internal data class ChatMediaServices(
    val imageImporter: ChatImageImportService,
    val audioRecorderFactory: ChatAudioRecorderFactory,
    val audioPlayerFactory: ChatAudioPlayerFactory,
    val imageDecoder: ChatImageDecoder,
    val draftCleaner: ChatDraftCleaner,
)

internal fun Context.androidChatMediaServices(mediaRepository: ChatMediaRepository): ChatMediaServices = ChatMediaServices(
    imageImporter = chatImageImporter(mediaRepository),
    audioRecorderFactory =
    ChatAudioRecorderFactory {
        ChatAudioRecorder(
            mediaRepository.createDraftFile("recording-${System.currentTimeMillis()}-", ".wav"),
        )
    },
    audioPlayerFactory = AndroidChatAudioPlayerFactory(this),
    imageDecoder = AndroidChatImageDecoder,
    draftCleaner = ChatDraftCleaner { uri -> mediaRepository.deleteDraft(uri, emptySet()) },
)

internal fun interface ChatDraftCleaner {
    fun delete(uri: String)
}

internal interface ChatAudioRecording {
    val file: File

    fun start()

    fun stopSafely(): Boolean
}

internal fun interface ChatAudioRecorderFactory {
    fun create(): ChatAudioRecording
}

internal interface ChatAudioPlayer {
    val isPlaying: Boolean

    fun start()

    fun pause()

    fun release()
}

internal fun interface ChatAudioPlayerFactory {
    fun create(
        audio: AudioPrompt,
        onCompletion: () -> Unit,
    ): ChatAudioPlayer
}

private class AndroidChatAudioPlayerFactory(
    private val context: Context,
) : ChatAudioPlayerFactory {
    override fun create(
        audio: AudioPrompt,
        onCompletion: () -> Unit,
    ): ChatAudioPlayer = AndroidChatAudioPlayer(context, audio, onCompletion)
}

private class AndroidChatAudioPlayer(
    context: Context,
    audio: AudioPrompt,
    onCompletion: () -> Unit,
) : ChatAudioPlayer {
    private val player = MediaPlayer()

    init {
        try {
            if (audio.uri.startsWith("content://")) {
                player.setDataSource(context, Uri.parse(audio.uri))
            } else {
                player.setDataSource(audio.uri)
            }
            player.setOnCompletionListener {
                it.seekTo(0)
                onCompletion()
            }
            player.prepare()
        } catch (error: Throwable) {
            player.release()
            throw error
        }
    }

    override val isPlaying: Boolean
        get() = player.isPlaying

    override fun start() = player.start()

    override fun pause() = player.pause()

    override fun release() = player.release()
}

internal fun interface ChatImageDecoder {
    fun decodeThumbnail(
        path: String,
        maxDimension: Int,
    ): Bitmap?
}

private object AndroidChatImageDecoder : ChatImageDecoder {
    override fun decodeThumbnail(
        path: String,
        maxDimension: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize =
            calculateImageSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxSize = maxDimension,
            )
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }
}
