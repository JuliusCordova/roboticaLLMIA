# LLMia Home — proyecto futuro
Fecha: 2026-11-27. Estado: propuesta, no implementada. No modifica el alcance académico de robótica.

## Visión
Seguridad doméstica agéntica accesible para hogares de clase media en Perú y Latinoamérica, aprovechando cámaras existentes. ML local para filtrar eventos, Gemini para interpretación contextual y Alexa/app para consultas y alertas. No sustituye servicios profesionales de seguridad.

## Inventario inicial
Dos cámaras Nexxt Home (exterior PTZ e interior fija), tres dispositivos Alexa (al menos dos Echo Show), integración Alexa–Nexxt operativa según el usuario, almacenamiento contratado únicamente para la exterior, robot ESP32-CAM y backend LLMia en GCP. Modelos exactos, RTSP/ONVIF, acceso API y retención cloud pendientes de confirmar. No publicar identificadores, IP, MAC ni imágenes privadas.

## Arquitectura propuesta
Cámaras → gateway local Python/OpenCV → detección YOLO y seguimiento ByteTrack → filtro de eventos → Pub/Sub/Cloud Run → Vertex AI Gemini Flash-Lite/Flash mediante model routing → Firestore (memoria), Cloud Storage (evidencias selectivas) → app móvil y Alexa Skill propia. Isolation Forest para anomalías en fase posterior. Robot opcional. SaaS multitenant con identidad, aislamiento, auditoría, cifrado, consentimiento, retención configurable y límites de consumo. Sin reconocimiento facial en MVP. Evaluar licencias comerciales de modelos y APIs.

## PoC (4 semanas)
1. Validar acceso autorizado a cámaras y captura local; si no hay API/RTSP/ONVIF, evaluar exportación autorizada de clips o cámara compatible.
2. ML local, detección y seguimiento; medir precisión y falsos positivos.
3. Gemini, memoria de eventos, alertas y medición de tokens/costo por evento.
4. Alexa Skill, dashboard y evaluación técnica/económica.

Presupuesto GCP PoC: USD 15–20/mes; sin compra inicial de hardware, usando laptop. Escenario ilustrativo de 100 eventos/día, 3 imágenes/evento, 560 tokens/imagen, 500 de contexto y 300 de salida: 6,54 M entrada y 0,9 M salida/mes. A tarifas referenciales Flash de USD 0,30 y 2,50 por millón, USD 4,21 de inferencia + USD 3 de infraestructura = USD 7,21/mes. Validar tarifas vigentes, disponibilidad de modelos, tokens reales, reintentos y costos compartidos. No incluye suscripción Nexxt, electricidad, impuestos ni desarrollo.

## Evolución comercial
- MVP: 6–8 semanas; app, onboarding, identidad, aislamiento y cuotas.
- Piloto: 10–20 hogares, 4–6 semanas; instalación, compatibilidad, disposición de pago.
- Producto: 8–12 semanas; facturación, soporte, seguridad y operación.
- Hipótesis de precios mensuales: Basic S/19,90 (2 cámaras); Plus S/39,90 (4); Family S/69,90 (8). Gateway opcional por separado; validar costo total para el hogar y demanda.
- Objetivo de costo variable GCP <= USD 2/hogar/mes a escala, no demostrado. Medir margen incluyendo IGV, soporte, pagos, adquisición, hardware y operación.

## Criterios de éxito propuestos
Precisión de detección de personas >=90%, reducción de falsas alertas >=70% frente a línea base, reducción de llamadas Gemini >=95%, latencia de alertas <=10 s, disponibilidad MVP >=99,5%. Medir recall, condiciones nocturnas y costo por evento; metas no alcanzadas aún.

## Próximos entregables
Product Vision, Lean Canvas, Charter, SDD y plan de pruebas; reutilizar patrones de https://github.com/JuliusCordova/DevPattern y componentes de roboticaLLMIA. Evaluar repositorio independiente LLMia-Home al iniciar MVP. Cumplimiento de protección de datos personales en Perú y revisión de licencias antes de comercializar.
