package com.ribani.app.data.repository

import android.content.Context
import com.ribani.app.data.remote.ActualizarConfiguracionRequest
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.ConfiguracionResponse
import com.ribani.app.notifications.MedicationAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ConfiguracionRepository {
    private fun guardarLocal(context: Context, c: ConfiguracionResponse): ConfiguracionResponse {
        context.getSharedPreferences("ribani_recordatorios", Context.MODE_PRIVATE).edit()
            .putBoolean("habilitados:${c.adulto_id}", c.notificaciones_habilitadas && c.recordatorio_medicamentos).apply()
        if (!c.notificaciones_habilitadas || !c.recordatorio_medicamentos) MedicationAlarmScheduler.cancelarTodas(context)
        return c
    }
    fun recordatoriosHabilitados(context: Context, usuarioId: String): Boolean =
        context.getSharedPreferences("ribani_recordatorios", Context.MODE_PRIVATE)
            .getBoolean("habilitados:$usuarioId", true)

    suspend fun obtener(context: Context): ConfiguracionResponse = withContext(Dispatchers.IO) {
        guardarLocal(context, ApiClient.configuracionService.obtener())
    }
    suspend fun actualizar(context: Context, body: ActualizarConfiguracionRequest): ConfiguracionResponse = withContext(Dispatchers.IO) {
        guardarLocal(context, ApiClient.configuracionService.actualizar(body)).also {
            MedicationAlarmScheduler.programarPendientes(context)
        }
    }
}
