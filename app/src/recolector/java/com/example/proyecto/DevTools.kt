package com.example.proyecto

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.proyecto.ui.theme.PlagaScanGreenDark
import com.example.proyecto.ui.theme.PlagaScanLightGray

/**
 * DevTools -- version del flavor "recolector" (herramientas de
 * desarrollo/validacion de campo). Expone Modo prueba y Recoleccion,
 * ausentes por completo en el flavor "normal" que usa el usuario final.
 */
object DevTools {
    val rutasExtra: List<String> = listOf(Routes.PRUEBA, Routes.RECOLECCION)
    val rutaInicio: String = Routes.PRUEBA

    @Composable
    fun BotonesExtra(navigate: (String) -> Unit, currentRoute: String?) {
        val activeColor = PlagaScanGreenDark
        val inactiveColor = PlagaScanLightGray

        Spacer(modifier = Modifier.height(1.dp))
        Button(
            onClick = { navigate(Routes.PRUEBA) },
            colors = if (currentRoute == Routes.PRUEBA)
                ButtonDefaults.buttonColors(containerColor = activeColor)
            else ButtonDefaults.buttonColors(containerColor = inactiveColor, contentColor = Color.Black)
        ) {
            Text("Modo prueba")
        }
        Spacer(modifier = Modifier.height(1.dp))
        Button(
            onClick = { navigate(Routes.RECOLECCION) },
            colors = if (currentRoute == Routes.RECOLECCION)
                ButtonDefaults.buttonColors(containerColor = activeColor)
            else ButtonDefaults.buttonColors(containerColor = inactiveColor, contentColor = Color.Black)
        ) {
            Text("Recoleccion")
        }
    }

    fun registrarPantallas(builder: NavGraphBuilder, viewModel: MainViewModel) {
        builder.composable(Routes.PRUEBA) { PruebaScreen(viewModel) }
        builder.composable(Routes.RECOLECCION) { RecoleccionScreen(viewModel) }
    }
}
