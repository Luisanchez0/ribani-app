package com.ribani.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.AlertasRepository
import com.ribani.app.data.repository.AuthRepository
import com.ribani.app.data.repository.AjustesRepository
import com.ribani.app.data.repository.MedicationRepository
import com.ribani.app.data.repository.SessionValidation
import com.ribani.app.screens.auth.LoginScreen
import com.ribani.app.screens.auth.ActivarInvitacionScreen
import com.ribani.app.screens.auth.RecuperarPasswordScreen
import com.ribani.app.screens.auth.TwoFactorLoginScreen
import com.ribani.app.screens.auth.RegisterScreen
import com.ribani.app.screens.contacts.ContactsScreen
import com.ribani.app.screens.fall.AlertaCaidaScreen
import com.ribani.app.screens.fall.SosScreen
import com.ribani.app.screens.home.HomeScreen
import com.ribani.app.screens.home.PermisoUbicacionFondoDialog
import com.ribani.app.screens.home.calcularEstadoInicio
import com.ribani.app.screens.invitaciones.InvitacionesScreen
import com.ribani.app.screens.location.LocationScreen
import com.ribani.app.screens.medication.MedicationDetailScreen
import com.ribani.app.screens.medication.MedicationHistoryScreen
import com.ribani.app.screens.medication.MedicationScreen
import com.ribani.app.screens.settings.SettingsScreen
import com.ribani.app.screens.settings.TwoFactorSettingsScreen
import com.ribani.app.sensors.FallAlertEvents
import com.ribani.app.sensors.MonitoreoController
import com.ribani.app.sensors.MonitoreoEstado
import com.ribani.app.ui.theme.FondoApp
import com.ribani.app.ui.theme.VerdeRIBANI
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val INTERVALO_ESTADO_INICIO_MS = 60_000L

const val LOGIN_ROUTE = "login?cuentaCreada={cuentaCreada}"
const val REGISTER_ROUTE = "register"
// Se llega tras un login que respondió twoFactorRequired (2FA activado)
const val TWO_FACTOR_LOGIN_ROUTE = "login/twofactor/{challengeToken}"
// Recuperación de contraseña (código por correo)
const val RECUPERAR_PASSWORD_ROUTE = "login/recuperar"
// Activación de una cuenta creada por invitación (código por correo)
const val ACTIVAR_INVITACION_ROUTE = "login/invitacion"

