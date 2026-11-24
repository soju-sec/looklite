package app.looklite.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppleCoral = Color(0xFFFF2D55)
val AppleLabelPrimary = Color(0xFF1D1D1F)
val AppleLabelSecondary = Color(0xFF6E6E73)
val AppleLabelTertiary = Color(0xFF8E8E93)
val AppleGlassBg = Color(0xEEFFFFFF)
val AppleGlassBorder = Color(0x40FFFFFF)
val AppleGlassBorderSubtle = Color(0x1F000000)
val AppleCardBg = Color(0xF5FFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = AppleCoral,
    onPrimary = Color.White,
    background = Color(0xFFF2F2F7),
    surface = AppleCardBg,
    onSurface = AppleLabelPrimary,
    onSurfaceVariant = AppleLabelSecondary
)

@Composable
fun LookLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
