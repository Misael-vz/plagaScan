package com.example.proyecto

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * PruebaScreen -- solo existe en el flavor "recolector" (herramienta de
 * desarrollo, nunca compilada dentro del flavor "normal" que usa el
 * usuario final). Permite analizar las imagenes de referencia
 * (test_sano.jpg / test_plaga.jpg) sin necesidad de camara, util para
 * pruebas rapidas del pipeline de segmentacion + clasificacion.
 */
@Composable
internal fun PruebaScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = "PlagaScan - Prueba con imagenes de assets")
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            val bitmap = context.assets.open("test_sano.jpg").use {
                BitmapFactory.decodeStream(it)
            }
            viewModel.analyzeBitmap(bitmap, notes = "test_sano.jpg")
        }) { Text("Analizar test_sano.jpg") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val bitmap = context.assets.open("test_plaga.jpg").use {
                BitmapFactory.decodeStream(it)
            }
            viewModel.analyzeBitmap(bitmap, notes = "test_plaga.jpg")
        }) { Text("Analizar test_plaga.jpg") }
        Spacer(modifier = Modifier.height(16.dp))
        ResultAndHistory(uiState, history)
    }
}
