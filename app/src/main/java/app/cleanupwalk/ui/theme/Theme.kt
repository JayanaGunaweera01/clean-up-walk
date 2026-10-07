package app.cleanupwalk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Leaf = Color(0xFF2E7D32)
private val LeafLight = Color(0xFFA5D6A7)
private val Soil = Color(0xFF6D4C41)

private val LightColors = lightColorScheme(
    primary = Leaf,
    secondary = Soil,
    tertiary = Color(0xFF00796B)
)

private val DarkColors = darkColorScheme(
    primary = LeafLight,
    secondary = Color(0xFFBCAAA4),
    tertiary = Color(0xFF80CBC4)
)

@Composable
fun CleanupWalkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
