package com.ribani.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ribani.app.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Botones "Ya la tomé" y "Omitir" de la notificación. Funciona con la app
 * cerrada y solo retira el aviso si el servidor confirmó el registro.
 */
class MedicationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val tomaId = intent.getStringExtra(MedicationNotificationContract.EXTRA_TOMA_ID)
            ?: return
        val estado = when (intent.action) {
            MedicationNotificationContract.ACTION_MARK_TAKEN -> "TOMADA"
            MedicationNotificationContract.ACTION_MARK_OMITTED -> "OMITIDA"
            else -> return
        }

        val appContext = context.applicationContext
        val resultado = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val guardada = withTimeoutOrNull(MAX_ESPERA_MS) {
                    MedicationRepository.guardarToma(tomaId, estado)
                } == true
                if (guardada) {
                    MedicationAlarmNotifications.cancelar(appContext, tomaId)
                    MedicationRepository.initialize(appContext)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            "No se guardó. Revisa tu conexión e inténtalo de nuevo.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } finally {
                resultado.finish()
            }
        }
    }

    private companion object {
        /** Android mata el receptor si tarda demasiado. */
        const val MAX_ESPERA_MS = 8_000L
    }
}
