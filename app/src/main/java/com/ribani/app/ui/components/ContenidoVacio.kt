package com.ribani.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.ui.theme.ContenedorVerdeSuave
import com.ribani.app.ui.theme.VerdeTomado

/**
 * Tarjeta de estado vacío compartida (misma presentación en todas las
 * pantallas).
 *
 * - mensaje: texto principal ("¡Al día! No tienes medicamentos...", etc.)
 * - mensajeEstado: texto secundario opcional (p. ej. causa del error / sin sesión).
 */
@Composable
fun ContenidoVacio(
    mensaje: String,
    mensajeEstado: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ContenedorVerdeSuave)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = VerdeTomado,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = mensaje,
                    fontSize = 18.sp,
                    color = Color(0xFF2E7D32)
                )
                if (mensajeEstado != null) {
                    Text(
                        text = mensajeEstado,
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
