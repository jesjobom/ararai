package com.jesjobom.ararai

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

class ChatImageTestProvider : ContentProvider() {
    private lateinit var imageFile: File

    override fun onCreate(): Boolean {
        val providerContext = context ?: return false
        imageFile = File(providerContext.cacheDir, "instrumentation-provider-image.png")
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return true
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> "provider-image.png"
                        OpenableColumns.SIZE -> imageFile.length()
                        else -> null
                    }
                },
            )
        }
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(imageFile, ParcelFileDescriptor.MODE_READ_ONLY)

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
