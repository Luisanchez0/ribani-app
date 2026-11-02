package com.ribani.app

import com.ribani.app.data.repository.ResultadoIntento
import com.ribani.app.data.repository.clasificarErrorHttp
import com.ribani.app.data.repository.instanteAviso
import com.ribani.app.screens.home.NivelEstado
import com.ribani.app.screens.home.calcularEstadoInicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoInicioTest {
    private fun estado(
        proteccion: Boolean? = true,
        cargando: Boolean = false,
        monitoreo: Boolean = true,
        sensores: Boolean = true,
        abiertas: Int? = 0,
        porEnviar: Int = 0,
    ) = calcularEstadoInicio(proteccion, cargando, monitoreo, sensores, abiertas, porEnviar)

    @Test fun soloTodoComprobadoDiceQueEstaBien() {
        val e = estado()
        assertEquals(NivelEstado.Ok, e.nivel)
        assertEquals("Todo está bien", e.titulo)
    }

    @Test fun mientrasCargaNoAfirmaQueProtege() {
        val e = estado(proteccion = null, cargando = true)
        assertEquals(NivelEstado.Comprobando, e.nivel)
        assertFalse(e.chip.contains("activado"))
    }

    @Test fun sinConexionNoAfirmaQueProtege() {
        val e = estado(proteccion = null, abiertas = null)
        assertEquals(NivelEstado.Aviso, e.nivel)
        assertFalse(e.chip.contains("activado"))
    }

    @Test fun sinFamiliarEsProblema() {
        assertEquals(NivelEstado.Problema, estado(proteccion = false).nivel)
    }

    @Test fun servicioDetenidoEsProblemaAunqueHayaFamiliar() {
        val e = estado(monitoreo = false)
        assertEquals(NivelEstado.Problema, e.nivel)
        assertFalse(e.titulo == "Todo está bien")
    }

    @Test fun sinSensoresEsProblema() {
        assertEquals(NivelEstado.Problema, estado(sensores = false).nivel)
    }

    @Test fun unaAlertaAbiertaNoDiceQueNoHayAlertas() {
        val e = estado(abiertas = 2)
        assertEquals(NivelEstado.Aviso, e.nivel)
        assertTrue(e.detalle.contains("2"))
    }

    @Test fun avisaDeAlertasSinEnviar() {
        val e = estado(porEnviar = 1)
        assertEquals(NivelEstado.Aviso, e.nivel)
    }

    @Test fun alertasSinComprobarNoAfirmaQueNoHay() {
        assertFalse(estado(abiertas = null).detalle.contains("No hay alertas"))
    }

    @Test fun clasificaErroresDeEnvio() {
        assertEquals(ResultadoIntento.Reintentar, clasificarErrorHttp(500))
        assertEquals(ResultadoIntento.Reintentar, clasificarErrorHttp(401))
        assertEquals(ResultadoIntento.Descartar, clasificarErrorHttp(403))
        assertEquals(ResultadoIntento.Descartar, clasificarErrorHttp(409))
    }

    @Test fun unaTomaPospuestaAvisaEnRecordarEn() {
        assertEquals("r", instanteAviso("p", "r"))
        assertEquals("p", instanteAviso("p", null))
    }
}
