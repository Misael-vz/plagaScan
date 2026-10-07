package com.example.proyecto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.proyecto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ResultCard -- tarjeta de resultado (RF02, RF03, RF07; prueba 3 de U3_P1).
 *
 * Diseno centrado y simetrico, en dos niveles para no saturar al usuario
 * (RNF06):
 *  - Nivel 1 (siempre visible): foto analizada, icono del semaforo, nombre
 *    comun de lo encontrado, chip de confianza, un mensaje corto de accion
 *    y el aviso de que no es un diagnostico (RF07).
 *  - Nivel 2 (al tocar "Ver que hacer y sintomas"): descripcion visual
 *    (RF03) y recomendacion completa (RF07). Ese boton pulsa 3 veces al
 *    aparecer el resultado para invitar a tocarlo y despues se queda quieto.
 */
@Composable
fun ResultCard(uiState: ScanUiState, modifier: Modifier = Modifier) {
    when {
        uiState.isLoading -> {
            Row(
                modifier = modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PlagaScanGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analizando…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        uiState.errorMessage != null -> AvisoMensaje(texto = uiState.errorMessage, modifier = modifier)
        uiState.classification != null -> ResultadoAnalisis(uiState, modifier)
        else -> Text(
            text = "Sin analizar todavía",
            style = MaterialTheme.typography.bodyLarge,
            color = PlagaScanGray,
            modifier = modifier.padding(16.dp)
        )
    }
}

/** Aviso amable (errores, "no se vio bien la hoja", etc.). */
@Composable
internal fun AvisoMensaje(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlagaScanAmberContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = PlagaScanAmberDark,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = texto, style = MaterialTheme.typography.bodyLarge, color = PlagaScanInk)
    }
}

@Composable
private fun ResultadoAnalisis(uiState: ScanUiState, modifier: Modifier) {
    val clasificacion = uiState.classification ?: return
    TarjetaResultado(
        clasificacion = clasificacion,
        nivel = uiState.riskLevel,
        confianza = uiState.confidence ?: 0f,
        imagePath = uiState.imagePath,
        modifier = modifier
    )
}

/**
 * Tarjeta completa de un resultado. La usan la pantalla de resultado (analisis
 * recien hecho) y el Historial (volver a ver un analisis guardado; ahi entra
 * con el detalle ya abierto y sin el pulso del boton).
 */
@Composable
internal fun TarjetaResultado(
    clasificacion: String,
    nivel: String?,
    confianza: Float,
    imagePath: String?,
    modifier: Modifier = Modifier,
    detalleAbierto: Boolean = false
) {
    val color = colorSemaforo(nivel)
    val fondo = fondoSemaforo(nivel)
    val confPct = confianzaEnPantalla(confianza)

    // Foto reducida (no la resolucion completa), decodificada fuera del hilo principal
    val miniatura by produceState<Bitmap?>(initialValue = null, imagePath) {
        value = imagePath?.let { ruta ->
            withContext(Dispatchers.IO) { cargarMiniatura(ruta, 600) }
        }
    }

    // Cada resultado nuevo empieza con el detalle cerrado y con el pulso;
    // desde el Historial (detalleAbierto) empieza abierto y sin pulso
    var verDetalle by remember(imagePath, confianza) { mutableStateOf(detalleAbierto) }
    val flecha by animateFloatAsState(
        targetValue = if (verDetalle) 180f else 0f,
        label = "flechaDetalle"
    )
    val pulso = remember(imagePath, confianza) { Animatable(1f) }
    LaunchedEffect(imagePath, confianza) {
        if (!detalleAbierto) {
            repeat(3) {
                pulso.animateTo(1.04f, tween(durationMillis = 350))
                pulso.animateTo(1f, tween(durationMillis = 350))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .tarjetaPlagaScan(LocalPaleta.current, RoundedCornerShape(24.dp), elevacion = 8.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---------- Nivel 1: siempre visible ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(PlagaScanSurfaceVariant)
        ) {
            miniatura?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Foto analizada",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        // Icono del semaforo con un aro suave alrededor
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(fondo.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(fondo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconoSemaforo(nivel),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = tituloResultado(clasificacion, nivel),
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(fondo)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Confianza $confPct %",
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = mensajeCorto(clasificacion, nivel),
            style = MaterialTheme.typography.bodyLarge,
            color = PlagaScanInk,
            textAlign = TextAlign.Center
        )

        // Boton que invita a ver el detalle (pulsa 3 veces y se queda quieto)
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pulso.value
                    scaleY = pulso.value
                }
                .clip(RoundedCornerShape(16.dp))
                .background(fondo)
                .clickable { verDetalle = !verDetalle }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (verDetalle) "Ocultar detalles" else "Ver qué hacer y síntomas",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = flecha }
            )
        }

        // ---------- Nivel 2: al tocar el boton ----------
        AnimatedVisibility(visible = verDetalle) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                SeccionDetalle(
                    icono = Icons.Outlined.Visibility,
                    titulo = "Qué buscar en la hoja",
                    texto = descripcionVisual(clasificacion, nivel)
                )
                Spacer(modifier = Modifier.height(14.dp))
                GuiaVisual()
                Spacer(modifier = Modifier.height(14.dp))
                SeccionDetalle(
                    icono = Icons.Outlined.Lightbulb,
                    titulo = "Qué hacer",
                    texto = recomendacionAccion(nivel)
                )
            }
        }

        // RF07: aviso corto, siempre visible
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Es una alerta, no un diagnóstico.",
            style = MaterialTheme.typography.bodySmall,
            color = PlagaScanGray,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Guia visual de referencia (RF03; protocolo, Anexo 1): dos fotos reales de
 * pulgon amarillo para que el productor compare con su propia hoja. Ambas con
 * licencia CC BY 4.0 de iNaturalist; el credito se muestra debajo de cada foto.
 */
