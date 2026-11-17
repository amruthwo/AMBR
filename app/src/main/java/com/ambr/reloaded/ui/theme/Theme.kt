package com.ambr.reloaded.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ambr.reloaded.R

/** Colours sampled straight from the AMBR brain logo. */
object Ambr {
    val Magenta = Color(0xFFD52B84)
    val Pink = Color(0xFFE35CA8)
    val Berry = Color(0xFF9E1779)
    val Plum = Color(0xFF570859)
    val Grape = Color(0xFF7B1476)
    val Ink = Color(0xFF2D0344)
    val Night = Color(0xFF14021F)
    val Neon = Color(0xFFD65CFF)   // the glow around the play triangle
    val Glow = Color(0xFFFF8AD0)

    /** Palette used by the drifting glitch-pixel particles. */
    val Pixels = listOf(Magenta, Pink, Berry, Neon, Glow, Grape)
}

private val AmbrDark = darkColorScheme(
    primary = Color(0xFFFF7AB8),
    onPrimary = Color(0xFF5C0034),
    primaryContainer = Color(0xFFB8186E),
    onPrimaryContainer = Color(0xFFFFD9E6),
    inversePrimary = Ambr.Magenta,
    secondary = Color(0xFFE3B5FF),
    onSecondary = Color(0xFF3F0F5C),
    secondaryContainer = Color(0xFF5A2580),
    onSecondaryContainer = Color(0xFFF3DAFF),
    tertiary = Color(0xFFFFB1C8),
    onTertiary = Color(0xFF5E1130),
    tertiaryContainer = Color(0xFF7B1E9E),
    onTertiaryContainer = Color(0xFFF8D8FF),
    background = Ambr.Night,
    onBackground = Color(0xFFF6DDF0),
    surface = Ambr.Night,
    onSurface = Color(0xFFF6DDF0),
    surfaceVariant = Color(0xFF4A2A55),
    onSurfaceVariant = Color(0xFFDCC0DA),
    surfaceTint = Color(0xFFFF7AB8),
    inverseSurface = Color(0xFFF6DDF0),
    inverseOnSurface = Color(0xFF2E1A33),
    outline = Color(0xFFA3889F),
    outlineVariant = Color(0xFF54394F),
    scrim = Color.Black,
    surfaceBright = Color(0xFF41224F),
    surfaceDim = Ambr.Night,
    surfaceContainerLowest = Color(0xFF0D0115),
    surfaceContainerLow = Color(0xFF1D0629),
    surfaceContainer = Color(0xFF250A33),
    surfaceContainerHigh = Color(0xFF2F1240),
    surfaceContainerHighest = Color(0xFF3A1A4D),
)

private val Bricolage = FontFamily(
    Font(R.font.bricolage, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.bricolage, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.bricolage, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.bricolage, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.bricolage, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private fun TextStyle.display(weight: FontWeight) = copy(fontFamily = Bricolage, fontWeight = weight)

private val base = Typography()
private val AmbrType = Typography(
    displayLarge = base.displayLarge.display(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.display(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.display(FontWeight.Bold),
    headlineLarge = base.headlineLarge.display(FontWeight.Bold),
    headlineMedium = base.headlineMedium.display(FontWeight.Bold),
    headlineSmall = base.headlineSmall.display(FontWeight.SemiBold),
    titleLarge = base.titleLarge.display(FontWeight.SemiBold),
    titleMedium = base.titleMedium.display(FontWeight.SemiBold),
    titleSmall = base.titleSmall.display(FontWeight.Medium),
    labelLarge = base.labelLarge.display(FontWeight.SemiBold),
)

@Composable
fun AmbrTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = AmbrDark,
        typography = AmbrType,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
