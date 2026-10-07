package com.example.proyecto.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: AnalysisResult): Long

    @Delete
    suspend fun delete(result: AnalysisResult)

    @Query("SELECT * FROM analysis_results ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AnalysisResult>>

    @Query("SELECT * FROM analysis_results WHERE id = :id")
    suspend fun getById(id: Long): AnalysisResult?

    @Query("SELECT * FROM analysis_results WHERE classification = 'plaga' ORDER BY timestamp DESC")
    fun getOnlyPlaga(): Flow<List<AnalysisResult>>

    // RF08 / prueba 14: cambia SOLO la etiqueta; fecha, resultado, cultivo,
    // ubicacion y foto del registro quedan intactos.
    @Query("UPDATE analysis_results SET etiqueta = :etiqueta WHERE id = :id")
    suspend fun updateEtiqueta(id: Long, etiqueta: String?)

    @Query("DELETE FROM analysis_results")
    suspend fun clearAll()
}