@Composable
fun AppNavigation(
    startWithFallAlert: Boolean = false,
    initialMedicationId: String? = null,
    startWithInvitaciones: Boolean = false
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val medicationUiState = MedicationRepository.uiState.collectAsState().value
    val ajustesUiState = AjustesRepository.uiState.collectAsState().value

    val alertasPorEnviar by AlertasRepository.porEnviar.collectAsState()
    val monitoreoActivo by MonitoreoEstado.activo.collectAsState()
    val sensoresDisponibles by MonitoreoEstado.sensoresDisponibles.collectAsState()

    // Verificación de sesión guardada antes de decidir la pantalla inicial
    var sessionChecked by remember { mutableStateOf(false) }
    // Solo decide la pantalla inicial: cambiarla después reiniciaría la navegación.
    var isLoggedIn by remember { mutableStateOf(false) }
    val sessionManager = remember { SessionManager(context.applicationContext) }
    val sesionActiva by sessionManager.isLoggedIn.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        val hasLocalSession = sessionManager.isLoggedIn.first()

        if (!hasLocalSession) {
            sessionChecked = true
            return@LaunchedEffect
        }

        // Hay sesión guardada: la validamos contra el servidor (GET /auth/me).
        // Si el token fue revocado, el interceptor intenta refresh; si también falla,
        // Invalid => vamos a login. Sin internet => Offline => entramos con sesión local.
        scope.launch {
            val validation = AuthRepository(sessionManager).getCurrentUser()
            when (validation) {
                is SessionValidation.Valid -> isLoggedIn = true
                is SessionValidation.Invalid -> {
                    sessionManager.clear()
                    isLoggedIn = false
                }
                is SessionValidation.Offline -> isLoggedIn = true
            }
            sessionChecked = true
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialMedicationId) {
        if (!initialMedicationId.isNullOrBlank()) {
            navController.navigate("medication/detail/$initialMedicationId") {
                launchSingleTop = true
            }
        }
    }

    // Sigue la sesión real, venga de un login, de cerrar sesión o de que el
    // servidor la haya revocado con la app abierta.
    LaunchedEffect(sesionActiva, sessionChecked) {
        if (!sessionChecked) return@LaunchedEffect
        when (sesionActiva) {
            true -> {
                MonitoreoController.sincronizar(context)
                // Carga las tomas y programa las alarmas sin esperar a abrir Medicinas.
                MedicationRepository.initialize(context)
            }
            false -> {
                MedicationRepository.limpiarSesion(context)
                AjustesRepository.limpiar()
                val ruta = navController.currentDestination?.route
                if (ruta != null && !ruta.startsWith("login") && ruta != REGISTER_ROUTE) {
                    navController.navigate("login") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
            }
            null -> Unit
        }
    }

    LaunchedEffect(navController) {
        FallAlertEvents.events.collect {
            navController.navigate("fall") {
                launchSingleTop = true
            }
        }
    }

    if (!sessionChecked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FondoApp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = VerdeRIBANI)
        }
        return
    }

    val showBottomBar = currentRoute?.startsWith("login") != true &&
        currentRoute != REGISTER_ROUTE &&
        currentRoute != "fall" &&
        currentRoute != "sos"

    Scaffold(
        containerColor = FondoApp,
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onHomeClick = { navigateToTab("home") },
                    onMedicationClick = { navigateToTab("medication") },
                    onLocationClick = { navigateToTab("location") },
                    onContactsClick = { navigateToTab("contacts") },
                    onSettingsClick = { navigateToTab("settings") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = when {
                startWithFallAlert -> "fall"
                startWithInvitaciones && isLoggedIn -> "invitaciones"
                isLoggedIn -> "home"
                else -> LOGIN_ROUTE
            },
            modifier = Modifier
                .fillMaxSize()
                .background(FondoApp)
                .padding(innerPadding)
        ) {
            composable(
                route = LOGIN_ROUTE,
                arguments = listOf(
                    navArgument("cuentaCreada") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val cuentaCreada = backStackEntry.arguments?.getBoolean("cuentaCreada") ?: false
                LoginScreen(
                    cuentaCreada = cuentaCreada,
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                        }
                    },
                    onTwoFactorRequired = { challengeToken ->
                        val encoded = android.net.Uri.encode(challengeToken)
                        navController.navigate("login/twofactor/$encoded") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(REGISTER_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRecuperar = {
                        navController.navigate(RECUPERAR_PASSWORD_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToActivarInvitacion = {
                        navController.navigate(ACTIVAR_INVITACION_ROUTE) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = TWO_FACTOR_LOGIN_ROUTE,
                arguments = listOf(
                    navArgument("challengeToken") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val challengeToken = backStackEntry.arguments?.getString("challengeToken").orEmpty()
                TwoFactorLoginScreen(
                    challengeToken = challengeToken,
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = RECUPERAR_PASSWORD_ROUTE) {
                RecuperarPasswordScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = ACTIVAR_INVITACION_ROUTE) {
                ActivarInvitacionScreen(
                    onActivada = {
                        navController.navigate("login?cuentaCreada=true") {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = REGISTER_ROUTE) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate("login?cuentaCreada=true") {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("home") {
                // Se recarga al volver (p. ej. tras aceptar una invitación) y
                // periódicamente: la familia puede atender una alerta mientras se mira.
                // También en cuanto la cola cambia, para contar la alerta recién enviada.
                LaunchedEffect(alertasPorEnviar) {
                    while (true) {
                        AjustesRepository.cargar(context)
                        delay(INTERVALO_ESTADO_INICIO_MS)
                    }
                }
                PermisoUbicacionFondoDialog()
                HomeScreen(
                    onSosClick = {
                        navController.navigate("sos") {
                            launchSingleTop = true
                        }
                    },
                    // Desde el modal de bloqueo del SOS: lleva a Invitaciones
                    // para vincularse con un FAMILIAR_ENCARGADO responsable.
                    onOpenInvitaciones = {
                        navController.navigate("invitaciones") {
                            launchSingleTop = true
                        }
                    },
                    estado = calcularEstadoInicio(
                        proteccionActiva = ajustesUiState.proteccionActiva,
                        cargando = ajustesUiState.cargando,
                        monitoreoActivo = monitoreoActivo,
                        sensoresDisponibles = sensoresDisponibles,
                        alertasAbiertas = ajustesUiState.alertasAbiertas,
                        alertasPorEnviar = alertasPorEnviar,
                    ),
                    sosBloqueado = ajustesUiState.proteccionActiva == false
                )
            }

            composable("sos") {
                SosScreen(onCerrar = { navController.popBackStack() })
            }

            composable("medication") {
                MedicationScreen(
                    uiState = medicationUiState,
                    onOpenDetail = { medicationId -> navController.navigate("medication/detail/$medicationId") },
                    onOpenHistory = { navController.navigate("medication/history") },
                    onTaken = { occurrenceId ->
                        MedicationRepository.markTaken(context, occurrenceId)
                    },
                    onPostpone = { occurrenceId ->
                            MedicationRepository.postpone(context, occurrenceId)
                        },
                    onOmit = { occurrenceId ->
                        MedicationRepository.markOmitted(context, occurrenceId)
                    },
                    onRefrescar = {
                        scope.launch { MedicationRepository.refrescar(context) }
                    }
                )
            }

            composable("medication/detail/{medicationId}") { backStackEntry ->
                val medicationId = backStackEntry.arguments?.getString("medicationId").orEmpty()
                MedicationDetailScreen(
                    medicationId = medicationId,
                    uiState = medicationUiState,
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate("medication/history") },
                    onTaken = { occurrenceId ->
                        MedicationRepository.markTaken(context, occurrenceId)
                    },
                    onPostpone = { occurrenceId ->
                            MedicationRepository.postpone(context, occurrenceId)
                        },
                    onOmit = { occurrenceId ->
                        MedicationRepository.markOmitted(context, occurrenceId)
                    }
                )
            }

            composable("medication/history") {
                MedicationHistoryScreen(
                    uiState = medicationUiState,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("location") {
                LocationScreen()
            }

            composable("contacts") {
                ContactsScreen()
            }

            composable("settings") {
                SettingsScreen(
                    onLogout = {
                        scope.launch {
                            AlertasRepository.limpiar()
                            // Al borrar la sesión, el efecto de sesionActiva limpia
                            // los datos, cancela alarmas y lleva al login.
                            AuthRepository(sessionManager).logout()
                        }
                    },
                    onOpenTwoFactor = {
                        navController.navigate("settings/twofactor") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("settings/twofactor") {
                TwoFactorSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("invitaciones") {
                InvitacionesScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("fall") {
                AlertaCaidaScreen(onCerrar = { navController.popBackStack() })
            }
        }
    }
}

