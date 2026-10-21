package com.ribani.app.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.ribani.app.BuildConfig
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.RegistrarDispositivoRequest

/**
 * Registra el teléfono como dispositivo del usuario con sesión y envía un
 * latido (POST /dispositivos + POST /dispositivos/{id}/latido).
 * El registro es idempotente por identificador, así que se puede repetir.
 * Los errores (sin red, sin sesión, 409) no se muestran: se reintenta en el
 * siguiente ciclo del servicio de monitoreo.
 */
object DispositivosRepository {

    suspend fun sincronizar(context: Context) {
        val appContext = context.applicationContext
        ApiClient.init(appContext)

        if (SessionManager(appContext).currentUser() == null) return

        val identificador = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.takeIf { it.length >= 8 } ?: return

        try {
            val api = ApiClient.dispositivosService
            val dispositivo = api.registrar(
                RegistrarDispositivoRequest(
                    nombre = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
                    plataforma = "ANDROID",
                    identificador = identificador,
                    version_app = BuildConfig.VERSION_NAME.take(30),
                ),
            )
            api.latido(dispositivo.id)
        } catch (_: Exception) {
            // Se reintenta en el próximo ciclo.
        }
    }
}
