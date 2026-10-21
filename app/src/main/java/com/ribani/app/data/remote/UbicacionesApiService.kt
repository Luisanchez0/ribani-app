package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST


interface UbicacionesApiService {

    @POST("ubicaciones")
    suspend fun reportar(@Body body: ReporteUbicacionRequest): UbicacionResponse

    /** POST /ubicaciones/lote — flush del batch acumulado (1..100 reportes). */
    @POST("ubicaciones/lote")
    suspend fun reportarLote(@Body body: ReporteLoteRequest): ReporteLoteResponse
}


data class ReporteLoteRequest(
    val reportes: List<ReporteUbicacionRequest>,
)

data class ReporteLoteResponse(
    val insertados: Int,
    val ultima_reporte: UbicacionResponse?,
)

/**
 * Body de CreateUbicacionDto (validación del backend):
 * - latitud [-90, 90], longitud [-180, 180]
 * - precision_metros [0, 10000], altitud_metros [-1000, 10000]
 * - fuente: GPS | RED | MANUAL
 * - registrada_en: ISO 8601 completo con zona (ej: 2026-10-03T08:00:00-06:00)
 * dispositivo_id es opcional: la app aún no registra dispositivos.
 */
data class ReporteUbicacionRequest(
    val latitud: Double,
    val longitud: Double,
    val precision_metros: Double? = null,
    val altitud_metros: Double? = null,
    val fuente: String? = null,
    val registrada_en: String? = null,
)

data class UbicacionResponse(
    val id: String,
    val adulto_id: String,
    val latitud: Double,
    val longitud: Double,
    val registrada_en: String?,
)
