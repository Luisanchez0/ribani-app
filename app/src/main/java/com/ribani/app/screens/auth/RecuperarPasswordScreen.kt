package com.ribani.app.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AuthRepository
import com.ribani.app.data.repository.ResetResult
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Recuperación de contraseña (código de 6 dígitos por correo):
 *
 * Paso 1: POST /auth/forgot-password → llega el código (10 min).
 * Paso 2: POST /auth/reset-password → código + contraseña nueva;
 *         el backend revoca todas las sesiones de la cuenta.
 *
 * La solicitud responde con un mensaje genérico: Success no garantiza
 * que el correo esté registrado (protección del backend).
 */
@Composable
fun RecuperarPasswordScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var paso by remember { mutableStateOf(0) } // 0 = solicitar, 1 = restablecer
    var email by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mensajeExito by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (paso == 0) "Recuperar contraseña" else "Restablecer contraseña",
            fontSize = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )

        if (paso == 0) {
            Text(
                text = "Escribe el correo de tu cuenta. Te enviaremos un código de " +
                    "6 dígitos válido por 10 minutos.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    scope.launch {
                        procesando = true
                        error = null
                        val repo = AuthRepository(SessionManager(context))
                        when (val r = repo.solicitarReset(email)) {
                            is ResetResult.Success -> {
                                mensajeExito = r.mensaje
                                paso = 1
                            }
                            is ResetResult.Error -> error = r.message
                        }
                        procesando = false
                    }
                },
                enabled = !procesando && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (procesando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Enviar código")
                }
            }
        } else {
            Text(
                text = "Escribe el código enviado a ${email.trim()} y tu contraseña nueva.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            mensajeExito?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                )
            }

            OutlinedTextField(
                value = codigo,
                onValueChange = { valor ->
                    codigo = valor.filter { it.isDigit() }.take(6)
                },
                label = { Text("Código del correo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña nueva") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text("Confirmar contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = passwordConfirm.isNotEmpty() && password != passwordConfirm,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    scope.launch {
                        procesando = true
                        error = null
                        val repo = AuthRepository(SessionManager(context))
                        when (val r = repo.resetPassword(email, codigo, password)) {
                            is ResetResult.Success -> {
                                mensajeExito = r.mensaje
                                // Sin sesión activa: vuelve al login normal.
                                SessionManager(context).clear()
                                onBack()
                            }
                            is ResetResult.Error -> error = r.message
                        }
                        procesando = false
                    }
                },
                enabled = !procesando &&
                    codigo.length == 6 &&
                    password.length >= 8 &&
                    password == passwordConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (procesando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Restablecer contraseña")
                }
            }

            OutlinedButton(
                onClick = {
                    paso = 0
                    codigo = ""
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reenviar código")
            }
        }

        TextButton(onClick = onBack) {
            Text("Volver a iniciar sesión")
        }
    }
}
