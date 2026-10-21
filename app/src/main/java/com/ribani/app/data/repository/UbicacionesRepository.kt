package com.ribani.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.ReporteUbicacionRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext

/**
 * Envío de coordenadas al backend con acumulación (batching) e intervalo
 * ADAPTATIVO según alertas activas del adulto.
 *
 * Ahorro de batería/datos (ver AGENTS.md del proyecto):
 * - Normal: fixes cada [INTERVALO_NORMAL_MS] (30 s) + flush cada 30 s.
 * - Modo alerta (hay alertas ABIERTA/EN_ATENCION): fixes cada
 *   [INTERVALO_ALERTA_MS] (10-15 s alternando) para tracking fino; el
 *   backend lo indica vía GET /alertas/intervalo-ubicacion y la propia
 *   creación de alertas (evaluarSalidaZonaSegura) ya lo impulsa.
 * - El intervalo se re-consulta cada [CHEQUEO_ALERTA_MS] (60 s) y la
 *   escucha se re-registra dinámicamente al cambiar.
 * - Cada fix encola y un flush por lote hace un único
 *   POST /ubicaciones/lote; fallos de red reintegran el lote a la cola.
 */
object UbicacionesRepository {

    /** Frecuencia de fixes en modo normal (ahorro de batería). */
    const val INTERVALO_NORMAL_MS = 30_000L

    /** Frecuencia de fixes en modo alerta (tracking fino). */
    const val INTERVALO_ALERTA_MS = 12_000L

    /** Cada cuánto se vacía la cola contra el backend. */
    private const val FLUSH_INTERVALO_MS = 30_000L

    /** Cada cuánto se re-consulta si hay alerta activa. */
    private const val CHEQUEO_ALERTA_MS = 60_000L

    /** Máximo del lote aceptado por POST /ubicaciones/lote (DTO server). */
    private const val MAX_LOTE = 100

    private val formatoIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /** Intervalo de fixes vigente (mutados bajo mutex/lectura volátil). */
    @Volatile
    private var intervaloVigenteMs: Long = INTERVALO_NORMAL_MS

    /**
     * Flag del último chequeo de alerta: guarda si el intervalo cambió desde
     * el último ciclo para decidir si reiniciar la escucha GPS.
     */
    @Volatile
    var intervaloCambio: Boolean = false
        private set

    /** Contexto de aplicación, inyectado por el servicio de monitoreo. */
    @Volatile
    private var appContext: Context? = null

