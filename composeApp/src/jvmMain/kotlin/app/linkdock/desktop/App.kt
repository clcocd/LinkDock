package app.linkdock.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.linkdock.desktop.app.AppController
import app.linkdock.desktop.domain.ThemeMode
import app.linkdock.desktop.ui.MainScreen

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8C7FF),
    onPrimary = Color(0xFF271648),
    secondary = Color(0xFFCBB6F9),
    onSecondary = Color(0xFF2D1D4B),
    tertiary = Color(0xFFF0B7FF),
    onTertiary = Color(0xFF452257),
    background = Color(0xFF0F0B17),
    onBackground = Color(0xFFF1ECF9),
    surface = Color(0xFF15101E),
    onSurface = Color(0xFFF1ECF9),
    surfaceVariant = Color(0xFF241C31),
    onSurfaceVariant = Color(0xFFD0C4E0),
    outline = Color(0xFF8C819B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7452B6),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6F5A92),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9255B1),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F4FB),
    onBackground = Color(0xFF1D1923),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1923),
    surfaceVariant = Color(0xFFEDE6F7),
    onSurfaceVariant = Color(0xFF4D4459),
    outline = Color(0xFF80768A)
)

@Composable
fun App(
) {
    val controller = remember { AppController() }
    val uiState by controller.uiState.collectAsState()

    val systemDark = isSystemInDarkTheme()

    val useDarkTheme = when (uiState.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors
    ) {
        MainScreen(
            controller = controller,
        )
    }
}
