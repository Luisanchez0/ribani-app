package com.ribani.app.notifications

enum class DecisionAviso { Programar, AvisarAhora, Ignorar }

/** Tras este margen, una toma sin avisar ya no hace sonar la alarma. */
const val GRACIA_AVISO_TARDIO_MS = 15 * 60_000L

/**
 * La familia puede crear una toma para dentro de segundos, y la app se entera en
 * su siguiente recarga: si la descubre ya vencida, debe avisar igualmente. Solo
 * una vez, porque cada recarga vuelve a pasar por aquí.
 */
fun decidirAviso(avisoMs: Long, ahoraMs: Long, yaAvisada: Boolean): DecisionAviso = when {
    avisoMs > ahoraMs -> DecisionAviso.Programar
    yaAvisada -> DecisionAviso.Ignorar
    ahoraMs - avisoMs <= GRACIA_AVISO_TARDIO_MS -> DecisionAviso.AvisarAhora
    else -> DecisionAviso.Ignorar
}
