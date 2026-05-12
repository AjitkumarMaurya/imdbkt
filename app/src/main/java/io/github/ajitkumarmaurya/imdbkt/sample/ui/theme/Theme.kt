package io.github.ajitkumarmaurya.imdbkt.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ImdbYellow = Color(0xFFF5C518)
private val ImdbBlack = Color(0xFF121212)
private val ImdbDarkGray = Color(0xFF1F1F1F)
private val ImdbGray = Color(0xFF2B2B2B)

private val DarkColorScheme = darkColorScheme(
    primary = ImdbYellow,
    onPrimary = ImdbBlack,
    secondary = ImdbYellow,
    onSecondary = ImdbBlack,
    background = ImdbBlack,
    surface = ImdbDarkGray,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = ImdbGray,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = ImdbYellow,
    secondary = ImdbYellow,
    onSecondary = Color.Black,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
)

@Composable
fun ImdbKtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
