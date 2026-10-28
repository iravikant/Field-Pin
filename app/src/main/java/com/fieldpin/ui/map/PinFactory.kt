package com.fieldpin.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.cos

/**
 * Draws a glossy "3D" map pin as a bitmap: ground shadow, extruded side, lit/shaded body,
 * recessed socket with a glass sphere, and a specular highlight. Pure Canvas, no assets.
 */
object PinFactory {
    /** Pin tip as a fraction of the bitmap height (use as the marker's Y anchor). */
    const val ANCHOR_Y = 62f / 72f

    fun create(density: Float, argb: Int, selected: Boolean): Bitmap {
        val s = density * if (selected) 1.25f else 1f
        val w = 52f * s
        val h = 72f * s
        val bmp = Bitmap.createBitmap(w.toInt() + 1, h.toInt() + 1, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val r = 17f * s
        val cy = 24f * s
        val tipY = 62f * s

        // 1. ground shadow (flattened radial gradient)
        c.save()
        c.translate(cx, tipY)
        c.scale(1f, 0.33f)
        c.drawCircle(0f, 0f, 17f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 17f * s, intArrayOf(0xA0000000.toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
        })
        c.restore()

        val body = teardrop(cx, cy, r, tipY)

        // 2. extruded side: same silhouette, darker, nudged right/down
        val side = Path(body).apply { offset(2.2f * s, 1.6f * s) }
        c.drawPath(side, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.blendARGB(argb, Color.BLACK, 0.55f) })

        // 3. lit body: light top-left → dark bottom-right
        val light = ColorUtils.blendARGB(argb, Color.WHITE, 0.4f)
        val dark = ColorUtils.blendARGB(argb, Color.BLACK, 0.3f)
        c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - r, cy - r, cx + r, tipY, intArrayOf(light, argb, dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        })

        // 4. cylindrical shading on the right edge + gloss, clipped to the body
        c.save()
        c.clipPath(body)
        c.drawRect(cx - r, 0f, cx + r, h, Paint().apply {
            shader = LinearGradient(cx - r, 0f, cx + r, 0f, intArrayOf(0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        val gx = cx - 0.38f * r
        val gy = cy - 0.55f * r
        c.drawCircle(gx, gy, 0.7f * r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(gx, gy, 0.7f * r, intArrayOf(0xB0FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        })
        c.restore()

        // 5. outline
        c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (if (selected) 2.5f else 1.2f) * s
            color = if (selected) Color.WHITE else ColorUtils.blendARGB(argb, Color.BLACK, 0.45f)
        })

        // 6. recessed socket with glass sphere
        val socket = r * 0.6f
        c.drawCircle(cx, cy, socket, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, cy - socket, cx, cy + socket,
                ColorUtils.blendARGB(argb, Color.BLACK, 0.7f), ColorUtils.blendARGB(argb, Color.WHITE, 0.25f), Shader.TileMode.CLAMP)
        })
        val sphere = r * 0.47f
        val sx = cx - sphere * 0.3f
        val sy = cy - sphere * 0.3f
        c.drawCircle(cx, cy, sphere, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(sx, sy, sphere * 1.6f, intArrayOf(Color.WHITE, 0xFFE3EBE9.toInt(), 0xFF8FA3A0.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        // status dot inside the sphere
        c.drawCircle(cx, cy, sphere * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb })
        return bmp
    }

    /** Circle of radius [r] at ([cx],[cy]) joined by tangents to a point at ([cx],[tipY]). */
    private fun teardrop(cx: Float, cy: Float, r: Float, tipY: Float): Path {
        val d = tipY - cy
        val phi = acos((r / d).toDouble())
        val tx = (r * sin(phi)).toFloat()
        val ty = (r * cos(phi)).toFloat()
        val startDeg = Math.toDegrees(Math.atan2(ty.toDouble(), tx.toDouble())).toFloat()
        val sweep = -360f + 2f * Math.toDegrees(phi).toFloat()
        return Path().apply {
            moveTo(cx, tipY)
            lineTo(cx + tx, cy + ty)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), startDeg, sweep)
            close()
        }
    }
}
