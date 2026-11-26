package com.jesjobom.ararai.ui

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatShareRequestTest {
    @Test
    fun `accepts plain text and preserves its formatting`() {
        val text = "  Shared text\nhttps://example.org"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

        assertEquals(text, (intent.toChatShareRequest() as ChatShareRequest.Text).value)
    }

    @Test
    fun `accepts a single content image`() {
        val uri = Uri.parse("content://example.provider/photo")
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)

        assertEquals(uri, (intent.toChatShareRequest() as ChatShareRequest.Image).uri)
    }

    @Test
    fun `rejects unsupported and incomplete shares`() {
        val file = Uri.parse("file:///tmp/photo.png")

        assertNull(Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").toChatShareRequest())
        assertNull(Intent(Intent.ACTION_SEND).setType("application/pdf").toChatShareRequest())
        val blankText = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, " ")
        assertNull(blankText.toChatShareRequest())
        assertNull(Intent(Intent.ACTION_SEND).setType("image/png").toChatShareRequest())
        val fileImage = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, file)
        assertNull(fileImage.toChatShareRequest())
    }
}
