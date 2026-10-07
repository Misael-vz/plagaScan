package com.example.proyecto

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * PlagaScanClassifier
 *
 * Carga el modelo MobileNetV2 (.tflite) de 3 CLASES entrenado en el
 * Modulo 1 (version ampliada 28 ago 2026): distingue entre hoja sana de
 * frijol, hoja de frijol con pulgon amarillo, y "no_frijol" (cualquier
 * otra especie de planta, entrenado con PlantDoc CC BY 4.0). Corre
 * inferencia sobre una imagen ya preprocesada (224x224x3).
 *
 * IMPORTANTE: el orden de clases del modelo es alfabetico, confirmado
 * en el entrenamiento: ['no_frijol', 'plaga', 'sano'] -> indices 0, 1, 2.
 * La salida ahora es un vector de 3 probabilidades (softmax), no un
 * unico valor sigmoide como en la version anterior de 2 clases.
 */
class PlagaScanClassifier(context: Context) {
    private var interpreter: Interpreter
    private val inputSize = 224

    companion object {
        private const val TAG = "PlagaScanClassifier"
        private val CLASS_NAMES = arrayOf("no_frijol", "plaga", "sano")

        /**
         * Calcula el semaforo (VERDE/AMARILLO/ROJO) a partir de una etiqueta
         * y una confianza. Se expone como funcion reutilizable para que el
         * ViewModel recalcule el semaforo sobre una confianza PROMEDIO
         * (secuencia de 3 fotos) con el mismo criterio exacto que una sola
         * foto -- evita inconsistencias entre ambos flujos.
         *
         * NOTA: "no_frijol" nunca deberia llegar aqui -- se maneja como
         * caso de error/rechazo en el ViewModel, igual que "no se detecto
         * una hoja clara" del segmentador, antes de calcular semaforo.
         */
        fun semaforoFor(label: String, confidence: Float): String = when {
            label == "sano" && confidence >= 0.80f -> "VERDE"
            label == "plaga" && confidence >= 0.80f -> "ROJO"
            else -> "AMARILLO"
        }
    }

    data class Result(
        val label: String,       // "sano", "plaga" o "no_frijol"
        val confidence: Float,   // 0.0 a 1.0, probabilidad de la clase ganadora
        val semaforo: String,    // "VERDE", "AMARILLO", "ROJO" -- vacio si label es "no_frijol"
        val isFrijol: Boolean    // false si el modelo determino que no es una hoja de frijol
    )

    init {
        val model = loadModelFile(context, "plagascan_mobilenetv2.tflite")
        interpreter = Interpreter(model)
        Log.d(TAG, "Modelo de 3 clases cargado correctamente")
    }

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            // El modelo espera RGB en float32, sin normalizar 0-1
            // (el preprocess_input de MobileNetV2 esta DENTRO del grafo
            // del modelo, confirmado contra train_model.py -- ver
            // auditoria de RF02)
            buffer.putFloat(((pixel shr 16) and 0xFF).toFloat()) // R
            buffer.putFloat(((pixel shr 8) and 0xFF).toFloat())  // G
            buffer.putFloat((pixel and 0xFF).toFloat())          // B
        }
        return buffer
    }

    fun classify(bitmap: Bitmap): Result {
        val inputBuffer = bitmapToByteBuffer(bitmap)
        // Salida de 3 probabilidades (softmax), en el orden alfabetico
        // de las clases: [no_frijol, plaga, sano]
        val output = Array(1) { FloatArray(3) }
        interpreter.run(inputBuffer, output)

        val probs = output[0]
        var maxIdx = 0
        for (i in probs.indices) {
            if (probs[i] > probs[maxIdx]) maxIdx = i
        }
        val label = CLASS_NAMES[maxIdx]
        val confidence = probs[maxIdx]
        val isFrijol = label != "no_frijol"
        val semaforo = if (isFrijol) semaforoFor(label, confidence) else ""

        Log.d(TAG, "Prediccion: $label (${confidence * 100}%) -> " +
            "isFrijol=$isFrijol semaforo=$semaforo " +
            "[no_frijol=${probs[0]}, plaga=${probs[1]}, sano=${probs[2]}]")

        return Result(label, confidence, semaforo, isFrijol)
    }

    fun close() {
        interpreter.close()
    }
}
