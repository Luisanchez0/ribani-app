package com.ribani.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest): LogoutResponse

    // Requiere header Authorization (lo añade AuthInterceptor)
    @GET("auth/me")
    suspend fun me(): MeResponse

    // ----- Seguridad en dos pasos (2FA) - requieren Authorization -----
    // El setup del QR lo hace el familiar desde su panel web
    // (POST /usuarios/{id}/2fa/setup); la app solo verifica y desactiva.

    @POST("auth/2fa/verify")
    suspend fun verifyTwoFactor(@Body body: VerifyTwoFactorRequest): VerifyTwoFactorResponse

    @POST("auth/2fa/disable")
    suspend fun disableTwoFactor(@Body body: DisableTwoFactorRequest): DisableTwoFactorResponse

    @POST("auth/2fa/login")
    suspend fun loginWithTwoFactor(@Body body: TwoFactorLoginRequest): AuthResponse

    // ----- Recuperación de contraseña (públicos, sin Authorization) -----

    /** POST /auth/forgot-password: manda el código de 6 dígitos por correo. */
    @POST("auth/forgot-password")
    suspend fun olvidarPassword(@Body body: SolicitarResetRequest): MensajeResponse

    /** POST /auth/reset-password: cambia la contraseña con el código. */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): MensajeResponse
}
