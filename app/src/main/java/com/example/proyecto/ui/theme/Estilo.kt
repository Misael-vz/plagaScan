package com.example.proyecto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Estilo unico de las tarjetas de PlagaScan (resultado e historial).
 *
 * Tema claro: fondo blanco con una sombra suave y un borde casi invisible,
 * en lugar del borde duro de antes. Tema oscuro: se conserva el borde,
 * porque sobre un fondo oscuro la sombra no se distingue.
 */
fun Modifier.tarjetaPlagaScan(paleta: PaletaPlagaScan, forma: Shape, elevacion: Dp = 6.dp): Modifier {
    val temaOscuro = paleta.background.luminance() < 0.5f
    return if (temaOscuro) {
        this
            .clip(forma)
            .background(paleta.surface)
            .border(1.dp, paleta.outline, forma)
    } else {
        this
            .shadow(
                elevation = elevacion,
                shape = forma,
                clip = false,
                ambientColor = paleta.ink.copy(alpha = 0.5f),
                spotColor = paleta.ink.copy(alpha = 0.5f)
            )
            .clip(forma)
            .background(paleta.surface)
            .border(1.dp, paleta.outline.copy(alpha = 0.6f), forma)
    }
}
