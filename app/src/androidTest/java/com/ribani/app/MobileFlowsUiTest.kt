package com.ribani.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ribani.app.navigation.BottomNavigationBar
import com.ribani.app.screens.home.HomeScreen
import com.ribani.app.screens.home.calcularEstadoInicio
import com.ribani.app.screens.medication.MedicationAlarmScreen
import com.ribani.app.ui.theme.RIBANITheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileFlowsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun captura(nombre: String) {
        val archivo = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "$nombre.png")
        archivo.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun unaAlarmaNoBloqueaConfirmacionPorContadorVisual() {
        var tomada = false
        compose.setContent { RIBANITheme { MedicationAlarmScreen("Medicamento de prueba", "Dosis de prueba", "08:00", countDownSeconds = 0, onTaken = { tomada = true }, onOmit = {}) } }
        compose.onNodeWithText("Ya la tomé").assertIsDisplayed().assertIsEnabled().performClick()
        assertTrue(tomada)
        captura("ui-alarma")
    }
    @Test fun unaAlarmaCompletaSeUsaConFuenteGrande() {
        var omitida = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                RIBANITheme {
                    MedicationAlarmScreen(
                        nombreMedicamento = "Losartán potásico",
                        dosis = "50 mg",
                        horaProgramada = "08:00 a.m.",
                        instrucciones = "Tomar con un vaso de agua, después del desayuno.",
                        countDownSeconds = 0,
                        onTaken = {},
                        onOmit = { omitida = true },
                        error = "No se guardó la toma. Revisa tu conexión e inténtalo de nuevo.",
                    )
                }
            }
        }
        captura("ui-alarma-fuente-grande")
        // Sin desplazar: las acciones deben quedar visibles con cualquier tamaño de letra.
        compose.onNodeWithText("Ya la tomé").assertIsDisplayed()
        compose.onNodeWithText("Omitir esta toma").assertIsDisplayed().performClick()
        assertTrue(omitida)
    }
    @Test fun capturaAlarmaCompleta() {
        compose.setContent {
            RIBANITheme {
                MedicationAlarmScreen(
                    nombreMedicamento = "Losartán potásico",
                    dosis = "50 mg",
                    horaProgramada = "08:00 a.m.",
                    instrucciones = "Tomar con un vaso de agua, después del desayuno.",
                    countDownSeconds = 0,
                    onTaken = {},
                    onOmit = {},
                )
            }
        }
        compose.onNodeWithText("Ya la tomé").assertIsDisplayed()
        captura("ui-alarma-completa")
    }
    @Test fun laNavegacionExponeLaPestanaSeleccionada() {
        compose.setContent { RIBANITheme { BottomNavigationBar("home", {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Inicio").assertIsSelected()
        compose.onNodeWithText("Medicinas").assertIsNotSelected()
    }
    @Test fun inicioPermiteAccederAlSosConFuenteGrande() {
        var pulsado = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                RIBANITheme {
                    HomeScreen(
                        onSosClick = { pulsado = true },
                        estado = calcularEstadoInicio(true, false, true, true, 0, 0),
                    )
                }
            }
        }
        compose.onNodeWithText("SOS").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(pulsado)
        captura("ui-inicio-fuente-grande")
    }
}
