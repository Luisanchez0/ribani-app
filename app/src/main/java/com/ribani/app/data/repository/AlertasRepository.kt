package com.ribani.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.CrearAlertaRequest
import com.ribani.app.data.remote.ResolverAlertaRequest
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/** Lo que el adulto ve mientras se pide ayuda. */
enum class EstadoAyuda { Inactivo, Enviando, Enviada, Reintentando, Rechazada }

/**
 * Envío de alertas al backend. Todo pasa por [ColaAlertas] y un único proceso
 * de envío con vida de aplicación: cerrar la pantalla no cancela una alerta, y
 * los fallos de red se reintentan solos y se muestran al adulto.
 */
object AlertasRepository {

    private const val TIPO_CAIDA = "CAIDA"
    private const val TIPO_SOS = "SOS"
    private const val PREFS = "ribani_alertas_pendientes"
    private const val CLAVE = "acciones"
    private const val DEMORA_PARA_INDICAR_HORA_MS = 60_000L
    private val ESPERAS_MS = listOf(5_000L, 15_000L, 30_000L, 60_000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val despertar = Channel<Unit>(Channel.CONFLATED)

    private val _estadoAyuda = MutableStateFlow(EstadoAyuda.Inactivo)
    val estadoAyuda: StateFlow<EstadoAyuda> = _estadoAyuda.asStateFlow()

    private val _porEnviar = MutableStateFlow(0)
    /** Alertas guardadas que aún no llegaron al backend. */
    val porEnviar: StateFlow<Int> = _porEnviar.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var cola: ColaAlertas? = null
    @Volatile private var solicitudActual: String? = null

    /** Se llama al crear el proceso: retoma lo que quedó pendiente en disco. */
    fun inicializar(context: Context) {
        if (cola != null) return
        synchronized(this) {
            if (cola != null) return
            val app = context.applicationContext
            appContext = app
            val nuevaCola = ColaAlertas(AlmacenPreferencias(app))
            cola = nuevaCola
            scope.launch { procesarSiempre(app, nuevaCola) }
        }
    }

    private suspend fun procesarSiempre(context: Context, cola: ColaAlertas) {
        var fallos = 0
        while (true) {
            val resumen = try {
                val usuario = SessionManager(context).currentUser()?.id
                cola.procesar(usuario) { ejecutar(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ResumenCola(maxOf(_porEnviar.value, 1), emptySet(), emptySet())
            }
            _porEnviar.value = resumen.pendientes
            actualizarEstadoSolicitud(resumen)

            if (resumen.pendientes == 0) {
                fallos = 0
                despertar.receive()
            } else {
                val espera = ESPERAS_MS[fallos.coerceAtMost(ESPERAS_MS.lastIndex)]
                fallos++
                withTimeoutOrNull(espera) { despertar.receive() }
            }
        }
    }

    private fun actualizarEstadoSolicitud(resumen: ResumenCola) {
        val id = solicitudActual ?: return
        _estadoAyuda.value = when (id) {
            in resumen.completadas -> {
                solicitudActual = null
                EstadoAyuda.Enviada
            }
            in resumen.descartadas -> {
                solicitudActual = null
                EstadoAyuda.Rechazada
            }
            else -> EstadoAyuda.Reintentando
        }
    }

    private suspend fun ejecutar(accion: AccionAlerta): ResultadoIntento = try {
        when (accion.tipoAccion) {
            AccionAlerta.CREAR -> crear(accion)
            else -> actualizar(accion)
        }
        ResultadoIntento.Hecho
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        clasificarErrorHttp(e.code())
    } catch (_: IOException) {
        ResultadoIntento.Reintentar
    } catch (_: Exception) {
        ResultadoIntento.Reintentar
    }

    private suspend fun crear(accion: AccionAlerta) {
        val demora = System.currentTimeMillis() - accion.creadaEnMs
        val descripcion = if (demora > DEMORA_PARA_INDICAR_HORA_MS) {
            "${accion.descripcion} (Enviada con ${demora / 60_000} min de retraso por falta de conexión.)"
        } else {
            accion.descripcion
        }
        ApiClient.alertasService.crearAlerta(
            CrearAlertaRequest(
                tipo = accion.tipoAlerta,
                titulo = accion.titulo.orEmpty(),
                severidad = "CRITICA",
                descripcion = descripcion,
                latitud = accion.latitud,
                longitud = accion.longitud,
            ),
        )
    }

    private suspend fun actualizar(accion: AccionAlerta) {
        val destino = accion.estadoDestino ?: return
        // EN_ATENCION solo es válido desde ABIERTA; cerrar vale también desde EN_ATENCION.
        val estadosOrigen = if (destino == "EN_ATENCION") {
            setOf("ABIERTA")
        } else {
            setOf("ABIERTA", "EN_ATENCION")
        }
        // GET /alertas viene ordenado de la más reciente a la más antigua.
        val alerta = ApiClient.alertasService.listarAlertas()
            .firstOrNull { it.tipo == accion.tipoAlerta && it.estado in estadosOrigen }
            ?: return
        ApiClient.alertasService.actualizarAlerta(
            id = alerta.id,
            body = ResolverAlertaRequest(estado = destino, detalle = accion.detalle),
        )
    }

    /** Posible caída detectada: queda registrada aunque el adulto no responda. */
    suspend fun reportarCaida(context: Context) {
        inicializar(context)
        val usuario = SessionManager(context).currentUser()?.id ?: return
        val (lat, lng) = ultimaLatitudLongitud(context)
        encolar(
            AccionAlerta.crear(
                id = UUID.randomUUID().toString(),
                usuarioId = usuario,
                tipoAlerta = TIPO_CAIDA,
                titulo = "Posible caída detectada",
                descripcion = "RIBANI detectó una posible caída del adulto mayor " +
                    "(caída libre, impacto y rotación).",
                latitud = lat,
                longitud = lng,
                ahoraMs = System.currentTimeMillis(),
            ),
        )
    }

    /** El adulto pide ayuda: crea un SOS y, si venía de una caída, la escala. */
    suspend fun pedirAyuda(context: Context, trasCaida: Boolean) {
        inicializar(context)
        _estadoAyuda.value = EstadoAyuda.Enviando
        val usuario = SessionManager(context).currentUser()?.id
        if (usuario == null) {
            _estadoAyuda.value = EstadoAyuda.Rechazada
            return
        }
        val (lat, lng) = ultimaLatitudLongitud(context)
        val ahora = System.currentTimeMillis()
        val sos = AccionAlerta.crear(
            id = UUID.randomUUID().toString(),
            usuarioId = usuario,
            tipoAlerta = TIPO_SOS,
            titulo = "El adulto mayor pide ayuda",
            descripcion = if (trasCaida) {
                "Presionó ¡NECESITO AYUDA! tras una alerta de posible caída."
            } else {
                "Presionó el botón SOS de la aplicación."
            },
            latitud = lat,
            longitud = lng,
            ahoraMs = ahora,
        )
        solicitudActual = sos.id
        encolar(sos)
        if (trasCaida) {
            encolar(
                AccionAlerta.actualizar(
                    id = UUID.randomUUID().toString(),
                    usuarioId = usuario,
                    tipoAlerta = TIPO_CAIDA,
                    estadoDestino = "EN_ATENCION",
                    detalle = "El adulto mayor presionó ¡NECESITO AYUDA!.",
                    ahoraMs = ahora,
                ),
            )
        }
    }

    /** "ESTOY BIEN": cierra la caída, aunque su creación siga pendiente en la cola. */
    suspend fun confirmarBien(context: Context) {
        encolarCambio(context, TIPO_CAIDA, "RESUELTA", "El adulto mayor confirmó que está bien.")
    }

    /** SOS pulsado por error: se cancela para que la familia no quede en alerta. */
    suspend fun cancelarAyuda(context: Context) {
        encolarCambio(context, TIPO_SOS, "CANCELADA", "El adulto mayor indicó que fue sin querer.")
        solicitudActual = null
        _estadoAyuda.value = EstadoAyuda.Inactivo
    }

    private suspend fun encolarCambio(
        context: Context,
        tipoAlerta: String,
        estado: String,
        detalle: String,
    ) {
        inicializar(context)
        val usuario = SessionManager(context).currentUser()?.id ?: return
        encolar(
            AccionAlerta.actualizar(
                id = UUID.randomUUID().toString(),
                usuarioId = usuario,
                tipoAlerta = tipoAlerta,
                estadoDestino = estado,
                detalle = detalle,
                ahoraMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun encolar(accion: AccionAlerta) {
        val colaActual = cola ?: return
        colaActual.agregar(accion)
        _porEnviar.value = colaActual.pendientes()
        despertar.trySend(Unit)
    }

    fun reintentarAhora() {
        despertar.trySend(Unit)
    }

    /** La pantalla de ayuda se cerró: el envío sigue, pero ya no hay nada que mostrar. */
    fun olvidarEstadoAyuda() {
        solicitudActual = null
        _estadoAyuda.value = EstadoAyuda.Inactivo
    }

    /** Cierre de sesión voluntario: lo pendiente pertenece a la cuenta que sale. */
    suspend fun limpiar() {
        cola?.limpiar()
        _porEnviar.value = 0
        olvidarEstadoAyuda()
    }

    /** Alertas sin cerrar del adulto. null = no se pudo comprobar. */
    suspend fun contarAbiertas(): Int? = try {
        ApiClient.alertasService.listarAlertas()
            .count { it.estado == "ABIERTA" || it.estado == "EN_ATENCION" }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Última ubicación conocida del dispositivo (fix rápido, sin GPS largo). */
    @SuppressLint("MissingPermission")
    private fun ultimaLatitudLongitud(context: Context): Pair<Double?, Double?> {
        val permisoFino = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val permisoGrueso = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!permisoFino && !permisoGrueso) return null to null

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnown = runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()

        return lastKnown?.latitude to lastKnown?.longitude
    }

    private class AlmacenPreferencias(context: Context) : AlmacenAcciones {
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private val gson = Gson()
        private val tipoLista = object : TypeToken<List<AccionAlerta>>() {}.type

        override fun leer(): List<AccionAlerta> {
            val json = prefs.getString(CLAVE, null) ?: return emptyList()
            return runCatching { gson.fromJson<List<AccionAlerta>>(json, tipoLista) }
                .getOrNull()
                .orEmpty()
        }

        override fun guardar(acciones: List<AccionAlerta>) {
            // commit: la alerta debe estar en disco antes de seguir.
            prefs.edit().putString(CLAVE, gson.toJson(acciones)).commit()
        }
    }
}

/** 4xx (salvo 401/408/429) no se arregla reintentando; 401 y 5xx sí pueden resolverse. */
internal fun clasificarErrorHttp(codigo: Int): ResultadoIntento = when (codigo) {
    401, 408, 429 -> ResultadoIntento.Reintentar
    in 400..499 -> ResultadoIntento.Descartar
    else -> ResultadoIntento.Reintentar
}
