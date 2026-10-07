package com.example.proyecto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tema de PlagaScan: CLARO por defecto (se lee mejor bajo el sol, prueba 4
 * de U3_P1 / RNF06) y OSCURO opcional, elegido por el usuario desde la barra
 * inferior. Ambos mantienen el verde de marca y el semaforo.
 */
private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF0DE),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF1B5E20),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF0DE),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFFF9A825),
    background = Color(0xFFF5F7F4),
    onBackground = Color(0xFF1B2A1F),
    surface = Color.White,
    onSurface = Color(0xFF1B2A1F),
    surfaceVariant = Color(0xFFEDF1EB),
    onSurfaceVariant = Color(0xFF6F7A71),
    outline = Color(0xFFDDE4DA),
    outlineVariant = Color(0xFFDDE4DA),
    error = Color(0xFFC62828),
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF7FD68A),
    onPrimary = Color(0xFF0B2410),
    primaryContainer = Color(0xFF1F3A24),
    onPrimaryContainer = Color(0xFFBDEBC2),
    secondary = Color(0xFF7FD68A),
    onSecondary = Color(0xFF0B2410),
    secondaryContainer = Color(0xFF1F3A24),
    onSecondaryContainer = Color(0xFFBDEBC2),
    tertiary = Color(0xFFF2B84B),
    background = Color(0xFF101512),
    onBackground = Color(0xFFE4EAE4),
    surface = Color(0xFF1A211C),
    onSurface = Color(0xFFE4EAE4),
    surfaceVariant = Color(0xFF232C25),
    onSurfaceVariant = Color(0xFF9AA59C),
    outline = Color(0xFF2C362F),
    outlineVariant = Color(0xFF2C362F),
    error = Color(0xFFFF8A80),
)

/** Esquinas uniformes en toda la app: 12 / 16 / 24 dp. */
private val PlagaScanShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ProyectoTheme(oscuro: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPaleta provides if (oscuro) PaletaOscura else PaletaClara) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography = Typography,
            shapes = PlagaScanShapes,
            content = content
        )
    }
}
