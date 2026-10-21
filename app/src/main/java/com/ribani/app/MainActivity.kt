package com.ribani.app

import android.app.AlarmManager
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.navigation.AppNavigation
import com.ribani.app.data.repository.MedicationRepository
import com.ribani.app.notifications.MedicationNotificationContract
import com.ribani.app.sensors.RibaniMonitoringService
import com.ribani.app.ui.theme.RIBANITheme

class MainActivity : ComponentActivity() {
    private var startWithFallAlert = false
    private var initialMedicationId: String? = null
    private var startWithInvitaciones = false

    /**
     * Android 17 (SDK 37)+: conectar por una IP privada requiere
     * ACCESS_LOCAL_NETWORK. Sin permiso, toda petición al servidor
     * queda sin respuesta (timeout), aunque Internet funcione.
     */
    private var rechazoPrevioRedLocal by mutableStateOf(false)
    private var redLocalArrancada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resolveLaunchIntent(intent, savedInstanceState)
        ApiClient.init(applicationContext)

        if (requierePermisoRedLocal() && !tienePermisoRedLocal()) {
            iniciarSolicitudPermisoRedLocal()
            return
        }

        iniciarAplicacion()
    }

    override fun onResume() {
        super.onResume()
        // Cubre el regreso desde Ajustes: si el permiso ya fue concedido,
        // reinicia para ejecutar el arranque completo de la app.
        if (!redLocalArrancada &&
            requierePermisoRedLocal() &&
            tienePermisoRedLocal()
        ) {
            recreate()
        }
    }

    private fun requierePermisoRedLocal(): Boolean =
        Build.VERSION.SDK_INT >= 37

    private fun tienePermisoRedLocal(): Boolean =
        ContextCompat.checkSelfPermission(this, PERMISO_RED_LOCAL) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Retrasa cargar medicamentos, validar sesión e iniciar solicitudes
     * al backend hasta que la red local esté permitida.
     */
    private fun iniciarSolicitudPermisoRedLocal() {
        requestPermissions(arrayOf(PERMISO_RED_LOCAL), REQ_PERMISO_RED_LOCAL)
        setContent {
            RIBANITheme {
                PantallaPermisoRedLocal(
                    rechazado = rechazoPrevioRedLocal,
                    onSolicitarDeNuevo = {
                        rechazoPrevioRedLocal = false
                        requestPermissions(arrayOf(PERMISO_RED_LOCAL), REQ_PERMISO_RED_LOCAL)
                    },
                    onAbrirAjustes = { abrirAjustesDeLaApp() }
                )
            }
        }
    }

    private fun abrirAjustesDeLaApp() {
        try {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            rechazoPrevioRedLocal = true
        }
    }

    private fun iniciarAplicacion() {
        redLocalArrancada = true
        // Solicitud única al abrir la app: ubicación + notificaciones.
        // Así el panel de Ubicación no crashea pidiendo permiso al abrirse
        // (en reinstalaciones) y los permisos se conceden de una sola vez.
        pedirPermisosIniciales()
        MedicationRepository.initialize(applicationContext)
        startMonitoringService()

        setContent {
            RIBANITheme {
                AppNavigation(
                    startWithFallAlert = startWithFallAlert,
                    initialMedicationId = initialMedicationId,
                    startWithInvitaciones = startWithInvitaciones
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISOS_INICIALES) {
            // Sin acción: LocationScreen y las notificaciones consultan
            // el estado real del permiso al usarse.
            return
        }
        if (requestCode != REQ_PERMISO_RED_LOCAL) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreate()
            return
        }
        rechazoPrevioRedLocal = true
    }

    @Composable
    private fun PantallaPermisoRedLocal(
        rechazado: Boolean,
        onSolicitarDeNuevo: () -> Unit,
        onAbrirAjustes: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Acceso a la red local",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "RIBANI necesita conectarse a su servidor dentro de la red Wi-Fi local. " +
                    "Concede el permiso de red local para continuar.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (rechazado) {
                Text(
                    "El permiso fue rechazado. Solicitar en Ajustes, en " +
                        "Permisos > Red local.",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = onSolicitarDeNuevo,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {
                Text("Solicitar de nuevo")
            }
            Button(
                onClick = onAbrirAjustes,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
            ) {
                Text("Abrir Ajustes")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_FALL_ALERT ||
            intent.action == MedicationNotificationContract.ACTION_OPEN_MEDICATION ||
            intent.action == Intent.ACTION_VIEW
        ) {
            resolveLaunchIntent(intent, null)
            recreate()
        }
    }

    private fun resolveLaunchIntent(intent: Intent?, savedInstanceState: Bundle?) {
        startWithFallAlert = intent?.action == ACTION_SHOW_FALL_ALERT ||
            savedInstanceState?.getBoolean(EXTRA_SHOW_FALL_ALERT, false) == true
        initialMedicationId = if (intent?.action == MedicationNotificationContract.ACTION_OPEN_MEDICATION) {
            intent.getStringExtra(MedicationNotificationContract.EXTRA_MEDICATION_ID)
        } else {
            null
        }
        startWithInvitaciones =
            savedInstanceState?.getBoolean(EXTRA_SHOW_INVITACIONES, false) == true ||
            (intent?.action == Intent.ACTION_VIEW &&
                intent.data?.host == HOST_INVITACIONES)
    }

    private fun pedirPermisosIniciales() {
        val pendientes = mutableListOf<String>()

        // Ubicación: la capa "mi ubicación" del mapa en el panel de
        // Ubicación crashea (SecurityException) si se solicita sin permiso.
        val tieneUbicacion =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!tieneUbicacion) {
            pendientes += Manifest.permission.ACCESS_FINE_LOCATION
            pendientes += Manifest.permission.ACCESS_COARSE_LOCATION
        }

        // Android 13+: notificaciones de medicamentos y alertas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            pendientes += Manifest.permission.POST_NOTIFICATIONS
        }

        if (pendientes.isEmpty()) return
        requestPermissions(pendientes.toTypedArray(), REQ_PERMISOS_INICIALES)
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, RibaniMonitoringService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // POST_NOTIFICATIONS ya se pide de una vez junto con ubicación
        // en pedirPermisosIniciales (al abrir la app), evitando diálogos
        // duplicados.

        pedirPermisoAlarmasExactas()
    }

    /**
     * Android 12+ (crítico en 14+): sin SCHEDULE_EXACT_ALARM concedida,
     * setAlarmClock lanza SecurityException y las alarmas de medicamento
     * no llegan. Envía al usuario al diálogo del sistema para
     * "Permitir alarmas y recordatorios". Es una única concesión
     * persistente en Settings, no un requestPermissions.
     */
    private fun pedirPermisoAlarmasExactas() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        if (!alarmManager.canScheduleExactAlarms()) {
            try {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                // Algunos OEMs no resuelven el intent; el fallback
                // setWindow del scheduler cubre alarma no exacta.
            }
        }
    }

    companion object {
        const val ACTION_SHOW_FALL_ALERT = "com.ribani.app.SHOW_FALL_ALERT"
        private const val EXTRA_SHOW_FALL_ALERT = "show_fall_alert"
        private const val EXTRA_SHOW_INVITACIONES = "show_invitaciones"
        const val HOST_INVITACIONES = "invitaciones"
        private const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val REQ_PERMISO_RED_LOCAL = 201
        private const val REQ_PERMISOS_INICIALES = 202
    }
}