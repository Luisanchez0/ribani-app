package com.ribani.app.screens.medication

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.ui.theme.TextoOscuro
import com.ribani.app.ui.theme.TextoPrincipal
import com.ribani.app.ui.theme.TextoSecundario
import com.ribani.app.ui.theme.TextoAjeno
import com.ribani.app.ui.theme.SuperficieTarjetaTintada
import com.ribani.app.ui.theme.VerdeBotonTomar
import com.ribani.app.ui.theme.Rojo
import com.ribani.app.data.repository.MedicationStatus
import com.ribani.app.data.repository.MedicationOccurrence
import com.ribani.app.data.repository.MedicationUiState

@Composable
fun MedicationScreen(
    uiState: MedicationUiState,
    onOpenDetail: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onTaken: (String) -> Unit,
    onPostpone: (String) -> Unit,
    onOmit: (String) -> Unit,
    /** Pull-to-refresh: recarga tomas/medicamentos del backend. */
    onRefrescar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = TextoOscuro
                )
                Text(
                    text = "Medicamentos",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            IconButton(onClick = onOpenHistory) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "Historial",
                    modifier = Modifier.size(36.dp),
                    tint = TextoOscuro
                )
            }
        }

        Text(
            text = "Pendientes de Hoy",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Pull-to-refresh: deslizar hacia abajo recarga del backend
        // (igual que salir y entrar, sin salir de la pantalla).
        val refrescando = uiState.refrescando
        PullToRefreshBox(
            isRefreshing = refrescando,
            onRefresh = onRefrescar,
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.pendingMedications.isEmpty()) {
                ContenidoVacio(uiState)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.pendingMedications) { occurrence ->
                        MedicationCard(
                            occurrence = occurrence,
                            onClick = { onOpenDetail(occurrence.medicationId) },
                            onTaken = { onTaken(occurrence.id) },
                            onPostpone = { onPostpone(occurrence.id) },
                            onOmit = { onOmit(occurrence.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContenidoVacio(uiState: MedicationUiState) {
    com.ribani.app.ui.components.ContenidoVacio(
        mensaje = if (uiState.cargado) {
            "¡Al día! No tienes medicamentos pendientes por hoy."
        } else {
            "Aún no hay medicamentos para mostrar."
        },
        mensajeEstado = uiState.mensajeEstado
    )
}

@Composable
private fun MedicationCard(
    occurrence: MedicationOccurrence,
    onClick: () -> Unit,
    onTaken: () -> Unit,
    onPostpone: () -> Unit,
    onOmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SuperficieTarjetaTintada)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = occurrence.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal
                    )
                    Text(
                        text = "${occurrence.dosage} • ${occurrence.time}",
                        fontSize = 18.sp,
                        color = TextoSecundario
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTaken,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeBotonTomar),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tomar", fontSize = 16.sp, color = Color.White)
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp),
                    tint = Color.White
                )
                }

                OutlinedButton(
                    onClick = onPostpone,
                    enabled = occurrence.status != MedicationStatus.POSTPONED,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Posponer", fontSize = 16.sp, color = Color.DarkGray)
                }

                OutlinedButton(
                    onClick = onOmit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Omitir", fontSize = 16.sp, color = Rojo)
                }
            }
        }
    }
}
