package com.ribani.app.screens.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.ContactoResponse
import com.ribani.app.ui.theme.TextoNeutro
import com.ribani.app.ui.theme.TextoOscuro

/**
 * Contactos de emergencia del adulto mayor (GET /contactos).
 * Al presionar una tarjeta se abre directamente la app de llamadas
 * del sistema (marcador) con el número del contacto listo para llamar.
 */
@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    var contactos by remember { mutableStateOf<List<ContactoResponse>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            contactos = ApiClient.contactosService.listarContactos()
        } catch (e: Exception) {
            error = "No se pudieron cargar los contactos. Intenta de nuevo."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(bottom = 30.dp)
        ) {
            Icon(
                Icons.Rounded.Contacts,
                contentDescription = null,
                modifier = Modifier.size(72.dp).padding(top = 8.dp),
                tint = TextoOscuro
            )
            Text(
                "Mis Contactos\nde Emergencia",
                fontSize = 35.sp,
                lineHeight = 42.sp,
                modifier = Modifier.padding(start = 24.dp)
            )
        }

        when {
            contactos == null && error == null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF006B3C))
                }
            }
            error != null -> {
                Text(
                    error!!,
                    fontSize = 22.sp,
                    color = Color(0xFFB3261E),
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
            contactos!!.isEmpty() -> {
                com.ribani.app.ui.components.ContenidoVacio(
                    mensaje = "Aún no tienes contactos de emergencia.",
                    mensajeEstado = "Tu familiar puede agregarlos desde la app web."
                )
            }
            else -> {
                LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
                    items(contactos!!, key = { it.id }) { contacto ->
                        ContactCard(
                            name = contacto.nombre,
                            phone = contacto.telefono,
                            parentesco = contacto.parentesco,
                            onLlamar = {
                                // ACTION_DIAL: abre directamente la app de llamadas
                                // (marcador) sin permiso CALL_PHONE; no marca solo.
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${contacto.telefono}")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * La BD guarda E.164 (+52 5512345678). El adulto mayor lee mejor el
 * número sin el código de país; para llamar siempre se usa el original.
 */
private fun telefonoLegible(e164: String): String =
    if (e164.startsWith("+")) {
        // +52 followed by local block; se agrupa con espacios
        val sinCodigo = e164.drop(1)
        sinCodigo.chunked(3).joinToString(" ")
    } else {
        e164
    }

@Composable
private fun ContactCard(
    name: String,
    phone: String,
    parentesco: String?,
    onLlamar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .background(Color(0xFFE7E7E7), RoundedCornerShape(26.dp))
            .clickable(onClick = onLlamar)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Call,
            contentDescription = "Llamar a $name",
            modifier = Modifier.size(76.dp).background(TextoNeutro, CircleShape).padding(18.dp),
            tint = Color.White
        )
        Column(modifier = Modifier.padding(start = 28.dp)) {
            Text(name, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(telefonoLegible(phone), fontSize = 21.sp, color = Color(0xFF333333))
            if (!parentesco.isNullOrBlank()) {
                Text(
                    parentesco,
                    fontSize = 17.sp,
                    color = TextoNeutro
                )
            }
        }
    }
}
