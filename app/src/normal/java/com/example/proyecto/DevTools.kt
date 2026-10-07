package com.example.proyecto

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder

/**
 * DevTools -- version del flavor "normal" (app real para el usuario final).
 *
 * A proposito NO expone ninguna pestaña ni pantalla de desarrollo/prueba
 * -- ni siquiera se compila el codigo de PruebaScreen/RecoleccionScreen
 * dentro de este flavor. Ver la version equivalente en
 * src/recolector/java/.../DevTools.kt para el flavor de desarrollo.
 */
object DevTools {
    val rutasExtra: List<String> = emptyList()
    val rutaInicio: String = Routes.CAMARA

    @Composable
    fun BotonesExtra(navigate: (String) -> Unit, currentRoute: String?) {
        // Intencionalmente vacio: sin pestañas de desarrollo en este flavor
    }

    fun registrarPantallas(builder: NavGraphBuilder, viewModel: MainViewModel) {
        // Intencionalmente vacio: sin pantallas de desarrollo en este flavor
    }
}
