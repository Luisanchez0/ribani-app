package com.ribani.app

import com.ribani.app.notifications.DecisionAviso
import com.ribani.app.notifications.GRACIA_AVISO_TARDIO_MS
import com.ribani.app.notifications.decidirAviso
import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionAvisoTest {
    private val ahora = 1_000_000_000L

    @Test fun unaTomaFuturaSeProgramaAunqueYaSeHayaAvisado() {
        assertEquals(DecisionAviso.Programar, decidirAviso(ahora + 1, ahora, false))
        // Al posponer cambia la hora de aviso: vuelve a ser un aviso pendiente.
        assertEquals(DecisionAviso.Programar, decidirAviso(ahora + 1, ahora, true))
    }

    @Test fun unaTomaDescubiertaTardeAvisaDeInmediato() {
        assertEquals(DecisionAviso.AvisarAhora, decidirAviso(ahora - 30_000, ahora, false))
        assertEquals(DecisionAviso.AvisarAhora, decidirAviso(ahora, ahora, false))
    }

    @Test fun noRepiteUnAvisoYaDado() {
        assertEquals(DecisionAviso.Ignorar, decidirAviso(ahora - 30_000, ahora, true))
    }

    @Test fun unaTomaMuyAntiguaNoHaceSonarLaAlarma() {
        assertEquals(
            DecisionAviso.AvisarAhora,
            decidirAviso(ahora - GRACIA_AVISO_TARDIO_MS, ahora, false),
        )
        assertEquals(
            DecisionAviso.Ignorar,
            decidirAviso(ahora - GRACIA_AVISO_TARDIO_MS - 1, ahora, false),
        )
    }
}
