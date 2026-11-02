package com.ribani.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.MedicamentoResponse
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.MedicationRepository
import com.ribani.app.notifications.MedicationAlarmNotifications
import com.ribani.app.screens.medication.MedicationAlarmScreen
import com.ribani.app.screens.medication.MedicationAlarmVerificando
import com.ribani.app.ui.theme.RIBANITheme

/**
 * Activity full-screen de alarma de medicamento (equivalente a
 * FallAlertActivity pero para tomas): se enciende la pantalla y muestra
 * sobre lock screen cuando el MedicationAlarmReceiver dispara la
 * notificación full-screen. Necesita la toma que se está notificando
 * (id del backend) para poder registrar TOMADA/OMITIDA directamente.
 */
class MedicationAlarmActivity : ComponentActivity() {
    private var notificacionActual = DEFAULT_NOTIFICATION_ID

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // La alarma puede lanzar esta Activity con el proceso recién creado
        // (app muerta): inicializa ApiClient/SessionManager antes de registrar
        // TOMADA/OMITIDA contra el backend.
        ApiClient.init(applicationContext)

        // Encender pantalla y mostrar sobre lock screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Encendida mientras suena; después se apaga sola para no agotar la batería.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            delay(MedicationAlarmNotifications.DURACION_ALARMA_MS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val notificationId = intent?.getIntExtra(
            MedicationAlarmNotifications.EXTRA_NOTIFICATION_ID,
            DEFAULT_NOTIFICATION_ID
        ) ?: DEFAULT_NOTIFICATION_ID
        notificacionActual = notificationId

        val tomaId = intent?.getStringExtra(EXTRA_TOMA_ID)
        val nombre = intent?.getStringExtra(EXTRA_NOMBRE_MEDICAMENTO).orEmpty()
        val dosis = intent?.getStringExtra(EXTRA_DOSIS_MEDICAMENTO).orEmpty()
        val horaProgramada = intent?.getStringExtra(EXTRA_HORA_PROGRAMADA).orEmpty()
        val ventanaSeg = intent?.getIntExtra(EXTRA_VENTANA_SEGUNDOS, VENTANA_DEFAULT)
            ?: VENTANA_DEFAULT
        val instrucciones = intent?.getStringExtra(
            MedicationAlarmNotifications.EXTRA_INSTRUCCIONES_MEDICAMENTO
        )

        setContent {
            RIBANITheme {
                var medicamento by remember { mutableStateOf<MedicamentoResponse?>(null) }
                var intento by remember { mutableIntStateOf(0) }
                var errorVerificacion by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(tomaId, intento) {
                    val usuario = SessionManager(applicationContext).currentUser()
                    if (usuario == null || tomaId == null) { finish(); return@LaunchedEffect }
                    try {
                        errorVerificacion = null
                        val toma = ApiClient.medicamentosService.obtenerToma(tomaId)
                        val m = ApiClient.medicamentosService.obtenerMedicamento(toma.medicamento_id)
                        if (toma.adulto_id != usuario.id || SessionManager(applicationContext).currentUser()?.id != usuario.id || toma.estado !in listOf("PENDIENTE", "POSPUESTA") || !m.activo) {
                            dismissNotification(notificationId); finish()
                        } else medicamento = m
                    } catch (e: Exception) { errorVerificacion = "No se pudo verificar el recordatorio. Revisa tu conexión e inténtalo de nuevo." }
                }
                LaunchedEffect(Unit) {
                    SessionManager(applicationContext).isLoggedIn.collect { if (!it) finish() }
                }
                if (medicamento == null) {
                    MedicationAlarmVerificando(
                        error = errorVerificacion,
                        onReintentar = { intento++ },
                    )
                    return@RIBANITheme
                }
                var ocupado by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                fun registrar(estado: String) {
                    if (tomaId == null || ocupado) return
                    scope.launch {
                        ocupado = true; error = null
                        val guardada = MedicationRepository.registrarDesdeAlarma(applicationContext, tomaId, estado)
                        ocupado = false
                        if (guardada) { dismissNotification(notificationId); finish() }
                        else error = "No se guardó la toma. Revisa tu conexión e inténtalo de nuevo."
                    }
                }
                MedicationAlarmScreen(
                    nombreMedicamento = medicamento!!.nombre,
                    dosis = "${medicamento!!.dosis} ${medicamento!!.unidad_dosis}",
                    horaProgramada = horaProgramada,
                    instrucciones = medicamento!!.instrucciones,
                    countDownSeconds = ventanaSeg,
                    ocupado = ocupado,
                    error = error,
                    onTaken = {
                        registrar("TOMADA")
                    },
                    onOmit = {
                        registrar("OMITIDA")
                    }
                )
            }
        }
    }

    /** Al salir sin responder (botón Atrás) la alarma no debe seguir sonando. */
    override fun onDestroy() {
        if (isFinishing) dismissNotification(notificacionActual)
        super.onDestroy()
    }

    /** Cancela la notificación de la alarma al atenderla en pantalla. */
    private fun dismissNotification(notificationId: Int) {
        MedicationAlarmNotifications.detener(this, notificationId)
    }

    private companion object {
        const val DEFAULT_NOTIFICATION_ID = 200
        const val VENTANA_DEFAULT = 60
        const val EXTRA_TOMA_ID = "extra_toma_id"
        const val EXTRA_NOMBRE_MEDICAMENTO = "extra_nombre_medicamento"
        const val EXTRA_DOSIS_MEDICAMENTO = "extra_dosis_medicamento"
        const val EXTRA_HORA_PROGRAMADA = "extra_hora_programada"
        const val EXTRA_VENTANA_SEGUNDOS = "extra_ventana_segundos"
    }
}
