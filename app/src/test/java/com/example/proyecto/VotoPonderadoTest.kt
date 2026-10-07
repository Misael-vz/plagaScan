package com.example.proyecto

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Test aislado de la logica de voto ponderado usada en
 * MainViewModel.analyzeSequence(), replicada aqui como funcion pura
 * (sin Bitmap, corrutinas ni Room) para poder correrla como test JVM
 * normal, sin necesidad de emulador ni dispositivo.
 *
 * Parte de la auditoria de RNF02 (secuencia de captura triple con voto
 * ponderado): el caso de empate exacto nunca habia ocurrido con datos
 * reales del dispositivo, asi que se valida aqui con datos fabricados
 * a proposito.
 */
class VotoPonderadoTest {

    data class Candidate(val label: String, val confidence: Float)

    private fun evaluar(candidates: List<Candidate>): Triple<String, Boolean, Float> {
        val grouped = candidates.groupBy { it.label }
        val weightedScore = grouped.mapValues { (_, group) -> group.sumOf { it.confidence.toDouble() } }
        val maxScore = weightedScore.values.max()
        val winningLabels = weightedScore.filter { it.value == maxScore }.keys
        val isTie = winningLabels.size > 1
        val winningLabel = if (isTie) candidates.maxByOrNull { it.confidence }!!.label else winningLabels.first()
        val winningGroup = grouped.getValue(winningLabel)
        val avgConfidence = winningGroup.map { it.confidence }.average().toFloat()
        return Triple(winningLabel, isTie, avgConfidence)
    }

    @Test
    fun `empate exacto se detecta correctamente`() {
        val candidatos = listOf(
            Candidate("sano", 0.40f),
            Candidate("sano", 0.40f),
            Candidate("plaga", 0.80f)
        )
        val (_, isTie, _) = evaluar(candidatos)
        assertEquals(true, isTie)
    }

    @Test
    fun `sin empate no marca falso positivo`() {
        val candidatos = listOf(
            Candidate("plaga", 0.90f),
            Candidate("plaga", 0.85f),
            Candidate("sano", 0.30f)
        )
        val (label, isTie, _) = evaluar(candidatos)
        assertEquals(false, isTie)
        assertEquals("plaga", label)
    }

    @Test
    fun `voto ponderado por suma, no por mayoria de fotos`() {
        // Una foto muy segura de "plaga" (0.99) contra dos fotos dudosas
        // de "sano" (0.55 cada una, suman 1.10) -- el grupo "sano" gana
        // por SUMA aunque "plaga" tenga la confianza individual mas alta.
        // Esto confirma que el algoritmo pondera por SUMA total del grupo,
        // no por confianza individual maxima ni por conteo de fotos.
        val candidatos = listOf(
            Candidate("plaga", 0.99f),
            Candidate("sano", 0.55f),
            Candidate("sano", 0.55f)
        )
        val (label, isTie, avgConf) = evaluar(candidatos)
        assertEquals(false, isTie)
        assertEquals("sano", label)
        assertEquals(0.55f, avgConf, 0.001f)
    }
}
