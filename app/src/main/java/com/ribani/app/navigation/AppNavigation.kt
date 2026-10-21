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
import com.ribani.app.screens.auth.RecuperarPasswordScreen
import com.ribani.app.screens.auth.TwoFactorLoginScreen
import com.ribani.app.screens.auth.RegisterScreen
import com.ribani.app.screens.contacts.ContactsScreen
import com.ribani.app.screens.fall.FallAlertScreen
import com.ribani.app.screens.home.HomeScreen
import com.ribani.app.screens.invitaciones.InvitacionesScreen
import com.ribani.app.screens.location.LocationScreen
import com.ribani.app.screens.medication.MedicationDetailScreen
import com.ribani.app.screens.medication.MedicationHistoryScreen
import com.ribani.app.screens.medication.MedicationScreen
import com.ribani.app.screens.settings.SettingsScreen
import com.ribani.app.screens.settings.TwoFactorSettingsScreen
import com.ribani.app.sensors.FallAlertEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val LOGIN_ROUTE = "login?cuentaCreada={cuentaCreada}"
const val REGISTER_ROUTE = "register"
// Se llega tras un login que respondió twoFactorRequired (2FA activado)
const val TWO_FACTOR_LOGIN_ROUTE = "login/twofactor/{challengeToken}"
// Recuperación de contraseña (código por correo)
const val RECUPERAR_PASSWORD_ROUTE = "login/recuperar"

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

    // Verificación de sesión guardada antes de decidir la pantalla inicial
    var sessionChecked by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessionManager = SessionManager(context)
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
                    // Sesión revocada: estado vacío (sin datos demo) y
                    // alarmas de la sesión anterior canceladas.
                    MedicationRepository.limpiarSesion(context)
                    // Resetea también el estado de Ajustes/protección.
                    AjustesRepository.limpiar()
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

    // Medicamentos: carga datos reales del backend al abrir la pestañas
    // (GET /tomas del dia + historial), como MedicaScreen de la web.
    // Ajustes: consulta /auth/me y /familiares/mis-familiares para saber si
    // el adulto tiene un FAMILIAR_ENCARGADO con vínculo ACTIVA (protección).
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            MedicationRepository.initialize(context)
            AjustesRepository.cargar(context)
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
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF006B3C))
        }
        return
    }

    val showBottomBar = currentRoute?.startsWith("login") != true &&
        currentRoute != REGISTER_ROUTE &&
        currentRoute != "fall"

    Scaffold(
        containerColor = Color.White,
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
                .background(Color.White)
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
                HomeScreen(
                    onSosClick = {
                        navController.navigate("fall") {
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
                    isMonitoring = ajustesUiState.proteccionActiva != false,
                    // null = sin aún respuesta del backend / offline:
                    // se muestra el estado por defecto (activado).
                    proteccionActiva = ajustesUiState.proteccionActiva
                )
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
                            AuthRepository(SessionManager(context)).logout()
                            // Limpia medicamentos e historial (incluido el
                            // estado local de aplazamientos) y cancela las
                            // alarmas de la sesión anterior.
                            MedicationRepository.limpiarSesion(context)
                            // Resetea el estado de Ajustes/protección.
                            AjustesRepository.limpiar()
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
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
                FallAlertScreen(
                    onOkay = {
                        navController.popBackStack()
                    },
                    onNeedHelp = {
                        // "¡NECESITO AYUDA!" desde la navegación interna:
                        // igual que en FallAlertActivity, escala la CAIDA
                        // abierta a EN_ATENCION y crea una SOS explícita
                        // (POST /alertas tipo SOS) para el panel del familiar.
                        // Es fire-and-forget: si el backend no responde, la
                        // alerta CAIDA sigue abierta y al familiar ya le llegó.
                        scope.launch {
                            AlertasRepository.escalarAlerta()
                            AlertasRepository.enviarAlerta(
                                context = context,
                                tipo = "SOS",
                                titulo = "El adulto mayor pide ayuda",
                                descripcion =
                                    "Presionó ¡NECESITO AYUDA! tras una alerta de posible caída.",
                            )
                        }
                        navController.popBackStack()
                    },
                    onTiempoAgotado = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

