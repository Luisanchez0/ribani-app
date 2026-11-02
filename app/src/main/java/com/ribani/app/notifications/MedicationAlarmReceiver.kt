package com.ribani.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dispara la alarma full-screen (como la alerta de caída) cuando el

 */
class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        // Alarmas exactas se entregan aun con la app muerta (proceso nuevo).
        ApiClient.init(context.applicationContext)

        if (intent.action == MedicationNotificationContract.ACTION_RECARGAR_TOMAS) {
            recargarTomas(context.applicationContext)
            return
        }

        val tomaId = intent.getStringExtra(MedicationNotificationContract.EXTRA_TOMA_ID)
            ?: return
        val nombre = intent.getStringExtra(MedicationAlarmNotifications.EXTRA_NOMBRE_MEDICAMENTO)
            .orEmpty()
        val dosis = intent.getStringExtra(MedicationAlarmNotifications.EXTRA_DOSIS_MEDICAMENTO)
            .orEmpty()
        val horaProgramada = intent.getStringExtra(MedicationAlarmNotifications.EXTRA_HORA_PROGRAMADA)
            .orEmpty()
        val ventanaSeg = intent.getIntExtra(
            MedicationAlarmNotifications.EXTRA_VENTANA_SEGUNDOS, 60
        )
        val instrucciones = intent.getStringExtra(
            MedicationAlarmNotifications.EXTRA_INSTRUCCIONES_MEDICAMENTO
        )


        intent.getStringExtra(MedicationAlarmScheduler.EXTRA_AVISO_ISO)?.let { iso ->
            MedicationAlarmScheduler.marcarAvisada(context.applicationContext, tomaId, iso)
        }

        val state = MedicationRepository.uiState.value
        if (state.cargado) {
            val siguePendiente = state.pendingMedications.any { it.id == tomaId }
            if (!siguePendiente) return
        } else {
            MedicationRepository.initialize(context.applicationContext)
        }

        MedicationAlarmNotifications.mostrarAlarmaToma(
            context = context,
            tomaId = tomaId,
            nombre = nombre,
            dosis = dosis,
            horaProgramada = horaProgramada,
            ventanaSegundos = ventanaSeg,
            instrucciones = instrucciones,
        )
    }

    /** Cambio de día: trae las tomas nuevas y programa sus alarmas. */
    private fun recargarTomas(appContext: Context) {
        val resultado = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(MAX_ESPERA_MS) { MedicationRepository.refrescar(appContext) }
            } finally {
                // Aunque hoy falle (sin red), mañana debe volver a intentarlo.
                MedicationAlarmScheduler.programarRecargaDiaria(appContext)
                resultado.finish()
            }
        }
    }

    private companion object {
        const val MAX_ESPERA_MS = 8_000L
    }
}
