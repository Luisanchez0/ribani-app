package com.ribani.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ribani.app.screens.fall.AlertaCaidaScreen
import com.ribani.app.sensors.AlarmaCaida
import com.ribani.app.ui.theme.RIBANITheme

/**
 * Se abre sobre la pantalla de bloqueo: el adulto responde sin desbloquear. Al
 * cerrarse vuelve al bloqueo, sin exponer el resto de la aplicación.
 */
class FallAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        // Sin esto, la pantalla se apagaría antes de agotar los 30 s de la alerta.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            RIBANITheme {
                AlertaCaidaScreen(
                    onRespondio = { AlarmaCaida.detener(this) },
                    onCerrar = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) AlarmaCaida.detener(this)
        super.onDestroy()
    }
}
