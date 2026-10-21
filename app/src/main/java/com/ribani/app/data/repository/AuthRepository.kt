package com.ribani.app.data.repository

import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.local.SessionUser
import com.ribani.app.data.remote.ApiClient
import retrofit2.HttpException
import java.io.IOException
import com.ribani.app.data.remote.ApiErrorParser
import com.ribani.app.data.remote.AuthResponse
import com.ribani.app.data.remote.AuthUserDto
import com.ribani.app.data.remote.DisableTwoFactorRequest
import com.ribani.app.data.remote.DisableTwoFactorResponse
import com.ribani.app.data.remote.LoginRequest
import com.ribani.app.data.remote.MeResponse
import com.ribani.app.data.remote.RefreshRequest
import com.ribani.app.data.remote.ResetPasswordRequest
import com.ribani.app.data.remote.SolicitarResetRequest
import com.ribani.app.data.remote.RegisterRequest
import com.ribani.app.data.remote.TwoFactorChallengeResponse
import com.ribani.app.data.remote.TwoFactorLoginRequest
import com.ribani.app.data.remote.VerifyTwoFactorRequest
import com.ribani.app.data.remote.VerifyTwoFactorResponse

sealed class ResetResult {
    data class Success(val mensaje: String) : ResetResult()
    data class Error(val message: String) : ResetResult()
}

sealed class AuthResult {
    data class Success(val user: SessionUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}


sealed class LoginResult {
    data class Success(val user: SessionUser) : LoginResult()

    data class TwoFactorRequired(val challengeToken: String, val expiresIn: Long?) : LoginResult()
    data class Error(val message: String) : LoginResult()
}


sealed class TwoFactorResult {
    data class Enabled(val message: String) : TwoFactorResult()
    data class Disabled(val message: String) : TwoFactorResult()
    data class Error(val message: String) : TwoFactorResult()
}


sealed class SessionValidation {
    /** Token válido: datos actualizados desde /auth/me */
    data class Valid(val user: SessionUser) : SessionValidation()
    /** Token inválido o revocado: hay que cerrar sesión */
    object Invalid : SessionValidation()
    /** Sin conexión con el servidor: permitimos usar la app con los datos guardados */
    object Offline : SessionValidation()
}

class AuthRepository(private val sessionManager: SessionManager) {

    private val api = ApiClient.authService