@Composable
private fun GuiaVisual() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(PlagaScanSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = PlagaScanGreenText, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Así se ve el pulgón amarillo", style = MaterialTheme.typography.titleMedium, color = PlagaScanInk)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Compáralo con tu hoja.", style = MaterialTheme.typography.bodyMedium, color = PlagaScanInk)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FotoGuia(
                recurso = R.drawable.guia_pulgon_hoja,
                pie = "En el envés de la hoja",
                credito = "Foto: Alex Abair · CC BY 4.0 · iNaturalist (recortada)",
                modifier = Modifier.weight(1f)
            )
            FotoGuia(
                recurso = R.drawable.guia_pulgon_tallo,
                pie = "Insectos amarillos agrupados",
                credito = "Foto: Jorge Íñiguez · CC BY 4.0 · iNaturalist (recortada)",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FotoGuia(recurso: Int, pie: String, credito: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Image(
            painter = painterResource(id = recurso),
            contentDescription = pie,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(PlagaScanSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = pie, style = MaterialTheme.typography.labelLarge, color = PlagaScanInk)
        Text(text = credito, style = MaterialTheme.typography.labelSmall, color = PlagaScanGray)
    }
}

@Composable
private fun SeccionDetalle(icono: ImageVector, titulo: String, texto: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(PlagaScanSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, contentDescription = null, tint = PlagaScanGreenText, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, style = MaterialTheme.typography.titleMedium, color = PlagaScanInk)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = PlagaScanInk)
        }
    }
}

/** Color fuerte del semaforo (texto e iconos, legible sobre blanco). */
@Composable
internal fun colorSemaforo(nivel: String?): Color = when (nivel) {
    "VERDE" -> PlagaScanGreenText
    "AMARILLO" -> PlagaScanAmberDark
    "ROJO" -> PlagaScanRedText
    else -> PlagaScanGray
}

/** Color suave del semaforo (fondos de chips, iconos y botones). */
@Composable
internal fun fondoSemaforo(nivel: String?): Color = when (nivel) {
    "VERDE" -> PlagaScanGreenContainer
    "AMARILLO" -> PlagaScanAmberContainer
    "ROJO" -> PlagaScanRedContainer
    else -> PlagaScanSurfaceVariant
}

private fun iconoSemaforo(nivel: String?): ImageVector = when (nivel) {
    "VERDE" -> Icons.Outlined.CheckCircle
    "ROJO" -> Icons.Outlined.BugReport
    else -> Icons.Outlined.Search
}

