@file:SuppressLint("MissingPermission", "UseFullScreenIntent")

package com.ribani.app.sensors

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ribani.app.R
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AlertasRepository
import com.ribani.app.data.repository.DispositivosRepository
import com.ribani.app.data.repository.MedicationRepository
import com.ribani.app.data.repository.UbicacionesEscucha
import com.ribani.app.data.repository.UbicacionesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        iniciarPrimerPlano()
        sensorManager.start()
        MonitoreoEstado.marcar(
            activo = true,
            sensoresDisponibles = sensorManager.accelerometerAvailable &&
                sensorManager.gyroscopeAvailable,
        )
        // Registro del teléfono y latido periódico para el panel web (Dispositivos).
        serviceScope.launch {
            while (isActive) {
                DispositivosRepository.sincronizar(applicationContext)
                delay(INTERVALO_LATIDO_MS)
            }
        }
        // Mantiene las alarmas al día aunque nadie abra la app. No hay avisos del
        // servidor: la familia puede crear una toma para dentro de un minuto, y este
        // intervalo marca cuánto tarda en enterarse el teléfono.
        serviceScope.launch {
            while (isActive) {
                MedicationRepository.refrescar(applicationContext, enSegundoPlano = true)
                delay(INTERVALO_MEDICAMENTOS_MS)
            }
        }
        // La sesión puede terminar con la app cerrada: al salir deja de vigilar y de usar el GPS.
        serviceScope.launch {
            SessionManager(applicationContext).isLoggedIn.collect {
                if (!MonitoreoController.debeMonitorear(applicationContext)) stopSelf()
            }
        }
    }

    /**
     * El tipo `location` permite recibir ubicación con la app en segundo plano.
     * Android lo rechaza si no hay permiso, o si el servicio arrancó sin la app
     * abierta y no hay permiso de ubicación "todo el tiempo": entonces se sigue
     * vigilando las caídas y la ubicación se retoma al abrir la app.
     */
    private fun iniciarPrimerPlano() {
        val tipoBase = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (hayPermisoUbicacion()) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    monitoringNotification(),
                    tipoBase or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
                return
            } catch (_: SecurityException) {
                // Sigue con el tipo base.
            }
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, monitoringNotification(), tipoBase)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Aplica permisos concedidos después de arrancar el servicio.
        iniciarPrimerPlano()
        serviceScope.launch {
            if (MonitoreoController.debeMonitorear(applicationContext)) {
                arrancarEscuchaSiHaceFalta()
            } else {
                detenerEscucha()
            }
        }
        return START_STICKY
    }

    private fun hayPermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun arrancarEscuchaSiHaceFalta() {
        if (!hayPermisoUbicacion()) return

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
        MonitoreoEstado.marcar(activo = false)
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
        // la confirmación del adulto en la pantalla. La cola la reintenta.
        serviceScope.launch {
            AlertasRepository.reportarCaida(this@RibaniMonitoringService)
        }

        AlarmaCaida.mostrar(this)
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
        AlarmaCaida.crearCanal(this)
    }

    private companion object {
        const val MONITORING_CHANNEL_ID = "ribani_monitoring"
        const val NOTIFICATION_ID = 100
        const val INTERVALO_LATIDO_MS = 5 * 60 * 1000L
        const val INTERVALO_MEDICAMENTOS_MS = 60 * 1000L
    }
}
