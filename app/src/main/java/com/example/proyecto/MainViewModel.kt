package com.example.proyecto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyecto.data.AnalysisRepository
import com.example.proyecto.data.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** RF08: largo maximo de la etiqueta que escribe el usuario en el historial. */
const val MAX_ETIQUETA = 40

// Estado de la pantalla de resultado, expuesto a la UI (Compose)
data class ScanUiState(
    val isLoading: Boolean = false,
    val classification: String? = null, // "plaga" | "sano"
    val confidence: Float? = null,
    val riskLevel: String? = null, // "VERDE" | "AMARILLO" | "ROJO"
    val errorMessage: String? = null,
    val imagePath: String? = null // foto analizada, para mostrarla en ResultCard
)

// Resultado individual de una foto dentro de la secuencia de 3, ya
// procesada por el segmentador + clasificador. Solo se generan para
// las fotos donde sí se detectó una hoja (segmentResult.found == true).
private data class SequenceCandidate(
    val originalBitmap: Bitmap,
    val label: String,
    val confidence: Float
)

/**
 * Estado de la pantalla de recoleccion de verdad confirmada por el
 * usuario (no por el modelo), para capturas de campo destinadas a
 * mejorar el dataset con evidencia real.
 */
data class GroundTruthUiState(
    val isProcessing: Boolean = false,
    val lastMessage: String? = null,
    val pending: PendingGroundTruthCapture? = null
)

/** Captura en modo "Frijol" esperando confirmacion de salud (sano/plaga). */
data class PendingGroundTruthCapture(
    val bitmap: Bitmap,
    val segmentadorDetecto: Boolean,
    val modeloPredijo: String,
    val modeloConfianza: Float
)

