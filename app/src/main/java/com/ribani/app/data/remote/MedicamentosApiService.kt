package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST


interface MedicamentosApiService {

    /** GET /medicamentos — catálogo del adulto autenticado o sus vinculados. */
    @GET("medicamentos")
    suspend fun listarMedicamentos(
        @Query("adultoId") adultoId: String? = null,
        @Query("soloActivos") soloActivos: String? = null,
    ): List<MedicamentoResponse>

    @GET("tomas")
    suspend fun listarTomas(
        @Query("adultoId") adultoId: String? = null,
        @Query("medicamentoId") medicamentoId: String? = null,
        @Query("estado") estado: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
    ): List<TomaResponse>

    @PATCH("tomas/{id}")
    suspend fun actualizarToma(
        @Path("id") id: String,
        @Body body: ActualizarTomaRequest,
    ): TomaResponse

    @GET("tomas/{id}") suspend fun obtenerToma(@Path("id") id: String): TomaResponse
    @GET("medicamentos/{id}") suspend fun obtenerMedicamento(@Path("id") id: String): MedicamentoResponse
    @POST("tomas/{id}/posponer") suspend fun posponer(@Path("id") id: String): TomaResponse
}


data class TomaResponse(
    val id: String,
    val medicamento_id: String,
    val horario_id: String?,
    val adulto_id: String,
    /** ISO 8601 con zona. */
    val programada_para: String,
    /** PENDIENTE | POSPUESTA | TOMADA | OMITIDA. */
    val estado: String,
    val tomada_en: String?,
    val dosis_tomada: Double?,
    val registrada_por: String?,
    val nota: String?,
    val recordar_en: String? = null,
)


data class MedicamentoResponse(
    val id: String,
    val adulto_id: String,
    val nombre: String,
    val descripcion: String?,
    val dosis: Double,
    val unidad_dosis: String,
    val instrucciones: String?,
    val fecha_inicio: String,
    val fecha_fin: String?,
    val zona_horaria: String?,
    val activo: Boolean,
)


data class ActualizarTomaRequest(
    val estado: String? = null,
    /** ISO 8601 con zona; opcional para TOMADA (default: ahora). */
    val tomada_en: String? = null,
    val dosis_tomada: Double? = null,
    val nota: String? = null,
)
