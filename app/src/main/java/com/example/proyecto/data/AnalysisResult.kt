package com.example.proyecto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analysis_results")
data class AnalysisResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    // Ruta de la foto original guardada localmente (internal storage)
    val imagePath: String,
    // "plaga" o "sano" — mismo criterio que PlagaScanClassifier
    val classification: String,
    // Score crudo del modelo (0.0 - 1.0), donde >=0.5 = sano
    val confidence: Float,
    // "VERDE" | "AMARILLO" | "ROJO" — resultado del semaforo
    val riskLevel: String,
    val notes: String? = null,
    // RF08: ubicacion aproximada (GPS del dispositivo, offline). Nullable:
    // el guardado del analisis nunca depende de tener ubicacion disponible.
    val latitude: Double? = null,
    val longitude: Double? = null,
    // RF08: cultivo asociado al analisis. Fijo en "Frijol" por el alcance
    // actual del proyecto (recorte aprobado por sinodales); el campo ya
    // existe para cuando se implemente el selector visual (RF06).
    val cropType: String = "Frijol",
    // RF08 / prueba 14: etiqueta escrita por el usuario (p. ej. "Surco 3").
    // Nullable: los registros anteriores quedan sin etiqueta.
    val etiqueta: String? = null
)
