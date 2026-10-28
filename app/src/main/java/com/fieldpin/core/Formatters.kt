package com.fieldpin.core

import com.fieldpin.domain.gis.GeoMath
import com.fieldpin.domain.model.GeoPoint
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Display formatting in one place so screens, stamps and exports stay consistent. */
object Fmt {
    private val intFormat: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.ENGLISH)

    fun area(sqm: Double): String = "${intFormat.format(sqm.roundToInt())} m²"

    fun areaWithAcres(sqm: Double): String =
        "${area(sqm)} (${String.format(Locale.ENGLISH, "%.2f", GeoMath.sqmToAcres(sqm))} acres)"

    fun acres(sqm: Double): String = String.format(Locale.ENGLISH, "%.2f acres", GeoMath.sqmToAcres(sqm))

    fun length(m: Double): String =
        if (m < 1000) "${intFormat.format(m.roundToInt())} m"
        else String.format(Locale.ENGLISH, "%.2f km", m / 1000)

    fun distance(m: Double): String =
        if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.ENGLISH, "%.1f km", m / 1000)

    fun accuracy(m: Float): String = "±${m.roundToInt()} m"

    fun date(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val sameYear = cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        return SimpleDateFormat(if (sameYear) "d MMM" else "d MMM yyyy", Locale.ENGLISH).format(Date(ms))
    }

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(ms))

    fun dateTime(ms: Long): String = "${date(ms)}, ${time(ms)}"

    /** Used for the burned-in photo stamp, e.g. "27 Oct 2026, 10:44:12 IST". */
    fun stampDateTime(ms: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm:ss z", Locale.ENGLISH).format(Date(ms))

    fun coordinates(p: GeoPoint): String {
        val ns = if (p.lat >= 0) "N" else "S"
        val ew = if (p.lng >= 0) "E" else "W"
        return String.format(Locale.ENGLISH, "%.4f° %s, %.4f° %s", abs(p.lat), ns, abs(p.lng), ew)
    }
}
