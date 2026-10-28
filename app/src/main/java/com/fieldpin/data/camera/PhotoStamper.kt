package com.fieldpin.data.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import com.fieldpin.core.Fmt
import com.fieldpin.domain.model.GeoPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class StampInfo(
    val siteName: String,
    val point: GeoPoint,
    val accuracyM: Float,
    val timeMs: Long,
    /** Extra line, e.g. when the position is the check-in position because GPS was unavailable. */
    val note: String? = null,
)

/**
 * Burns the evidence stamp into the JPEG that CameraX saved, so it is part of the pixels of the
 * stored file (not an overlay that only exists on the preview). Steps: decode (down-sampled if very
 * large) → apply EXIF rotation so the stamp is always upright → draw → re-encode over the file →
 * write GPS + timestamp EXIF.
 */
class PhotoStamper {

    fun stamp(file: File, info: StampInfo) {
        val rotated = decodeUpright(file) ?: error("Could not read the captured photo")
        val canvasBitmap = if (rotated.isMutable) rotated else rotated.copy(Bitmap.Config.ARGB_8888, true).also { rotated.recycle() }
        try {
            drawStamp(Canvas(canvasBitmap), canvasBitmap.width, canvasBitmap.height, info)
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                check(canvasBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG encode failed" }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } finally {
            canvasBitmap.recycle()
        }
        writeExif(file, info)
    }

    private fun decodeUpright(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return decoded
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun drawStamp(canvas: Canvas, width: Int, height: Int, info: StampInfo) {
        val unit = min(width, height) / 38f // text scales with the photo
        val margin = unit * 0.9f
        val pad = unit * 0.8f
        val accentW = unit * 0.3f
        val accentInset = pad * 0.8f
        val textGap = pad * 0.7f
        val textLeftOffset = accentInset + accentW + textGap

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = unit * 1.15f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            typeface = Typeface.DEFAULT
            textSize = unit * 0.95f
        }

        val maxTextW = width - 2 * margin - textLeftOffset - pad
        val lines: List<Pair<String, Paint>> = buildList {
            add(info.siteName to title)
            add("${Fmt.coordinates(info.point)}  ${Fmt.accuracy(info.accuracyM)}" to body)
            add(Fmt.stampDateTime(info.timeMs) to body)
            info.note?.let { add(it to body) }
        }.map { (text, paint) -> ellipsize(text, paint, maxTextW) to paint }

        val lineGap = unit * 0.35f
        val textW = lines.maxOf { (t, p) -> p.measureText(t) }
        val textH = lines.sumOf { (_, p) -> p.fontSpacing.toDouble() }.toFloat() + lineGap * (lines.size - 1)
        val boxW = textLeftOffset + textW + pad
        val boxH = textH + 2 * pad
        val left = margin
        val bottom = height - margin
        val top = bottom - boxH

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(175, 0, 0, 0) }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 212, 191) } // brand teal
        val radius = unit * 0.6f
        canvas.drawRoundRect(RectF(left, top, left + boxW, bottom), radius, radius, bg)
        val barLeft = left + accentInset
        canvas.drawRoundRect(RectF(barLeft, top + pad, barLeft + accentW, bottom - pad), accentW / 2, accentW / 2, accent)

        var y = top + pad
        for ((text, paint) in lines) {
            y -= paint.ascent()
            canvas.drawText(text, left + textLeftOffset, y, paint)
            y += paint.descent() + lineGap
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun writeExif(file: File, info: StampInfo) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toExifDms(info.point.lat))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (info.point.lat >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toExifDms(info.point.lng))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (info.point.lng >= 0) "E" else "W")
            exif.setAttribute(ExifInterface.TAG_GPS_DOP, "${(info.accuracyM * 100).toInt()}/100")
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "FieldPin | ${info.siteName} | ${Fmt.accuracy(info.accuracyM)}")
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.saveAttributes()
        } // EXIF is a bonus; the stamp in the pixels is the evidence, so never fail the photo over it
    }

    /** EXIF stores coordinates as degrees/minutes/seconds rationals, e.g. "28/1,36/1,500400/10000". */
    private fun toExifDms(value: Double): String {
        val abs = Math.abs(value)
        val degrees = abs.toInt()
        val minutesFull = (abs - degrees) * 60
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60
        return "$degrees/1,$minutes/1,${(seconds * 10_000).toLong()}/10000"
    }

    private companion object {
        const val MAX_SIDE_PX = 2800
        const val JPEG_QUALITY = 90
    }
}
