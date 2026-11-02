package com.ribani.app

import com.ribani.app.data.repository.AccionAlerta
import com.ribani.app.data.repository.AlmacenAcciones
import com.ribani.app.data.repository.ColaAlertas
import com.ribani.app.data.repository.ResultadoIntento
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColaAlertasTest {
    private class AlmacenMemoria : AlmacenAcciones {
        var acciones: List<AccionAlerta> = emptyList()
        override fun leer() = acciones
        override fun guardar(acciones: List<AccionAlerta>) { this.acciones = acciones }
    }

    private var ahora = 1_000_000L
    private val almacen = AlmacenMemoria()
    private val cola = ColaAlertas(almacen) { ahora }

    private fun accion(id: String, usuario: String = "u1") =
        AccionAlerta.crear(id, usuario, "SOS", "Ayuda", "Pide ayuda", null, null, ahora)

    @Test fun seGuardaEnDiscoAntesDeIntentarEnviar() = runBlocking {
        cola.agregar(accion("a"))
        assertEquals(listOf("a"), almacen.acciones.map { it.id })
    }

    @Test fun sobreviveAlReinicioDelProceso() = runBlocking {
        cola.agregar(accion("a"))
        val colaNueva = ColaAlertas(almacen) { ahora }
        val enviadas = mutableListOf<String>()
        colaNueva.procesar("u1") { enviadas += it.id; ResultadoIntento.Hecho }
        assertEquals(listOf("a"), enviadas)
        assertTrue(almacen.acciones.isEmpty())
    }

    @Test fun unFalloTemporalConservaLaAccionYElOrden() = runBlocking {
        cola.agregar(accion("a"))
        cola.agregar(accion("b"))
        val intentos = mutableListOf<String>()
        val resumen = cola.procesar("u1") { intentos += it.id; ResultadoIntento.Reintentar }
        // No se salta "a": confirmar o escalar una caída depende de ese orden.
        assertEquals(listOf("a"), intentos)
        assertEquals(2, resumen.pendientes)
        assertEquals(listOf("a", "b"), almacen.acciones.map { it.id })
    }

    @Test fun unRechazoDefinitivoSeDescartaYContinua() = runBlocking {
        cola.agregar(accion("a"))
        cola.agregar(accion("b"))
        val resumen = cola.procesar("u1") {
            if (it.id == "a") ResultadoIntento.Descartar else ResultadoIntento.Hecho
        }
        assertEquals(setOf("a"), resumen.descartadas)
        assertEquals(setOf("b"), resumen.completadas)
        assertEquals(0, resumen.pendientes)
    }

    @Test fun sinSesionNoEnviaNiPierdeNada() = runBlocking {
        cola.agregar(accion("a"))
        var llamadas = 0
        val resumen = cola.procesar(null) { llamadas++; ResultadoIntento.Hecho }
        assertEquals(0, llamadas)
        assertEquals(1, resumen.pendientes)
    }

    @Test fun lasAlertasDeOtraCuentaNoSeEnvianConLaActual() = runBlocking {
        cola.agregar(accion("ajena", usuario = "otro"))
        cola.agregar(accion("mia"))
        val enviadas = mutableListOf<String>()
        val resumen = cola.procesar("u1") { enviadas += it.id; ResultadoIntento.Hecho }
        assertEquals(listOf("mia"), enviadas)
        assertEquals(setOf("ajena"), resumen.descartadas)
    }

    @Test fun unaAlertaCaducadaNoAvisaDeUnaEmergenciaAntigua() = runBlocking {
        cola.agregar(accion("vieja"))
        ahora += ColaAlertas.CADUCIDAD_MS + 1
        var llamadas = 0
        val resumen = cola.procesar("u1") { llamadas++; ResultadoIntento.Hecho }
        assertEquals(0, llamadas)
        assertEquals(setOf("vieja"), resumen.descartadas)
    }

    @Test fun limpiarVaciaLaCola() = runBlocking {
        cola.agregar(accion("a"))
        cola.limpiar()
        assertEquals(0, cola.pendientes())
    }
}
