package com.ribani.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── RIBANI: tokens de color centralizados (light mode) ─────────────────────
// Se usan desde todas las pantallas para evitar hex codificados por pantalla.

// Primario / marca
val VerdeRIBANI = Color(0xFF006B3C)
val VerdeClaro = Color(0xFF63B52A)
val VerdeEstadoOk = Color(0xFF34A853)
val ContenedorVerdeSuave = Color(0xFFE8F5E9)

// Peligro / alerta
val Rojo = Color(0xFFC62828)
// Contraste WCAG 4.98:1 con texto blanco (antes #EF2020 = 4.29:1,
// insuficiente como texto de error de 14sp).
val RojoBoton = Color(0xFFD32F2F)
val ContenedorRojoSuave = Color(0xFFFDECEA)

// Estados de medicación
// Contraste WCAG ≥4.5:1 con texto blanco (verificado):
// VerdeBotonTomar 5.16:1, NaranjaPostpuesto 5.02:1 (y 4.61:1 sobre tarjeta
// F5F5F5), VerdeTomado 5.13:1.
val VerdeBotonTomar = Color(0xFF287D30)
val NaranjaPostpuesto = Color(0xFFB45309)
val VerdeTomado = Color(0xFF2E7D32)

// Superficies
val FondoApp = Color(0xFFFAFAF8)
val SuperficieTarjeta = Color(0xFFF3F2F7)
val SuperficieTarjetaTintada = Color(0xFFECECFD)
val Separador = Color(0xFFE0DFE3)
val ContenedorCircularIcono = Color(0xFFD0B8FF)

// Texto
val TextoPrincipal = Color(0xFF151515)
val TextoOscuro = Color(0xFF211F24)
val TextoSecundario = Color(0xFF444444)
val TextoAjeno = Color(0xFF6B7280)
val TextoOscuroSecundario = Color(0xFF4B5563)
val TextoEnTarjetasGris = Color(0xFF374151)
val TextoDatos = Color(0xFF111827)
val TextoNeutro = Color(0xFF616161)

// Barra de navegación
val FondoBarraNavegacion = Color(0xFFECECFD)
val IconoActivoBarra = VerdeRIBANI
val IconoInactivoBarra = Color(0xFF5F5D63)
