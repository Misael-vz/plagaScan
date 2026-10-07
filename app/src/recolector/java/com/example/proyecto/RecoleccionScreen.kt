package com.example.proyecto

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import android.util.Size
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.proyecto.ui.theme.PlagaScanGreen
import com.example.proyecto.ui.theme.PlagaScanGreenDark
import com.example.proyecto.ui.theme.PlagaScanLightGray
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.proyecto.ui.theme.PlagaScanGreenLight
import androidx.camera.core.CameraControl
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

private enum class ModoRecoleccion { OTROS, FRIJOL, EXAMEN }

/**
 * RecoleccionScreen
 *
 * Pantalla dedicada a recolectar datos de campo con VERDAD CONFIRMADA
 * POR EL USUARIO, no por el modelo -- evita el riesgo de entrenar sobre
 * los propios errores del modelo. Tres modos:
 *
 *  - "Otros": el usuario confirma que la foto NO es frijol. Se guarda
 *    junto con lo que el modelo predijo, para comparar despues.
 *  - "Frijol": el usuario confirma que SI es su cultivo real, y ademas
 *    confirma si esa hoja especifica esta sana o tiene plaga.
 *  - "Examen": modo normal (igual que Modo camara), el modelo decide
 *    solo -- para probar el comportamiento real sin intervencion.
 *
 * Cada captura en "Otros"/"Frijol" queda registrada en
 * ground_truth_log.csv (almacenamiento privado de la app), comparando
 * la etiqueta confirmada contra la prediccion del modelo -- extraible
 * despues con adb para analizar aciertos y errores reales de campo.
 */
@Composable
fun RecoleccionScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var modo by remember { mutableStateOf(ModoRecoleccion.OTROS) }
    val groundTruthState by viewModel.groundTruthState.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selector de modo
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Button(
                onClick = { modo = ModoRecoleccion.OTROS; viewModel.cancelPendingGroundTruth() },
                colors = if (modo == ModoRecoleccion.OTROS)
                    ButtonDefaults.buttonColors(containerColor = PlagaScanGreenDark)
                else ButtonDefaults.buttonColors(containerColor = PlagaScanLightGray)
            ) { Text("Otros") }
            Spacer(modifier = Modifier.height(1.dp))
            Button(
                onClick = { modo = ModoRecoleccion.FRIJOL; viewModel.cancelPendingGroundTruth() },
                colors = if (modo == ModoRecoleccion.FRIJOL)
                    ButtonDefaults.buttonColors(containerColor = PlagaScanGreenDark)
                else ButtonDefaults.buttonColors(containerColor = PlagaScanLightGray)
            ) { Text("Frijol") }
            Spacer(modifier = Modifier.height(1.dp))
            Button(
                onClick = { modo = ModoRecoleccion.EXAMEN; viewModel.cancelPendingGroundTruth() },
                colors = if (modo == ModoRecoleccion.EXAMEN)
                    ButtonDefaults.buttonColors(containerColor = PlagaScanGreenDark)
                else ButtonDefaults.buttonColors(containerColor = PlagaScanLightGray)
            ) { Text("Examen") }
        }

        if (!hasPermission) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Se necesita permiso de camara para recolectar datos.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Conceder permiso")
                }
            }
            return
        }

        when (modo) {
            ModoRecoleccion.EXAMEN -> {
                // Reutiliza el flujo normal de secuencia de 3 fotos, igual
                // que Modo camara -- el modelo decide solo, sin intervencion.
                val uiState by viewModel.uiState.collectAsState()
                Column(modifier = Modifier.weight(1f, fill = true)) {
                    CameraScreen(
                        onSequenceComplete = { bitmaps ->
                            viewModel.analyzeSequence(bitmaps, notes = "recoleccion_examen")
                        },
                        onError = { },
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState.isLoading) {
                        Text("Analizando...")
                    } else if (uiState.classification != null) {
                        Text("Resultado: ${uiState.classification} (${uiState.riskLevel})")
                    } else if (uiState.errorMessage != null) {
                        Text("Error: ${uiState.errorMessage}")
                    }
                }
            }

            ModoRecoleccion.OTROS -> {
                CapturaSimple(
                    instruccion = "Toma una foto de una planta que sabes con certeza que NO es frijol",
                    onCaptured = { bitmap -> viewModel.captureGroundTruthOtros(bitmap) }
                )
                EstadoRecoleccion(groundTruthState)
            }

            ModoRecoleccion.FRIJOL -> {
                if (groundTruthState.pending != null) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Confirma el estado real de esta hoja de frijol:")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Button(onClick = { viewModel.confirmGroundTruthFrijol("sano") }) {
                                Text("Sana")
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Button(onClick = { viewModel.confirmGroundTruthFrijol("plaga") }) {
                                Text("Con plaga")
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Button(onClick = { viewModel.confirmGroundTruthFrijol("dudoso") }) {
                                Text("Caso dudoso")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.cancelPendingGroundTruth() }) {
                            Text("Cancelar")
                        }
                    }
                } else {
                    CapturaSimple(
                        instruccion = "Toma una foto de tu planta de frijol real (cualquier etapa)",
                        onCaptured = { bitmap -> viewModel.captureGroundTruthFrijolPendiente(bitmap) }
                    )
                    EstadoRecoleccion(groundTruthState)
                }
            }
        }
    }
}

@Composable
private fun EstadoRecoleccion(state: GroundTruthUiState) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (state.isProcessing) {
            Text("Procesando...")
        }
        state.lastMessage?.let {
            Text(it, fontSize = 13.sp)
        }
    }
}

/**
 * Preview de camara simple con un solo boton de captura (a diferencia
 * de CameraScreen, que maneja la secuencia guiada de 3 fotos) -- pensado
 * para recoleccion rapida de datos de campo, una foto a la vez.
 */
@Composable
private fun CapturaSimple(instruccion: String, onCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .build()
    }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomRatio = (zoomRatio * zoom).coerceIn(1f, 5f)
                        cameraControl?.setZoomRatio(zoomRatio)
                    }
                }
        )

        // Mismo marco-guia tipo mira de camara que en CameraScreen, para
        // que Recoleccion se sienta consistente con el modo normal.
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.4f)
        ) {
            val esquina = size.minDimension * 0.18f
            fun dibujarEsquinas(color: androidx.compose.ui.graphics.Color, grosor: Float) {
                drawLine(color, Offset(0f, esquina), Offset(0f, 0f), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(0f, 0f), Offset(esquina, 0f), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width - esquina, 0f), Offset(size.width, 0f), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, esquina), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(0f, size.height - esquina), Offset(0f, size.height), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(0f, size.height), Offset(esquina, size.height), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width - esquina, size.height), Offset(size.width, size.height), grosor, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - esquina), grosor, cap = StrokeCap.Round)
            }
            dibujarEsquinas(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), 6.dp.toPx())
            dibujarEsquinas(PlagaScanGreenLight, 4.dp.toPx())
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xCC000000))
                .padding(12.dp)
        ) {
            Text(text = instruccion, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (isCapturing) return@Button
                    isCapturing = true
                    capturePhoto(
                        imageCapture = imageCapture,
                        executor = ContextCompat.getMainExecutor(context),
                        onImageCaptured = { bitmap ->
                            isCapturing = false
                            onCaptured(bitmap)
                        },
                        onError = { isCapturing = false }
                    )
                },
                enabled = !isCapturing
            ) {
                Text(if (isCapturing) "Procesando..." else "Capturar")
            }
        }
    }
}
