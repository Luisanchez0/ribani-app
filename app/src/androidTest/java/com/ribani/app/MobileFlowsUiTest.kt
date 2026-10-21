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
        compose.onNodeWithText("YA LA TOMÉ").performScrollTo().assertIsEnabled().performClick()
        assertTrue(tomada)
        captura("ui-alarma")
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
                RIBANITheme { HomeScreen({ pulsado = true }, false) }
            }
        }
        compose.onNodeWithText("SOS").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(pulsado)
        captura("ui-inicio-fuente-grande")
    }
}
