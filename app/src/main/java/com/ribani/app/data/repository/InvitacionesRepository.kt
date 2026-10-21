package com.ribani.app.data.repository

import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.ApiErrorParser
import com.ribani.app.data.remote.InvitacionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InvitacionesUiState(
    val cargando: Boolean = false,
    val invitations: List<InvitacionItem> = emptyList(),
    val procesandoId: String? = null,
    val error: String? = null,
    val mensaje: String? = null
)

object InvitacionesRepository {

    private val _uiState = MutableStateFlow(InvitacionesUiState())
    val uiState: StateFlow<InvitacionesUiState> = _uiState.asStateFlow()

    suspend fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true, error = null, mensaje = null)
        try {
            val lista = ApiClient.familiaresService.pendientes()
            _uiState.value = InvitacionesUiState(invitations = lista)
        } catch (e: Exception) {
            _uiState.value = InvitacionesUiState(
                error = ApiErrorParser.getErrorMessage(e)
            )
        }
    }

    suspend fun aceptar(id: String) = decidir(id, aceptar = true)

    suspend fun rechazar(id: String) = decidir(id, aceptar = false)

    private suspend fun decidir(id: String, aceptar: Boolean) {
        _uiState.value = _uiState.value.copy(procesandoId = id, error = null, mensaje = null)
        try {
            if (aceptar) {
                ApiClient.familiaresService.aceptar(id)
            } else {
                ApiClient.familiaresService.rechazar(id)
            }
            _uiState.value = _uiState.value.copy(
                procesandoId = null,
                invitations = _uiState.value.invitations.filter { it.id != id },
                mensaje = if (aceptar) "Vínculo aceptado" else "Invitación rechazada"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                procesandoId = null,
                error = ApiErrorParser.getErrorMessage(e)
            )
        }
    }
}
