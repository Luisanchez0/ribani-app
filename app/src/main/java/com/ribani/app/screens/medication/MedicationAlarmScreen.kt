package com.ribani.app.screens.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Pantalla de alarma de toma de medicamento, al estilo de FallAlertScreen:
 * mensaje grande + botones de confirmación (TOMAR / OMITIR) +
 * cuenta regresiva de la ventana para registrar la toma.
 */
@Composable
fun MedicationAlarmScreen(
    nombreMedicamento: String,
    dosis: String,
    horaProgramada: String,
    /** Instrucciones del medicamento (p. ej. "Tomar con alimentos"). */
    instrucciones: String? = null,
    countDownSeconds: Int,
    onTaken: () -> Unit,
    onOmit: () -> Unit,
    ocupado: Boolean = false,
    error: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFE3E0FF))
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Recordatorio de medicamento",
            fontSize = 28.sp
        )

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Text(
            text = nombreMedicamento,
            fontSize = 24.sp,
        )

        if (dosis.isNotBlank()) {
            Text(
                text = dosis,
                fontSize = 21.sp
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = "Programada para las $horaProgramada",
            fontSize = 18.sp
        )

        if (!instrucciones.isNullOrBlank()) {
            Spacer(
                modifier = Modifier.height(10.dp)
            )
            Text(
                text = instrucciones,
                fontSize = 17.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF444444),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(35.dp)
        )

        Button(
            onClick = onTaken,
            enabled = !ocupado,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF006B3C)
            )
        ) {
            Text(
                text = if (ocupado) "Guardando…" else "YA LA TOMÉ",
                fontSize = 20.sp
            )
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onOmit,
            enabled = !ocupado,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = "OMITIR ESTA TOMA",
                fontSize = 20.sp,
                color = Color(0xFFC62828)
            )
        }

        Spacer(
            modifier = Modifier.height(50.dp)
        )

        error?.let { Text(it, color = Color(0xFFC62828), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Text(
            text = "Puedes registrar la toma dentro del horario permitido.",
            fontSize = 18.sp
        )
    }
}
