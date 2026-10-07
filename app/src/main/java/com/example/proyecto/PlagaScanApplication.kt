package com.example.proyecto

import android.app.Application
import com.example.proyecto.data.AnalysisRepository
import com.example.proyecto.data.PlagaScanDatabase

class PlagaScanApplication : Application() {

    // Se crea perezosamente, una sola vez, cuando algo la pida por primera vez
    val database: PlagaScanDatabase by lazy { PlagaScanDatabase.getInstance(this) }
    val repository: AnalysisRepository by lazy { AnalysisRepository(database.analysisDao()) }
}
