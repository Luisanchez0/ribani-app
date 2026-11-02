package com.ribani.app.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Sonido y vibración de las alarmas, con reproductor propio. El de la notificación
 * no sirve: Android solo mantiene un sonido de notificación, y un mensaje de
 * WhatsApp bastaba para dejar la alarma en silencio.
 */
object SonidoAlarma {
    private val ATRIBUTOS = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val VIBRACION = longArrayOf(0, 800, 400, 800, 400)

    private val principal = Handler(Looper.getMainLooper())
    /** Puede haber varias alarmas a la vez: suena hasta atender la última. */
    private val activas = mutableSetOf<Int>()
    private var reproductor: MediaPlayer? = null
    private var vibrador: Vibrator? = null
    private var foco: AudioFocusRequest? = null

    /** Para pruebas y diagnóstico. */
    @get:Synchronized
    val sonando: Boolean get() = reproductor?.isPlaying == true

    @Synchronized
    fun iniciar(context: Context, id: Int, duracionMs: Long) {
        val app = context.applicationContext
        activas += id
        principal.removeCallbacksAndMessages(id)
        // Tope de seguridad, por si nadie responde.
        principal.postAtTime({ detener(app, id) }, id, android.os.SystemClock.uptimeMillis() + duracionMs)
        if (reproductor != null) return

        pedirFoco(app)
        reproductor = crearReproductor(app)
        vibrador = (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.also {
            @Suppress("DEPRECATION")
            it.vibrate(VibrationEffect.createWaveform(VIBRACION, 0), ATRIBUTOS)
        }
    }

    @Synchronized
    fun detener(context: Context, id: Int) {
        principal.removeCallbacksAndMessages(id)
        activas -= id
        if (activas.isNotEmpty()) return

        runCatching { reproductor?.stop() }
        reproductor?.release()
        reproductor = null
        vibrador?.cancel()
        vibrador = null
        foco?.let { peticion ->
            context.applicationContext.getSystemService(AudioManager::class.java)
                ?.abandonAudioFocusRequest(peticion)
        }
        foco = null
    }

    private fun crearReproductor(context: Context): MediaPlayer? {
        for (uri in tonosDisponibles(context)) {
            val candidato = MediaPlayer()
            try {
                candidato.setAudioAttributes(ATRIBUTOS)
                candidato.setDataSource(context, uri)
                candidato.isLooping = true
                candidato.prepare()
                candidato.start()
                return candidato
            } catch (_: Exception) {
                // Tono inaccesible o dañado: prueba con el siguiente.
                candidato.release()
            }
        }
        return null
    }

    private fun tonosDisponibles(context: Context): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    )

    /** Pausa la música mientras suena la alarma. */
    private fun pedirFoco(context: Context) {
        val peticion = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ATRIBUTOS)
            .build()
        context.getSystemService(AudioManager::class.java)?.requestAudioFocus(peticion)
        foco = peticion
    }
}
