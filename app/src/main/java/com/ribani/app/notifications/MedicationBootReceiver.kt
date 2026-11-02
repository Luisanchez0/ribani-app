package com.ribani.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ribani.app.data.repository.MedicationRepository
import com.ribani.app.sensors.MonitoreoController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tras reiniciar el teléfono: las alarmas se pierden y nada vigila las caídas
 * hasta que algo los reactive. El adulto no tiene por qué abrir la app.
 */
class MedicationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            return
        }

        val appContext = context.applicationContext
        val resultado = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // El servicio también reintenta las tomas si aún no hay red.
                MonitoreoController.sincronizar(appContext)
                withTimeoutOrNull(MAX_ESPERA_MS) {
                    MedicationRepository.refrescar(appContext)
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
