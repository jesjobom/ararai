package com.jesjobom.ararai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.jesjobom.ararai.chat.FileChatMediaRepository
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
class ChatImageImporterTest {
    @Test
    fun `streams and normalizes a valid image into app-owned storage`() {
        val mediaDir = temporaryDirectory()
        val source = jpeg(width = 80, height = 40)
        val importer = importer(mediaDir, source, normalizedDimension = 32)

        val imported = importer.import(TEST_URI)

        assertTrue(imported.file.isFile)
        assertEquals("source.png", imported.displayName)
        assertTrue(imported.file.length() > 0L)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imported.file.absolutePath, bounds)
        assertEquals(32, bounds.outWidth)
        assertEquals(16, bounds.outHeight)
        assertFalse(mediaDir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `applies quarter-turn EXIF orientation before normalization`() {
        val mediaDir = temporaryDirectory()
        val source = jpeg(width = 80, height = 40)
        val importer =
            importer(
                mediaDir = mediaDir,
                source = source,
                normalizedDimension = 32,
                orientation = ExifInterface.ORIENTATION_ROTATE_90,
            )

        val imported = importer.import(TEST_URI)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imported.file.absolutePath, bounds)
        assertEquals(16, bounds.outWidth)
        assertEquals(32, bounds.outHeight)
    }

    @Test
    fun `supports every transforming EXIF orientation`() {
        val source =
            Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE)
            }

        val transformingOrientations =
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL..ExifInterface.ORIENTATION_ROTATE_270

        transformingOrientations.forEach { orientation ->
            val transformed = source.applyExifOrientation(orientation)
            val swapsAxes = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
            assertEquals(if (swapsAxes) 3 else 2, transformed.width)
            assertEquals(if (swapsAxes) 2 else 3, transformed.height)
            assertTrue(transformed !== source)
            transformed.recycle()
        }
        assertTrue(source.applyExifOrientation(ExifInterface.ORIENTATION_NORMAL) === source)
        source.recycle()
    }

    @Test
    fun `rejects declared source size before opening the provider`() {
        val mediaDir = temporaryDirectory()
        var opened = false
        val importer =
            ChatImageImporter(
                mediaRepository = FileChatMediaRepository(mediaDir),
                openSource = {
                    opened = true
                    ByteArrayInputStream(byteArrayOf())
                },
                sourceDisplayName = { null },
                declaredSourceSize = { 5L },
                maxSourceBytes = 4L,
            )

        assertFailureContains("20 MB") { importer.import(TEST_URI) }

        assertFalse(opened)
        assertTrue(mediaDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `rejects streamed source that exceeds the byte limit and removes partial files`() {
        val mediaDir = temporaryDirectory()
        val importer = importer(mediaDir, byteArrayOf(1, 2, 3, 4, 5), maxSourceBytes = 4L)

        assertFailureContains("20 MB") { importer.import(TEST_URI) }

        assertTrue(mediaDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `rejects decoded dimensions above the configured limit`() {
        val mediaDir = temporaryDirectory()
        val importer =
            importer(
                mediaDir = mediaDir,
                source = jpeg(width = 20, height = 10),
                maxDecodedDimension = 16,
                normalizedDimension = 8,
            )

        assertFailureContains("8192 px") { importer.import(TEST_URI) }

        assertTrue(mediaDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `rejects malformed input and removes partial files`() {
        val mediaDir = temporaryDirectory()
        val importer =
            ChatImageImporter(
                mediaRepository = FileChatMediaRepository(mediaDir),
                openSource = { ByteArrayInputStream("not an image".encodeToByteArray()) },
                sourceDisplayName = { null },
                declaredSourceSize = { null },
                readBounds = { null },
            )

        assertFailureContains("decode") { importer.import(TEST_URI) }

        assertTrue(mediaDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `cleans up when the provider fails during streaming`() {
        val mediaDir = temporaryDirectory()
        val importer =
            ChatImageImporter(
                mediaRepository = FileChatMediaRepository(mediaDir),
                openSource = { FailingInputStream() },
                sourceDisplayName = { null },
                declaredSourceSize = { null },
            )

        assertFailureContains("provider failed") { importer.import(TEST_URI) }

        assertTrue(mediaDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `cleans up when import is cancelled during streaming`() {
        val mediaDir = temporaryDirectory()
        val importer =
            ChatImageImporter(
                mediaRepository = FileChatMediaRepository(mediaDir),
                openSource = { CancellingInputStream() },
                sourceDisplayName = { null },
                declaredSourceSize = { null },
            )

        assertFailureContains("cancelled") { importer.import(TEST_URI) }

        assertTrue(mediaDir.listFiles().orEmpty().isEmpty())
    }

    private fun importer(
        mediaDir: File,
        source: ByteArray,
        maxSourceBytes: Long = MAX_CHAT_IMAGE_SOURCE_BYTES,
        maxDecodedDimension: Int = MAX_CHAT_IMAGE_DECODED_DIMENSION,
        normalizedDimension: Int = NORMALIZED_CHAT_IMAGE_DIMENSION,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
    ): ChatImageImporter = ChatImageImporter(
        mediaRepository = FileChatMediaRepository(mediaDir),
        openSource = { ByteArrayInputStream(source) },
        sourceDisplayName = { "source.png" },
        declaredSourceSize = { source.size.toLong() },
        readOrientation = { orientation },
        maxSourceBytes = maxSourceBytes,
        maxDecodedDimension = maxDecodedDimension,
        normalizedDimension = normalizedDimension,
    )

    private fun temporaryDirectory(): File = createTempDirectory("chat-image-import-").toFile().apply { deleteOnExit() }

    private fun jpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun assertFailureContains(
        expected: String,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected import to fail")
        } catch (error: Throwable) {
            assertNotNull(error.message)
            assertTrue("Expected '${error.message}' to contain '$expected'", error.message!!.contains(expected, true))
        }
    }

    private class FailingInputStream : InputStream() {
        private var reads = 0

        override fun read(): Int {
            if (reads++ == 0) return 1
            throw IOException("provider failed")
        }
    }

    private class CancellingInputStream : InputStream() {
        override fun read(): Int = throw CancellationException("import cancelled")
    }

    private companion object {
        val TEST_URI: Uri = Uri.parse("content://ararai.test/image")
    }
}
