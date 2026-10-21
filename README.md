# RIBANI Android

Aplicación Jetpack Compose para el adulto mayor. Consume la API REST de
`ribani-server`; el familiar administra medicamentos desde `ribani-web`.

## Flujos conectados

- Medicamentos y tomas reales, sin datos de demostración. El estado se limpia
  al salir o cambiar de usuario.
- Registro de TOMADA/OMITIDA desde la pantalla o notificación, incluso con el
  proceso recién iniciado. La alarma se cierra después de confirmar el guardado.
- Posponer una vez durante diez minutos mediante `POST /tomas/:id/posponer`.
  Se conserva el horario y la ventana originales; `recordar_en` indica el aviso.
- Ajustes propios con `GET/PATCH /configuracion`, usando JWT. Interruptores de
  notificaciones y medicamentos persistidos, familiares vinculados, acceso a
  invitaciones, sonido del dispositivo y configuración 2FA.
- SOS y alertas de caída esperan la respuesta de la API y conservan errores para
  reintentar. El indicador de monitoreo refleja el servicio local.

Las alarmas verifican usuario, estado de la toma, medicamento y preferencias
antes de mostrar información; si no pueden verificar, muestran un aviso genérico.
Un aviso cuyo horario cambió se reprograma con la información consultada.

En Android 17 (SDK 37), la conexión al backend por una IP privada requiere
`ACCESS_LOCAL_NETWORK`. La app lo solicita antes de iniciar llamadas y permite
concederlo desde Ajustes si fue rechazado. Esto es adicional a `INTERNET`.
Para el equipo actual se conserva `http://192.168.100.63:3000/` en ApiClient.
Cambiar la constante requiere volver a compilar e instalar la app.

Con un dispositivo conectado y el servidor iniciado, se puede comprobar la
conexión desde el proceso de la app, sin iniciar sesión ni escribir en la BD:

```powershell
adb shell am instrument -w -e class com.ribani.app.BackendConnectivityTest -e backendConnectivity true com.ribani.app.test/androidx.test.runner.AndroidJUnitRunner
```

Esta comprobación se omite en las ejecuciones normales sin backend. Requiere
instalar previamente el APK de pruebas y aceptar el permiso de red local.

## Desarrollo y comprobaciones

Requiere Android SDK y Java compatibles con el Gradle existente. Revisar la URL
del backend en `data/remote/ApiClient.kt` según el dispositivo utilizado. No
guardar tokens, contraseñas ni credenciales de Meta en la app.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest --console=plain
```

Con un dispositivo disponible:

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

También se puede instalar ambos APK compilados y ejecutar directamente
`adb shell am instrument -w com.ribani.app.test/androidx.test.runner.AndroidJUnitRunner`.
Las pruebas de interfaz no utilizan cuentas reales ni generan alertas.

Ver [revisión de interfaz y límites de validación](docs/revision-ui-ux.md).
