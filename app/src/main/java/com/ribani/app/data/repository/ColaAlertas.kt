package com.ribani.app.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Acción pendiente contra el backend. `tipoAccion` CREAR hace POST /alertas;
 * ACTUALIZAR hace PATCH sobre la última alerta abierta de `tipoAlerta`.
 */
data class AccionAlerta(
    val id: String,
    val usuarioId: String,
    val tipoAccion: String,
    val tipoAlerta: String,
    val creadaEnMs: Long,
    val titulo: String? = null,
    val descripcion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val estadoDestino: String? = null,
    val detalle: String? = null,
) {
    companion object {
        const val CREAR = "CREAR"
        const val ACTUALIZAR = "ACTUALIZAR"

        fun crear(
            id: String,
            usuarioId: String,
            tipoAlerta: String,
            titulo: String,
            descripcion: String,
            latitud: Double?,
            longitud: Double?,
            ahoraMs: Long,
        ) = AccionAlerta(
            id = id,
            usuarioId = usuarioId,
            tipoAccion = CREAR,
            tipoAlerta = tipoAlerta,
            creadaEnMs = ahoraMs,
            titulo = titulo,
            descripcion = descripcion,
            latitud = latitud,
            longitud = longitud,
        )

        fun actualizar(
            id: String,
            usuarioId: String,
            tipoAlerta: String,
            estadoDestino: String,
            detalle: String,
            ahoraMs: Long,
        ) = AccionAlerta(
            id = id,
            usuarioId = usuarioId,
            tipoAccion = ACTUALIZAR,
            tipoAlerta = tipoAlerta,
            creadaEnMs = ahoraMs,
            estadoDestino = estadoDestino,
            detalle = detalle,
        )
    }
}

enum class ResultadoIntento { Hecho, Reintentar, Descartar }

interface AlmacenAcciones {
    fun leer(): List<AccionAlerta>
    fun guardar(acciones: List<AccionAlerta>)
}

data class ResumenCola(
    val pendientes: Int,
    val completadas: Set<String>,
    val descartadas: Set<String>,
)

/**
 * Cola FIFO persistente: una alerta se escribe en disco antes del primer
 * intento, así que sobrevive a cerrar la pantalla, a perder la red y a que
 * el sistema mate el proceso. El orden importa: "ESTOY BIEN" debe llegar
 * después de la CAIDA que cierra, por eso un fallo temporal detiene la cola.
 */
class ColaAlertas(
    private val almacen: AlmacenAcciones,
    private val ahoraMs: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutexEscritura = Mutex()

    suspend fun agregar(accion: AccionAlerta) = mutexEscritura.withLock {
        almacen.guardar(almacen.leer() + accion)
    }

    suspend fun limpiar() = mutexEscritura.withLock { almacen.guardar(emptyList()) }

    suspend fun pendientes(): Int = mutexEscritura.withLock { almacen.leer().size }

    private suspend fun quitar(id: String) = mutexEscritura.withLock {
        almacen.guardar(almacen.leer().filterNot { it.id == id })
    }

    suspend fun procesar(
        usuarioActual: String?,
        ejecutar: suspend (AccionAlerta) -> ResultadoIntento,
    ): ResumenCola = mutex.withLock {
        val completadas = mutableSetOf<String>()
        val descartadas = mutableSetOf<String>()

        if (usuarioActual != null) {
            for (accion in mutexEscritura.withLock { almacen.leer() }) {
                val caducada = ahoraMs() - accion.creadaEnMs > CADUCIDAD_MS
                if (caducada || accion.usuarioId != usuarioActual) {
                    quitar(accion.id)
                    descartadas += accion.id
                    continue
                }
                when (ejecutar(accion)) {
                    ResultadoIntento.Hecho -> {
                        quitar(accion.id)
                        completadas += accion.id
                    }
                    ResultadoIntento.Descartar -> {
                        quitar(accion.id)
                        descartadas += accion.id
                    }
                    ResultadoIntento.Reintentar -> break
                }
            }
        }

        ResumenCola(pendientes(), completadas, descartadas)
    }

    companion object {
        /** Pasado un día ya no avisa: sería una falsa emergencia para la familia. */
        const val CADUCIDAD_MS = 24 * 60 * 60 * 1000L
    }
}
