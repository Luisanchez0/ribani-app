package com.ribani.app.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AuthRepository
import com.ribani.app.data.repository.SessionValidation
import com.ribani.app.data.repository.TwoFactorResult
import kotlinx.coroutines.launch

private val VerdeRIBANI = Color(0xFF006B3C)
private val VerdeClaro = Color(0xFF63B52A)
private val RojoError = com.ribani.app.ui.theme.RojoBoton
private val TextoOscuro = Color(0xFF211F24)

/**
 * Apartado "Seguridad en dos pasos" de Configuración.
 *
 * Flujo asistido: el QR NO se genera en esta app. El familiar/cuidador
 * encargado lo genera desde su panel web (POST /usuarios/{id}/2fa/setup del
 * servidor, protegido por permiso + vínculo + rol del destino). Aquí el
 * adulto mayor puede:
 *  - Ver el estado de protección (GET /auth/me -> twoFactorEnabled).
 *  - Confirmar la activación ingresando el código de 6 dígitos de su app
 *    authenticator después de escanear el QR (POST /auth/2fa/verify).
 *  - Desactivar la protección con su contraseña (POST /auth/2fa/disable).
 */
@Composable
fun TwoFactorSettingsScreen(
    onBack: () -> Unit = {},
    onTwoFactorChanged: (enabled: Boolean) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthRepository(SessionManager(context)) }

    var enabled by remember { mutableStateOf(false) }
    var isLoadingState by remember { mutableStateOf(true) }

    var showCodeInput by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    var showDisableDialog by remember { mutableStateOf(false) }
    var disablePassword by remember { mutableStateOf("") }
    var disableError by remember { mutableStateOf<String?>(null) }
    var isDisabling by remember { mutableStateOf(false) }

    var infoMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshStatus() {
        val validation = repository.getCurrentUser()
        if (validation is SessionValidation.Valid) {
            enabled = validation.user.twoFactorEnabled
        }
    }

    // Cargamos el estado real desde GET /auth/me (twoFactorEnabled)
    LaunchedEffect(Unit) {
        refreshStatus()
        isLoadingState = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoOscuro)
            }
            Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(56.dp), tint = VerdeRIBANI)
            Text(
                "Seguridad en dos pasos",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        if (isLoadingState) {
            CircularProgressIndicator(color = VerdeRIBANI, modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        if (enabled) {
            // ------- 2FA ACTIVADO -------
            Icon(
                Icons.Rounded.VerifiedUser, null,
                Modifier
                    .size(110.dp)
                    .align(Alignment.CenterHorizontally),
                tint = VerdeClaro
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Tu cuenta está protegida",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Además de tu contraseña, al iniciar sesión se pedirá un código de 6 dígitos de tu app authenticator (Google Authenticator, Authy, etc.).",
                fontSize = 17.sp,
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = {
                    disablePassword = ""
                    disableError = null
                    showDisableDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RojoError)
            ) {
                Text("Desactivar", fontSize = 22.sp, color = Color.White)
            }
        } else {

            Icon(
                Icons.Rounded.PhonelinkLock, null,
                Modifier
                    .size(110.dp)
                    .align(Alignment.CenterHorizontally),
                tint = com.ribani.app.ui.theme.ContenedorCircularIcono
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Protege tu cuenta",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "La verificación en dos pasos añade una capa extra de seguridad: aunque alguien conozca tu contraseña, necesitará el código que genera tu app authenticator.",
                fontSize = 17.sp,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Tu familiar o cuidador encargado activará esta protección desde su panel web, donde se genera el código QR. Escanea ese QR con tu app authenticator y luego confirma aquí con el código de 6 dígitos.",
                fontSize = 17.sp,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(28.dp))

            if (!showCodeInput) {
                Button(
                    onClick = {
                        code = ""
                        verifyError = null
                        showCodeInput = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeRIBANI)
                ) {
                    Text("Ya escaneé el código QR", fontSize = 21.sp, color = Color.White)
                }
            } else {
                Text(
                    "Ingresa el código de 6 dígitos que muestra tu app authenticator:",
                    fontSize = 17.sp,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it
                        verifyError = null
                    },
                    label = { Text("Código de verificación", fontSize = 17.sp) },
                    singleLine = true,
                    isError = verifyError != null,
                    supportingText = verifyError?.let { msg ->
                        { Text(msg, fontSize = 14.sp, color = RojoError) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerdeClaro,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (code.length != 6) {
                            verifyError = "Ingresa los 6 dígitos de tu app authenticator"
                            return@Button
                        }
                        scope.launch {
                            isVerifying = true
                            when (val result = repository.verifyTwoFactor(code)) {
                                is TwoFactorResult.Enabled -> {
                                    enabled = true
                                    onTwoFactorChanged(true)
                                    showCodeInput = false
                                    code = ""
                                    infoMessage = result.message
                                }
                                is TwoFactorResult.Error -> verifyError = result.message
                                else -> Unit
                            }
                            isVerifying = false
                        }
                    },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerdeRIBANI,
                        disabledContainerColor = Color(0xFF9BBFA9)
                    )
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    } else {
                        Text("Confirmar y activar", fontSize = 22.sp, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showCodeInput = false }) {
                    Text("Cancelar", fontSize = 16.sp, color = Color.DarkGray)
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isLoadingState = true
                        refreshStatus()
                        isLoadingState = false
                    }
                },
                enabled = !isLoadingState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text("Actualizar estado", fontSize = 19.sp, color = VerdeRIBANI)
            }
        }

        infoMessage?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Text(msg, fontSize = 15.sp, color = VerdeClaro)
        }
    }

    // ------- Diálogo para desactivar (pide contraseña) -------
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDisabling) showDisableDialog = false },
            title = { Text("Desactivar 2FA", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Para desactivar la verificación en dos pasos ingresa tu contraseña:",
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = disablePassword,
                        onValueChange = {
                            disablePassword = it
                            disableError = null
                        },
                        label = { Text("Contraseña", fontSize = 16.sp) },
                        singleLine = true,
                        isError = disableError != null,
                        supportingText = disableError?.let { msg ->
                            { Text(msg, fontSize = 13.sp, color = RojoError) }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDisabling && disablePassword.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isDisabling = true
                            when (val result = repository.disableTwoFactor(disablePassword)) {
                                is TwoFactorResult.Disabled -> {
                                    enabled = false
                                    onTwoFactorChanged(false)
                                    showDisableDialog = false
                                    infoMessage = result.message
                                }
                                is TwoFactorResult.Error -> disableError = result.message
                                else -> Unit
                            }
                            isDisabling = false
                        }
                    }
                ) {
                    if (isDisabling) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Text("Desactivar", fontSize = 17.sp, color = RojoError)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isDisabling, onClick = { showDisableDialog = false }) {
                    Text("Cancelar", fontSize = 17.sp, color = Color.DarkGray)
                }
            }
        )
    }
}
