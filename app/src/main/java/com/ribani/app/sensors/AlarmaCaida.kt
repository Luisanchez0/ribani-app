@file:SuppressLint("MissingPermission", "UseFullScreenIntent")

package com.ribani.app.sensors

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ribani.app.FallAlertActivity
import com.ribani.app.R
import com.ribani.app.notifications.SonidoAlarma

/**
 * Alarma de posible caída: abre [FallAlertActivity] sobre la pantalla de bloqueo
 * y suena sin parar, como un despertador, hasta que el adulto responde. Debe
 * poder atenderse sin desbloquear el teléfono.
 */
object AlarmaCaida {
    /** Android no deja cambiar el sonido de un canal ya creado: por eso es uno nuevo. */
    private const val CANAL = "ribani_aviso_caida"
    private val CANALES_ANTERIORES = listOf("ribani_emergency", "ribani_alarma_caida")
    private const val NOTIFICACION = 101
    private const val SOLICITUD = 102

    /** Margen sobre los 30 s de la pantalla, por si no llegó a abrirse. */
    private const val DURACION_MAXIMA_MS = 60_000L

    fun crearCanal(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        CANALES_ANTERIORES.forEach { manager.deleteNotificationChannel(it) }
        manager.createNotificationChannel(
            NotificationChannel(CANAL, "Alarma de caída", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Abre la alerta sobre la pantalla de bloqueo."
                // Sin sonido propio: lo pone SonidoAlarma, que nada puede interrumpir.
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun mostrar(context: Context) {
        crearCanal(context)
        val intent = Intent(context, FallAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SOLICITUD,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Posible caída detectada")
            .setContentText("¿Te encuentras bien? Toca para responder.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setTimeoutAfter(DURACION_MAXIMA_MS)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        SonidoAlarma.iniciar(context, NOTIFICACION, DURACION_MAXIMA_MS)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICACION, notificacion)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones; la apertura directa de abajo sigue siendo posible.
        }

        // Con el teléfono en uso Android solo muestra un aviso arriba; con RIBANI
        // abierta esto la lleva a la pantalla completa. Si no se permite, no pasa nada.
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            // Android bloquea abrir pantallas desde segundo plano; queda la notificación.
        }
    }

    fun detener(context: Context) {
        SonidoAlarma.detener(context, NOTIFICACION)
        NotificationManagerCompat.from(context).cancel(NOTIFICACION)
    }
}
