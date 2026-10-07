package com.example.proyecto

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.proyecto.ui.theme.*
import java.util.concurrent.Executor

/**
 * CameraScreen -- secuencia de captura triple guiada (RNF02; prueba 1 de U3_P1):
 * 1) planta completa, 2) hoja, 3) detalle. El boton "Analizar" solo aparece al
 * completar las 3 capturas.
 *
 * Diseno (rediseno visual): la camara ocupa toda la pantalla; arriba y abajo
 * hay degradados suaves (no bloques negros) con pastillas translucidas, una
 * barra de progreso en 3 partes, miniaturas centradas de las fotos tomadas y
 * un boton circular de captura como el de la camara del telefono.
 *
 * Rendimiento: la captura se limita a 1280x960 (suficiente para el pipeline,
 * que normaliza a 224x224) -- sin esto, 3 fotos de 12MP en memoria ya
 * provocaron ANR en la prueba de estres documentada.
 */
@Composable
fun CameraScreen(
    onSequenceComplete: (List<Bitmap>) -> Unit,
    onError: (String) -> Unit,
    onCaptureStarted: () -> Unit = {},
    // RF01: si no es null, se muestra el boton "Galeria" junto al obturador
    onGalleryClick: (() -> Unit)? = null
) {
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

    val indicaciones = listOf(
        "Aléjate un poco y encuadra toda la planta",
        "Acércate y enfoca solo la hoja que quieres revisar",
        "Acércate aún más: busca manchas o insectos en la hoja"
    )
    val nombrePaso = listOf("Planta completa", "Hoja", "Detalle")

    // Control de la camara para el zoom con el gesto de pellizcar
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    // Linterna (luz continua) para fotos con poca luz; solo si el telefono tiene flash
    var tieneLinterna by remember { mutableStateOf(false) }
    var linternaEncendida by remember { mutableStateOf(false) }

    var capturedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    // Evita encolar varias capturas con toques rapidos (ANR documentado)
    var isCapturing by remember { mutableStateOf(false) }
    // Evita analizar dos veces la misma secuencia con un doble toque en "Analizar"
    var enviado by remember(capturedBitmaps) { mutableStateOf(false) }
    val step = capturedBitmaps.size // 0,1,2 mientras se captura; 3 = secuencia completa
    // RF06: cuadro informativo del cultivo seleccionado
    var mostrarCultivo by remember { mutableStateOf(false) }

    // La linterna se apaga sola al completar las 3 fotos y al salir de la camara
    LaunchedEffect(step) {
        if (step >= 3) cameraControl?.enableTorch(false)
    }
    DisposableEffect(Unit) {
        onDispose { cameraControl?.enableTorch(false) }
    }

    fun capturar() {
        if (isCapturing || step >= 3) return
        if (step == 0) onCaptureStarted()
        isCapturing = true
        capturePhoto(
            imageCapture = imageCapture,
            executor = ContextCompat.getMainExecutor(context),
            onImageCaptured = { bitmap ->
                capturedBitmaps = capturedBitmaps + bitmap
                isCapturing = false
            },
            onError = { msg ->
                isCapturing = false
                onError(msg)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        cameraControl = camera.cameraControl
                        tieneLinterna = camera.cameraInfo.hasFlashUnit()
                        camera.cameraInfo.torchState.observe(lifecycleOwner) { estado ->
                            linternaEncendida = estado == TorchState.ON
                        }
                    } catch (e: Exception) {
                        onError("No se pudo abrir la cámara. Cierra la app y vuelve a abrirla.")
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        // Rango 1x-5x: suficiente para acercarse a una hoja
                        zoomRatio = (zoomRatio * zoom).coerceIn(1f, 5f)
                        cameraControl?.setZoomRatio(zoomRatio)
                    }
                }
        )

        // Esquinas guia: se achican en cada paso (planta -> hoja -> detalle).
        // Solo orientan al usuario; no afectan el recorte real de OpenCV.
        if (step < 3) {
            val ancho = when (step) { 0 -> 0.86f; 1 -> 0.64f; else -> 0.44f }
            val alto = when (step) { 0 -> 0.50f; 1 -> 0.38f; else -> 0.28f }
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(ancho)
                    .fillMaxHeight(alto)
            ) {
                val esquina = size.minDimension * 0.16f
                fun esquinas(color: Color, grosor: Float) {
                    drawLine(color, Offset(0f, esquina), Offset(0f, 0f), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(0f, 0f), Offset(esquina, 0f), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(size.width - esquina, 0f), Offset(size.width, 0f), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(size.width, 0f), Offset(size.width, esquina), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(0f, size.height - esquina), Offset(0f, size.height), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(0f, size.height), Offset(esquina, size.height), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(size.width - esquina, size.height), Offset(size.width, size.height), grosor, cap = StrokeCap.Round)
                    drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - esquina), grosor, cap = StrokeCap.Round)
                }
                esquinas(Color.Black.copy(alpha = 0.30f), 6.dp.toPx())
                esquinas(Color.White, 3.5.dp.toPx())
            }
        }

        // ---------- Encabezado: degradado, pastillas, indicacion y progreso ----------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pastilla(if (step < 3) "Foto ${step + 1} de 3" else "3 de 3 fotos")
                Spacer(modifier = Modifier.weight(1f))
                PastillaCultivo(onClick = { mostrarCultivo = true })
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (step < 3) indicaciones[step] else "¡Listo! Ya tienes las 3 fotos",
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    i < step -> PlagaScanGreenLight
                                    i == step -> Color.White
                                    else -> Color.White.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }
        }

        // ---------- Controles inferiores: degradado, miniaturas y boton ----------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { i ->
                    val bmp = capturedBitmaps.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                1.5.dp,
                                if (bmp != null) Color.White else Color.White.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Foto ${i + 1}",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (step < 3) {
                // Obturador centrado. A la izquierda, "Galeria" (RF01) solo antes
                // de la primera foto; a la derecha, la linterna (si el telefono tiene flash).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (step == 0 && !isCapturing && onGalleryClick != null) {
                            BotonGaleria(onClick = onGalleryClick)
                        }
                    }
                    BotonObturador(procesando = isCapturing, onClick = { capturar() })
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (tieneLinterna) {
                            BotonLinterna(
                                encendida = linternaEncendida,
                                onClick = { cameraControl?.enableTorch(!linternaEncendida) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isCapturing) "Guardando foto…" else "Tomar foto: ${nombrePaso[step]}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { capturedBitmaps = emptyList() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Tomar de nuevo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            if (!enviado) {
                                enviado = true
                                onSequenceComplete(capturedBitmaps)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlagaScanGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Analizar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (mostrarCultivo) {
        DialogoCultivo(onCerrar = { mostrarCultivo = false })
    }
}

/**
 * RF06 / prueba 2: pastilla del cultivo. Se puede tocar para ver cual cultivo
 * esta seleccionado y por que. El alcance vigente (sinodales) es solo frijol.
 */
@Composable
private fun PastillaCultivo(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = "Frijol", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(2.dp))
        Icon(Icons.Outlined.ExpandMore, contentDescription = "Ver cultivo", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** RF06: explica el cultivo seleccionado sin ofrecer opciones que el modelo no conoce. */
@Composable
private fun DialogoCultivo(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = PlagaScanSurface,
        icon = {
            Icon(Icons.Outlined.Eco, contentDescription = null, tint = PlagaScanGreenText, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Cultivo seleccionado", style = MaterialTheme.typography.titleLarge, color = PlagaScanInk)
        },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlagaScanGreenContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PlagaScanGreenText, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Frijol", style = MaterialTheme.typography.titleMedium, color = PlagaScanGreenText)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "PlagaScan está especializado en hojas de frijol y en el pulgón amarillo. " +
                        "Por eso el cultivo ya viene seleccionado: así el análisis es más preciso.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PlagaScanInk
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Otros cultivos podrán agregarse en versiones futuras.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlagaScanGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text("Entendido", color = PlagaScanGreenText, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

/** Pastilla translucida para textos cortos sobre la camara. */
@Composable
private fun Pastilla(texto: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = texto, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** RF01: boton para elegir una foto ya tomada, con el mismo estilo translucido. */
@Composable
private fun BotonGaleria(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = "Elegir foto de la galería",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Galería", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Linterna: luz continua del telefono para tomar fotos con poca luz. */
@Composable
private fun BotonLinterna(encendida: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (encendida) Color.White else Color.White.copy(alpha = 0.2f))
                .border(1.5.dp, Color.White.copy(alpha = if (encendida) 1f else 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (encendida) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                contentDescription = if (encendida) "Apagar linterna" else "Encender linterna",
                tint = if (encendida) Color.Black else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Linterna", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Boton circular de captura, como el de la camara del telefono. */
@Composable
private fun BotonObturador(procesando: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = !procesando, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (procesando) PlagaScanGreen.copy(alpha = 0.6f) else PlagaScanGreen),
            contentAlignment = Alignment.Center
        ) {
            if (procesando) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

internal fun capturePhoto(
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                onImageCaptured(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                onError("No se pudo tomar la foto. Intenta de nuevo.")
            }
        }
    )
}

/**
 * Convierte el ImageProxy (JPEG en memoria) a Bitmap y corrige la rotacion
 * segun los metadatos de la captura, para que la foto no salga de lado al
 * pasar por el pipeline de OpenCV.
 */
internal fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotationDegrees = image.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    } else {
        rawBitmap
    }
}
