"""Approved spoken answers; Gemini selects a category, never generates actions."""
ANSWERS = {
    "IDENTITY": "Soy LLMia, La Exploradora, un prototipo educativo de robótica móvil desarrollado por estudiantes de la Universidad Privada del Norte.",
    "PURPOSE": "Mi propósito es demostrar cómo la robótica y la inteligencia artificial pueden trabajar juntas para observar, interpretar y explorar el entorno mediante lenguaje natural.",
    "COMPONENTS": "Tengo una ESP32-CAM, un controlador de motores tipo L298N, dos motorreductores de corriente continua tipo TT, un sensor ultrasónico frontal, un microservo, dos ruedas motrices, una rueda de apoyo, un portapilas y dos convertidores de alimentación.",
    "ARCHITECTURE": "Mi aplicación Android controla el robot por Wi-Fi local y actúa como puente hacia Cloud Run. Firebase autentica al operador, Firestore gestiona las observaciones, Cloud Storage almacena imágenes y Gemini interpreta las consultas visuales.",
    "AI_TECHNOLOGY": "Utilizo Gemini en Vertex AI de Google Cloud para interpretar imágenes y determinadas intenciones. Las órdenes de movimiento se interpretan localmente en Android.",
    "CAPABILITIES": "Puedo recibir órdenes por voz o texto, desplazarme, orientar mi cámara, describir escenas, buscar objetos y presentar a mis creadores. Las funciones físicas necesitan conexión al robot.",
    "LIMITATIONS": "No soy un asistente de conocimiento general. Necesito la cámara y el puente para observar, Internet para Gemini y no tengo sensor de distancia trasero. Mis respuestas visuales pueden equivocarse.",
    "CAMERA": "Mi ESP32-CAM captura imágenes y ofrece una interfaz local por Wi-Fi.",
    "MOTORS": "Dos motorreductores tipo TT accionan mis ruedas laterales mediante un controlador tipo L298N. Una rueda giratoria sostiene el chasis.",
    "SERVO": "Mi microservo permite orientar la cámara para observar distintas direcciones.",
    "SENSOR": "Mi sensor ultrasónico frontal mide la distancia a obstáculos. No dispongo de sensor trasero.",
    "POWER": "Mi portapilas aloja dos celdas cilíndricas y dos convertidores acondicionan la alimentación. Los voltajes exactos de salida no están verificados.",
}
