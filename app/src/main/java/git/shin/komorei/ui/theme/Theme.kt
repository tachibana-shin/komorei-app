package git.shin.komorei.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = AnimeRed,
    onPrimary = Color.White,
    primaryContainer = AnimeRedContainer,
    onPrimaryContainer = AnimeRedLight,
    secondary = NeonViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C1645),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = GoldRating,
    onTertiary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderDark,
    outlineVariant = Color(0xFF1E2638)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Cinema dark mode default
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}

