package com.ribani.app.screens.fall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.repository.AlertasRepository
import com.ribani.app.data.repository.EstadoAyuda
import com.ribani.app.ui.theme.Rojo
import com.ribani.app.ui.theme.VerdeRIBANI
import kotlinx.coroutines.launch

/** Botón SOS: la ayuda se pide de inmediato, sin preguntas intermedias. */
@Composable
fun SosScreen(onCerrar: () -> Unit) {
    val context = LocalContext.current
    var solicitada by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!solicitada) {
            solicitada = true
            AlertasRepository.pedirAyuda(context, trasCaida = false)
        }
    }
    SolicitudAyudaContenido(onCerrar = onCerrar)
}

/**
 * Alerta de posible caída: pregunta al adulto y, si pide ayuda, mantiene la
 * pantalla abierta mostrando si el aviso llegó de verdad.
 */
@Composable
fun AlertaCaidaScreen(
    onCerrar: () -> Unit,
    /** El adulto ya contestó: es el momento de callar la alarma. */
    onRespondio: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pidioAyuda by rememberSaveable { mutableStateOf(false) }

    if (pidioAyuda) {
        SolicitudAyudaContenido(onCerrar = onCerrar)
        return
    }

    FallAlertScreen(
        onOkay = {
            onRespondio()
            // La cola es de la aplicación: cerrar la pantalla no cancela el envío.
            scope.launch {
                AlertasRepository.confirmarBien(context)
                onCerrar()
            }
        },
        onNeedHelp = {
            onRespondio()
            pidioAyuda = true
            scope.launch { AlertasRepository.pedirAyuda(context, trasCaida = true) }
        },
        onTiempoAgotado = onCerrar,
    )
}

@Composable
private fun SolicitudAyudaContenido(onCerrar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado by AlertasRepository.estadoAyuda.collectAsState()

    DisposableEffect(Unit) {
        onDispose { AlertasRepository.olvidarEstadoAyuda() }
    }

    SolicitudAyudaPantalla(
        estado = estado,
        onReintentar = { AlertasRepository.reintentarAhora() },
        onCancelar = {
            scope.launch {
                AlertasRepository.cancelarAyuda(context)
                onCerrar()
            }
        },
        onCerrar = onCerrar,
    )
}

@Composable
fun SolicitudAyudaPantalla(
    estado: EstadoAyuda,
    onReintentar: () -> Unit,
    onCancelar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val titulo = when (estado) {
        EstadoAyuda.Enviada -> "Aviso enviado a tu familiar"
        EstadoAyuda.Reintentando -> "Aún no se pudo enviar"
        EstadoAyuda.Rechazada -> "No se pudo enviar el aviso"
        else -> "Pidiendo ayuda…"
    }
    val detalle = when (estado) {
        EstadoAyuda.Enviada -> "Tu familiar ya puede ver tu alerta en su panel."
        EstadoAyuda.Reintentando ->
            "Seguimos intentando aunque cierres esta pantalla. " +
                "Si es urgente, llama a tu familiar o a emergencias."
        EstadoAyuda.Rechazada ->
            "Llama a tu familiar o a emergencias. RIBANI no pudo registrar tu alerta."
        else -> "Estamos avisando a tu familiar."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (estado) {
            EstadoAyuda.Enviada -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(90.dp),
                tint = VerdeRIBANI,
            )
            EstadoAyuda.Reintentando, EstadoAyuda.Rechazada -> Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                modifier = Modifier.size(90.dp),
                tint = Rojo,
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                color = Rojo,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = titulo,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = detalle, fontSize = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(35.dp))

        if (estado == EstadoAyuda.Reintentando) {
            Button(
                onClick = onReintentar,
                modifier = Modifier.fillMaxWidth().heightIn(min = 65.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Rojo),
            ) {
                Text("REINTENTAR AHORA", fontSize = 20.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onCerrar,
            modifier = Modifier.fillMaxWidth().heightIn(min = 65.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VerdeRIBANI),
        ) {
            Text("VOLVER AL INICIO", fontSize = 20.sp)
        }

        if (estado != EstadoAyuda.Rechazada) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCancelar,
                modifier = Modifier.fillMaxWidth().heightIn(min = 65.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Fue sin querer, cancelar", fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
