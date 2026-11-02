package com.ribani.app.screens.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.ui.theme.ContenedorRojoSuave
import com.ribani.app.ui.theme.FondoApp
import com.ribani.app.ui.theme.Rojo
import com.ribani.app.ui.theme.SuperficieTarjetaTintada
import com.ribani.app.ui.theme.TextoAjeno
import com.ribani.app.ui.theme.TextoOscuro
import com.ribani.app.ui.theme.TextoPrincipal
import com.ribani.app.ui.theme.TextoSecundario
import com.ribani.app.ui.theme.VerdeBotonTomar
import com.ribani.app.ui.theme.VerdeRIBANI

/**
 *
 *
 * Pantalla de alarma de toma: sigue a "Medicamentos" y "Detalle de Medicamento"
 * (encabezado con icono, tarjeta tintada, mismos colores), con botones mayores
 * porque aquí la única tarea es responder.
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
    MarcoAlarma(
        acciones = {
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Rojo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ContenedorRojoSuave, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onTaken,
                enabled = !ocupado,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeBotonTomar),
            ) {
                if (!ocupado) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = if (ocupado) "Guardando…" else "Ya la tomé",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOmit,
                enabled = !ocupado,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = "Omitir esta toma",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Rojo,
                )
            }
        },
    ) {
        Encabezado()

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SuperficieTarjetaTintada),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Medication,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = VerdeRIBANI,
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = nombreMedicamento,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                        )
                        if (dosis.isNotBlank()) {
                            Text(text = dosis, fontSize = 20.sp, color = TextoSecundario)
                        }
                    }
                }

                if (horaProgramada.isNotBlank()) {
                    Dato(etiqueta = "Hora", valor = horaProgramada, tamano = 22)
                }
                if (!instrucciones.isNullOrBlank()) {
                    Dato(etiqueta = "Instrucciones", valor = instrucciones, tamano = 20)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Puedes registrar la toma dentro del horario permitido.",
            fontSize = 16.sp,
            color = TextoSecundario,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Lo que se ve mientras se comprueba la toma, antes de la pantalla de alarma. */
@Composable
fun MedicationAlarmVerificando(
    error: String?,
    onReintentar: () -> Unit,
) {
    MarcoAlarma {
        Encabezado()

        Spacer(Modifier.height(24.dp))

        if (error == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = VerdeRIBANI)
                Text(
                    text = "Comprobando tu recordatorio…",
                    fontSize = 20.sp,
                    color = TextoSecundario,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        } else {
            Text(
                text = error,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Rojo,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ContenedorRojoSuave, RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onReintentar,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeRIBANI),
            ) {
                Text("Volver a intentar", fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

/**
 * Centra el contenido y lo deja desplazable con fuente grande. Las acciones van
 * fijas abajo: responder a la alarma nunca debe exigir desplazarse.
 */
@Composable
private fun MarcoAlarma(
    acciones: @Composable () -> Unit = {},
    contenido: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoApp)
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val altoDisponible = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = altoDisponible)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                contenido()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        ) {
            acciones()
        }
    }
}

@Composable
private fun Encabezado() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Alarm,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = TextoOscuro,
        )
        Text(
            text = "Hora de tu medicamento",
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, tamano: Int) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = etiqueta,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextoAjeno,
    )
    Text(
        text = valor,
        fontSize = tamano.sp,
        fontWeight = FontWeight.Medium,
        color = TextoPrincipal,
    )
}
