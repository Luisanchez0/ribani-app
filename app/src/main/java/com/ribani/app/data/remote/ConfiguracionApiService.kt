package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface ConfiguracionApiService {
    @GET("configuracion") suspend fun obtener(): ConfiguracionResponse
    @PATCH("configuracion") suspend fun actualizar(@Body body: ActualizarConfiguracionRequest): ConfiguracionResponse
}
data class ConfiguracionResponse(
    val adulto_id: String,
    val notificaciones_habilitadas: Boolean,
    val recordatorio_medicamentos: Boolean,
    val minutos_repeticion: Int,
    val max_repeticiones: Int,
    val familiares_activos: Int,
    val zona_horaria: String,
)
data class ActualizarConfiguracionRequest(
    val notificaciones_habilitadas: Boolean? = null,
    val recordatorio_medicamentos: Boolean? = null,
)
