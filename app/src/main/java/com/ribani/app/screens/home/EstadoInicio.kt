package com.ribani.app.screens.home

enum class NivelEstado { Ok, Comprobando, Aviso, Problema }

data class EstadoInicio(
    val nivel: NivelEstado,
    val chip: String,
    val titulo: String,
    val detalle: String,
)

/**
 * Lo que la pantalla de inicio afirma. Solo dice que protege si lo comprobó:
 * un adulto mayor no debe confiar en un "activado" que no es cierto.
 */
fun calcularEstadoInicio(
    proteccionActiva: Boolean?,
    cargando: Boolean,
    monitoreoActivo: Boolean,
    sensoresDisponibles: Boolean,
    alertasAbiertas: Int?,
    alertasPorEnviar: Int,
): EstadoInicio {
    val activado = "Sistema activado y monitoreando"
    return when {
        proteccionActiva == false -> EstadoInicio(
            NivelEstado.Problema,
            "Sistema desactivado • Sin familiar encargado",
            "Protección desactivada",
            "No tienes un familiar encargado vinculado",
        )
        proteccionActiva == null && cargando -> EstadoInicio(
            NivelEstado.Comprobando,
            "Comprobando el sistema…",
            "Comprobando…",
            "Estamos revisando tu protección",
        )
        !monitoreoActivo -> EstadoInicio(
            NivelEstado.Problema,
            "Monitoreo detenido",
            "Monitoreo inactivo",
            "No se están vigilando las caídas. Cierra y vuelve a abrir la app",
        )
        !sensoresDisponibles -> EstadoInicio(
            NivelEstado.Problema,
            "Sensores no disponibles",
            "Monitoreo inactivo",
            "Este teléfono no tiene los sensores para detectar caídas",
        )
        alertasPorEnviar > 0 -> EstadoInicio(
            NivelEstado.Aviso,
            "Alertas pendientes de enviar",
            "Sin enviar todavía",
            "Seguimos intentando enviar $alertasPorEnviar aviso(s) a tu familiar",
        )
        proteccionActiva == null -> EstadoInicio(
            NivelEstado.Aviso,
            "Sin conexión con el servidor",
            "No se pudo comprobar",
            "Las caídas se vigilan, pero revisa tu conexión a internet",
        )
        alertasAbiertas == null -> EstadoInicio(
            NivelEstado.Ok,
            activado,
            "Protección activa",
            "No se pudieron comprobar tus alertas",
        )
        alertasAbiertas > 0 -> EstadoInicio(
            NivelEstado.Aviso,
            activado,
            "Tienes alertas abiertas",
            "$alertasAbiertas alerta(s) esperando atención de tu familiar",
        )
        else -> EstadoInicio(NivelEstado.Ok, activado, "Todo está bien", "No hay alertas abiertas")
    }
}
