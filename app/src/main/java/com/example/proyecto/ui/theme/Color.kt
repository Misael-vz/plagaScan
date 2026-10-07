package com.example.proyecto.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de color de PlagaScan (tema claro y oscuro).
 *
 * - Colores FIJOS: iguales en ambos temas (marca, camara, herramientas Dev).
 * - Colores DEL TEMA: cambian segun el tema elegido por el usuario. Se leen
 *   con el mismo nombre de siempre (PlagaScanInk, PlagaScanSurface...), pero
 *   solo dentro de pantallas de Compose.
 * El semaforo mantiene su significado (verde = sano, amarillo = revisar,
 * rojo = plaga); en oscuro usa tonos mas brillantes para que se lean bien.
 */

// ---------------------------------------------------------------- fijos
val PlagaScanGreen = Color(0xFF2E7D32)       // botones principales, obturador
val PlagaScanGreenLight = Color(0xFF81C784)  // progreso de la camara
val PlagaScanGreenDark = Color(0xFF1B5E20)   // botones de herramientas Dev
val PlagaScanRed = Color(0xFFC62828)
val PlagaScanRedLight = Color(0xFFE57373)
val PlagaScanAmber = Color(0xFFF9A825)
val PlagaScanAmberLight = Color(0xFFFFD54F)
val PlagaScanSurfaceDark = Color(0xFF1B1B1B)
val PlagaScanErrorSurface = Color(0xFF3A3A3A)
val PlagaScanLightGray = Color(0xFFE0E0E0)

// ---------------------------------------------------------------- del tema
@Immutable
data class PaletaPlagaScan(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val gray: Color,
    val outline: Color,
    val surfaceVariant: Color,
    val greenText: Color,
    val greenContainer: Color,
    val amberText: Color,
    val amberContainer: Color,
    val redText: Color,
    val redContainer: Color,
)

val PaletaClara = PaletaPlagaScan(
    background = Color(0xFFF5F7F4),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1B2A1F),
    gray = Color(0xFF6F7A71),
    outline = Color(0xFFDDE4DA),
    surfaceVariant = Color(0xFFEDF1EB),
    greenText = Color(0xFF2E7D32),
    greenContainer = Color(0xFFDDF0DE),
    amberText = Color(0xFF9A5B00),
    amberContainer = Color(0xFFFFF1D2),
    redText = Color(0xFFC62828),
    redContainer = Color(0xFFFCE4E2),
)

val PaletaOscura = PaletaPlagaScan(
    background = Color(0xFF101512),
    surface = Color(0xFF1A211C),
    ink = Color(0xFFE4EAE4),
    gray = Color(0xFF9AA59C),
    outline = Color(0xFF2C362F),
    surfaceVariant = Color(0xFF232C25),
    greenText = Color(0xFF7FD68A),
    greenContainer = Color(0xFF1F3A24),
    amberText = Color(0xFFF2B84B),
    amberContainer = Color(0xFF3A2E12),
    redText = Color(0xFFFF8A80),
    redContainer = Color(0xFF3D1E1C),
)

val LocalPaleta = staticCompositionLocalOf { PaletaClara }

val PlagaScanBackground: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.background
val PlagaScanSurface: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.surface
val PlagaScanInk: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.ink
val PlagaScanGray: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.gray
val PlagaScanOutline: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.outline
val PlagaScanSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.surfaceVariant
val PlagaScanGreenText: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.greenText
val PlagaScanGreenContainer: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.greenContainer
val PlagaScanAmberDark: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.amberText
val PlagaScanAmberContainer: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.amberContainer
val PlagaScanRedText: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.redText
val PlagaScanRedContainer: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.redContainer
