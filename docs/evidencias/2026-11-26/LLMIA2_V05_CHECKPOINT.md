# LLMia 2 v0.5.0 — Punto de control 26/11/2026 (Perú)

## Alcance
Presentación multimedia de los cinco creadores mediante comandos de voz e interpretación Gemini, fotografía privada y narración, con retorno previsto a los controles tres segundos después de finalizar la narración. Implementado, pendiente de aceptación funcional. Firmware y aplicación original sin cambios. PR #6 en borrador; no merge ni promoción a producción.

## Base reproducible
- Rama: feature/v0.5-multimedia-presentation
- Commit compilado: dc7171521ac162b78c680b8d55c0dacdfff7cda0
- [GitHub Actions QA](https://github.com/JuliusCordova/roboticaLLMIA/actions/runs/45937560795): backend 47 pruebas OK, Android unit tests/lint/assembleDebug OK, Docker QA OK.

## Infraestructura de prueba
- Proyecto: proyectopersonal-480420; región us-central1.
- Cloud Run: https://llmia-v2-913797829604.us-central1.run.app ; revisión llmia-v2-00001-p5f.
- Cloud Build: 6c3fc13f-1e22-4d8d-86aa-9c03c9114d67.
- Imagen: us-central1-docker.pkg.dev/proyectopersonal-480420/llmia/web-v2@sha256:7d18d101202a5d6138253b2846f182ec54ff13de44544003fe197916be35b48b
- Firestore llmia-v2 independiente, protección contra eliminación habilitada.
- Identidad llmia-v2-runtime, permisos Firestore condicionados a la nueva base, roles personalizados Firebase y Vertex, acceso multimedia limitado y prefijo v2/observations/ separado.
- Bucket privado compartido; multimedia/equipo/grupo.jpg e integrantes.json cargados. Lectura autenticada efectiva aún pendiente.
- Firebase y secreto de dispositivo compartidos con el entorno anterior: aislamiento parcial de credenciales, no aislamiento total.
- HTTP: /health nuevo y original 200; status, team, team/image y POST presentation/intent sin token 401. Seis comprobaciones satisfactorias; no demuestran acceso autenticado.

## Evidencia local Android
Fuente: salidas PowerShell aportadas por el operador.
- Windows 11, Java Temurin 21.0.13.1, Gradle 8.13 desde caché local.
- Firebase Android registrado para pe.edu.upn.llmia.v2; SHA-1 y SHA-256 registrados. local.properties ignorado por Git, configuración real incorporada sin publicar claves.
- Bloqueo inicial Lint PropertyEscape en sdk.dir. Resuelto escapando separadores de Windows y ejecutando compilación limpia.
- Comando: gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug --rerun-tasks --no-build-cache
- Resultado: BUILD SUCCESSFUL in 2m 26s; 56 actionable tasks: 56 executed.
- Advertencias no bloqueantes: iconos KeyboardArrowLeft/Right obsoletos, override obsoleto en Voice.kt, biblioteca libandroidx.graphics.path.so empaquetada sin strip. Revisión completa de advertencias Lint pendiente.
- APK generado: android/app/build/outputs/apk/debug/app-debug.apk (26/11/2026 23:44:02 local).
- Nombre: LLMia 2; package: pe.edu.upn.llmia.v2; versionName 0.5.0; versionCode 6; minSdk 29; targetSdk 35.
- SHA-256 APK: 475DE2B39B790274267ABC35E49FAD90B4ECAE12B24EBE4415451B1542EDBB90
- apksigner: Verifies; v2 true; un firmante Android Debug RSA 2048.
- SHA-256 certificado: b05a00b60c52b35421e9c90a2e4cd9f69250e7e53369bb57e1c34a02ec33f293
- SHA-1 certificado: 5c74fc967d88da7afde393d28f54167f60084b3d
- Ambas huellas coinciden con las registradas en Firebase. Firma de depuración para prueba académica, no distribución comercial.
- El binario no se ha subido al repositorio. No se incluyen credenciales ni claves privadas.

## Reanudación: paso 38
1. Copiar APK a entregables/LLMia2-v0.5.0.apk y verificar SHA-256; copia aún no confirmada.
2. Revisar informe Lint completo.
3. Instalar junto a la app original en Android 10 o superior; comprobar autenticación Google.
4. Validar conexión y controles originales con ruedas levantadas inicialmente; usar solo una app para controlar el robot a la vez.
5. Probar «Muestra la foto del grupo», narración de cinco integrantes y retorno tres segundos después de finalizar la voz.
6. Probar variante semántica Gemini, STOP, cancelación y pérdida de conectividad.
7. Registrar evidencias autenticadas de Storage, Gemini y operación del robot; distribuir APK a compañeros tras aceptación.

Estado: compilación y firma verificadas; aceptación funcional y distribución pendientes.
