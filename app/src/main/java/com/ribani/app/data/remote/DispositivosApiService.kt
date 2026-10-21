package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface DispositivosApiService {

    /** POST /dispositivos — upsert por identificador (idempotente). */
    @POST("dispositivos")
    suspend fun registrar(@Body body: RegistrarDispositivoRequest): DispositivoResponse

    /** POST /dispositivos/{id}/latido — actualiza ultimo_contacto_en. */
    @POST("dispositivos/{id}/latido")
    suspend fun latido(@Path("id") id: String): LatidoResponse
}

/**
 * Body de CreateDispositivoDto:
 * - nombre (1..100), plataforma: ANDROID | IOS | WEB
 * - identificador (8..255), opcional: se guarda hasheado en el backend
 * - version_app (máx. 30)
 */
data class RegistrarDispositivoRequest(
    val nombre: String,
    val plataforma: String,
    val identificador: String? = null,
    val version_app: String? = null,
)

data class DispositivoResponse(
    val id: String,
)

data class LatidoResponse(
    val id: String,
    val ultimo_contacto_en: String?,
)
