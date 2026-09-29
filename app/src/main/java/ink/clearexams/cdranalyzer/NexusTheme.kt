package ink.clearexams.cdranalyzer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusColors = darkColorScheme(
    primary = Color(0xFF33DDFF),
    onPrimary = Color(0xFF001419),
    primaryContainer = Color(0xFF0B3444),
    onPrimaryContainer = Color(0xFFBFF6FF),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF24184A),
    onSecondaryContainer = Color(0xFFE7DBFF),
    tertiary = Color(0xFF31F7A8),
    onTertiary = Color(0xFF002117),
    background = Color(0xFF050A12),
    onBackground = Color(0xFFE7F7FF),
    surface = Color(0xFF09121E),
    onSurface = Color(0xFFE7F7FF),
    surfaceVariant = Color(0xFF0A1422),
    onSurfaceVariant = Color(0xFF8FB3C4),
    outline = Color(0xFF27546B),
    outlineVariant = Color(0xFF17394A),
    error = Color(0xFFFF5D78),
    onError = Color.White
)

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NexusColors, content = content)
}
