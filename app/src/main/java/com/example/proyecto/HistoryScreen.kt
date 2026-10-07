package com.example.proyecto

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.proyecto.data.AnalysisResult
import com.example.proyecto.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HistoryScreen (RF04, RF08, RNF06; pruebas 5, 11 y 12 de U3_P1).
 *
 * Tarjetas blancas del mismo tamano: miniatura a la izquierda y, a la
 * derecha, una etiqueta de color con el resultado y renglones con icono
 * para fecha, ubicacion guardada (RF08, con las coordenadas como
 * comprobante) y fotos usadas. Borrar pide confirmacion. Miniaturas
 * reducidas y cargadas fuera del hilo principal.
 */
@Composable
fun HistoryScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val history by viewModel.history.collectAsState()
    var porBorrar by remember { mutableStateOf<AnalysisResult?>(null) }
    var porEtiquetar by remember { mutableStateOf<AnalysisResult?>(null) }
    // Confirmacion para borrar todo el historial
    var borrarTodo by remember { mutableStateOf(false) }
    // Registro abierto para volver a ver su resultado completo (RF03, RF07)
    var porVer by remember { mutableStateOf<AnalysisResult?>(null) }
    // La lista conserva su posicion al regresar del detalle
    val estadoLista = rememberLazyListState()

    if (history.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(PlagaScanGreenContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = PlagaScanGreenText,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Todavía no hay análisis",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ve a Cámara y toma las 3 fotos de tu planta para empezar.",
                style = MaterialTheme.typography.bodyLarge,
                color = PlagaScanGray,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val viendo = porVer
    if (viendo != null) {
        DetalleHistorial(item = viendo, onCerrar = { porVer = null }, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = estadoLista,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Historial",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { borrarTodo = true }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            tint = PlagaScanGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Borrar todo",
                            style = MaterialTheme.typography.labelLarge,
                            color = PlagaScanGray
                        )
                    }
                }
                Text(
                    text = if (history.size == 1) "1 análisis guardado"
                           else "${history.size} análisis guardados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlagaScanGray
                )
                Text(
                    text = "Toca un análisis para volver a ver qué hacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlagaScanGray
                )
            }
        }
        items(history, key = { it.id }) { item ->
            HistoryItemCard(
                item = item,
                onDelete = { porBorrar = item },
                onEtiquetar = { porEtiquetar = item },
                onVer = { porVer = item }
            )
        }
    }

    porBorrar?.let { item ->
        AlertDialog(
            onDismissRequest = { porBorrar = null },
            containerColor = PlagaScanSurface,
            title = { Text("¿Borrar este análisis?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Se quitará del historial y no se podrá recuperar.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteResult(item)
                    porBorrar = null
                }) { Text("Borrar", color = PlagaScanRedText, style = MaterialTheme.typography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { porBorrar = null }) {
                    Text("Cancelar", color = PlagaScanGreenText, style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }

    if (borrarTodo) {
        AlertDialog(
            onDismissRequest = { borrarTodo = false },
            containerColor = PlagaScanSurface,
            title = { Text("¿Borrar todo el historial?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = if (history.size == 1) "Se borrará 1 análisis con su foto. No se podrá recuperar."
                           else "Se borrarán los ${history.size} análisis con sus fotos. No se podrán recuperar.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    borrarTodo = false
                }) { Text("Borrar todo", color = PlagaScanRedText, style = MaterialTheme.typography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { borrarTodo = false }) {
                    Text("Cancelar", color = PlagaScanGreenText, style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }

    porEtiquetar?.let { item ->
        DialogoEtiqueta(
            inicial = item.etiqueta.orEmpty(),
            onGuardar = { texto ->
                viewModel.guardarEtiqueta(item, texto)
                porEtiquetar = null
            },
            onCancelar = { porEtiquetar = null }
        )
    }
}

/**
 * RF08 / prueba 14: cuadro para escribir o editar la etiqueta de un registro
 * (por ejemplo, el sector de la parcela). Vacia = se quita la etiqueta.
 */
@Composable
private fun DialogoEtiqueta(inicial: String, onGuardar: (String) -> Unit, onCancelar: () -> Unit) {
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = PlagaScanSurface,
        title = {
            Text(
                text = if (inicial.isBlank()) "Agregar etiqueta" else "Editar etiqueta",
                style = MaterialTheme.typography.titleLarge,
                color = PlagaScanInk
            )
        },
        text = {
            Column {
                Text(
                    text = "Escribe dónde tomaste la foto o algo que quieras recordar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PlagaScanInk
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { if (it.length <= MAX_ETIQUETA) texto = it },
                    singleLine = true,
                    placeholder = { Text("Ej. Surco 3, parcela de atrás") },
                    supportingText = { Text("${texto.length}/$MAX_ETIQUETA · Déjala vacía para quitarla") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PlagaScanInk,
                        unfocusedTextColor = PlagaScanInk,
                        focusedBorderColor = PlagaScanGreenText,
                        unfocusedBorderColor = PlagaScanOutline,
                        cursorColor = PlagaScanGreenText,
                        focusedPlaceholderColor = PlagaScanGray,
                        unfocusedPlaceholderColor = PlagaScanGray,
                        focusedSupportingTextColor = PlagaScanGray,
                        unfocusedSupportingTextColor = PlagaScanGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(texto) }) {
                Text("Guardar", color = PlagaScanGreenText, style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar", color = PlagaScanGray, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

/**
 * RF03 / RF07: vuelve a mostrar el resultado completo de un analisis guardado
 * (foto, semaforo, que buscar y que hacer). Se abre al tocar un registro y se
 * cierra con el boton, con la X o con el boton Atras del telefono.
 */
@Composable
private fun DetalleHistorial(item: AnalysisResult, onCerrar: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onCerrar)
    val fecha = remember(item.timestamp) {
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val etiqueta = item.etiqueta
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Análisis guardado", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (etiqueta.isNullOrBlank()) fecha else "$fecha · $etiqueta",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlagaScanGray
                )
            }
            IconButton(onClick = onCerrar) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cerrar",
                    tint = PlagaScanGray
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaResultado(
            clasificacion = item.classification,
            nivel = item.riskLevel,
            confianza = item.confidence,
            imagePath = item.imagePath,
            detalleAbierto = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        BotonPrincipal(texto = "Volver al historial", onClick = onCerrar)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun HistoryItemCard(
    item: AnalysisResult,
    onDelete: () -> Unit,
    onEtiquetar: () -> Unit,
    onVer: () -> Unit
) {
    val color = colorSemaforo(item.riskLevel)
    val fondo = fondoSemaforo(item.riskLevel)
    val titulo = tituloResultado(item.classification, item.riskLevel)
    val confPct = confianzaEnPantalla(item.confidence)
    val fecha = remember(item.timestamp) {
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val nota = remember(item.notes) { notaAmigable(item.notes) }
    val miniatura by produceState<Bitmap?>(initialValue = null, item.imagePath) {
        value = withContext(Dispatchers.IO) { cargarMiniatura(item.imagePath, 200) }
    }
    val lat = item.latitude
    val lon = item.longitude

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tarjetaPlagaScan(LocalPaleta.current, RoundedCornerShape(20.dp), elevacion = 4.dp)
            .clickable(onClickLabel = "Ver qué hacer", onClick = onVer)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PlagaScanSurfaceVariant)
        ) {
            miniatura?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Foto del análisis",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
            // Pista visual: el registro se puede abrir para volver a ver que hacer
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fondo)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = titulo, style = MaterialTheme.typography.labelLarge, color = color)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${item.cropType} · Confianza $confPct %",
                style = MaterialTheme.typography.bodyMedium,
                color = PlagaScanInk
            )
            Spacer(modifier = Modifier.height(4.dp))
            LineaInfo(Icons.Outlined.Schedule, fecha)
            // RF08: la ubicacion aproximada queda a la vista como comprobante
            if (lat != null && lon != null) {
                LineaInfo(Icons.Outlined.LocationOn, "Ubicación guardada", "%.4f, %.4f".format(lat, lon))
            } else {
                LineaInfo(Icons.Outlined.LocationOff, "Sin ubicación")
            }
            nota?.let { LineaInfo(Icons.Outlined.PhotoLibrary, it) }
            // RF08 / prueba 14: etiqueta escrita por el usuario
            val etiqueta = item.etiqueta
            if (!etiqueta.isNullOrBlank()) {
                LineaInfo(Icons.Outlined.Sell, etiqueta)
            }
            TextButton(
                onClick = onEtiquetar,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = PlagaScanGreenText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (etiqueta.isNullOrBlank()) "Agregar etiqueta" else "Editar etiqueta",
                    style = MaterialTheme.typography.labelLarge,
                    color = PlagaScanGreenText
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Borrar análisis",
                tint = PlagaScanGray
            )
        }
    }
}

/** Renglon de informacion con icono pequeno, alineado a la izquierda. */
@Composable
private fun LineaInfo(icono: ImageVector, texto: String, detalle: String? = null) {
    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = PlagaScanGray,
            modifier = Modifier.padding(top = 1.dp).size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = texto, style = MaterialTheme.typography.bodySmall, color = PlagaScanInk)
            detalle?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = PlagaScanGray)
            }
        }
    }
}

private val REGEX_FOTOS = Regex("""(\d)/(\d) validas""")

/** Traduce la nota interna del registro a una frase simple (RNF06). */
private fun notaAmigable(notas: String?): String? {
    if (notas.isNullOrBlank()) return null
    if (notas.startsWith("test_")) return "Prueba con imagen de ejemplo"
    if (notas.startsWith("galeria")) {
        val f = REGEX_FOTOS.find(notas)
        return if (f == null) "Foto elegida de la galería"
               else "De la galería · se usaron ${f.groupValues[1]} de ${f.groupValues[2]} fotos"
    }
    val m = REGEX_FOTOS.find(notas) ?: return null
    return "Se usaron ${m.groupValues[1]} de ${m.groupValues[2]} fotos"
}
