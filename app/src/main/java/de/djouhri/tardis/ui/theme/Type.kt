package de.djouhri.tardis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.djouhri.tardis.R

/**
 * Inter fuer alle 15 Stile, lokal in `res/font` (OFL). Tabellenziffern, damit
 * Messwerte beim Aktualisieren nicht springen.
 */
private val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

private fun TextStyle.inter() = copy(fontFamily = Inter, fontFeatureSettings = "tnum")

private val basis = Typography()

val Typography = Typography(
    displayLarge = basis.displayLarge.inter(),
    displayMedium = basis.displayMedium.inter(),
    displaySmall = basis.displaySmall.inter(),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp).inter(),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp).inter(),
    headlineSmall = basis.headlineSmall.copy(fontWeight = FontWeight.SemiBold).inter(),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp).inter(),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp).inter(),
    titleSmall = basis.titleSmall.copy(letterSpacing = 0.sp).inter(),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp).inter(),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp).inter(),
    bodySmall = basis.bodySmall.copy(letterSpacing = 0.sp).inter(),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp).inter(),
    labelMedium = basis.labelMedium.inter(),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp).inter(),
)
