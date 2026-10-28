package com.fieldpin.ui.theme

import androidx.compose.ui.graphics.Color
import com.fieldpin.domain.model.SiteStatus

// Brand palette: black, teal, green, white.
val Black = Color(0xFF000000)
val Ink = Color(0xFF070B0B)
val InkMid = Color(0xFF0E1514)
val InkHigh = Color(0xFF151E1D)
val InkTop = Color(0xFF1C2827)
val Line = Color(0xFF2A3A38)
val LineSoft = Color(0xFF1B2726)

val Teal300 = Color(0xFF5EEAD4)
val Teal400 = Color(0xFF2DD4BF)
val Teal600 = Color(0xFF0D9488)
val Teal700 = Color(0xFF0F766E)
val TealDeep = Color(0xFF0B3B36)
val Green400 = Color(0xFF4ADE80)
val Green600 = Color(0xFF16A34A)
val Green700 = Color(0xFF15803D)
val GreenDeep = Color(0xFF12361F)

val White = Color(0xFFFFFFFF)
val Snow = Color(0xFFF2F7F6)
val Mist = Color(0xFF9DB1AE)
val Slate = Color(0xFF52625F)

val ErrorRed = Color(0xFFFF6B6B)
val ErrorRedLight = Color(0xFFB3261E)

/** Fixed semantic colours of the site status (green = Active, amber = Needs repair, red = Closed). */
object StatusColors {
    val Active = Color(0xFF22C55E)
    val Repair = Color(0xFFF59E0B)
    val Closed = Color(0xFFEF4444)
}

fun SiteStatus.color(): Color = when (this) {
    SiteStatus.ACTIVE -> StatusColors.Active
    SiteStatus.NEEDS_REPAIR -> StatusColors.Repair
    SiteStatus.CLOSED -> StatusColors.Closed
}

fun com.fieldpin.domain.model.Condition.color(): Color = when (this) {
    com.fieldpin.domain.model.Condition.GOOD -> StatusColors.Active
    com.fieldpin.domain.model.Condition.FAIR -> StatusColors.Repair
    com.fieldpin.domain.model.Condition.POOR -> StatusColors.Closed
}
