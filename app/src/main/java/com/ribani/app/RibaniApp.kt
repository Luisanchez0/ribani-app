package com.ribani.app

import android.app.Application
import com.ribani.app.data.remote.ApiClient
import com.ribani.app.data.repository.AlertasRepository

/**
 * Se ejecuta antes que cualquier pantalla, receptor o servicio. Sin esto, un
 * receptor de arranque podía crear el cliente HTTP sin el token.
 */
class RibaniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        AlertasRepository.inicializar(this)
    }
}
