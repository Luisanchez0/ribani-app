package com.ribani.app.screens.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.repository.MedicationStatus
import com.ribani.app.data.repository.MedicationUiState

@Composable
fun MedicationDetailScreen(
    medicationId: String,
    uiState: MedicationUiState,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onTaken: (String) -> Unit,
    onPostpone: (String) -> Unit,
    onOmit: (String) -> Unit
) {
    val medication = uiState.medications.find { it.id == medicationId }
        ?: uiState.medications.firstOrNull()

    val pendingOccurrence = uiState.pendingMedications.find { it.medicationId == medicationId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "Detalle de Medicamento",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        if (medication != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECECFD))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = com.ribani.app.ui.theme.VerdeRIBANI
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = medication.name,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = medication.dosage,
                                fontSize = 20.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Frecuencia",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = medication.schedule,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Instrucciones",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = medication.instructions.ifEmpty { "Sin instrucciones adicionales" },
                        fontSize = 20.sp
                    )
                }
            }

            if (pendingOccurrence != null) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Toma Pendiente Hoy",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Hora programada: ${pendingOccurrence.time}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onTaken(pendingOccurrence.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = com.ribani.app.ui.theme.VerdeBotonTomar),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Tomar", fontSize = 16.sp, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { onPostpone(pendingOccurrence.id) },
                                enabled = pendingOccurrence.status != MedicationStatus.POSTPONED,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Posponer", fontSize = 16.sp, color = Color.DarkGray)
                            }

                            OutlinedButton(
                                onClick = { onOmit(pendingOccurrence.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Omitir", fontSize = 16.sp, color = com.ribani.app.ui.theme.Rojo)
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Medicamento no encontrado entre tus datos.",
                fontSize = 18.sp,
                color = com.ribani.app.ui.theme.TextoSecundario,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )
        }
    }
}
