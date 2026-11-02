package com.ribani.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query


interface ContactosApiService {

    /**
     * GET /contactos — ContactosController del backend.
     * Para el adulto autenticado devuelve sus propios contactos
     * (y los de sus vinculados si aplica); `soloActivos` default true.
     */
    @GET("contactos")
    suspend fun listarContactos(
        @Query("soloActivos") soloActivos: String? = "true",
    ): List<ContactoResponse>
}

data class ContactoResponse(
    val id: String,
    val adulto_id: String,
    val nombre: String,
    val parentesco: String?,
    val telefono: String,
    val email: String?,
    val prioridad: Int,
    val activo: Boolean,
    val created_at: String,
)
