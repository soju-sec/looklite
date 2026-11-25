package app.looklite.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Darkroom Studio Palette
val StudioCanvasBg = Color(0xFF0A0A0C)
val StudioWorkbenchBg = Color(0xFF131316)
val StudioElevatedBg = Color(0xFF1C1C21)
val StudioHairlineBorder = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
val StudioGlassOverlay = Color(0xCC131316)
val StudioGlassBorder = Color(0x22FFFFFF)
val StudioTextPrimary = Color(0xFFF4F4F6)
val StudioTextSecondary = Color(0xFF8E8E98)
val StudioAccent = Color(0xFFFF3B5C)

private val DarkStudioColorScheme = darkColorScheme(
    primary = StudioAccent,
    onPrimary = Color.White,
    background = StudioCanvasBg,
    onBackground = StudioTextPrimary,
    surface = StudioWorkbenchBg,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioElevatedBg,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioHairlineBorder
)

@Composable
fun LookLiteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkStudioColorScheme,
        content = content
    )
}
