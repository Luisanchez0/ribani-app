package com.ribani.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ribani.app.data.remote.ApiClient
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnóstico explícito: no se ejecuta en las pruebas de UI sin backend. */
@RunWith(AndroidJUnit4::class)
class BackendConnectivityTest {
    @Test fun elBackendRespondeDesdeElProcesoDeLaApp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("backendConnectivity") == "true")
        val conexion = URL(ApiClient.BASE_URL + "api-json").openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 5_000
            conexion.readTimeout = 5_000
            assertEquals(200, conexion.responseCode)
        } finally {
            conexion.disconnect()
        }
    }
}
