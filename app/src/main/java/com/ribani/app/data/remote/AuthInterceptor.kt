package com.ribani.app.data.remote

import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.toSessionUser
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException

interface AlmacenTokens {
    suspend fun accessToken(): String?
    suspend fun refreshToken(): String?
    /** Guarda la sesión renovada; false si la respuesta no trae lo necesario. */
    suspend fun guardar(respuesta: AuthResponse): Boolean
    suspend fun limpiar()
}

class AlmacenTokensSesion(private val sesion: SessionManager) : AlmacenTokens {
    override suspend fun accessToken() = sesion.accessToken()
    override suspend fun refreshToken() = sesion.refreshToken()
    override suspend fun guardar(respuesta: AuthResponse): Boolean {
        val user = respuesta.user?.toSessionUser() ?: return false
        sesion.saveSession(
            accessToken = respuesta.accessToken.orEmpty(),
            refreshToken = respuesta.refreshToken.orEmpty(),
            user = user,
        )
        return true
    }
    override suspend fun limpiar() = sesion.clear()
}

/**
 * Añade el access token y, ante un 401, renueva la sesión y reintenta una vez.
 * Solo se cierra la sesión si el servidor rechaza el refresh token: un corte de
 * red se propaga como IOException para que la app lo trate como "sin conexión".
 */
class AuthInterceptor(
    private val tokens: AlmacenTokens,
    private val renovar: suspend (String) -> AuthResponse,
) : Interceptor {

    private enum class Renovacion { Renovada, Rechazada, Temporal }

    private val candado = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val path = original.url.encodedPath
        val isAuthCall = path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/logout")

        if (isAuthCall) return chain.proceed(original)

        val tokenEnviado = runBlocking { tokens.accessToken() }
        val response = chain.proceed(original.conToken(tokenEnviado))
        if (response.code != 401) return response

        return when (asegurarRenovada(tokenEnviado)) {
            Renovacion.Renovada -> {
                response.close()
                chain.proceed(original.conToken(runBlocking { tokens.accessToken() }))
            }
            Renovacion.Rechazada -> response
            Renovacion.Temporal -> {
                response.close()
                throw IOException("No se pudo renovar la sesión temporalmente.")
            }
        }
    }

    private fun Request.conToken(token: String?): Request =
        if (token.isNullOrBlank()) this
        else newBuilder().header("Authorization", "Bearer $token").build()

    /**
     * Una sola renovación a la vez: el refresh token rota, y reutilizarlo desde
     * peticiones simultáneas podría hacer que el servidor revoque la sesión.
     */
    private fun asegurarRenovada(tokenEnviado: String?): Renovacion = synchronized(candado) {
        runBlocking {
            val actual = tokens.accessToken()
            if (!actual.isNullOrBlank() && actual != tokenEnviado) {
                return@runBlocking Renovacion.Renovada
            }
            val refresh = tokens.refreshToken()
            if (refresh.isNullOrBlank()) return@runBlocking Renovacion.Rechazada

            try {
                if (tokens.guardar(renovar(refresh))) Renovacion.Renovada else Renovacion.Temporal
            } catch (e: HttpException) {
                if (e.code() in 400..499 && e.code() != 408 && e.code() != 429) {
                    tokens.limpiar()
                    Renovacion.Rechazada
                } else {
                    Renovacion.Temporal
                }
            } catch (_: IOException) {
                Renovacion.Temporal
            }
        }
    }
}
