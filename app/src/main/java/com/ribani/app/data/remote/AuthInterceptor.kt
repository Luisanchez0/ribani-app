package com.ribani.app.data.remote

import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.toSessionUser
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade el header Authorization con el access token guardado.
 * Si el servidor responde 401, intenta refrescar el token con
 * POST /auth/refresh y reintenta la petición una vez.
 */
class AuthInterceptor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val isAuthCall = original.url.encodedPath.contains("/auth/login") ||
            original.url.encodedPath.contains("/auth/register") ||
            original.url.encodedPath.contains("/auth/refresh") ||
            original.url.encodedPath.contains("/auth/logout")

        val request = if (!isAuthCall) {
            val token = runBlocking { sessionManager.accessToken() }
            original.newBuilder().apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }.build()
        } else {
            original
        }

        val response = chain.proceed(request)

        // Si el token expiró, intentamos refrescarlo (solo en rutas protegidas)
        if (response.code == 401 && !isAuthCall) {
            val refreshed = runBlocking { refreshAccessToken() }

            if (refreshed) {
                response.close()
                val newToken = runBlocking { sessionManager.accessToken() }
                val retryRequest = original.newBuilder().apply {
                    if (!newToken.isNullOrBlank()) {
                        header("Authorization", "Bearer $newToken")
                    }
                }.build()
                return chain.proceed(retryRequest)
            }
        }

        return response
    }

    private suspend fun refreshAccessToken(): Boolean {
        val refreshToken = sessionManager.refreshToken() ?: return false

        return try {
            val api = ApiClient.authService
            val response = api.refresh(RefreshRequest(refreshToken))
            val user = response.user?.toSessionUser() ?: return false
            sessionManager.saveSession(
                accessToken = response.accessToken.orEmpty(),
                refreshToken = response.refreshToken.orEmpty(),
                user = user,
            )
            true
        } catch (_: Exception) {
            // Refresh falló (token revocado/expirado): limpiamos sesión
            sessionManager.clear()
            false
        }
    }
}
