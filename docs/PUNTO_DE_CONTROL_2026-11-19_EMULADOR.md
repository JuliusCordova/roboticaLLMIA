# Punto de control — Internet de prueba para Cloud Run en Android

19 de noviembre de 2026. Continúa el punto de control de Android v0.2.1, en la misma rama `fix/android-v021-control` y PR #5.

## Evidencia observada del operador

- La app abre en el emulador y muestra Conducir/Conversar/Ajustes.
- «gira a la izquierda» solicita conexión al robot; «no gires a la izquierda» se rechaza como orden desconocida.
- Firebase Android registrado para `pe.edu.upn.llmia`; la captura muestra SHA-1 agregado y la app muestra sesión de Julio Cesar Cordova Diaz. Esto acredita inicio de sesión, no autorización del UID en Cloud Run.
- La comprobación de nube anterior mostraba CellularOffline: no encontraba transporte celular.

## Cambio

Opción local `llmia.debug.allowDefaultCloudNetwork=true`, optativa y exclusiva de debug. Permite usar la red predeterminada validada hacia Cloud Run cuando no hay celular y excluye la red del robot. Release conserva celular. No altera rutas del robot, origen HTTPS, validación TLS ni permisos del backend.

«Comprobar Cloud Run» verifica salud y, si hay sesión iniciada, la autorización mediante la ruta de estado del robot. No requiere token de dispositivo ni crea una captura. La UI distingue «Internet de prueba» y «Operador autorizado» de una mera respuesta pública de salud.

Se añadieron seis pruebas de política de red y se corrigió la preparación de SDK en CI especificando platform-tools: el paquete legado tools ya no se encontraba en la ejecución anterior. El workflow comprueba además que debug puede activar el flag y release lo mantiene desactivado.

## Reanudar en Android Studio

1. Actualizar la misma rama o descargar nuevamente su ZIP. Conservar el archivo local.properties del equipo; no sobrescribir sdk.dir ni los valores Firebase al actualizar.
2. Añadir el flag, sincronizar Gradle y ejecutar debug.
3. En Ajustes verificar «Nube (Internet de prueba)», mantener sesión de Google y pulsar «Comprobar Cloud Run».
4. Registrar el resultado: salud, operador autorizado o error 401/403/red. No registrar tokens.

Pendiente: comprobación desde el emulador después de instalar este cambio, autorización efectiva del UID, cámara real hasta Gemini y pruebas físicas. No se desplegó un backend ni se modificó Firebase, IAM o el firmware.
