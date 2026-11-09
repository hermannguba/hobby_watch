package tv.hobby.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

val Ink = Color(0xFF101114)
val CardInk = Color(0xFF1C1E26)
val Paper = Color(0xFFF4F1EA)
val Muted = Color(0xFFB7B3AA)
val Gold = Color(0xFFF2C14E)
val Live = Color(0xFFE5484D)
val ScreenPadding = 48.dp

@Composable
fun HobbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Ink,
            background = Ink,
            onBackground = Paper,
            surface = CardInk,
            onSurface = Paper,
            surfaceVariant = CardInk,
            onSurfaceVariant = Muted,
            border = Gold,
        ),
        typography = Typography(),
        content = content,
    )
}
