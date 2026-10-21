package com.ribani.app.screens.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ribani.app.data.local.SessionManager
import com.ribani.app.data.repository.UbicacionesRepository
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LOCATION_UPDATE_INTERVAL_MS = 5_000L

private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

private fun formatTimestamp(timeMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.getDefault())
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

@Composable
fun LocationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationManager = remember(context) {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var statusMessage by remember {
        mutableStateOf("Activa la ubicación para ver tu posición en tiempo real.")
    }
    var lastUpdateMessage by remember { mutableStateOf("Aún no hay una ubicación recibida.") }

    val defaultMapLocation = LatLng(19.4326, -99.1332)
    val mapLocation = currentLocation?.let { LatLng(it.latitude, it.longitude) } ?: defaultMapLocation
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapLocation, 15f)
    }

    LaunchedEffect(mapLocation) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(mapLocation, 15f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        statusMessage = if (granted) {
            "Ubicación activada. Escuchando cambios en tiempo real..."
        } else {
            "Necesitamos permiso de ubicación para mostrar tu posición."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = location
                    statusMessage = "Actualizando ubicación en tiempo real..."
                    lastUpdateMessage = "Última actualización: ${formatTimestamp(location.time)}"

                    // Acumula en la cola del repositorio; el flush por lote
                    // (POST /ubicaciones/lote, cada 30 s) hace el envío.
                    UbicacionesRepository.inicializar(context)
                    scope.launch { UbicacionesRepository.encolar(location) }
                }

                override fun onProviderEnabled(provider: String) {
                    if (currentLocation == null) {
                        statusMessage = "Buscando tu ubicación..."
                    }
                }

                override fun onProviderDisabled(provider: String) {
                    statusMessage = "El proveedor de ubicación está desactivado. Activa GPS o red."
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    // Sin uso en esta implementación.
                }
            }

            try {
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER
                ).filter { provider ->
                    runCatching {
                        locationManager.isProviderEnabled(provider)
                    }.getOrDefault(false)
                }

                if (providers.isEmpty()) {
                    statusMessage = "No hay proveedores de ubicación disponibles. Activa GPS o red."
                } else {
                    providers.forEach { provider ->
                        runCatching {
                            locationManager.getLastKnownLocation(provider)?.let { location ->
                                currentLocation = location
                                lastUpdateMessage = "Última actualización: ${formatTimestamp(location.time)}"
                                statusMessage = "Ubicación lista. Los valores se están actualizando."
                            }
                            locationManager.requestLocationUpdates(
                                provider,
                                LOCATION_UPDATE_INTERVAL_MS,
                                0f,
                                locationListener,
                                Looper.getMainLooper()
                            )
                        }
                    }
                }
            } catch (_: SecurityException) {
                hasPermission = false
                statusMessage = "No se pudo acceder a la ubicación. Vuelve a conceder el permiso."
            }

            onDispose {
                runCatching { locationManager.removeUpdates(locationListener) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = Color(0xFF1F2937)
                )
                Text(
                    text = "Mi ubicación",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1F2937)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFFEAF2FF))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        // La capa "mi ubicación" de Maps SDK lanza
                        // SecurityException si se activa sin permiso
                        // concedido: solo se activa con hasPermission.
                        // (Causa del cierre tras reinstalar: el mapa se
                        // renderizaba con isMyLocationEnabled = true
                        // mientras el diálogo de permiso seguía visible.)
                        isMyLocationEnabled = hasPermission,
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = false,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = false
                    )
                ) {
                    Marker(
                        state = remember(mapLocation) {
                            MarkerState(position = mapLocation)
                        },
                        title = "Tu ubicación"
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .background(
                            color = Color(0xFF34A853).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "En vivo",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF5F7FA),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
            ) {
                Text(
                    text = statusMessage,
                    fontSize = 14.sp,
                    color = Color(0xFF374151),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (hasPermission) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Latitud",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = currentLocation?.let { String.format(Locale.getDefault(), "%.6f", it.latitude) } ?: "--",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Longitud",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = currentLocation?.let { String.format(Locale.getDefault(), "%.6f", it.longitude) } ?: "--",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = lastUpdateMessage,
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            currentLocation = null
                            statusMessage = "Buscando una nueva ubicación..."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar ubicación")
                    }
                } else {
                    Text(
                        text = "Necesitas conceder permiso para ver tu ubicación en tiempo real.",
                        fontSize = 15.sp,
                        color = Color(0xFF4B5563),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Conceder permiso")
                    }
                }
            }
        }
    }
}
