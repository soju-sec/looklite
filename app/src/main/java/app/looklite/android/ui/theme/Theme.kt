package app.looklite.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Apple Darkroom Studio Palette
val StudioCanvasBg = Color(0xFF0A0A0C)
val StudioWorkbenchBg = Color(0xFF141416)
val StudioElevatedBg = Color(0xFF1C1C1E)
val StudioSecondaryFill = Color(0xFF2C2C2E)
val StudioHairlineBorder = Color(0x18FFFFFF)
val StudioGlassOverlay = Color(0xCC1C1C1E)
val StudioGlassBorder = Color(0x26FFFFFF)
val StudioTextPrimary = Color(0xFFF5F5F7)
val StudioTextSecondary = Color(0xFF8E8E93)
val StudioAccent = Color(0xFFFF375F)

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
