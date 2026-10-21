package com.ribani.app.data.remote

// Modelos que coinciden con las respuestas de ribani-server (auth)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RegisterRequest(
    val nombre: String,
    val apellido: String,
    val email: String,
    val telefono: String? = null,
    val password: String,
)

data class RefreshRequest(
    val refreshToken: String,
)

data class AuthUserDto(
    val id: String,
    val nombre: String,
    val apellido: String,
    val email: String,
    val telefono: String?,
    val emailVerificado: Boolean? = null,
    val twoFactorEnabled: Boolean? = null,
    val rol: RolDto?,
)

/** Campos presentes en POST /auth/login cuando el usuario tiene 2FA activado */
data class TwoFactorChallengeResponse(
    val twoFactorRequired: Boolean,
    val challengeToken: String,
    val expiresIn: Long? = null,
)

data class RolDto(
    val id: String,
    val codigo: String,
    val nombre: String,
)

/**
 * Respuesta de POST /auth/login y POST /auth/2fa/login.
 * Con 2FA activado el login no emite tokens: devuelve twoFactorRequired + challengeToken.
 */
data class AuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
    val user: AuthUserDto? = null,
    val twoFactorRequired: Boolean = false,
    val challengeToken: String? = null,
    val challengeExpiresIn: Long? = null,
)

data class RegisterResponse(
    val id: String,
    val nombre: String,
    val apellido: String,
    val email: String,
    val telefono: String?,
    val activo: Boolean?,
    val rol: RolDto?,
    val emailVerificado: Boolean? = null,
)

data class LogoutResponse(
    val message: String? = null,
)

/** Respuesta de GET /auth/me */
data class MeResponse(
    val id: String,
    val nombre: String,
    val apellido: String,
    val email: String,
    val telefono: String?,
    val emailVerificado: Boolean? = null,
    val twoFactorEnabled: Boolean? = null,
    val ultimoAcceso: String? = null,
    val rol: RolDto?,
    val permisos: List<String> = emptyList(),
)

// ----- Seguridad en dos pasos (2FA) -----
// Nota: el QR de configuración lo genera el familiar/cuidador desde su panel
// web (POST /usuarios/{id}/2fa/setup del servidor); la app no pide setup.

data class VerifyTwoFactorRequest(
    val code: String,
)

data class VerifyTwoFactorResponse(
    val twoFactorEnabled: Boolean,
    val mensaje: String? = null,
)

data class DisableTwoFactorRequest(
    val password: String,
)

data class DisableTwoFactorResponse(
    val twoFactorEnabled: Boolean,
    val mensaje: String? = null,
)

data class TwoFactorLoginRequest(
    val challengeToken: String,
    val code: String,
)

data class ApiError(
    val message: String? = null,
    val statusCode: Int? = null,
)

// ----- Recuperación de contraseña -----

data class SolicitarResetRequest(
    val email: String,
)

/** Cuerpo de POST /auth/forgot-password y POST /auth/reset-password. */
data class MensajeResponse(
    val mensaje: String? = null,
)

data class ResetPasswordRequest(
    val email: String,
    val codigo: String,
    val password: String,
)
