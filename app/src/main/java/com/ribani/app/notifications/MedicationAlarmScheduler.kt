package com.ribani.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.MedicationOccurrence
import com.ribani.app.data.repository.MedicationRepository
import com.ribani.app.data.repository.ConfiguracionRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking


object MedicationAlarmScheduler {

    private const val REQUEST_CODE_BASE = 3000
    private const val REQUEST_CODE_RECARGA = 2999
    private const val AVISADAS = "avisadas"
    const val EXTRA_AVISO_ISO = "extra_aviso_iso"
    private val HORA_RECARGA = LocalTime.of(0, 15)
    /** Countdown visual máximo en pantalla (segundos). */
    private const val COUNTDOWN_VISUAL_MAX_SEG = 60

    /** Programa (o reprograma) las alarmas de las tomas pendientes actuales. */
    fun programarPendientes(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val usuarioId = runBlocking { SessionManager(context).currentUser()?.id } ?: return
        if (!ConfiguracionRepository.recordatoriosHabilitados(context, usuarioId)) { cancelarTodas(context); return }
        val pendientes = MedicationRepository.uiState.value.pendingMedications
        val preferences = context.getSharedPreferences("ribani_alarmas", Context.MODE_PRIVATE)
        val anteriores = preferences.getStringSet("ids", emptySet()).orEmpty().toSet()
        val actuales = pendientes.map { it.id }.toSet()
        anteriores.forEach { cancelar(context, alarmManager, it); if (it !in actuales) MedicationAlarmNotifications.cancelar(context, it) }
        preferences.edit()
            .putStringSet("ids", pendientes.map { it.id }.toSet())
            // Los avisos de tomas ya atendidas no hacen falta.
            .putStringSet(
                AVISADAS,
                preferences.getStringSet(AVISADAS, emptySet()).orEmpty()
                    .filter { it.substringBefore('@') in actuales }
                    .toSet(),
            )
            .apply()

        // Cancela alarmas previas para no duplicar notificaciones.
        pendientes.forEach { cancelar(context, alarmManager, it.id) }

        pendientes.forEach { programarUna(context, it, usuarioId) }
        programarRecargaDiaria(context)
    }

