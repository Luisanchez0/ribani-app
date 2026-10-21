package com.ribani.app.screens.auth

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AuthRepository
import com.ribani.app.data.repository.LoginResult
import kotlinx.coroutines.launch

private val VerdeRIBANI = Color(0xFF006B3C)
private val VerdeClaro = Color(0xFF63B52A)
private val RojoError = com.ribani.app.ui.theme.RojoBoton

/**
 * CASO 1: Login con 2FA activado.
 * Se llega aquí tras un login correcto (el servidor devolvió
 * twoFactorRequired + challengeToken): solo falta ingresar el código de
 * 6 dígitos de la app authenticator (POST /auth/2fa/login).
 *
 * CASO 2: si el 2FA está desactivado, el login entra directo a la app y
 * esta pantalla nunca se muestra.
 */
@Composable
fun TwoFactorLoginScreen(
    challengeToken: String,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Icon(
            imageVector = Icons.Rounded.PhonelinkLock,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = VerdeClaro
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Verificación en dos pasos",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Ingresa el código de 6 dígitos que genera tu app authenticator.",
            fontSize = 17.sp,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it
                errorMessage = null
            },
            label = { Text("Código de verificación", fontSize = 18.sp) },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = errorMessage?.let { msg ->
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

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (code.length != 6) {
                    errorMessage = "Ingresa los 6 dígitos de tu app authenticator"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    val repository = AuthRepository(SessionManager(context))
                    val result = repository.loginWithTwoFactor(challengeToken, code)
                    isLoading = false
                    when (result) {
                        is LoginResult.Success -> onLoginSuccess()
                        is LoginResult.Error -> errorMessage = result.message
                        is LoginResult.TwoFactorRequired ->
                            errorMessage = "Código incorrecto. Vuelve a intentarlo."
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VerdeRIBANI,
                disabledContainerColor = Color(0xFF9BBFA9)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    text = "Verificar código",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            Text(
                text = "Volver al inicio de sesión",
                fontSize = 17.sp,
                color = VerdeRIBANI,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
