# Revisión de interfaz Android

Se aplicó `.agents/skills/ui-ux-pro-max/SKILL.md` al código Jetpack Compose
existente. Se consultaron las guías de accesibilidad, errores y superficies
táctiles; se mantuvo la paleta y el tema claro del proyecto.

## Correcciones

- Navegación inferior con semántica de pestaña, selección anunciable, respuesta
  táctil y etiquetas que pueden ocupar dos líneas.
- Inicio, ajustes, ubicación, medicamentos y alertas admiten desplazamiento y
  alturas adaptables para pantallas pequeñas y texto ampliado.
- Acciones principales con área táctil amplia; confirmación de toma disponible
  sin el bloqueo artificial del antiguo contador de 60 segundos. El servidor
  conserva la validación de la ventana médica original.
- Errores con texto y acción para reintentar; operaciones que esperan guardado
  muestran ocupación y no comunican éxito antes de confirmarlo.
- Medicamentos vacíos, monitoreo, familiares y ubicación muestran el estado
  disponible, sin medicamentos ficticios ni ubicación por defecto presentada
  como posición actual.
- Contraste del botón de toma ajustado al verde principal. Iconos decorativos
  no repiten etiquetas al lector de pantalla.

## Verificación

En el emulador Pixel 7 con superficie de 375 dp se aprobaron cuatro pruebas
instrumentadas: tres de flujos y una de contexto. Comprueban confirmación de toma
con contador en cero, semántica de pestaña seleccionada y acceso al SOS con fuente
al 200 %. Una ejecución adicional con rotación horizontal aprobó los tres
flujos; la captura de Inicio confirma el acceso al SOS mediante desplazamiento.
Se inspeccionaron las capturas y se ajustaron el margen de la tarjeta y el icono
de monitoreo inactivo. Artefactos locales: `build/qa/`.

Se instalaron los APK compilados y se ejecutó directamente `adb am instrument`.
`connectedDebugAndroidTest --offline` no pudo resolver una dependencia UTP fuera
de caché. Un bloqueo inicial de System UI se resolvió antes de la ejecución
aprobada. No se utilizaron cuentas ni conexión con la base real.

Android Lint pasó sin errores y conserva 49 advertencias: versiones de
dependencias, comprobaciones de bibliotecas antiguas, recursos y recomendaciones
de mantenimiento. Esto no equivale a una auditoría manual completa de
accesibilidad. No se comprobó navegación real con
TalkBack, sensores físicos, entrega de alarmas bajo las restricciones de cada
fabricante ni recorridos completos con cuentas reales.

Referencias:

- [Accesibilidad de Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- [Soporte de diferentes tamaños de pantalla](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes).

## Corrección de conexión en Android 17

La validación inicial de UI no verificó la conexión desde el Motorola físico.
El dispositivo usa Android 17 y la app target SDK 37: faltaba declarar y solicitar
`ACCESS_LOCAL_NETWORK` para la IP privada del backend. Se conservó la URL
`http://192.168.100.63:3000/` y se agregó la solicitud antes de cargar datos.
Si se rechaza, la pantalla explica el acceso y permite solicitarlo o abrir Ajustes.

La prueba optativa `BackendConnectivityTest` reprodujo el timeout desde el proceso
de la app antes del cambio. Tras instalar la corrección y conceder el permiso en
el teléfono, el mismo GET `/api-json` recibió HTTP 200 en 0,138 segundos. La prueba
no inicia sesión ni escribe en la base de datos. Compilación y pruebas unitarias
aprobadas. Las pruebas de UI anteriores no bastaban para afirmar conectividad.

- [Permiso de red local en Android 17](https://developer.android.com/privacy-and-security/local-network-permission).
