package com.ribani.app

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ribani.app.notifications.SonidoAlarma
import com.ribani.app.sensors.AlarmaCaida
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No envía alertas al servidor: solo comprueba cómo se presenta la alarma. */
@RunWith(AndroidJUnit4::class)
class AlarmaCaidaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After fun detener() = AlarmaCaida.detener(context)

    private fun alarmaActiva(): Notification? =
        manager.activeNotifications.firstOrNull { it.id == 101 }?.notification

    @Test fun suenaComoAlarmaHastaQueSeResponda() {
        AlarmaCaida.mostrar(context)
        val alarma = assertNotNullValue(alarmaActiva())
        assertNotNull(alarma.fullScreenIntent)
        assertEquals(Notification.CATEGORY_ALARM, alarma.category)

        val canal = manager.getNotificationChannel(alarma.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, canal.importance)
        // El sonido no depende de la notificación: otra no puede interrumpirlo.
        assertEquals(null, canal.sound)
        assertTrue(SonidoAlarma.sonando)
    }

    @Test fun responderDetieneLaAlarma() {
        AlarmaCaida.mostrar(context)
        assertNotNull(alarmaActiva())
        AlarmaCaida.detener(context)
        // Android procesa la cancelación de forma asíncrona.
        val limite = System.currentTimeMillis() + 3_000
        while (alarmaActiva() != null && System.currentTimeMillis() < limite) Thread.sleep(100)
        assertEquals(null, alarmaActiva())
        assertTrue(!SonidoAlarma.sonando)
    }

    private fun assertNotNullValue(alarma: Notification?): Notification {
        assertNotNull(alarma)
        return alarma!!
    }
}
