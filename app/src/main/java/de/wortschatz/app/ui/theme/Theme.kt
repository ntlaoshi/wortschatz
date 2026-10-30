package de.wortschatz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 主色取自德语辞书的靛蓝烫金质感：沉稳蓝 + 暖金点缀
private val Indigo = Color(0xFF2C4A6E)
private val IndigoLight = Color(0xFFA8C7E8)
private val Gold = Color(0xFF8A6A2F)
private val PaperLight = Color(0xFFFBF9F6)
private val PaperDark = Color(0xFF12151A)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E4F5),
    onPrimaryContainer = Color(0xFF0A1D33),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E3C3),
    onSecondaryContainer = Color(0xFF2B1F05),
    tertiary = Color(0xFF4F6B4A),
    tertiaryContainer = Color(0xFFD6E8D1),
    onTertiaryContainer = Color(0xFF11240E),
    background = PaperLight,
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDE8E1),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFC9C2B6),
    outlineVariant = Color(0xFFE3DDD4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF0A1D33),
    primaryContainer = Color(0xFF1E3A57),
    onPrimaryContainer = Color(0xFFD8E4F5),
    secondary = Color(0xFFE0C48A),
    onSecondary = Color(0xFF2B1F05),
    secondaryContainer = Color(0xFF4A3812),
    onSecondaryContainer = Color(0xFFF5E3C3),
    tertiary = Color(0xFFA9CFA1),
    tertiaryContainer = Color(0xFF2A3D26),
    onTertiaryContainer = Color(0xFFD6E8D1),
    background = PaperDark,
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF1A1E24),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF5A5F66),
    outlineVariant = Color(0xFF3A3F46),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/** 语义化附加色，Material 色板之外的表达需求 */
object ExtraColors {
    val success = Color(0xFF3E7A4E)
    val successContainer = Color(0xFFD7EBDC)
    val warning = Color(0xFF9A6B00)
    val warningContainer = Color(0xFFFDEFC7)
    val levelA1 = Color(0xFF2E7D5B)
    val levelA2 = Color(0xFF2C5F8A)
    val levelB1 = Color(0xFF7A5A2E)
    val levelB2 = Color(0xFF6D3F6E)

    fun levelColor(level: String) = when (level.uppercase()) {
        "A1" -> levelA1
        "A2" -> levelA2
        "B1" -> levelB1
        "B2" -> levelB2
        else -> levelA1
    }
}

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 31.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WortschatzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
