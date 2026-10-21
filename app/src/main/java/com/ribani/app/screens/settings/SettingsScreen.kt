package com.ribani.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.ribani.app.data.repository.AjustesRepository
import com.ribani.app.ui.theme.ContenedorCircularIcono
import com.ribani.app.ui.theme.SuperficieTarjeta
import com.ribani.app.ui.theme.TextoOscuro
import com.ribani.app.ui.theme.TextoSecundario
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pantalla de Configuración funcional:
 *
 * - Protección: ACTIVADA si el ADULTO_MAYOR tiene un FAMILIAR_ENCARGADO
 *   vinculado (vínculo adulto_familiares ACTIVA); DESACTIVADA si no.
 * - Información personal: datos del adulto (GET /auth/me).
 * - Volumen de alertas: abre los Ajustes del sistema, apartado de sonido.
 * - Acerca de RIBANI: información de la app.
 */
@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    onOpenTwoFactor: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAcercaDialog by remember { mutableStateOf(false) }

    // Estado: datos personales + estado de protección (vínculos).
    val estado = AjustesRepository.uiState.collectAsState().value
    LaunchedEffect(Unit) {
        AjustesRepository.cargar(context)
    }

    val etiquetaProteccion = when (estado.proteccionActiva) {
        true -> "Protección: Activada"
        false -> "Protección: Desactivada"
        null -> "Protección: Desconocida"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(72.dp), tint = TextoOscuro)
            Text("Configuración", fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp))
        }
        Text("Información", fontSize = 28.sp, modifier = Modifier.padding(start = 40.dp, bottom = 14.dp))
        // Activada/Desactivada según el vínculo ACTIVA con un familiar encargado.
        SettingCard(Icons.Rounded.Lock, etiquetaProteccion)
        Spacer(Modifier.height(18.dp))
        SettingCard(Icons.Rounded.Person, "Información Personal", onClick = { showInfoDialog = true })
        Spacer(Modifier.height(18.dp))
        SettingCard(Icons.Rounded.VolumeUp, "Volumen de alertas", onClick = {
            // Abre los Ajustes del sistema en el apartado de sonido.
            try {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                )
            } catch (e: Exception) {
                // Algunas versiones no resuelven el intent; abre el detalle
                // de la app como último recurso.
                try {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        })
        Spacer(Modifier.height(18.dp))
        SettingCard(Icons.Rounded.VerifiedUser, "Seguridad en dos pasos", onClick = onOpenTwoFactor)
        Spacer(Modifier.height(48.dp))
        Text("Acerca de", fontSize = 28.sp, modifier = Modifier.padding(start = 40.dp, bottom = 14.dp))
        SettingCard(Icons.Rounded.Info, "Acerca de RIBANI", onClick = { showAcercaDialog = true })
        Spacer(Modifier.height(48.dp))
        Text("Acciones", fontSize = 28.sp, modifier = Modifier.padding(start = 40.dp, bottom = 14.dp))
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RoundedCornerShape(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = com.ribani.app.ui.theme.RojoBoton)
        ) {
            Text("Cerrar Sesión", fontSize = 27.sp, color = Color.White)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("Cerrar Sesión", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("¿Estás seguro de que deseas cerrar sesión?", fontSize = 18.sp)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Sí, cerrar sesión", fontSize = 18.sp, color = com.ribani.app.ui.theme.RojoBoton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", fontSize = 18.sp, color = Color.DarkGray)
                }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text("Información Personal", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    FilaDato("Nombre", "${estado.nombre} ${estado.apellido}".trim().ifEmpty { "—" })
                    FilaDato("Correo", estado.email.ifEmpty { "—" })
                    FilaDato("Teléfono", estado.telefono?.ifBlank { null } ?: "No registrado")
                    FilaDato(
                        "Cuenta",
                        when (estado.emailVerificado) {
                            true -> "Verificada"
                            false -> "Sin verificar"
                            null -> "—"
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Cerrar", fontSize = 18.sp, color = Color(0xFF006B3C))
                }
            }
        )
    }

    if (showAcercaDialog) {
        AlertDialog(
            onDismissRequest = { showAcercaDialog = false },
            title = {
                Text("Acerca de RIBANI", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "RIBANI acompaña a adultos mayores que viven solos: " +
                            "alertas de caídas, recordatorios de medicamentos, " +
                            "contactos de emergencia y ubicación compartida con " +
                            "su familiar responsable.",
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Proyecto académico — Aplicaciones Web y Móviles ",
                        fontSize = 15.sp,
                        color = TextoSecundario
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Versión 1.0",
                        fontSize = 15.sp,
                        color = TextoSecundario
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAcercaDialog = false }) {
                    Text("Cerrar", fontSize = 18.sp, color = Color(0xFF006B3C))
                }
            }
        )
    }
}

/** Texto de rol legible para usuarios (siempre SDK-código del backend). */
private fun tituloDeRol(rolCodigo: String): String = when (rolCodigo) {
    "ADULTO_MAYOR" -> "Adulto mayor"
    "FAMILIAR_ENCARGADO" -> "Familiar encargado"
    "ADMINISTRADOR" -> "Administrador"
    else -> rolCodigo.ifEmpty { "—" }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = etiqueta, fontSize = 14.sp, color = TextoSecundario)
        Text(
            text = valor,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextoOscuro
        )
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(SuperficieTarjeta, RoundedCornerShape(26.dp))
            .let { mod ->
                if (onClick != null) mod.clickable(onClick = onClick) else mod
            }
            .padding(horizontal = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            Modifier
                .size(62.dp)
                .background(ContenedorCircularIcono, CircleShape)
                .padding(17.dp),
            tint = TextoOscuro
        )
        Text(label, fontSize = 22.sp, modifier = Modifier.padding(start = 22.dp))
    }
}
