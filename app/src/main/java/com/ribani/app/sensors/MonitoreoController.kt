package com.ribani.app.sensors

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ribani.app.data.local.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado real del servicio de monitoreo, para no suponerlo en la interfaz. */
object MonitoreoEstado {
    private val _activo = MutableStateFlow(false)
    val activo: StateFlow<Boolean> = _activo.asStateFlow()

    private val _sensoresDisponibles = MutableStateFlow(true)
    val sensoresDisponibles: StateFlow<Boolean> = _sensoresDisponibles.asStateFlow()

    fun marcar(activo: Boolean, sensoresDisponibles: Boolean = _sensoresDisponibles.value) {
        _activo.value = activo
        _sensoresDisponibles.value = sensoresDisponibles
    }
}

/**
 * Único punto que arranca el monitoreo. Solo vigila a un adulto mayor con
 * sesión: los cuidadores usan la web y no deben reportar su ubicación. El
 * servicio se detiene solo cuando la sesión termina.
 */
object MonitoreoController {
    private const val ROL_ADULTO = "ADULTO_MAYOR"

    suspend fun debeMonitorear(context: Context): Boolean {
        val sesion = SessionManager(context.applicationContext)
        val usuario = sesion.currentUser() ?: return false
        return !sesion.accessToken().isNullOrBlank() && usuario.rolCodigo == ROL_ADULTO
    }

    /** Idempotente: también aplica permisos recién concedidos al servicio en marcha. */
    suspend fun sincronizar(context: Context) {
        if (!debeMonitorear(context)) return
        val app = context.applicationContext
        try {
            ContextCompat.startForegroundService(app, Intent(app, RibaniMonitoringService::class.java))
        } catch (_: IllegalStateException) {
            // Android no permite iniciarlo ahora (app en segundo plano); se reintenta al abrirla.
        } catch (_: SecurityException) {
            // Igual que arriba: no debe tumbar la app que lo solicitó.
        }
    }
}
