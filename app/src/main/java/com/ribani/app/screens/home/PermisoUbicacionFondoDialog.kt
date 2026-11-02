package com.ribani.app.screens.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ribani.app.sensors.MonitoreoController
import kotlinx.coroutines.launch

private const val PREFS = "ribani_permisos"
private const val CLAVE_PREGUNTADO = "ubicacion_fondo_preguntada"

/**
 * Pide "permitir todo el tiempo" una sola vez, explicando para qué. Sin este
 * permiso la ubicación se reanuda al abrir la app, no sola tras un reinicio.
 */
@Composable
fun PermisoUbicacionFondoDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(debePreguntar(context)) }
    val solicitar = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        scope.launch { MonitoreoController.sincronizar(context) }
    }

    if (!visible) return

    // Solo cuenta una respuesta explícita: si el diálogo se cierra sin que el
    // adulto lo vea (p. ej. con el teléfono bloqueado), se vuelve a preguntar.
    fun responder() {
        visible = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(CLAVE_PREGUNTADO, true).apply()
    }

    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text("Tu ubicación en todo momento", fontSize = 24.sp) },
        text = {
            Text(
                "Para que tu familiar sepa dónde estás si te pasa algo, incluso " +
                    "después de reiniciar el teléfono, elige \"Permitir todo el tiempo\" " +
                    "en la siguiente pantalla.",
                fontSize = 18.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                responder()
                solicitar.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }) { Text("Continuar", fontSize = 18.sp) }
        },
        dismissButton = {
            TextButton(onClick = { responder() }) { Text("Ahora no", fontSize = 18.sp) }
        },
    )
}

private fun debePreguntar(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val permitido = { permiso: String ->
        ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED
    }
    if (permitido(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return false
    if (!permitido(Manifest.permission.ACCESS_FINE_LOCATION) &&
        !permitido(Manifest.permission.ACCESS_COARSE_LOCATION)
    ) {
        return false
    }
    return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(CLAVE_PREGUNTADO, false)
}
