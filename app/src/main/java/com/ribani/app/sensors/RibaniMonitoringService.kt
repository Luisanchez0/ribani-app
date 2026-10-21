@file:SuppressLint("MissingPermission", "UseFullScreenIntent")

package com.ribani.app.sensors

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ribani.app.FallAlertActivity
import com.ribani.app.R
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AlertasRepository
import com.ribani.app.data.repository.DispositivosRepository
import com.ribani.app.data.repository.UbicacionesEscucha
import com.ribani.app.data.repository.UbicacionesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: monitoreo de caídas (RIBANI_SENSOR) + escucha
 * continua de ubicación (UbicacionesRepository / POST /ubicaciones en cada
 * fix) mientras la app está abierta o en segundo plano.
 */
class RibaniMonitoringService : Service() {
    private lateinit var sensorManager: RibaniSensorManager
    private var escuchaUbicaciones: UbicacionesEscucha? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = RibaniSensorManager(applicationContext) {
            showFallAlert()
        }
        startForeground(NOTIFICATION_ID, monitoringNotification())
        sensorManager.start()
        // Registro del teléfono y latido periódico para el panel web (Dispositivos).
        serviceScope.launch {
            while (isActive) {
                DispositivosRepository.sincronizar(applicationContext)
                delay(INTERVALO_LATIDO_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ubicaciones: solo si hay sesión con un adulto mayor autenticado.
        // Se chequea en cada onStartCommand para detener/arrancar tras login o logout.
        serviceScope.launch {
            val haySesion = SessionManager(applicationContext).isLoggedIn.first()
            if (haySesion) {
                arrancarEscuchaSiHaceFalta()
            } else {
                detenerEscucha()
            }
        }
        return START_STICKY
    }

    private fun arrancarEscuchaSiHaceFalta() {
        val hayPermiso = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        if (!hayPermiso) return

        if (escuchaUbicaciones == null) {
            UbicacionesRepository.inicializar(applicationContext)
            escuchaUbicaciones = UbicacionesRepository.iniciarEscucha(applicationContext)
            escuchaUbicaciones?.iniciar()
        }
    }

    private fun detenerEscucha() {
        escuchaUbicaciones?.detener()
        escuchaUbicaciones = null
    }

    override fun onDestroy() {
        detenerEscucha()
        sensorManager.stop()
        serviceScope.cancel()
        UbicacionesRepository.destructor()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFallAlert() {
        // La alerta CAIDA se registra en el backend al detectarla (regla:
        // toda posible caída deja alerta abierta), independientemente de
        // la confirmación del adulto en la pantalla.
        serviceScope.launch {
            AlertasRepository.enviarAlerta(
                context = this@RibaniMonitoringService,
                tipo = "CAIDA",
                titulo = "Posible caída detectada",
                descripcion = "RIBANI detectó una posible caída del adulto mayor " +
                    "(caída libre, impacto, rotación e inactividad).",
            )
        }

        val intent = Intent(this, FallAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            FALL_ALERT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Posible caída detectada")
            .setContentText("¿Te encuentras bien?")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()


        if (canPostNotifications()) {
            NotificationManagerCompat.from(this).notify(ALERT_NOTIFICATION_ID, notification)
        }
    }

    private fun monitoringNotification(): Notification =
        NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RIBANI activo")
            .setContentText("Monitoreando sensores de seguridad")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MONITORING_CHANNEL_ID,
                "Monitoreo de RIBANI",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Alertas de emergencia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MONITORING_CHANNEL_ID = "ribani_monitoring"
        const val ALERT_CHANNEL_ID = "ribani_emergency"
        const val NOTIFICATION_ID = 100
        const val ALERT_NOTIFICATION_ID = 101
        const val FALL_ALERT_REQUEST_CODE = 102
        const val INTERVALO_LATIDO_MS = 5 * 60 * 1000L
    }
}
