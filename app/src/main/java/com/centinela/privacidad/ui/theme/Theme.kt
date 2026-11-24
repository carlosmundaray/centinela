package com.centinela.privacidad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SlateBg = Color(0xFF0B1120)
val SlateSurface = Color(0xFF131C31)
val SlateCard = Color(0xFF1A2540)
val SlateBorder = Color(0xFF283556)
val EmeraldSafe = Color(0xFF10B981)
val EmeraldContainer = Color(0xFF064E3B)
val CrimsonAlert = Color(0xFFEF4444)
val CrimsonContainer = Color(0xFF7F1D1D)
val AmberMic = Color(0xFFF59E0B)
val AmberContainer = Color(0xFF78350F)
val CyanAccent = Color(0xFF38BDF8)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val CentinelaDarkScheme = darkColorScheme(
    primary = EmeraldSafe,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CyanAccent,
    onSecondary = Color(0xFF082F49),
    tertiary = AmberMic,
    background = SlateBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFECACA),
    outline = SlateBorder
)

@Composable
fun CentinelaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CentinelaDarkScheme,
        content = content
    )
}
