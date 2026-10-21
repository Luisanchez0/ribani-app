package com.ribani.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.PatternsCompat
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AuthRepository
import com.ribani.app.data.repository.LoginResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    cuentaCreada: Boolean = false,
    onLoginSuccess: () -> Unit,
    onTwoFactorRequired: (challengeToken: String) -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onNavigateToRecuperar: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val emailError = if (email.isNotBlank() && !PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
        "Ingresa un correo electrónico válido"
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = com.ribani.app.ui.theme.VerdeClaro
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "RIBANI",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "Guardia de caídas",
                    fontSize = 18.sp,
                    color = Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "Iniciar Sesión",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        if (cuentaCreada) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.align(Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = com.ribani.app.ui.theme.VerdeClaro
                )
                Text(
                    text = " Cuenta creada correctamente. Inicia sesión.",
                    fontSize = 15.sp,
                    color = com.ribani.app.ui.theme.VerdeClaro
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = null
            },
            label = { Text("Correo electrónico", fontSize = 18.sp) },
            singleLine = true,
            isError = emailError != null,
            supportingText = if (emailError != null) {
                { Text(emailError, fontSize = 14.sp, color = com.ribani.app.ui.theme.RojoBoton) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF63B52A),
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Contraseña", fontSize = 18.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF63B52A),
                unfocusedBorderColor = Color.Gray
            )
        )

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 15.sp,
                color = com.ribani.app.ui.theme.RojoBoton,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                val emailValid = PatternsCompat.EMAIL_ADDRESS.matcher(email.trim()).matches()
                if (!emailValid) {
                    errorMessage = "Ingresa un correo electrónico válido"
                    return@Button
                }
                if (password.length < 8) {
                    errorMessage = "La contraseña debe tener al menos 8 caracteres"
                    return@Button
                }

                isLoading = true
                errorMessage = null
                scope.launch {
                    val repository = AuthRepository(SessionManager(context))
                    val result = repository.login(email, password)
                    isLoading = false
                    when (result) {
                        is LoginResult.Success -> onLoginSuccess()
                        is LoginResult.TwoFactorRequired -> onTwoFactorRequired(result.challengeToken)
                        is LoginResult.Error -> errorMessage = result.message
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF006B3C),
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
                    text = "Ingresar",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onNavigateToRecuperar) {
            Text(
                text = "¿Olvidaste tu contraseña?",
                fontSize = 15.sp,
                color = Color(0xFF444444)
            )
        }

        TextButton(onClick = onNavigateToRegister) {
            Text(
                text = "¿No tienes cuenta? Crear cuenta",
                fontSize = 17.sp,
                color = Color(0xFF006B3C),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
