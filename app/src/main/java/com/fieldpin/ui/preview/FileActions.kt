package com.fieldpin.ui.preview

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Content URI for a file under app-private storage (see res/xml/file_paths.xml). */
fun fileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = fileUri(context, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Hands the file to whatever app can show it. Tells the person when there is none. */
fun openFileExternally(context: Context, file: File, mimeType: String) {
    val uri = fileUri(context, file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can open this file", Toast.LENGTH_LONG).show()
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ENGLISH, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0))
}
