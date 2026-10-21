package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST


interface AlertasApiService {

    /** GET /alertas/intervalo-ubicacion — ritmo recomendado de reporte. */
    @GET("alertas/intervalo-ubicacion")
    suspend fun intervaloUbicacion(): IntervaloUbicacionResponse

    /**
     * POST /alertas — CreateAlertaDto del backend (alertas.controller.ts).
     * El rol ADULTO_MAYOR tiene el permiso `alertas.crear`; sin adulto_id
     * el backend usa el usuario autenticado.
     */
    @POST("alertas")
    suspend fun crearAlerta(@Body body: CrearAlertaRequest): CrearAlertaResponse

    /**
     * PATCH /alertas/{id} — ResolverAlertaDto del backend. El rol
     * ADULTO_MAYOR tiene el permiso `alertas.actualizar` y puede operar
     * sobre sus propias alertas (assertPuedeVer).
     */
    @PATCH("alertas/{id}")
    suspend fun actualizarAlerta(
        @Path("id") id: String,
        @Body body: ResolverAlertaRequest,
    )

    /** Última alerta CRITICA ABIERTA/EN_ATENCION del adulto autenticado. */
    @GET("alertas")
    suspend fun listarAlertas(): List<AlertaListaItem>
}

data class ResolverAlertaRequest(
    val estado: String,
    val detalle: String? = null,
)

data class AlertaListaItem(
    val id: String,
    val tipo: String,
    val estado: String,
    val severidad: String,
)

data class IntervaloUbicacionResponse(
    val intervalo_segundos: Int,
)

data class CrearAlertaRequest(
    val tipo: String,
    val titulo: String,
    val severidad: String? = null,
    val descripcion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
)

data class CrearAlertaResponse(
    val id: String,
    val adulto_id: String,
    val tipo: String,
    val severidad: String,
    val estado: String,
)
