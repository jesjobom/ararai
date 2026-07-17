package com.jesjobom.ararai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.jesjobom.ararai.chat.ChatMediaRepository
import java.io.File
import java.io.InputStream

internal data class ImportedChatImage(val file: File, val displayName: String?)
internal data class ChatImageBounds(val width: Int, val height: Int)

internal fun interface ChatImageImportService {
    fun import(uri: Uri): ImportedChatImage
}

internal class ChatImageImporter(
    private val mediaRepository: ChatMediaRepository,
    private val openSource: (Uri) -> InputStream?,
    private val sourceDisplayName: (Uri) -> String?,
    private val declaredSourceSize: (Uri) -> Long?,
    private val readBounds: (File) -> ChatImageBounds? = ::readChatImageBounds,
    private val readOrientation: (File) -> Int = ::readChatImageOrientation,
    private val maxSourceBytes: Long = MAX_CHAT_IMAGE_SOURCE_BYTES,
    private val maxDecodedDimension: Int = MAX_CHAT_IMAGE_DECODED_DIMENSION,
    private val normalizedDimension: Int = NORMALIZED_CHAT_IMAGE_DIMENSION,
) : ChatImageImportService {
    override fun import(uri: Uri): ImportedChatImage {
        require(maxSourceBytes > 0L)
        require(maxDecodedDimension > 0)
        require(normalizedDimension in 1..maxDecodedDimension)
        declaredSourceSize(uri)?.takeIf { it >= 0L }?.let { size ->
            require(size <= maxSourceBytes) { SOURCE_TOO_LARGE_ERROR }
        }
        val temporaryFile = mediaRepository.createDraftFile(".image-import-", ".tmp")
        val outputFile = mediaRepository.createDraftFile("image-", ".jpg")
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var normalized: Bitmap? = null
        try {
            val source = openSource(uri) ?: error("Unable to open selected image")
            source.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    input.copyToBounded(output, maxSourceBytes)
                }
            }

            val bounds = readBounds(temporaryFile)
                ?.takeIf { it.width > 0 && it.height > 0 }
                ?: error("Unable to decode selected image")
            require(bounds.width <= maxDecodedDimension && bounds.height <= maxDecodedDimension) {
                DECODED_IMAGE_TOO_LARGE_ERROR
            }
            decoded = BitmapFactory.decodeFile(
                temporaryFile.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateImageSampleSize(bounds.width, bounds.height, normalizedDimension)
                },
            ) ?: error("Unable to decode selected image")
            oriented = decoded.applyExifOrientation(readOrientation(temporaryFile))
            normalized = oriented.scaleImageToFit(normalizedDimension)
            outputFile.outputStream().buffered().use { output ->
                check(normalized.compress(Bitmap.CompressFormat.JPEG, CHAT_IMAGE_JPEG_QUALITY, output)) {
                    "Unable to store selected image"
                }
            }
            return ImportedChatImage(outputFile, sourceDisplayName(uri))
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            temporaryFile.delete()
            if (normalized != null && normalized !== oriented && normalized !== decoded) normalized.recycle()
            if (oriented != null && oriented !== decoded) oriented.recycle()
            decoded?.recycle()
        }
    }
}

internal fun Context.chatImageImporter(mediaRepository: ChatMediaRepository): ChatImageImporter = ChatImageImporter(
    mediaRepository = mediaRepository,
    openSource = { uri -> contentResolver.openInputStream(uri) },
    sourceDisplayName = { uri -> uri.displayName(this) },
    declaredSourceSize = { uri -> uri.size(this) },
)

private fun InputStream.copyToBounded(output: java.io.OutputStream, maxBytes: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) return
        copied += read
        require(copied <= maxBytes) { SOURCE_TOO_LARGE_ERROR }
        output.write(buffer, 0, read)
    }
}

private fun Bitmap.scaleImageToFit(maxSize: Int): Bitmap {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSize) return this
    val scale = maxSize.toFloat() / largestSide.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

internal fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setValues(
            floatArrayOf(-1f, 0f, width.toFloat(), 0f, 1f, 0f, 0f, 0f, 1f),
        )
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setValues(
            floatArrayOf(-1f, 0f, width.toFloat(), 0f, -1f, height.toFloat(), 0f, 0f, 1f),
        )
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setValues(
            floatArrayOf(1f, 0f, 0f, 0f, -1f, height.toFloat(), 0f, 0f, 1f),
        )
        ExifInterface.ORIENTATION_TRANSPOSE -> matrix.setValues(
            floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
        )
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setValues(
            floatArrayOf(0f, -1f, height.toFloat(), 1f, 0f, 0f, 0f, 0f, 1f),
        )
        ExifInterface.ORIENTATION_TRANSVERSE -> matrix.setValues(
            floatArrayOf(0f, -1f, height.toFloat(), -1f, 0f, width.toFloat(), 0f, 0f, 1f),
        )
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setValues(
            floatArrayOf(0f, 1f, 0f, -1f, 0f, width.toFloat(), 0f, 0f, 1f),
        )
        else -> return this
    }
    val swapsAxes = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
    return Bitmap.createBitmap(
        if (swapsAxes) height else width,
        if (swapsAxes) width else height,
        config ?: Bitmap.Config.ARGB_8888,
    ).also { transformed ->
        Canvas(transformed).drawBitmap(this, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

internal fun calculateImageSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= maxSize || sampledHeight / 2 >= maxSize) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize
}

private fun readChatImageBounds(file: File): ChatImageBounds? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return ChatImageBounds(options.outWidth, options.outHeight)
}

private fun readChatImageOrientation(file: File): Int =
    ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )

private fun Uri.displayName(context: Context): String? =
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: lastPathSegment

private fun Uri.size(context: Context): Long? =
    context.contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }

internal const val MAX_CHAT_IMAGE_SOURCE_BYTES = 20L * 1024L * 1024L
internal const val MAX_CHAT_IMAGE_DECODED_DIMENSION = 8_192
internal const val NORMALIZED_CHAT_IMAGE_DIMENSION = 1_024
private const val CHAT_IMAGE_JPEG_QUALITY = 88
private const val SOURCE_TOO_LARGE_ERROR = "Selected image exceeds the 20 MB limit"
private const val DECODED_IMAGE_TOO_LARGE_ERROR = "Selected image dimensions exceed the 8192 px limit"
