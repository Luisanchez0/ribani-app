package com.ribani.app.data.repository

import android.content.Context
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.remote.ActualizarTomaRequest
import com.ribani.app.data.remote.MedicamentoResponse
import com.ribani.app.data.remote.TomaResponse
import com.ribani.app.notifications.MedicationAlarmScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class MedicationStatus {
    PENDING, TAKEN, POSTPONED, OMITTED
}

data class MedicationOccurrence(
    val id: String,
    val medicationId: String,
    val name: String,
    val dosage: String,
    val time: String,
    val status: MedicationStatus = MedicationStatus.PENDING,
    /** programada_para original (ISO 8601) para programar alarmas exactas. */
    val programadaIso: String? = null,
    /** Instrucciones del medicamento (del catálogo GET /medicamentos). */
    val instrucciones: String? = null,
)

data class Medication(
    val id: String,
    val name: String,
    val dosage: String,
    val schedule: String,
    val instructions: String = "",
    val isTaken: Boolean = false
)

data class MedicationUiState(
    val pendingMedications: List<MedicationOccurrence> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val history: List<MedicationOccurrence> = emptyList(),
    /** true cuando los datos vienen del backend (no de los demo). */
    val cargado: Boolean = false,
    /** true mientras corre un refresco iniciado por pull-to-refresh. */
    val refrescando: Boolean = false,
    /** Mensaje de estado para la UI (offline/demo). */
    val mensajeEstado: String? = null
)

/**
 * Repositorio de medicamentos alimentado por el backend (GET /tomas y
 * PATCH /tomas/{id}), a la par del panel web:
 *
 * - "Pendientes de hoy": tomas PENDIENTE programadas para hoy.
 * - "Historial": últimas tomas TOMADA/OMITIDA (para TOMADA se muestra
 *   la hora real tomada_en si existe).
 * - "Mis medicamentos": derivado de las tomas (el alta/edición de
 *   medicamentos la hace el familiar desde su panel web).
 * - Sin sesión o sin conexión: estado vacío con un mensaje de estado
 *   (SIN datos de demostración); las acciones no pegan al backend sin sesión.
 */
object MedicationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val formatoHora = DateTimeFormatter.ofPattern("hh:mm a")
    private val formatoDiaHora = DateTimeFormatter.ofPattern("'Ayer', hh:mm a'.'")

    /** Estado inicial/limpio: sin datos (NO hay medicamentos de demostración). */
    private val _uiState = MutableStateFlow(MedicationUiState())
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    fun initialize(context: Context) {
        scope.launch {
            val manager = SessionManager(context)

            if (!manager.isLoggedIn.first()) {
                _uiState.value = MedicationUiState(
                    mensajeEstado = "Inicia sesión para ver tus medicamentos."
                )
                return@launch
            }

            refrescar(context)
        }
    }

    /**
     * Limpieza al cerrar sesión o cambiar de usuario: vacía el estado
     * y cancela las alarmas de la sesión anterior. Invocado desde
     * Configuración (Cerrar Sesión) y cuando la sesión quedó invalidada.
     */
    fun limpiarSesion(context: Context) {
        _uiState.value = MedicationUiState()
        MedicationAlarmScheduler.cancelarTodas(context)
    }

    /** Recarga del backend: pendientes de hoy + historial reciente. */
    suspend fun refrescar(context: Context) {
        val manager = SessionManager(context)

        if (!manager.isLoggedIn.first()) {
            _uiState.value = MedicationUiState(
                mensajeEstado = "Inicia sesión para ver tus medicamentos."
            )
            return
        }

        // Pull-to-refresh: indicador visible durante la recarga.
        _uiState.value = _uiState.value.copy(refrescando = true)

        try {
            val zona = ZoneId.systemDefault()
            val hoy = LocalDate.now(zona)
            val desde = hoy.atStartOfDay(zona).toInstant().toString()
            val hasta = hoy.plusDays(1).atStartOfDay(zona).toInstant().toString()
            // Hasta 7 días de historial recargas de historial.
            val desdeHistorial = hoy.minusDays(7)
                .atStartOfDay(zona).toInstant().toString()

            // Paralelo: catálogo real de medicamentos + tomas.
            // GET /tomas no trae el nombre del medicamento (solo su id);
            // el catálogo se cruza aquí para mostrar el nombre real en
            // pendientes, alarma e historial.
            val catalogo = ApiClient.medicamentosService.listarMedicamentos(
                soloActivos = "false",
            )
            // id → medicamento, para cruzar el nombre real en cada toma
            // (incluye inactivos: el historial debe mostrar el nombre
            // aunque el medicamento ya esté eliminado).
            val nombrePorId: Map<String, MedicamentoResponse> =
                catalogo.associateBy { it.id }

            val tomasPendientes = ApiClient.medicamentosService.listarTomas(
                estado = "PENDIENTE",
                desde = desde,
                hasta = hasta,
            )
            val tomasRegistradas = ApiClient.medicamentosService.listarTomas(
                estado = "TOMADA",
                desde = desdeHistorial,
                hasta = hasta,
            )
            val tomasOmitidas = ApiClient.medicamentosService.listarTomas(
                estado = "OMITIDA",
                desde = desdeHistorial,
                hasta = hasta,
            )

            val pendientesUI = tomasPendientes.map { it.aOcurrencia(nombrePorId) }

            // Tarjetas "Mis medicamentos" desde el catálogo real del
            // backend; los horarios mostrados se derivan de las tomas
            // vistas. Se excluyen inactivos (p. ej. medicamentos
            // eliminados): no deben aparecer como tarjetas actuales.
            // Se ordenan por nombre para lectura estable.
            val catalogoUI = catalogo
                .asSequence()
                .filter { it.activo }
                .map { medicamento ->
                    val tomasDelMedicamento =
                        (tomasPendientes + tomasRegistradas + tomasOmitidas)
                            .filter { it.medicamento_id == medicamento.id }
                    val horarioTexto = tomasDelMedicamento
                        .map { it.programadaParaLocal().format(formatoHora) }
                        .distinct()
                        .take(4)
                        .joinToString(" · ")
                    Medication(
                        id = medicamento.id,
                        name = medicamento.nombre,
                        dosage = "${medicamento.dosis} ${medicamento.unidad_dosis}",
                        schedule = horarioTexto.ifEmpty { "Según lo programado" },
                        instructions = medicamento.instrucciones ?: "",
                    )
                }
                .sortedBy { it.name.lowercase() }
                .toList()

            val historialUI = (tomasRegistradas + tomasOmitidas)
                .sortedByDescending { it.programada_para }
                .map { it.aOcurrenciaHistorial(nombrePorId) }

            _uiState.value = MedicationUiState(
                pendingMedications = pendientesUI,
                medications = catalogoUI,
                history = historialUI,
                cargado = true,
                refrescando = false,
                mensajeEstado = null
            )

            // Con el estado fresco del backend, reprograma las alarmas de
            // las tomas pendientes de hoy ( MedicationAlarmScheduler).
            MedicationAlarmScheduler.programarPendientes(context)
        } catch (e: Exception) {
            // Sin red/401/etc.: se conserva lo ya cargado (si existe) y se avisa;
            // si nunca cargó, queda el estado vacío con el mensaje (no demo).
            _uiState.value =
                _uiState.value
                    .takeIf { it.cargado }
                    ?.copy(
                        refrescando = false,
                        mensajeEstado = "No se pudo actualizar: ${e.message ?: "sin conexión"}"
                    )
                    ?: MedicationUiState(
                        mensajeEstado =
                            "No se pudo actualizar: ${e.message ?: "sin conexión"}"
                    )
        }
    }

    /** PATCH /tomas/{id} con estado TOMADA (ventana del horario la valida el server). */
    fun markTaken(context: Context, occurrenceId: String) {
        actualizar(context, occurrenceId, ActualizarTomaRequest(estado = "TOMADA"))
    }

    /** PATCH /tomas/{id} con estado OMITIDA (sin ventana, como en la web). */
    fun markOmitted(context: Context, occurrenceId: String) {
        actualizar(context, occurrenceId, ActualizarTomaRequest(estado = "OMITIDA"))
    }

    /**
     * Posponer: POST /tomas/{id}/posponer — mueve la toma una vez,
     * diez minutos, dentro de la ventana original; el server la deja
     * en estado POSPUESTA (sigue dentro del grupo de pendientes).
     * El estado persiste en el backend, igual que tomar/omitir.
     */
    fun postpone(context: Context, occurrenceId: String) {
        // Solo si la lista es del backend (id UUID real, no demo).
        if (!_uiState.value.cargado) return

        scope.launch {
            try {
                ApiClient.medicamentosService.posponer(occurrenceId)
                // Re-sincroniza con el backend (estado en server es la verdad).
                refrescar(context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    mensajeEstado = "No se pudo posponer: ${e.message ?: "intenta de nuevo"}"
                )
            }
        }
    }

    private fun actualizar(
        context: Context,
        occurrenceId: String,
        cambio: ActualizarTomaRequest,
    ) {
        // Solo si la lista es del backend (id UUID real, no demo).
        if (!_uiState.value.cargado) return

        scope.launch {
            try {
                ApiClient.medicamentosService.actualizarToma(occurrenceId, cambio)
                // Re-sincroniza con el backend (estado en server es la verdad).
                refrescar(context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    mensajeEstado = "No se pudo registrar la toma: ${e.message ?: "intenta de nuevo"}"
                )
            }
        }
    }

    // ----- Ajustes de shape -----

    private fun TomaResponse.programadaParaLocal(): ZonedDateTime =
        Instant.parse(programada_para).atZone(ZoneId.systemDefault())

    private fun TomaResponse.aOcurrencia(
        nombrePorId: Map<String, MedicamentoResponse>,
    ): MedicationOccurrence {
        val medicamento = nombrePorId[medicamento_id]
        return MedicationOccurrence(
            id = id,
            medicationId = medicamento_id,
            name = medicamento?.nombre
                ?: nota
                ?: "Medicamento",
            dosage = medicamento?.let { "${it.dosis} ${it.unidad_dosis}" }
                ?: dosageDe(this),
            time = programadaParaLocal().format(formatoHora),
            // GET /tomas?estado=PENDIENTE incluye también POSPUESTA
            // (pospuesta: sigue pendiente de hoy, badge "pospuesto").
            status = if (estado == "POSPUESTA") {
                MedicationStatus.POSTPONED
            } else {
                MedicationStatus.PENDING
            },
            programadaIso = programada_para,
            instrucciones = medicamento?.instrucciones,
        )
    }

    private fun TomaResponse.aOcurrenciaHistorial(
        nombrePorId: Map<String, MedicamentoResponse>,
    ): MedicationOccurrence {
        val estadoUI = if (estado == "TOMADA") {
            MedicationStatus.TAKEN
        } else if (estado == "OMITIDA") {
            MedicationStatus.OMITTED
        } else {
            MedicationStatus.PENDING
        }

        // En TOMADA se prefiere la hora real de la toma si existe.
        val momento = if (estado == "TOMADA" && tomada_en != null) {
            Instant.parse(tomada_en).atZone(ZoneId.systemDefault())
        } else {
            programadaParaLocal()
        }

        val medicamento = nombrePorId[medicamento_id]

        return MedicationOccurrence(
            id = id,
            medicationId = medicamento_id,
            name = medicamento?.nombre
                ?: nota
                ?: "Medicamento",
            dosage = medicamento?.let { "${it.dosis} ${it.unidad_dosis}" }
                ?: dosageDe(this),
            time = momento.format(formatoDiaHora),
            status = estadoUI,
            instrucciones = medicamento?.instrucciones,
        )
    }

    /** Fallback si el catálogo no trae al medicamento de la toma. */
    private fun dosageDe(toma: TomaResponse): String =
        toma.dosis_tomada?.let { dosis ->
            "${dosis.toString().trimEnd('0', '.')} mg"
        } ?: "Dosis según indicación"

    /**
     * Registra el estado de la toma (TOMADA u OMITIDA) desde la alarma full-screen,
     * actualiza el backend y refresca el repositorio local.
     */
    suspend fun registrarDesdeAlarma(
        context: Context,
        tomaId: String,
        estado: String,
    ): Boolean {
        return try {
            ApiClient.medicamentosService.actualizarToma(
                tomaId,
                ActualizarTomaRequest(estado = estado)
            )
            refrescar(context)
            true
        } catch (_: Exception) {
            false
        }
    }
}
