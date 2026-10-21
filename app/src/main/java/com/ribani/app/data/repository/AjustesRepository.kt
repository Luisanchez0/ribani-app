package com.ribani.app.data.repository

import android.content.Context
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.ApiErrorParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de la pantalla de Ajustes:
 *
 * - Datos personales del usuario autenticado (GET /auth/me), con los
 *   datos locales guardados como base mientras el backend no responde.
 * - Estado de protección del ADULTO_MAYOR: ACTIVADA solo si existe
 *   algún vínculo (adulto_familiares) con estado ACTIVA
 *   (GET /familiares/mis-familiares).
 */
data class AjustesUiState(
    val cargando: Boolean = false,
    val nombre: String = "",
    val apellido: String = "",
    val email: String = "",
    val telefono: String? = null,
    val rolCodigo: String = "",
    val emailVerificado: Boolean? = null,
    /** null = no se pudo determinar (sin sesión / error backend). */
    val proteccionActiva: Boolean? = null,
    val error: String? = null,
)

object AjustesRepository {

    private val _uiState = MutableStateFlow(AjustesUiState())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    suspend fun cargar(context: Context) {
        _uiState.value = _uiState.value.copy(cargando = true, error = null)

        var estado = _uiState.value.copy(cargando = true)

        // Datos locales como base (muestran algo aunque el backend no responda).
        SessionManager(context).currentUser()?.let { local ->
            estado = estado.copy(
                nombre = local.nombre,
                apellido = local.apellido,
                email = local.email,
                telefono = local.telefono,
                rolCodigo = local.rolCodigo,
            )
        }

        // Datos personal frescos del backend (GET /auth/me).
        try {
            val me = ApiClient.authService.me()
            estado = estado.copy(
                nombre = me.nombre,
                apellido = me.apellido,
                email = me.email,
                telefono = me.telefono,
                rolCodigo = me.rol?.codigo.orEmpty(),
                emailVerificado = me.emailVerificado,
            )
        } catch (e: Exception) {
            if (estado.nombre.isEmpty()) {
                estado = estado.copy(error = ApiErrorParser.getErrorMessage(e))
            }
        }

        // Protección: ¿algún vínculo ACTIVA con un familiar encargado?
        try {
            val vinculos = ApiClient.familiaresService.misFamiliares()
            estado = estado.copy(
                proteccionActiva = vinculos.any { it.estado.equals("ACTIVA", true) }
            )
        } catch (e: Exception) {
            estado = estado.copy(proteccionActiva = null)
        }

        _uiState.value = estado.copy(cargando = false)
    }

    /** Resetea el estado al cerrar sesión / cambiar de usuario. */
    fun limpiar() {
        _uiState.value = AjustesUiState()
    }
}
