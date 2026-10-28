package com.fieldpin.data.repo

import android.content.Context
import java.io.File

/** App-private storage for inspection photos (not visible in the gallery, wiped on logout). */
class PhotoStorage(context: Context) {
    private val root = File(context.filesDir, "photos")

    fun newPhotoFile(inspectionId: String): File {
        val dir = File(root, inspectionId).apply { mkdirs() }
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    }

    fun deleteInspection(inspectionId: String) {
        File(root, inspectionId).deleteRecursively()
    }

    fun deleteAll() {
        root.deleteRecursively()
    }
}