    fun inicializar(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    appContext = context.applicationContext
                }
            }
        }
    }

    fun intervaloVigente(): Long = intervaloVigenteMs

    private val mutex = Mutex()
    private val cola = ArrayDeque<Location>()
    private var jobFlush: Job? = null
    private var jobChequeoAlerta: Job? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutexEnvio = Mutex()
    @Volatile private var revision = 0L

    /**
     * Encola un fix de forma no bloqueante: retorna inmediatamente y deja
     * que el temporizador de flush haga el envío por lote.
     */
    suspend fun encolar(location: Location) {
        val context = appContext ?: return
        val manager = SessionManager(context)

        val haySesion = runCatching {
            withTimeout(2_000) { manager.isLoggedIn.first() }
        }.getOrDefault(false)

        if (!haySesion) return

        mutex.withLock {
            cola.addLast(location)
            // Protege memoria: si acumuló más del máximo de un lote, descarta
            // los más antiguos (el historial reciente es lo que importa).
            while (cola.size > MAX_LOTE) cola.removeFirst()
        }

        iniciarTemporizadorSiHaceFalta()
        iniciarChequeoAlertaSiHaceFalta()
    }

    private fun iniciarTemporizadorSiHaceFalta() {
        if (jobFlush?.isActive == true) return

        jobFlush = scope.launch {
            while (true) {
                delay(FLUSH_INTERVALO_MS)
                vaciar()
            }
        }
    }

    /**
     * Consulta periódica del intervalo recomendado al backend
     * (GET /alertas/intervalo-ubicacion): 10..15 s con alerta activa,
     * 30 s en normal. Marca [intervaloCambio] cuando difiere del vigente,
     * señal con la que la escucha se re-registra.
     */
    private fun iniciarChequeoAlertaSiHaceFalta() {
        if (jobChequeoAlerta?.isActive == true) return

        jobChequeoAlerta = scope.launch {
            while (true) {
                delay(CHEQUEO_ALERTA_MS)
                runCatching { sincronizarIntervalo() }
            }
        }
    }

    suspend fun sincronizarIntervalo() {
        val context = appContext ?: return
        val manager = SessionManager(context)

        val haySesion = runCatching {
            withTimeout(5_000) { manager.isLoggedIn.first() }
        }.getOrDefault(false)

        if (!haySesion) {
            marcarIntervalo(INTERVALO_NORMAL_MS)
            return
        }

        val respuesta = runCatching {
            ApiClient.alertasService.intervaloUbicacion()
        }.getOrNull() ?: return

        val segundos = respuesta.intervalo_segundos
        marcarIntervalo((segundos.toLong() * 1000L).coerceIn(10_000L, 30_000L))
    }

    private fun marcarIntervalo(nuevoMs: Long) {
        if (intervaloVigenteMs == nuevoMs) return
        intervaloVigenteMs = nuevoMs
        intervaloCambio = true
    }

    /** El consumidor debe consumir/consumir[group] el flag una vez aplicado. */
    fun consumirCambioIntervalo(): Boolean {
        if (!intervaloCambio) return false
        intervaloCambio = false
        return true
    }

    /**
     * Vacía la cola en un solo POST /ubicaciones/lote. Los fallos (offline,
     * 401) re-encierran los reportes al frente de la cola para el próximo
     * intento, con tope de MAX_LOTE.
     */
    suspend fun vaciar() = mutexEnvio.withLock envio@{
        val locations: List<Location>
        val revisionEnvio = revision

        mutex.withLock {
            if (cola.isEmpty()) return@envio
            locations = List(cola.size) { cola.removeFirst() }
        }

        val lote = locations.map { aDto(it) }

        try {
            ApiClient.ubicacionesService.reportarLote(
                com.ribani.app.data.remote.ReporteLoteRequest(lote)
            )
        } catch (e: Exception) {
            // Offline/401: conservar los reportes para el próximo flush.
            mutex.withLock {
                if (revision != revisionEnvio) return@withLock
                locations.asReversed().forEach { cola.addFirst(it) }
                while (cola.size > MAX_LOTE) cola.removeFirst()
            }
        }
    }

    private fun aDto(location: Location): ReporteUbicacionRequest {
        val fix = location.time

        return ReporteUbicacionRequest(
            latitud = location.latitude,
            longitud = location.longitude,
            precision_metros = location.accuracy.takeIf { it > 0f }
                ?.toDouble()
                ?.coerceAtMost(10_000.0),
            altitud_metros = location.altitude.takeIf { it != 0.0 }
                ?.coerceIn(-1_000.0, 10_000.0),
            fuente = when (location.provider) {
                "network", "passive" -> "RED"
                else -> "GPS"
            },
            registrada_en = Instant.ofEpochMilli(fix)
                .atZone(ZoneId.systemDefault())
                .format(formatoIso),
        )
    }

    /**
     * Escucha continuo de ubicación que acumula en la cola (envío por lote).
     * Referenciado desde RibaniMonitoringService: funciona con la app en
     * segundo plano mientras el servicio de monitoreo viva.
     */
    fun iniciarEscucha(context: Context): UbicacionesEscucha =
        UbicacionesEscucha(context)

    fun destructor() {
        revision++
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        jobFlush = null
        jobChequeoAlerta = null
        // Los reportes pendientes pertenecen a la sesión que se está cerrando.
        kotlinx.coroutines.runBlocking { mutex.withLock { cola.clear() } }
    }
}

/**
 * Listener continuo con FusedLocationProvider. Cada fix entra a la cola;
 * el flush por lote es aparte.
 *
 * Ajuste dinámico: en cada [onLocationResult] y en el propio [iniciar]
 * revisa si el intervalo recomendado cambió (normal 30 s ↔ alerta ~12 s);
 * si cambió, re-registra las actualizaciones con el nuevo intervalo.
 */
class UbicacionesEscucha(
    private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val escuchaScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            escuchaScope.launch {
                UbicacionesRepository.encolar(location)
                verificarCambioDeIntervalo()
            }
        }
    }

    /**
     * La escucha deja de registrar/desregistrar en cada cambio: si el
     * backend bajó el intervalo a modo alerta (o lo volvió a 30 s), se
     * re-request con el intervalo vigente.
     */
    private suspend fun verificarCambioDeIntervalo() {
        if (!UbicacionesRepository.consumirCambioIntervalo()) return

        runCatching {
            client.removeLocationUpdates(callback)
            registrar()
        }
    }

    @SuppressLint("MissingPermission")
    private fun registrar() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UbicacionesRepository.intervaloVigente(),
        ).build()

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun iniciar() {
        // Primera sincronización del modo (normal/alerta) antes de registrar.
        escuchaScope.launch {
            runCatching { UbicacionesRepository.sincronizarIntervalo() }
            verificarCambioDeIntervalo()
        }

        registrar()
    }

    fun detener() {
        client.removeLocationUpdates(callback)
        escuchaScope.cancel()
    }
}
