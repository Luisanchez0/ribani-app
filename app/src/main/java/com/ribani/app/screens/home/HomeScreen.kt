package com.ribani.app.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.ui.theme.ContenedorRojoSuave
import com.ribani.app.ui.theme.ContenedorVerdeSuave
import com.ribani.app.ui.theme.Rojo
import com.ribani.app.ui.theme.TextoPrincipal
import com.ribani.app.ui.theme.VerdeClaro
import com.ribani.app.ui.theme.VerdeEstadoOk
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay


@Composable
fun HomeScreen(
    onSosClick: () -> Unit,
    /** Atenúa el modal de bloqueo: lleva a la pantalla de Invitaciones
     *  para vincularse con un FAMILIAR_ENCARGADO responsable. */
    onOpenInvitaciones: () -> Unit = {},
    isMonitoring: Boolean,
    /** null = sin respuesta del backend (offline/cargando);
     *  false = el adulto no tiene un FAMILIAR_ENCARGADO con vínculo ACTIVA. */
    proteccionActiva: Boolean? = null
) {
    val sinProteccion = proteccionActiva == false
    val currentTime = remember { mutableStateOf(LocalTime.now()) }  

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = LocalTime.now()
            delay(1_000)
        }
    }

    var mostrarModalBloqueo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Fondo rojizo suave cuando el adulto mayor aún no tiene un
            // FAMILIAR_ENCARGADO con vínculo ACTIVA (protección no configurada).
            .background(if (sinProteccion) Rojo.copy(alpha = 0.08f) else VerdeClaro.copy(alpha = 0.12f))
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(48.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.HealthAndSafety,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = VerdeClaro
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "RIBANI",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal
                    )
                    Text(
                        text = "Guardia de caídas",
                        fontSize = 17.sp,
                        color = TextoPrincipal
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Estado del sistema: chip flotante sobre la tarjeta
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (sinProteccion) ContenedorRojoSuave else ContenedorVerdeSuave)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (isMonitoring) VerdeEstadoOk else Rojo)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when {
                        sinProteccion -> "Sistema desactivado • Sin familiar encargado"
                        isMonitoring -> "Sistema activado y monitoreando"
                        else -> "Sensores no disponibles"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextoPrincipal
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta de estado principal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = currentTime.value.format(
                        DateTimeFormatter.ofPattern("hh:mm a", LocalConfiguration.current.locales[0])
                    ),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextoPrincipal
                )

                Spacer(modifier = Modifier.height(24.dp))

                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = if (isMonitoring) "Todo está bien" else "Atención requerida",
                    modifier = Modifier.size(84.dp),
                    tint = if (isMonitoring) VerdeEstadoOk else Rojo
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isMonitoring) "Todo está bien" else "Monitoreo inactivo",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextoPrincipal
                )

                Text(
                    text = when {
                        sinProteccion -> "No tienes un familiar encargado vinculado"
                        isMonitoring -> "No hay alertas recientes"
                        else -> "Revisa la conexión de sensores"
                    },
                    fontSize = 18.sp,
                    color = Color(0xFF697077),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // Sin vínculo con un FAMILIAR_ENCARGADO la función SOS
                    // está bloqueada: se muestra un modal explicativo en
                    // lugar de enviar la alerta.
                    if (sinProteccion) {
                        mostrarModalBloqueo = true
                    } else {
                        onSosClick()
                    }
                },
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Rojo
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Sos,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                    Text(
                        text = "SOS",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Toca el botón para pedir ayuda de inmediato",
                fontSize = 14.sp,
                color = Color(0xFF697077)
            )

            if (sinProteccion) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Función SOS bloqueada", 
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Rojo
                )
            }
        }
    }

    // Modal: el botón SOS está bloqueado mientras el adulto mayor no esté
    // vinculado (vínculo ACTIVA) con un FAMILIAR_ENCARGADO responsable.
    if (mostrarModalBloqueo) {
        AlertDialog(
            onDismissRequest = { mostrarModalBloqueo = false },
            title = {
                Text(
                    text = "Función bloqueada",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "El botón SOS está bloqueado porque aún no tienes un " +
                        "familiar responsable vinculado. Activa la protección " +
                        "conectándote con tu FAMILIAR_ENCARGADO para poder " +
                        "usar esta función.",
                    fontSize = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { mostrarModalBloqueo = false }) {
                    Text(
                        text = "Entendido",
                        fontSize = 18.sp,
                        color = Rojo
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    mostrarModalBloqueo = false
                    onOpenInvitaciones()
                }) {
                    Text(
                        text = "Vincular mi familiar responsable",
                        fontSize = 18.sp,
                        color = VerdeClaro
                    )
                }
            }
        )
    }
}
