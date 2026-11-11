# Pausa de arquitectura — LLMia UPN

**Fecha:** 2026-11-11 (America/Lima)  
**Estado:** EN PAUSA POR DECISIÓN DE ARQUITECTURA. No asumir una opción final.

## Resultados ya validados

- Firmware original ESP32-CAM AI Thinker crea la red local `Carrito_CAM`; el operador pudo visualizar fotos en `http://192.168.4.1/capture`.
- La cámara continúa capturando: Monitor Serie muestra `Foto guardada en galeria`; también aparecieron avisos `cam_hal: FB-OVF`, cuya causa no se ha determinado.
- Arduino IDE 2.3.10: paquete `esp32` de Espressif 3.3.12 y `ESP32Servo` 3.2.1 instalados; `AI Thinker ESP32-CAM` seleccionado. Sketch actualizado compiló (33% de flash y 18% de RAM global).
- Windows identificó el adaptador USB serial CH340 (VID_1A86 PID_7523); tras controlador correcto se vio COM7 y Monitor Serie recibió datos.
- La escritura del nuevo firmware **NO** se completó: `Failed to connect to ESP32: Wrong boot mode detected (0xb)`; requiere resolver modo download antes de flashear. No confundir compilación con programación.
- En GCP, Cloud Run `llmia-demo` con revisión registrada `llmia-demo-00003-ptg` respondió `{"status":"ok","mode":"gemini"}`; hubo descripción de una imagen **simulada** por Gemini. **No** se ha verificado una imagen física procesada por Gemini.
- La laptop Windows logró abrir tanto `Carrito_CAM` por Wi-Fi como el servicio de Cloud Run por Ethernet; conexión dual confirmada por el usuario.

## Alternativas bajo consideración

| Opción | Flujo | Ventaja | Pendiente/limitación |
| --- | --- | --- | --- |
| A: dispositivo directo | ESP32-CAM → Wi-Fi router → Cloud Run/Gemini | Operación sin intermediario | Requiere flashear firmware Wi-Fi STA + cliente HTTPS seguro; falla de modo boot pendiente |
| B: puente Windows temporal | ESP32-CAM AP `Carrito_CAM` → laptop Python Wi-Fi/Ethernet → Cloud Run/Gemini | Reutiliza firmware actual | Depende de laptop y de credencial del dispositivo; **no se ha ejecutado la prueba real del puente** |
| C: puente móvil | ESP32-CAM → dispositivo móvil con acceso simultáneo a LAN del robot e Internet → Cloud Run | Portabilidad | Requiere desarrollo y validación específica; usuario cuenta con iPhone, no Android |

## Código ya registrado en main

- `firmware/LLMia/LLMia.ino`: AP+STA opcional y corrección de puntero PSRAM, pero **no grabado** en el dispositivo.
- `firmware/LLMia/WIFI_SETUP.md`: configuración Wi-Fi. Las contraseñas no deben versionarse.
- `backend/llmia/camera_bridge.py`: cliente Windows para capturar `/capture` y reenviar a Cloud Run; disponible pero **aún no probado en la laptop**.
- `backend/tests/test_camera_bridge.py`: pruebas unitarias propuestas; ejecución local pendiente.
- `docs/puente-windows.md`: instrucciones y consideraciones de credenciales.
- `docs/progreso-integracion-2026-11-10.md`: evidencia de nube y simulador.
- PR #1 y #2 ya fusionados.

## Decisiones arquitectónicas pendientes (NO resueltas)

1. Selección de arquitectura destino: placa directa, laptop temporal o móvil como puente.
2. Alcance de la demo UPN: visión bajo demanda, voz, navegación, control desde móvil; ordenar por prioridad.
3. Estrategia de conexión y secretos por dispositivo (provisión/revocación, TLS, operación sin laptop).
4. Estrategia de robustez de cámara ante `cam_hal: FB-OVF` y límites de captura.
5. Pruebas de aceptación con imágenes reales, latencia, exactitud y costo.
6. Procedimiento seguro para poner ESP32-CAM en modo download, solo si se decide programarla.

## Regla para retomar

**No realizar más despliegues, flasheos ni cambios de arquitectura** hasta recibir la decisión del usuario. En el próximo encuentro, empezar revisando alternativas y objetivos; ejecutar luego una sola prueba por vez. Nunca copiar contraseñas de Wi-Fi ni tokens de Cloud Run al repositorio o al chat.

**Último punto exacto:** usuario confirmó que ambas URL abren desde la misma laptop; se preparó y fusionó el puente Python, se explicó su propósito, pero el usuario suspendió para evaluar la arquitectura antes de instalarlo o ejecutarlo.
