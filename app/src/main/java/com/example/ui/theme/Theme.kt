package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val GeoDarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
  )

private val GeoLightColorScheme =
  lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
  )

private val GeoOutdoorHighContrastScheme =
  lightColorScheme(
    primary = OutdoorPrimary,
    onPrimary = OutdoorOnPrimary,
    primaryContainer = OutdoorPrimaryContainer,
    onPrimaryContainer = OutdoorOnPrimaryContainer,
    secondary = OutdoorSecondary,
    onSecondary = OutdoorOnSecondary,
    secondaryContainer = OutdoorSecondaryContainer,
    onSecondaryContainer = OutdoorOnSecondaryContainer,
    tertiary = OutdoorTertiary,
    onTertiary = OutdoorOnTertiary,
    tertiaryContainer = OutdoorTertiaryContainer,
    onTertiaryContainer = OutdoorOnTertiaryContainer,
    background = OutdoorBackground,
    onBackground = OutdoorOnBackground,
    surface = OutdoorSurface,
    onSurface = OutdoorOnSurface,
    surfaceVariant = OutdoorSurfaceVariant,
    onSurfaceVariant = OutdoorOnSurfaceVariant,
    surfaceContainerLowest = OutdoorSurface,
    surfaceContainerLow = OutdoorSurfaceVariant,
    surfaceContainer = OutdoorSurfaceVariant,
    surfaceContainerHigh = OutdoorSurfaceVariant,
    surfaceContainerHighest = OutdoorSurfaceVariant,
    outline = OutdoorOutline,
    outlineVariant = OutdoorOutline,
    error = OutdoorError,
    onError = OutdoorOnError,
  )

val GeoNexoShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
  )

@Composable
fun GeoNexoTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  highContrastOutdoor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      highContrastOutdoor -> GeoOutdoorHighContrastScheme
      darkTheme -> GeoDarkColorScheme
      else -> GeoLightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = GeoNexoTypography,
    shapes = GeoNexoShapes,
    content = content,
  )
}
