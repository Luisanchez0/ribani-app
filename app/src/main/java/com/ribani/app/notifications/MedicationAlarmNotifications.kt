package com.ribani.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ribani.app.MedicationAlarmActivity
import com.ribani.app.R


object MedicationAlarmNotifications {

    const val CHANNEL_ID = "ribani_medication_alarm"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    /** Base para ids de notificación de toma (evita colisión con otras alertas). */
    private const val NOTIF_BASE = 500000

    const val EXTRA_TOMA_ID = "extra_toma_id"
    const val EXTRA_NOMBRE_MEDICAMENTO = "extra_nombre_medicamento"
    const val EXTRA_DOSIS_MEDICAMENTO = "extra_dosis_medicamento"
    const val EXTRA_HORA_PROGRAMADA = "extra_hora_programada"
    const val EXTRA_VENTANA_SEGUNDOS = "extra_ventana_segundos"
    const val EXTRA_INSTRUCCIONES_MEDICAMENTO = "extra_instrucciones_medicamento"

    /** Id de notificación único por toma para poder cancelarla luego. */
    fun notificationIdFor(tomaId: String): Int = (tomaId.hashCode() and 0x7FFFFFFF) or NOTIF_BASE

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarmas de medicamentos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun fullScreenPendingIntent(
        context: Context,
        tomaId: String,
        nombre: String,
        dosis: String,
        horaProgramada: String,
        ventanaSegundos: Int,
        notificationId: Int,
        instrucciones: String? = null,
    ): PendingIntent {
        val intent = Intent(context, MedicationAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TOMA_ID, tomaId)
            putExtra(EXTRA_NOMBRE_MEDICAMENTO, nombre)
            putExtra(EXTRA_DOSIS_MEDICAMENTO, dosis)
            putExtra(EXTRA_HORA_PROGRAMADA, horaProgramada)
            putExtra(EXTRA_VENTANA_SEGUNDOS, ventanaSegundos)
            if (!instrucciones.isNullOrBlank()) {
                putExtra(EXTRA_INSTRUCCIONES_MEDICAMENTO, instrucciones)
            }
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        tomaId: String,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, MedicationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TOMA_ID, tomaId)
        }
        return PendingIntent.getBroadcast(
            context,
            (notificationId.toString() + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    fun mostrarAlarmaToma(
        context: Context,
        tomaId: String,
        nombre: String,
        dosis: String,
        horaProgramada: String,
        ventanaSegundos: Int,
        instrucciones: String? = null,
    ) {
        ensureChannel(context)
        if (!canPostNotifications(context)) return

        val notificationId = notificationIdFor(tomaId)
        val contentIntent = fullScreenPendingIntent(
            context, tomaId, nombre, dosis, horaProgramada, ventanaSegundos, notificationId,
            instrucciones
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Es hora de tu medicamento 💊")
            .setContentText("$nombre $dosis — $horaProgramada")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(contentIntent, true)
            .setContentIntent(contentIntent)// Acciones rápidas sin abrir la app (via MedicationActionReceiver).
            .addAction(0, "Ya la tomé", actionPendingIntent(
                context, MedicationNotificationContract.ACTION_MARK_TAKEN, tomaId, notificationId
            ))
            .addAction(0, "Omitir", actionPendingIntent(
                context, MedicationNotificationContract.ACTION_MARK_OMITTED, tomaId, notificationId
            ))
            .build()

        try { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        catch (_: SecurityException) { /* El usuario puede revocar el permiso mientras se prepara el aviso. */ }
    }

    fun cancelar(context: Context, tomaId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(tomaId))
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