class MainViewModel(
    application: android.app.Application,
    private val repository: AnalysisRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // Historial completo, expuesto como StateFlow reactivo desde Room
    private val _history = MutableStateFlow<List<AnalysisResult>>(emptyList())
    val history: StateFlow<List<AnalysisResult>> = _history.asStateFlow()

    // Estado de la pantalla de recoleccion de verdad confirmada
    private val _groundTruthState = MutableStateFlow(GroundTruthUiState())
    val groundTruthState: StateFlow<GroundTruthUiState> = _groundTruthState.asStateFlow()

    private val leafSegmenter = LeafSegmenter()
    private val classifier = PlagaScanClassifier(application.applicationContext)
    private val locationHelper = LocationHelper(application.applicationContext)

    init {
        viewModelScope.launch {
            repository.allResults.collect { _history.value = it }
        }
    }

    /**
     * Corre el pipeline completo (OpenCV -> modelo) sobre un solo bitmap.
     * Se conserva para el Modo prueba (imagenes fijas de assets).
     */
    fun analyzeBitmap(bitmap: Bitmap, notes: String? = null) {
        // Evita analizar dos veces (doble toque): si ya hay un analisis en curso, se ignora
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val inicio = System.currentTimeMillis()
            _uiState.value = ScanUiState(isLoading = true)
            try {
                val quadResult = withContext(Dispatchers.Default) {
                    val segmentResult = leafSegmenter.segment(bitmap)
                    if (!segmentResult.found) {
                        return@withContext QuadResult(false, "", 0f, "")
                    }
                    val result = classifier.classify(segmentResult.bitmap)
                    if (!result.isFrijol) {
                        return@withContext QuadResult(
                            false, "", 0f, "",
                            "Esto no parece una hoja de frijol. Asegúrate de " +
                                "fotografiar tu planta de frijol."
                        )
                    }
                    QuadResult(true, result.label, result.confidence, result.semaforo)
                }
                val (found, label, confidence, semaforo) = quadResult

                if (!found) {
                    _uiState.value = ScanUiState(
                        isLoading = false,
                        errorMessage = quadResult.errorMessage
                    )
                    return@launch
                }

                val savedPath = withContext(Dispatchers.IO) {
                    saveImageToInternalStorage(bitmap)
                }

                // RNF01: el resultado se muestra sin esperar a la ubicacion
                _uiState.value = ScanUiState(
                    isLoading = false,
                    classification = label,
                    confidence = confidence,
                    riskLevel = semaforo,
                    imagePath = savedPath
                )
                Log.d("PlagaScan", "Tiempo de analisis (1 foto): ${System.currentTimeMillis() - inicio} ms")

                // RF08: ubicacion aproximada con limite de tiempo
                val location = locationHelper.getLastLocation()

                val analysisResult = AnalysisResult(
                    imagePath = savedPath,
                    classification = label,
                    confidence = confidence,
                    riskLevel = semaforo,
                    notes = notes,
                    latitude = location?.first,
                    longitude = location?.second,
                    cropType = "Frijol"
                )
                repository.save(analysisResult)
            } catch (e: Exception) {
                _uiState.value = ScanUiState(isLoading = false, errorMessage = "Algo salió mal al analizar. Intenta de nuevo.")
            }
        }
    }

    /**
     * RF01: analiza de 1 a 3 fotos elegidas de la galeria, en cuanto se eligen.
     * Cada foto se lee ya reducida (lado mayor de 1280 px, igual que la camara)
     * y pasa por el mismo pipeline (OpenCV -> modelo -> semaforo):
     *  - 1 foto  -> analisis directo (analyzeBitmap)
     *  - 2 o 3   -> voto ponderado por confianza, igual que la camara (RNF02)
     * Queda en el historial con la nota "galeria".
     */
    fun analyzeGalleryImages(uris: List<Uri>) {
        if (_uiState.value.isLoading || uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = ScanUiState(isLoading = true)
            val bitmaps = withContext(Dispatchers.IO) {
                uris.take(3).mapNotNull { uri ->
                    try { leerFotoDeGaleria(uri, 1280) } catch (e: Exception) { null }
                }
            }
            // Se libera el estado de carga para que el analisis pueda arrancar
            _uiState.value = ScanUiState()
            when {
                bitmaps.isEmpty() -> _uiState.value =
                    ScanUiState(errorMessage = "No se pudo abrir esa foto. Elige otra.")
                bitmaps.size == 1 -> analyzeBitmap(bitmaps.first(), notes = "galeria")
                else -> analyzeSequence(bitmaps, notes = "galeria")
            }
        }
    }

    /** Lee la foto reducida y derecha (respeta la rotacion guardada en la foto). */
    private fun leerFotoDeGaleria(uri: Uri, ladoMax: Int): Bitmap {
        val resolver = getApplication<android.app.Application>().contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fuente = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(fuente) { decoder, info, _ ->
                // Bitmap en memoria normal (no de GPU): OpenCV necesita leer sus pixeles
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val escala = ladoMax.toFloat() / maxOf(w, h)
                if (escala < 1f) decoder.setTargetSize((w * escala).toInt(), (h * escala).toInt())
            }
        }
        // Android 7 y 8: lectura reducida + rotacion segun los datos EXIF
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMax) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: throw IllegalStateException("No se pudo leer la foto")
        val grados = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (grados == 0f) return bmp
        val matriz = Matrix().apply { postRotate(grados) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matriz, true)
    }

    /**
     * Corre el pipeline sobre las 3 fotos de la secuencia guiada (RNF02:
     * planta completa, hoja afectada, detalle) y agrega los resultados con
     * VOTACION PONDERADA POR CONFIANZA (no conteo simple):
     *
     * 1. Se descartan las fotos donde el segmentador no detecta una hoja
     *    valida (found == false) -- igual criterio que analyzeBitmap.
     * 2. Si ninguna foto es valida, se informa al usuario sin persistir.
     * 3. Cada foto valida "vota" por su etiqueta (sano/plaga) con un peso
     *    igual a su propia confianza.
     * 4. Empate exacto en puntaje ponderado -> AMARILLO explicito.
     * 5. El semaforo final se recalcula desde la CONFIANZA PROMEDIO del
     *    grupo ganador (PlagaScanClassifier.semaforoFor).
     *
     * Todo el procesamiento (segmentacion + clasificacion de las 3 fotos)
     * corre en Dispatchers.Default para no bloquear el hilo principal.
     */
    fun analyzeSequence(bitmaps: List<Bitmap>, notes: String? = null) {
        // Evita analizar dos veces (doble toque): si ya hay un analisis en curso, se ignora
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val inicio = System.currentTimeMillis()
            _uiState.value = ScanUiState(isLoading = true)
            try {
                val (candidateList, algunaRechazadaPorNoFrijol, detalleFotos) = withContext(Dispatchers.Default) {
                    val list = mutableListOf<SequenceCandidate>()
                    var rechazada = false
                    // RNF02 (diagnostico): destino de cada foto de la secuencia
                    val detalle = mutableListOf<String>()
                    for ((i, bmp) in bitmaps.withIndex()) {
                        val n = i + 1
                        val segmentResult = leafSegmenter.segment(bmp)
                        if (!segmentResult.found) {
                            detalle.add("f$n=sin_hoja")
                            continue
                        }
                        val result = classifier.classify(segmentResult.bitmap)
                        val conf = String.format(Locale.US, "%.2f", result.confidence)
                        if (!result.isFrijol) {
                            rechazada = true
                            detalle.add("f$n=no_frijol $conf")
                            continue
                        }
                        detalle.add("f$n=${result.label} $conf")
                        list.add(
                            SequenceCandidate(
                                originalBitmap = bmp,
                                label = result.label,
                                confidence = result.confidence
                            )
                        )
                    }
                    Triple(list, rechazada, detalle.joinToString("; "))
                }
                Log.d("PlagaScan", "Secuencia (${notes ?: "sin nota"}): $detalleFotos")

                if (candidateList.isEmpty()) {
                    val mensaje = if (algunaRechazadaPorNoFrijol) {
                        "Esto no parece una hoja de frijol. Asegúrate de " +
                            "fotografiar tu planta de frijol."
                    } else {
                        "No se vio bien la hoja en ninguna de las 3 fotos. " +
                            "Tómalas otra vez, un poco más cerca."
                    }
                    _uiState.value = ScanUiState(isLoading = false, errorMessage = mensaje)
                    return@launch
                }


                val grouped = candidateList.groupBy { it.label }

                val weightedScore = grouped.mapValues { (_, group) ->
                    group.sumOf { it.confidence.toDouble() }
                }
                val maxScore = weightedScore.values.max()
                val winningLabels = weightedScore.filter { it.value == maxScore }.keys
                val isTie = winningLabels.size > 1

                val winningLabel = if (isTie) {
                    candidateList.maxByOrNull { it.confidence }!!.label
                } else {
                    winningLabels.first()
                }

                val winningGroup = grouped.getValue(winningLabel)
                val avgConfidence = winningGroup.map { it.confidence }.average().toFloat()
                val bestInGroup = winningGroup.maxByOrNull { it.confidence }!!

                val semaforo = if (isTie) {
                    "AMARILLO"
                } else {
                    PlagaScanClassifier.semaforoFor(winningLabel, avgConfidence)
                }

                val savedPath = withContext(Dispatchers.IO) {
                    saveImageToInternalStorage(bestInGroup.originalBitmap)
                }

                // RNF01: el resultado se muestra en cuanto termina el modelo, sin
                // esperar a la ubicacion (que sin senal puede tardar varios segundos)
                _uiState.value = ScanUiState(
                    isLoading = false,
                    classification = winningLabel,
                    confidence = avgConfidence,
                    riskLevel = semaforo,
                    imagePath = savedPath
                )
                Log.d("PlagaScan", "Tiempo de analisis (3 fotos): ${System.currentTimeMillis() - inicio} ms")

                // RF08: ubicacion aproximada con limite de tiempo; el registro se
                // guarda despues, en segundo plano
                val location = locationHelper.getLastLocation()

                val validCount = candidateList.size
                val totalCount = bitmaps.size
                val combinedNotes = buildString {
                    if (!notes.isNullOrBlank()) append(notes).append(" ")
                    append("[secuencia 3 fotos: $validCount/$totalCount validas")
                    if (isTie) append(", empate sano/plaga - revisar de nuevo")
                    append("]")
                    // RNF02 (diagnostico): que paso con cada foto
                    append(" [detalle: ").append(detalleFotos).append("]")
                }

                val analysisResult = AnalysisResult(
                    imagePath = savedPath,
                    classification = winningLabel,
                    confidence = avgConfidence,
                    riskLevel = semaforo,
                    notes = combinedNotes,
                    latitude = location?.first,
                    longitude = location?.second,
                    cropType = "Frijol"
                )
                repository.save(analysisResult)
            } catch (e: Exception) {
                _uiState.value = ScanUiState(isLoading = false, errorMessage = "Algo salió mal al analizar. Intenta de nuevo.")
            }
        }
    }

    fun deleteResult(result: AnalysisResult) {
        viewModelScope.launch {
            repository.delete(result)
            // RNF05: borrar tambien la foto, para que el espacio se libere
            withContext(Dispatchers.IO) { borrarFotoDeHistorial(result.imagePath) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val fotos = _history.value.map { it.imagePath }
            repository.clear()
            withContext(Dispatchers.IO) { fotos.forEach { borrarFotoDeHistorial(it) } }
        }
    }

    /**
     * RF08 / prueba 14: guarda la etiqueta que escribio el usuario (maximo 40
     * caracteres). Si la deja vacia, se quita. Solo cambia ese campo.
     */
    fun guardarEtiqueta(result: AnalysisResult, texto: String) {
        val limpia = texto.trim().take(MAX_ETIQUETA).ifBlank { null }
        viewModelScope.launch { repository.setEtiqueta(result.id, limpia) }
    }

    fun resetUiState() {
        _uiState.value = ScanUiState()
    }

    // ------------------------------------------------------------------
    // Recoleccion de datos con verdad confirmada por el usuario (no por
    // el modelo) -- pantalla "Recoleccion". Cada captura se guarda en una
    // carpeta separada del historial normal, junto con un log CSV que
    // compara la etiqueta confirmada por el usuario contra lo que predijo
    // el modelo, para evaluar y mejorar el dataset con evidencia real de
    // campo, sin reforzar los propios errores del modelo.
    // ------------------------------------------------------------------

    fun captureGroundTruthOtros(bitmap: Bitmap) {
        viewModelScope.launch {
            _groundTruthState.value = GroundTruthUiState(isProcessing = true)
            val (segFound, predLabel, predConf) = withContext(Dispatchers.Default) {
                val segmentResult = leafSegmenter.segment(bitmap)
                if (!segmentResult.found) {
                    Triple(false, "no_detectado", 0f)
                } else {
                    val result = classifier.classify(segmentResult.bitmap)
                    Triple(true, result.label, result.confidence)
                }
            }
            val savedPath = withContext(Dispatchers.IO) {
                saveGroundTruthImage(bitmap, "otros")
            }
            logGroundTruth(
                modo = "otros",
                etiquetaConfirmada = "no_frijol",
                saludConfirmada = "na",
                segmentadorDetecto = segFound,
                modeloPredijo = predLabel,
                modeloConfianza = predConf,
                archivoImagen = savedPath
            )
            _groundTruthState.value = GroundTruthUiState(
                lastMessage = "Guardado como 'otros'. Modelo predijo: " + predLabel
            )
        }
    }

    fun captureGroundTruthFrijolPendiente(bitmap: Bitmap) {
        viewModelScope.launch {
            _groundTruthState.value = GroundTruthUiState(isProcessing = true)
            val (segFound, predLabel, predConf) = withContext(Dispatchers.Default) {
                val segmentResult = leafSegmenter.segment(bitmap)
                if (!segmentResult.found) {
                    Triple(false, "no_detectado", 0f)
                } else {
                    val result = classifier.classify(segmentResult.bitmap)
                    Triple(true, result.label, result.confidence)
                }
            }
            _groundTruthState.value = GroundTruthUiState(
                pending = PendingGroundTruthCapture(bitmap, segFound, predLabel, predConf)
            )
        }
    }

    fun confirmGroundTruthFrijol(saludConfirmada: String) {
        val pending = _groundTruthState.value.pending ?: return
        viewModelScope.launch {
            _groundTruthState.value = GroundTruthUiState(isProcessing = true)
            // Los casos "dudoso" se guardan en su propia carpeta, separados
            // de sano/plaga -- nunca se usan para entrenar esas dos clases,
            // ya que la propia persona que los captura no tiene certeza de
            // cual es la etiqueta correcta. Sirven como evidencia aparte
            // de casos limite/ambiguos observados en campo real.
            val subcarpeta = if (saludConfirmada == "dudoso") "frijol_dudoso" else "frijol"
            val savedPath = withContext(Dispatchers.IO) {
                saveGroundTruthImage(pending.bitmap, subcarpeta)
            }
            logGroundTruth(
                modo = "frijol",
                etiquetaConfirmada = "frijol",
                saludConfirmada = saludConfirmada,
                segmentadorDetecto = pending.segmentadorDetecto,
                modeloPredijo = pending.modeloPredijo,
                modeloConfianza = pending.modeloConfianza,
                archivoImagen = savedPath
            )
            _groundTruthState.value = GroundTruthUiState(
                lastMessage = "Guardado como " + subcarpeta + "/" + saludConfirmada + ". Modelo predijo: " + pending.modeloPredijo
            )
        }
    }

    fun cancelPendingGroundTruth() {
        _groundTruthState.value = GroundTruthUiState()
    }

    fun clearGroundTruthMessage() {
        _groundTruthState.value = GroundTruthUiState()
    }

    private fun saveGroundTruthImage(bitmap: Bitmap, subfolder: String): String {
        val dir = File(
            getApplication<android.app.Application>().filesDir,
            "ground_truth_captures/" + subfolder
        )
        if (!dir.exists()) dir.mkdirs()
        val fileName = "gt_" + System.currentTimeMillis() + ".jpg"
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    private fun logGroundTruth(
        modo: String,
        etiquetaConfirmada: String,
        saludConfirmada: String,
        segmentadorDetecto: Boolean,
        modeloPredijo: String,
        modeloConfianza: Float,
        archivoImagen: String
    ) {
        val logFile = File(
            getApplication<android.app.Application>().filesDir,
            "ground_truth_log.csv"
        )
        val esNuevo = !logFile.exists()
        java.io.FileWriter(logFile, true).use { writer ->
            if (esNuevo) {
                writer.append("timestamp,modo,etiqueta_confirmada,salud_confirmada,segmentador_detecto,modelo_predijo,modelo_confianza,archivo_imagen")
                writer.append(System.lineSeparator())
            }
            val timestamp = System.currentTimeMillis()
            val fila = listOf(
                timestamp, modo, etiquetaConfirmada, saludConfirmada,
                segmentadorDetecto, modeloPredijo, modeloConfianza, archivoImagen
            ).joinToString(",")
            writer.append(fila)
            writer.append(System.lineSeparator())
        }
    }

    /**
     * RNF05 / prueba 15 de U3_P1: la foto del historial se guarda reducida
     * (lado mayor de 800 px, JPEG calidad 80, ~80 KB) en lugar de la captura
     * completa. Es suficiente para verla en la app y ocupa mucho menos espacio.
     * (Las capturas de Recoleccion se guardan aparte, completas, porque se
     * usan para entrenar el modelo.)
     */
    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val fileName = "scan_${System.currentTimeMillis()}.jpg"
        val file = File(getApplication<android.app.Application>().filesDir, fileName)
        val ladoMax = 800
        val escala = ladoMax.toFloat() / maxOf(bitmap.width, bitmap.height)
        val reducida = if (escala < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * escala).toInt(),
                (bitmap.height * escala).toInt(),
                true
            )
        } else {
            bitmap
        }
        FileOutputStream(file).use { out ->
            reducida.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        if (reducida !== bitmap) reducida.recycle()
        return file.absolutePath
    }

    /**
     * Borra la foto de un registro del historial. Por seguridad solo borra
     * archivos "scan_*.jpg" que esten directamente en la carpeta de la app.
     */
    private fun borrarFotoDeHistorial(ruta: String) {
        val carpeta = getApplication<android.app.Application>().filesDir.canonicalFile
        val foto = File(ruta).canonicalFile
        if (foto.parentFile == carpeta && foto.name.startsWith("scan_")) {
            foto.delete()
        }
    }

    override fun onCleared() {
        super.onCleared()
        classifier.close()
    }

    private data class QuadResult(
        val found: Boolean,
        val label: String,
        val confidence: Float,
        val semaforo: String,
        val errorMessage: String = "No se vio bien la hoja. Acerca un poco más la cámara."
    )
}
