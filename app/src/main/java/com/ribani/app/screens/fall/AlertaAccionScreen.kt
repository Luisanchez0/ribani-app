package com.ribani.app.screens.fall

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ribani.app.data.repository.AlertasRepository
import kotlinx.coroutines.launch

@Composable
fun AlertaAccionScreen(esCaida: Boolean, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FallAlertScreen(
        onOkay = {
            if (!esCaida) onCerrar() else scope.launch {
                val guardada = AlertasRepository.confirmarBien()
                if (guardada) onCerrar()
            }
        },
        onNeedHelp = { scope.launch {
            val resultado = AlertasRepository.enviarAlerta(context, "SOS", "El adulto mayor pide ayuda", if (esCaida) "Solicitó ayuda tras una posible caída." else "Solicitó ayuda desde el botón SOS.")
            if (resultado is AlertasRepository.Resultado.Enviada) onCerrar()
        } },
        onTiempoAgotado = onCerrar,
    )
}