    suspend fun login(email: String, password: String): LoginResult {
        return try {
            val response = api.login(LoginRequest(email.trim(), password))
            handleLoginResponse(response)
        } catch (e: Exception) {
            LoginResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }


    suspend fun loginWithTwoFactor(challengeToken: String, code: String): LoginResult {
        return try {
            val response = api.loginWithTwoFactor(
                TwoFactorLoginRequest(challengeToken, code),
            )
            handleLoginResponse(response)
        } catch (e: Exception) {
            LoginResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }

    private suspend fun handleLoginResponse(response: AuthResponse): LoginResult {
        return if (response.twoFactorRequired) {
            LoginResult.TwoFactorRequired(
                challengeToken = response.challengeToken.orEmpty(),
                expiresIn = response.challengeExpiresIn,
            )
        } else {
            val user = response.user?.toSessionUser()
                ?: return LoginResult.Error("Respuesta inválida del servidor.")
            sessionManager.saveSession(
                accessToken = response.accessToken.orEmpty(),
                refreshToken = response.refreshToken.orEmpty(),
                user = user,
            )
            LoginResult.Success(user)
        }
    }

    // ----- Seguridad en dos pasos (2FA) -----

    // Nota: el QR lo genera el familiar/cuidador desde su panel web
    // (POST /usuarios/{id}/2fa/setup); la app no solicita setup propio.

    /** POST /auth/2fa/verify: confirma el código y activa el 2FA */
    suspend fun verifyTwoFactor(code: String): TwoFactorResult {
        return try {
            val response = api.verifyTwoFactor(VerifyTwoFactorRequest(code))
            sessionManager.setTwoFactorEnabled(true)
            TwoFactorResult.Enabled(response.mensaje ?: "2FA habilitado correctamente.")
        } catch (e: Exception) {
            TwoFactorResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }

    /** POST /auth/2fa/disable: requiere la contraseña de la cuenta */
    suspend fun disableTwoFactor(password: String): TwoFactorResult {
        return try {
            val response = api.disableTwoFactor(DisableTwoFactorRequest(password))
            sessionManager.setTwoFactorEnabled(false)
            TwoFactorResult.Disabled(response.mensaje ?: "2FA deshabilitado correctamente.")
        } catch (e: Exception) {
            TwoFactorResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }

    suspend fun register(
        nombre: String,
        apellido: String,
        email: String,
        telefono: String?,
        password: String,
    ): AuthResult {
        return try {
            val response = api.register(
                RegisterRequest(
                    nombre = nombre.trim(),
                    apellido = apellido.trim(),
                    email = email.trim(),
                    telefono = telefono?.trim()?.takeIf { it.isNotBlank() },
                    password = password,
                ),
            )
            AuthResult.Success(
                SessionUser(
                    id = response.id,
                    nombre = response.nombre,
                    apellido = response.apellido,
                    email = response.email,
                    telefono = response.telefono,
                    rolCodigo = response.rol?.codigo.orEmpty(),
                ),
            )
        } catch (e: Exception) {
            AuthResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }

    /**
     * Valida la sesión guardada contra GET /auth/me.
     * Llama a este método al iniciar la app para comprobar que el token
     * sigue siendo válido y refrescar los datos del usuario.
     */
    suspend fun getCurrentUser(): SessionValidation {
        val localUser = sessionManager.currentUser() ?: return SessionValidation.Invalid

        return try {
            val me = api.me()
            val user = me.toSessionUser()
            sessionManager.updateUser(user)
            SessionValidation.Valid(user)
        } catch (e: Exception) {
            when {
                // Token inválido/revocado tras el intento de refresh del interceptor
                e is HttpException && (e.code() == 401 || e.code() == 403) -> SessionValidation.Invalid
                // Sin internet u servidor caído: sesión local sigue creíble
                e is IOException -> SessionValidation.Offline
                else -> SessionValidation.Offline
            }
        }
    }

    // ----- Recuperación de contraseña -----

    /**
     * POST /auth/forgot-password: pide el código de 6 dígitos por correo
     * (vigencia 10 min). El backend responde con un mensaje genérico —
     * NO revela si el correo existe, así que Success no garantiza cuenta.
     */
    suspend fun solicitarReset(email: String): ResetResult {
        return try {
            val response = api.olvidarPassword(SolicitarResetRequest(email.trim()))
            ResetResult.Success(response.mensaje ?: "Código enviado a tu correo.")
        } catch (e: Exception) {
            ResetResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }

    /**
     * POST /auth/reset-password: cambia la contraseña con el código y
     * revoca las sesiones activas de la cuenta.
     */
    suspend fun resetPassword(email: String, codigo: String, password: String): ResetResult {
        return try {
            val response = api.resetPassword(
                ResetPasswordRequest(
                    email = email.trim(),
                    codigo = codigo.trim(),
                    password = password,
                ),
            )
            ResetResult.Success(
                response.mensaje ?: "Contraseña actualizada. Ya puedes iniciar sesión.",
            )
        } catch (e: Exception) {
            ResetResult.Error(ApiErrorParser.getErrorMessage(e))
        }
    }

    suspend fun logout(): Boolean {
        return try {
            val refreshToken = sessionManager.refreshToken()
            if (!refreshToken.isNullOrBlank()) {
                api.logout(RefreshRequest(refreshToken))
            }
            true
        } catch (_: Exception) {
            // Aunque falle la llamada, cerramos sesión localmente
            true
        } finally {
            sessionManager.clear()
        }
    }
}

fun MeResponse.toSessionUser(): SessionUser = SessionUser(
    id = this.id,
    nombre = this.nombre,
    apellido = this.apellido,
    email = this.email,
    telefono = this.telefono,
    rolCodigo = this.rol?.codigo.orEmpty(),
    twoFactorEnabled = this.twoFactorEnabled ?: false,
)

fun AuthUserDto.toSessionUser(): SessionUser = SessionUser(
    id = this.id,
    nombre = this.nombre,
    apellido = this.apellido,
    email = this.email,
    telefono = this.telefono,
    rolCodigo = this.rol?.codigo.orEmpty(),
    twoFactorEnabled = this.twoFactorEnabled ?: false,
)
