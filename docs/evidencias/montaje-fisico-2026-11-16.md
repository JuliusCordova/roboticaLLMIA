# Evidencia del montaje físico de LLMia — 16 de noviembre de 2026

**Origen:** dos fotografías compartidas por el usuario en la conversación del proyecto Robotica (vista superior/frontal y vista inferior).  
**Estado:** evidencia visual descriptiva; las imágenes originales no se adjuntan como binarios en este commit.

## Vista frontal y superior

Se observa un robot móvil sobre chasis perforado de MDF, con dos ruedas laterales, rueda loca de soporte, placa ESP32-CAM elevada y orientada hacia el frente, y controlador de motores de placa roja con disipador negro (apariencia compatible con L298N). Se observa cableado de distintos colores.

## Vista inferior

Se aprecian dos motorreductores amarillos tipo TT, dos ruedas motrices, soporte metálico de rueda loca, portaceldas para dos baterías cilíndricas, módulos convertidores DC-DC y un sensor ultrasónico de dos transductores en el borde del chasis.

## Conclusiones sustentadas y límites

- **Montaje físico observado:** estructura, ruedas, cámara, controlador, cableado y sensor están presentes.
- **No verificable a partir de fotos:** polaridad y tensión real de alimentación, conexiones exactas de GPIO, funcionamiento de motores y ultrasonido, autonomía o seguridad eléctrica.
- La cámara ya se había comprobado por separado a través de `http://192.168.4.1/capture`; este registro fotográfico no constituye una nueva prueba funcional.
- Revisar sujeción del cableado cerca de partes móviles, fijación de cámara y medición de salidas de los reguladores antes de ensayos motrices.
- Conservar el firmware instalado y continuar según [ADR-02 Android como puente](../ADR-02-android-bridge.md), sin órdenes remotas de motores en esta etapa.

## Archivos fotográficos

Se recibieron en conversación dos imágenes: **vista superior/frontal** y **vista inferior**. **Pendiente incorporar los JPEG originales en el repositorio**; evitar marcar esta evidencia como publicación de archivos gráficos hasta comprobar su presencia en GitHub.

## Estado de integración

Android Studio mostró la app LLMia Bridge en emulador; el acceso a Cloud Run produjo `UnknownHostException` (ver [evidencia de emulador](../evidencia-android-studio-2026-11-16.md)). El extremo a extremo ESP32-CAM → Android → Gemini todavía no ha sido validado.
