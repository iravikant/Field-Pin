package com.fieldpin.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

class DocumentImportException(message: String) : Exception(message)

/** App-private copies of attached files (not visible to other apps, wiped on logout). */
class DocumentStorage(private val context: Context) {
    private val root = File(context.filesDir, "documents")

    class Imported(val file: File, val name: String, val mimeType: String, val sizeBytes: Long)

    /** Copies the picked [uri] into private storage. Blocking: call from a background dispatcher. */
    fun import(siteId: String, uri: Uri): Imported {
        val resolver = context.contentResolver
        var name = "document"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) c.getString(n)?.takeIf { it.isNotBlank() }?.let { name = it }
                if (s >= 0 && !c.isNull(s)) declaredSize = c.getLong(s)
            }
        }
        name = sanitize(name)
        if (declaredSize > MAX_BYTES) throw DocumentImportException("$name is larger than ${MAX_BYTES / (1024 * 1024)} MB")

        val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: guessMime(name)
        val dir = File(root, siteId).apply { mkdirs() }
        val target = uniqueFile(dir, name)
        var copied = 0L
        try {
            val input = resolver.openInputStream(uri) ?: throw DocumentImportException("Could not read $name")
            input.use { src ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_BYTES) throw DocumentImportException("$name is larger than ${MAX_BYTES / (1024 * 1024)} MB")
                        out.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw if (e is DocumentImportException) e else DocumentImportException("Could not attach $name")
        }
        return Imported(target, target.name, mime, copied)
    }

    fun delete(path: String) {
        File(path).delete()
    }

    fun deleteAll() {
        root.deleteRecursively()
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().trimStart('.')
        return cleaned.ifBlank { "document" }.take(120)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
    }
}
