# LLMia 2 v0.5.0 — Evidencia de aceptación multimedia
Fecha: 2026-11-27 (Perú). Proyecto UPN — LLMia La Exploradora.
Rama: feature/v0.5-multimedia-presentation. PR #6 (sin merge).
Código probado: a474cdd5485d6679b3a64163ae6a6342a0b687e9.

## Alcance
Ampliación posterior al cierre académico original. Presentación de creadores sin exigir conexión al robot cuando está desconectado e inactivo. Se conserva autenticación y STOP confirmado cuando está conectado. Firmware y diseño de conducción sin cambios.

## Entorno
Android Studio, emulator-5554, Windows, Gradle 8.13, paquete pe.edu.upn.llmia.v2, versión 0.5.0. Backend de pruebas https://llmia-v2-913797829604.us-central1.run.app, revisión llmia-v2-00001-p5f, Firestore llmia-v2, identidad llmia-v2-runtime. Firebase y bucket privado compartidos con permisos delimitados; token de dispositivo compartido. No se modificó llmia-demo.

## Evidencia de compilación e instalación
- git log -1: a474cdd fix(android): skip physical STOP request for disconnected idle presentation.
- gradle --no-daemon assembleDebug: BUILD SUCCESSFUL in 3m 5s; 39 tareas, 4 ejecutadas y 35 actualizadas.
- adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk: Performing Streamed Install / Success.
- CI integral anterior al ajuste: https://github.com/JuliusCordova/roboticaLLMIA/actions/runs/45937560795 (47 pruebas backend, Android unit/lint/build y contenedor exitosos). No se atribuye ese resultado al último commit.

## Prueba funcional
Entrada escrita: «muestra la foto del grupo».
Precondición: sesión de operador e Internet; robot desconectado.
Resultado: fotografía original compuesta visible, botón STOP disponible, mensaje de presentación y retorno a interfaz de conducción. El operador confirmó haber escuchado la narración. La recuperación de multimedia autenticada queda demostrada funcionalmente por la visualización; no se adjuntan trazas HTTP de esta sesión.

| Verificación | Estado |
|---|---|
| Comando escrito literal | PASS |
| Visualización de fotografía del equipo | PASS |
| Presentación sin robot conectado | PASS |
| Narración audible | PASS — confirmación del operador |
| Retorno a controles | PASS — evidencia visual |
| Duración exacta de espera de 3 segundos | Pendiente de medición |
| Pronunciación individual de los cinco nombres | No verificada individualmente |
| Intención semántica Gemini con frase no literal | Pendiente |
| Entrada por micrófono | Pendiente |
| STOP y conducción con robot físico | Pendiente |
| Cámara, sensor, servo y puente con Android físico | Pendiente |

## Evidencias fotográficas
Dos fotografías aportadas por el operador en la conversación, 27/11/2026 aproximadamente 09:14 según reloj visible:
- 12AF8CE0-B0FF-4EDE-830F-CACC5252D64B.jpeg: pantalla de presentación con composición original y STOP.
- 94B867A0-AE69-4B64-9416-F4C23F47AF7C.jpeg: interfaz de conducción restaurada, sin señal de cámara y mensaje de presentación.
Los archivos binarios aún no están incorporados al repositorio; este registro documenta su contenido observado.

## Próximos pasos
Probar frase «Me gustaría conocer a las personas que te construyeron» para activar Gemini, actualizar APK de entregables (el anterior no contiene el ajuste), calcular nuevo SHA256 y probar con Android físico de un integrante. El emulador usa red virtual y no se ha validado conexión directa a Carrito_CAM; se difiere la prueba física por decisión del operador. No se declara aceptación física ni despliegue productivo.

Referencia previa: ../2026-11-26/LLMIA2_V05_CHECKPOINT.md.
