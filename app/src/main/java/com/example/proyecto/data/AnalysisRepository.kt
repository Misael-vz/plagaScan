package com.example.proyecto.data

import kotlinx.coroutines.flow.Flow

class AnalysisRepository(private val dao: AnalysisDao) {

    val allResults: Flow<List<AnalysisResult>> = dao.getAll()
    val plagaResults: Flow<List<AnalysisResult>> = dao.getOnlyPlaga()

    suspend fun save(result: AnalysisResult): Long = dao.insert(result)

    suspend fun delete(result: AnalysisResult) = dao.delete(result)

    suspend fun clear() = dao.clearAll()

    suspend fun setEtiqueta(id: Long, etiqueta: String?) = dao.updateEtiqueta(id, etiqueta)
}
