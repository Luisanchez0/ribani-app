package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoints de familiares/invitaciones; requieren Authorization (AuthInterceptor). */
interface FamiliaresApiService {

    @GET("familiares/invitaciones/pendientes")
    suspend fun pendientes(): List<InvitacionItem>

    /** GET /familiares/mis-familiares — vínculos del adulto autenticado
     *  (con familiar anidado). Sirve para saber si ya está protegido por
     *  un FAMILIAR_ENCARGADO (alguno con estado ACTIVA). */
    @GET("familiares/mis-familiares")
    suspend fun misFamiliares(): List<VinculoFamiliarResponse>

    @POST("familiares/invitaciones/{id}/aceptar")
    suspend fun aceptar(@Path("id") id: String): VinculoResponse

    @POST("familiares/invitaciones/{id}/rechazar")
    suspend fun rechazar(@Path("id") id: String): VinculoResponse
}

data class InvitacionItem(
    val id: String,
    val estado: String,
    val parentesco: String?,
    val usuarios_adulto_familiares_familiar_idTousuarios: FamiliarResumen
)

data class FamiliarResumen(
    val id: String,
    val nombre: String,
    val apellido: String,
    val email: String,
    val telefono: String?
)

/** Fila de adulto_familiares con el familiar anidado (SELECT_RELACION). */
data class VinculoFamiliarResponse(
    val id: String,
    val estado: String,
    val parentesco: String? = null,
    val usuarios_adulto_familiares_familiar_idTousuarios: FamiliarResumen? = null,
)

data class VinculoResponse(
    val id: String,
    val estado: String
)
