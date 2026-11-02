package com.ribani.app

import com.ribani.app.data.remote.AlmacenTokens
import com.ribani.app.data.remote.AuthInterceptor
import com.ribani.app.data.remote.AuthResponse
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class AuthInterceptorTest {
    private class Tokens(var access: String? = "viejo", var refresh: String? = "r1") : AlmacenTokens {
        var limpiada = false
        override suspend fun accessToken() = access
        override suspend fun refreshToken() = refresh
        override suspend fun guardar(respuesta: AuthResponse): Boolean {
            access = respuesta.accessToken
            refresh = respuesta.refreshToken
            return true
        }
        override suspend fun limpiar() {
            limpiada = true
            access = null
            refresh = null
        }
    }

    /** Responde 401 salvo que llegue el token nuevo. */
    private class Cadena(private val tokenValido: String = "nuevo") : Interceptor.Chain {
        private val req = Request.Builder().url("http://x/alertas").build()
        val autorizaciones = java.util.Collections.synchronizedList(mutableListOf<String?>())
        override fun proceed(request: Request): Response {
            val auth = request.header("Authorization")
            autorizaciones += auth
            return respuesta(request, if (auth == "Bearer $tokenValido") 200 else 401)
        }
        override fun request() = req
        override fun connection() = null
        override fun call() = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    private companion object {
        fun respuesta(request: Request, codigo: Int): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message("x")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        fun errorHttp(codigo: Int) = HttpException(
            retrofit2.Response.error<Any>(codigo, "{}".toResponseBody("application/json".toMediaType())),
        )

        fun renovada() = AuthResponse(accessToken = "nuevo", refreshToken = "r2")
    }

    @Test fun renuevaYReintentaConElTokenNuevo() {
        val tokens = Tokens()
        val cadena = Cadena()
        val r = AuthInterceptor(tokens) { renovada() }.intercept(cadena)
        assertEquals(200, r.code)
        assertEquals(listOf<String?>("Bearer viejo", "Bearer nuevo"), cadena.autorizaciones.toList())
    }

    @Test fun unCorteDeRedAlRenovarConservaLaSesion() {
        val tokens = Tokens()
        val interceptor = AuthInterceptor(tokens) { throw IOException("sin red") }
        var lanzo = false
        try {
            interceptor.intercept(Cadena())
        } catch (_: IOException) {
            lanzo = true
        }
        // IOException y no 401: quien llama lo trata como "sin conexión", no como sesión inválida.
        assertTrue(lanzo)
        assertFalse(tokens.limpiada)
        assertEquals("r1", tokens.refresh)
    }

    @Test fun unErrorDelServidorAlRenovarConservaLaSesion() {
        val tokens = Tokens()
        val interceptor = AuthInterceptor(tokens) { throw errorHttp(503) }
        var lanzo = false
        try {
            interceptor.intercept(Cadena())
        } catch (_: IOException) {
            lanzo = true
        }
        assertTrue(lanzo)
        assertFalse(tokens.limpiada)
    }

    @Test fun unRefreshRechazadoCierraLaSesionYDevuelve401() {
        val tokens = Tokens()
        val r = AuthInterceptor(tokens) { throw errorHttp(401) }.intercept(Cadena())
        assertEquals(401, r.code)
        assertTrue(tokens.limpiada)
    }

    @Test fun variasPeticionesSimultaneasRenuevanUnaSolaVez() {
        val tokens = Tokens()
        val renovaciones = AtomicInteger()
        val interceptor = AuthInterceptor(tokens) {
            renovaciones.incrementAndGet()
            Thread.sleep(100)
            renovada()
        }
        val hilos = Executors.newFixedThreadPool(5)
        val fin = CountDownLatch(5)
        val codigos = java.util.Collections.synchronizedList(mutableListOf<Int>())
        repeat(5) {
            hilos.execute {
                codigos += interceptor.intercept(Cadena()).code
                fin.countDown()
            }
        }
        assertTrue(fin.await(5, TimeUnit.SECONDS))
        hilos.shutdown()
        assertEquals(1, renovaciones.get())
        assertEquals(List(5) { 200 }, codigos.toList())
    }
}