    /**
     * Las tomas se generan por día en el servidor (00:05 hora de CDMX): sin esta
     * alarma, las de mañana no sonarían si nadie abre la app.
     */
    fun programarRecargaDiaria(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val zona = ZoneId.systemDefault()
        val cuando = LocalDate.now(zona).plusDays(1)
            .atTime(HORA_RECARGA)
            .atZone(zona)
            .toInstant()
            .toEpochMilli()
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cuando,
            recargaPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)!!,
        )
    }

    private fun recargaPendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_RECARGA,
            Intent(context, MedicationAlarmReceiver::class.java)
                .setAction(MedicationNotificationContract.ACTION_RECARGAR_TOMAS),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Reprograma una toma verificada, incluso al recibir una alarma de otro proceso. */
    fun programarUna(context: Context, toma: MedicationOccurrence, usuarioId: String) {
        if (!ConfiguracionRepository.recordatoriosHabilitados(context, usuarioId)) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val ahora = Instant.now()
        val iso = toma.avisoIso ?: return
        val trigger = runCatching { Instant.parse(iso) }.getOrNull() ?: return

        val preferences = context.getSharedPreferences("ribani_alarmas", Context.MODE_PRIVATE)
        val decision = decidirAviso(
            avisoMs = trigger.toEpochMilli(),
            ahoraMs = ahora.toEpochMilli(),
            yaAvisada = claveAviso(toma.id, iso) in preferences.getStringSet(AVISADAS, emptySet()).orEmpty(),
        )
        if (decision == DecisionAviso.Ignorar) return

        preferences.edit().putStringSet("ids", preferences.getStringSet("ids", emptySet()).orEmpty() + toma.id).apply()

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = MedicationNotificationContract.ACTION_OPEN_MEDICATION
            putExtra(MedicationNotificationContract.EXTRA_TOMA_ID, toma.id)
            putExtra(EXTRA_AVISO_ISO, iso)
            putExtra("usuario_id", usuarioId)
            putExtra(
                MedicationAlarmNotifications.EXTRA_NOMBRE_MEDICAMENTO, toma.name
            )
            putExtra(
                MedicationAlarmNotifications.EXTRA_DOSIS_MEDICAMENTO, toma.dosage
            )
            putExtra(
                MedicationAlarmNotifications.EXTRA_HORA_PROGRAMADA, toma.time
            )
            if (!toma.instrucciones.isNullOrBlank()) {
                putExtra(
                    MedicationAlarmNotifications.EXTRA_INSTRUCCIONES_MEDICAMENTO,
                    toma.instrucciones
                )
            }
            putExtra(
                MedicationAlarmNotifications.EXTRA_VENTANA_SEGUNDOS,
                COUNTDOWN_VISUAL_MAX_SEG
            )
        }

        if (decision == DecisionAviso.AvisarAhora) {
            // El receptor la marca como avisada: la siguiente recarga no repite.
            context.sendBroadcast(intent)
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeDe(toma.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (puedeProgramarExacta(alarmManager)) {
            // setAlarmClock: exacta, sobrevive Doze y es visible para el usuario.
            val info = AlarmManager.AlarmClockInfo(
                trigger.toEpochMilli(),
                // Al abrir la app: sin intent de "abrir alarma" (null).
                null
            )
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            // Android 14+: SCHEDULE_EXACT_ALARM viene denegada por
            // defecto. setWindow no es exacta pero llega (±10 min) y
            // evita SecurityException — mejor "tarde" que nunca.
            // El usuario puede conceder la exacta desde Configuración
            // (lo pedimos desde MainActivity).
            alarmManager.setWindow(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() +
                    (trigger.toEpochMilli() - ahora.toEpochMilli()),
                10 * 60_000L,
                pendingIntent
            )
        }
    }

    /** La alarma ya sonó para esta hora; si se pospone, la hora cambia y vuelve a sonar. */
    fun marcarAvisada(context: Context, tomaId: String, iso: String) {
        val preferences = context.getSharedPreferences("ribani_alarmas", Context.MODE_PRIVATE)
        // commit: una recarga simultánea no debe volver a disparar la alarma.
        preferences.edit()
            .putStringSet(
                AVISADAS,
                preferences.getStringSet(AVISADAS, emptySet()).orEmpty() + claveAviso(tomaId, iso),
            )
            .commit()
    }

    private fun claveAviso(tomaId: String, iso: String) = "$tomaId@$iso"

    /**
     * Android 12+: setAlarmClock requiere USE_EXACT_ALARM o la aprobación
     * explícita de SCHEDULE_EXACT_ALARM (denegada por default en 14+).
     * En versiones anteriores no aplica la restricción.
     */
    private fun puedeProgramarExacta(alarmManager: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    fun cancelar(context: Context, tomaId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancelar(context, alarmManager, tomaId)
    }

    private fun cancelar(
        context: Context,
        alarmManager: AlarmManager,
        tomaId: String,
    ) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java)
            .setAction(MedicationNotificationContract.ACTION_OPEN_MEDICATION)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeDe(tomaId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    fun cancelarTodas(context: Context) {
        val preferences = context.getSharedPreferences("ribani_alarmas", Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        recargaPendingIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
        preferences.getStringSet("ids", emptySet()).orEmpty().toSet().forEach { cancelar(context, alarmManager, it); MedicationAlarmNotifications.cancelar(context, it) }
        preferences.edit().remove("ids").remove(AVISADAS).apply()
    }

    private fun requestCodeDe(tomaId: String): Int =
        REQUEST_CODE_BASE + (tomaId.hashCode() and 0x7FFFFFFF) % 1_000_000

    /** Etiqueta de hora legible (hh:mm a) a partir del ISO de la toma. */
    fun horaLegible(iso: String): String =
        runCatching {
            Instant.parse(iso).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("hh:mm a"))
        }.getOrDefault("")

    /** Segundos de ventana visual (tope COUNTDOWN_VISUAL_MAX_SEG). */
    fun countdownVisualSegundos(ventanaMinutos: Int): Int =
        (ventanaMinutos * 60).coerceAtMost(COUNTDOWN_VISUAL_MAX_SEG)

    /** Utilidad simple para tests/dev: distancia de una toma a ahora. */
    fun segundosHasta(iso: String): Long =
        runCatching {
            Duration.between(Instant.now(), Instant.parse(iso)).seconds
        }.getOrDefault(-1L)

    // Referencia de compatibilidad con versiones previas de Android.
    private val sdkOk: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}