/**
 * RF03: nombre comun de lo encontrado, tambien cuando el semaforo es AMARILLO.
 * La clase "sano" del modelo significa "sin pulgon amarillo": la app no revisa
 * otros danos (hojas secas, manchas, otras plagas), por eso no dice "Planta sana".
 */
internal fun tituloResultado(clasificacion: String, nivel: String?): String = when {
    clasificacion == "plaga" && nivel == "ROJO" -> "Pulgón amarillo detectado"
    clasificacion == "plaga" -> "Posible pulgón amarillo"
    clasificacion == "sano" && nivel == "VERDE" -> "Sin pulgón amarillo"
    clasificacion == "sano" -> "Probablemente sin pulgón"
    else -> "Resultado"
}

/** Nivel 1: una sola frase de accion, en lenguaje sencillo. */
private fun mensajeCorto(clasificacion: String, nivel: String?): String = when {
    nivel == "ROJO" -> "Revisa las hojas de alrededor y pide apoyo a un técnico agrícola."
    clasificacion == "plaga" -> "Toma las fotos otra vez con buena luz para estar más seguros."
    nivel == "VERDE" -> "La app solo revisa pulgón amarillo. Si ves manchas u otra cosa rara, revisa la hoja de cerca."
    else -> "La app no está segura. Revisa la hoja de cerca y repite las fotos con buena luz."
}

/**
 * RF03: descripcion visual. Contenido agronomico pendiente de citar con
 * fuente; en AMARILLO se redacta como posible.
 */
private fun descripcionVisual(clasificacion: String, nivel: String?): String = when {
    clasificacion == "plaga" && nivel == "ROJO" -> "Manchas amarillas irregulares en la " +
        "hoja, insectos diminutos (1-2 mm) de color amarillo agrupados, sobre todo " +
        "en el envés, y en casos avanzados hojas rizadas o deformes."
    clasificacion == "plaga" -> "Podrían ser manchas amarillas o insectos diminutos " +
        "agrupados. Voltea la hoja y revisa el envés de cerca."
    clasificacion == "sano" && nivel == "VERDE" -> "Una hoja sana es de color verde " +
        "parejo, sin manchas amarillas, sin deformaciones y sin insectos. " +
        "Compárala con la tuya, sobre todo por el envés."
    clasificacion == "sano" -> "La app no encontró señales claras de pulgón " +
        "amarillo, pero no está segura. Voltea la hoja y revisa el envés de cerca."
    else -> "Sin información para este resultado."
}

/** RF07: recomendacion completa segun el semaforo (no es diagnostico). */
private fun recomendacionAccion(nivel: String?): String = when (nivel) {
    "VERDE" -> "La app solo busca pulgón amarillo y puede no notar cosas " +
        "pequeñas. Si ves manchas, insectos u otra cosa rara, tómale una foto " +
        "de cerca o consulta a un técnico agrícola. Sigue revisando tu cultivo, " +
        "sobre todo el envés de las hojas nuevas."
    "AMARILLO" -> "Repite las 3 fotos con buena luz y más cerca de la hoja. " +
        "Si vuelve a salir amarillo, revisa otra vez en un par de días."
    "ROJO" -> "Revisa las hojas cercanas, sobre todo el envés. Consulta a un " +
        "técnico agrícola para confirmar y decidir el tratamiento. " +
        "La app no recomienda productos químicos."
    else -> "Sin recomendación para este resultado."
}

/**
 * Confianza como porcentaje entero para mostrar en pantalla. Nunca muestra
 * 100 %: el modelo puede equivocarse aunque su salida redondee a 100.
 * Solo afecta al texto; el valor guardado y el semaforo no cambian.
 */
internal fun confianzaEnPantalla(confianza: Float): String =
    "%.0f".format((confianza * 100f).coerceAtMost(99f))

/** Carga la foto reducida (inSampleSize) para no ocupar memoria de mas. */
internal fun cargarMiniatura(ruta: String, ladoMax: Int): Bitmap? = try {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(ruta, limites)
    var muestra = 1
    while (limites.outWidth / (muestra * 2) >= ladoMax &&
        limites.outHeight / (muestra * 2) >= ladoMax
    ) {
        muestra *= 2
    }
    BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra })
} catch (e: Exception) {
    null
}
