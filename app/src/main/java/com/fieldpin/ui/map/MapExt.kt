package com.fieldpin.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.fieldpin.domain.model.GeoPoint
import com.fieldpin.domain.model.SiteStatus
import com.fieldpin.ui.theme.Teal400
import com.fieldpin.ui.theme.color
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng

fun GeoPoint.toLatLng() = LatLng(lat, lng)

fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

/** A marker bitmap plus the vertical anchor (0..1) of its tip, so the tip sits on the site. */
class PinIcon(val descriptor: BitmapDescriptor, val anchorY: Float)

/**
 * Draws the map's bitmaps with Canvas: glossy, shaded 3D site pins (one per status, plus a larger
 * "selected" variant), the user's location dot and numbered corner handles. Results are cached, so
 * a thousand pins share three bitmaps. Must be created inside the map's content (the Maps SDK has
 * to be initialised before BitmapDescriptorFactory can be used).
 */
class PinIcons(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val pins = HashMap<String, PinIcon>()
    private val numbered = HashMap<String, BitmapDescriptor>()
    private var dot: BitmapDescriptor? = null

    fun pin(status: SiteStatus, selected: Boolean): PinIcon =
        pins.getOrPut("${status.name}/$selected") { renderPin(status.color().toArgb(), selected) }

    fun userDot(): BitmapDescriptor = dot ?: renderDot().also { dot = it }

    fun numbered(label: String, argb: Int): BitmapDescriptor =
        numbered.getOrPut("$label/$argb") { renderNumbered(label, argb) }

    // ------------------------------------------------------------ 3D pin

    private fun renderPin(argb: Int, selected: Boolean): PinIcon {
        val s = density * if (selected) 1.3f else 1f
        val w = (44 * s).toInt()
        val h = (58 * s).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cx = w / 2f
        val r = 15f * s // head radius
        val cy = r + 6f * s
        val tipY = cy + r * 1.95f

        // 1. soft ground shadow under the tip (a squashed radial gradient)
        paint.shader = RadialGradient(cx, tipY, r * 0.9f, Color.argb(140, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.save()
        c.scale(1f, 0.28f, cx, tipY)
        c.drawCircle(cx, tipY, r * 0.9f, paint)
        c.restore()

        // 2. glow behind the selected pin
        if (selected) {
            paint.shader = RadialGradient(cx, cy, r * 1.8f, ColorUtils.setAlphaComponent(argb, 150), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 1.8f, paint)
        }
        paint.shader = null

        // 3. teardrop silhouette
        val body = Path().apply {
            moveTo(cx, tipY)
            cubicTo(cx - r * 0.35f, tipY - r * 0.9f, cx - r, cy + r * 0.55f, cx - r, cy)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f)
            cubicTo(cx + r, cy + r * 0.55f, cx + r * 0.35f, tipY - r * 0.9f, cx, tipY)
            close()
        }
        val light = ColorUtils.blendARGB(argb, Color.WHITE, 0.35f)
        val dark = ColorUtils.blendARGB(argb, Color.BLACK, 0.4f)

        paint.shader = LinearGradient(cx, cy - r, cx, tipY, light, dark, Shader.TileMode.CLAMP)
        c.drawPath(body, paint)

        c.save()
        c.clipPath(body)
        // roundness: light edge on the left, shade on the right
        paint.shader = LinearGradient(
            cx - r, 0f, cx + r, 0f,
            intArrayOf(Color.argb(45, 255, 255, 255), Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(cx - r, cy - r, cx + r, tipY, paint)
        // glossy specular highlight
        paint.shader = RadialGradient(cx - r * 0.38f, cy - r * 0.5f, r * 0.9f, Color.argb(200, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(cx - r, cy - r, cx + r, tipY, paint)
        c.restore()
        paint.shader = null

        // 4. rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (selected) 2.6f else 1.2f) * s
        paint.color = if (selected) Color.WHITE else ColorUtils.setAlphaComponent(dark, 230)
        c.drawPath(body, paint)
        paint.style = Paint.Style.FILL

        // 5. socket with a status-coloured core
        val hole = r * 0.44f
        paint.color = Color.argb(120, 0, 0, 0)
        c.drawCircle(cx, cy + r * 0.07f, hole + 0.6f * s, paint)
        paint.shader = RadialGradient(cx - hole * 0.3f, cy - hole * 0.3f, hole * 1.3f, Color.WHITE, Color.rgb(205, 220, 217), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, hole, paint)
        paint.shader = null
        paint.color = argb
        c.drawCircle(cx, cy, hole * 0.46f, paint)

        return PinIcon(BitmapDescriptorFactory.fromBitmap(bmp), tipY / h)
    }

    // ---------------------------------------------------------- user dot

    private fun renderDot(): BitmapDescriptor {
        val size = (30 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = size / 2f
        paint.shader = RadialGradient(m, m, m, Color.argb(90, 45, 212, 191), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(m, m, m, paint)
        paint.shader = null
        paint.color = Color.argb(70, 0, 0, 0)
        c.drawCircle(m, m + 1.2f * density, 10.5f * density, paint)
        paint.color = Color.WHITE
        c.drawCircle(m, m, 10.5f * density, paint)
        paint.shader = RadialGradient(m - 2f * density, m - 2f * density, 9f * density, Color.rgb(94, 234, 212), Teal400.toArgb(), Shader.TileMode.CLAMP)
        c.drawCircle(m, m, 7.5f * density, paint)
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    // -------------------------------------------------- numbered handles

    private fun renderNumbered(label: String, argb: Int): BitmapDescriptor {
        val size = (30 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = size / 2f
        paint.color = Color.argb(80, 0, 0, 0)
        c.drawCircle(m, m + 1.2f * density, m - 1.5f * density, paint)
        paint.color = Color.WHITE
        c.drawCircle(m, m, m - 1.5f * density, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), ColorUtils.blendARGB(argb, Color.WHITE, 0.25f), argb, Shader.TileMode.CLAMP)
        c.drawCircle(m, m, m - 4f * density, paint)
        paint.shader = null
        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f * density
        c.drawText(label, m, m - (paint.descent() + paint.ascent()) / 2f, paint)
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }
}
