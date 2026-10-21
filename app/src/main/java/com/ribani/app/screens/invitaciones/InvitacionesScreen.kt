package com.ribani.app.screens.invitaciones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.repository.InvitacionesRepository
import com.ribani.app.data.repository.InvitacionesUiState
import com.ribani.app.data.remote.InvitacionItem
import kotlinx.coroutines.launch

private val VerdeRIBANI = Color(0xFF006B3C)
private val RojoRIBANI = Color(0xFFB3261E)

@Composable
fun InvitacionesScreen(
    onBack: () -> Unit,
    onInvitacionAceptada: () -> Unit = {}
) {
    val uiState by InvitacionesRepository.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        InvitacionesRepository.cargar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 22.dp)
    ) {
        Text("Invitaciones", fontSize = 32.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(18.dp))

        // Siempre visible: errores de carga O de aceptar/rechazar.
        uiState.error?.let {
            Text(
                it,
                color = RojoRIBANI,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        when {
            uiState.cargando -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = VerdeRIBANI)
                }
            }

            uiState.error != null -> {
                Text(
                    "Error: ${uiState.error}",
                    color = RojoRIBANI,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {	onBack() }) { Text("Volver") }
            }

            uiState.invitations.isEmpty() -> {
                Text(
                    "No tienes invitaciones pendientes.",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("Volver") }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    uiState.invitations.forEach { invitacion ->
                        InvitacionCard(
                            invitacion = invitacion,
                            procesando = uiState.procesandoId == invitacion.id,
                            onAceptar = {
                                scope.launch { InvitacionesRepository.aceptar(invitacion.id) }
                            },
                            onRechazar = {
                                scope.launch { InvitacionesRepository.rechazar(invitacion.id) }
                            }
                        )
                    }
                }

                uiState.mensaje?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = VerdeRIBANI, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun InvitacionCard(
    invitacion: InvitacionItem,
    procesando: Boolean,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    val familiar = invitacion.usuarios_adulto_familiares_familiar_idTousuarios

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE7E7E7), RoundedCornerShape(26.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.GroupAdd,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF616161), RoundedCornerShape(50)),
                tint = Color.White
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(familiar.nombre, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${familiar.nombre} ${familiar.apellido}",
                    fontSize = 14.sp,
                    color = Color(0xFF333333)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "${familiar.nombre} quiere acompañar y cuidar de ti desde la app.",
            fontSize = 15.sp,
            color = Color(0xFF333333)
        )

        if (procesando) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = VerdeRIBANI, modifier = Modifier.size(22.dp))
        } else {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAceptar,
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeRIBANI),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Aceptar")
                }
                OutlinedButton(
                    onClick = onRechazar,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Rechazar", color = RojoRIBANI)
                }
            }
        }
    }
}
