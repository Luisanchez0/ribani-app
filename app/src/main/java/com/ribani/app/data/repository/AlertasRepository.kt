package com.ribani.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.CrearAlertaRequest
import com.ribani.app.data.remote.ResolverAlertaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Envío de alertas al backend (POST /alertas).
 * La conexión y el envío corren en IO; los fallos se reportan en el
 * resultado sin lanzar (la app no debe morir por una alerta fallida).
 */
object AlertasRepository {

    sealed interface Resultado {
        data class Enviada(val alertaId: String) : Resultado
        data object Fallida : Resultado
    }

    /**
     * Crea la alerta en el backend. Tipo CAIDA al detectar una posible
     * caída; SOS cuando el adulto pide ayuda. La severidad CRITICA va
     * al tope del panel web del familiar.
     */
    suspend fun enviarAlerta(
        context: Context,
        tipo: String,
        titulo: String,
        descripcion: String,
    ): Resultado = withContext(Dispatchers.IO) {
        try {
            val (lat, lng) = ultimaLatitudLongitud(context)
            val request = CrearAlertaRequest(
                tipo = tipo,
                titulo = titulo,
                severidad = "CRITICA",
                descripcion = descripcion,
                latitud = lat,
                longitud = lng,
            )
            val respuesta = ApiClient.alertasService.crearAlerta(request)
            Resultado.Enviada(respuesta.id)
        } catch (e: Exception) {
            Resultado.Fallida
        }
    }

    /**
     * El adulto confirmó que está bien: cierra la última CAIDA abierta
     * (RESUELTA). El familiar la ve como "confirmada" en el panel.
     */
    suspend fun confirmarBien(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val caidaAbierta = ApiClient.alertasService.listarAlertas()
                .first { it.tipo == "CAIDA" && it.estado == "ABIERTA" }
            ApiClient.alertasService.actualizarAlerta(
                id = caidaAbierta.id,
                body = ResolverAlertaRequest(
                    estado = "RESUELTA",
                    detalle = "El adulto mayor confirmó que está bien.",
                ),
            )
        }.isSuccess
    }

    /**
     * El adulto pidió ayuda dentro de la alerta: la CAIDA abierta pasa a
     * EN_ATENCION (además del SOS explícito que ya se crea aparte).
     */
    suspend fun escalarAlerta(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val caidaAbierta = ApiClient.alertasService.listarAlertas()
                .firstOrNull { it.tipo == "CAIDA" && it.estado == "ABIERTA" } ?: return@runCatching
            ApiClient.alertasService.actualizarAlerta(
                id = caidaAbierta.id,
                body = ResolverAlertaRequest(
                    estado = "EN_ATENCION",
                    detalle = "El adulto mayor presionó ¡NECESITO AYUDA!.",
                ),
            )
        }.isSuccess
    }

    /** Última ubicación conocida del dispositivo (fix rápido, sin GPS largo). */
    private fun ultimaLatitudLongitud(context: Context): Pair<Double?, Double?> {
        val permisoFino = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val permisoGrueso = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!permisoFino && !permisoGrueso) return null to null

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnown = runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()

        return lastKnown?.latitude to lastKnown?.longitude
    }
}
