package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = OnGoldSecondaryDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = OnGoldContainerDark,
    tertiary = TerracottaTertiaryDark,
    onTertiary = OnTerracottaTertiaryDark,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = OnTerracottaContainerDark,
    background = SanctuaryBackgroundDark,
    onBackground = OnSanctuaryDark,
    surface = SanctuarySurfaceDark,
    onSurface = OnSanctuaryDark,
    surfaceVariant = SanctuarySurfaceVariantDark,
    onSurfaceVariant = OnSanctuaryVariantDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = OnEmeraldPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = GoldSecondaryLight,
    onSecondary = OnGoldSecondaryLight,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = OnGoldContainerLight,
    tertiary = TerracottaTertiaryLight,
    onTertiary = OnTerracottaTertiaryLight,
    tertiaryContainer = TerracottaContainerLight,
    onTertiaryContainer = OnTerracottaContainerLight,
    background = ParchmentBackgroundLight,
    onBackground = OnParchmentLight,
    surface = ParchmentSurfaceLight,
    onSurface = OnParchmentLight,
    surfaceVariant = ParchmentSurfaceVariantLight,
    onSurfaceVariant = OnParchmentVariantLight,
    outline = OutlineLight
)

val VaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun VaultJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content
    )
}
