package com.ribani.app.data.remote

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

object ApiErrorParser {

    private val gson = Gson()

    fun getErrorMessage(error: Throwable): String {
        return when (error) {
            is HttpException -> parseHttpException(error)
            is IOException -> "Sin conexión con el servidor. Verifica tu internet."
            else -> "Ocurrió un error inesperado. Intenta de nuevo."
        }
    }

    private fun parseHttpException(error: HttpException): String {
        return try {
            val body = error.response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                val apiError = gson.fromJson(body, ApiError::class.java)
                apiError.message?.takeIf { it.isNotBlank() }
                    ?: fallbackForCode(error.code())
            } else {
                fallbackForCode(error.code())
            }
        } catch (_: Exception) {
            fallbackForCode(error.code())
        }
    }

    private fun fallbackForCode(code: Int): String = when (code) {
        400 -> "Datos inválidos. Revisa la información ingresada."
        401 -> "Correo o contraseña incorrectos."
        403 -> "No tienes permisos para realizar esta acción."
        404 -> "Recurso no encontrado."
        409 -> "Ya existe un usuario con ese correo electrónico."
        422 -> "Datos inválidos. Revisa la información ingresada."
        500 -> "Error del servidor. Intenta más tarde."
        else -> "Error (código $code). Intenta de nuevo."
    }
}
