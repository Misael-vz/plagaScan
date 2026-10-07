package com.example.proyecto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * LocationHelper (RF08, RNF01, RNF03)
 *
 * Obtiene la ubicacion del dispositivo para guardarla con cada analisis,
 * sin salir nunca del telefono:
 *  1. Primero la ULTIMA ubicacion conocida (instantanea) si tiene menos de
 *     10 minutos.
 *  2. Si no hay, pide una nueva con un limite de tiempo (5 s por defecto).
 *     Con permiso preciso usa GPS, que funciona sin internet; con permiso
 *     aproximado usa antenas/wifi.
 * Si no hay permiso o no se consigue a tiempo, devuelve null: la ubicacion
 * es informacion adicional, nunca un requisito para completar un analisis.
 */
class LocationHelper(private val context: Context) {

    private fun tiene(permiso: String) =
        ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(limiteMs: Long = 5_000): Pair<Double, Double>? {
        val precisa = tiene(Manifest.permission.ACCESS_FINE_LOCATION)
        val aproximada = tiene(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!precisa && !aproximada) return null

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)

            val ultima = esperar(client.lastLocation, 1_000)
            if (ultima != null && edadEnMinutos(ultima) <= 10) {
                return Pair(ultima.latitude, ultima.longitude)
            }

            val prioridad = if (precisa) Priority.PRIORITY_HIGH_ACCURACY
                            else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val cancelacion = CancellationTokenSource()
            val nueva = esperar(client.getCurrentLocation(prioridad, cancelacion.token), limiteMs) {
                cancelacion.cancel()
            }
            (nueva ?: ultima)?.let { Pair(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    private fun edadEnMinutos(ubicacion: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - ubicacion.elapsedRealtimeNanos) / 60_000_000_000L

    /** Espera una tarea de Google Play Services con limite de tiempo; null si no llega. */
    private suspend fun esperar(
        tarea: Task<Location?>,
        limiteMs: Long,
        alCancelar: () -> Unit = {}
    ): Location? = withTimeoutOrNull(limiteMs) {
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { alCancelar() }
            tarea.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        }
    }
}
