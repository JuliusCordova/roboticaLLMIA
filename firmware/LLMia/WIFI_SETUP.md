# Conexion de la ESP32-CAM a Wi-Fi con Internet (fase 1)

El firmware conserva el punto de acceso `Carrito_CAM` y su servidor local. Si se incorpora un archivo local `wifi_credentials.h`, tambien se conecta a un router de 2.4 GHz mediante modo AP+STA.

1. Descarga o clona la rama `feature/esp32-wifi-sta` y abre `firmware/LLMia/LLMia.ino` en Arduino IDE.
2. En la **misma carpeta** del archivo .ino, crea `wifi_credentials.h` con este contenido (no publiques el archivo):

```cpp
#pragma once
#define LLMIA_WIFI_SSID "Wifi Spike"
#define LLMIA_WIFI_PASSWORD "ESCRIBE_AQUI_TU_CLAVE_REAL"
```

3. Mantén **desconectada la alimentación de los motores** durante esta fase. Conecta la ESP32-CAM por USB y programa con la configuracion de placa que ya funciona en tu equipo.
4. Abre el Monitor Serie a 115200. El programa debe mostrar una IP local del punto de acceso `Carrito_CAM` y otra IP asignada por el router (si pudo conectarse).
5. Con el celular o PC en la red del router, abre `http://<IP-del-robot>/capture`. Como prueba de respaldo, `http://192.168.4.1/capture` debe seguir funcionando estando conectado a `Carrito_CAM`.

**Importante:** esta fase solo agrega conectividad de red. El firmware **todavia no envia imagenes a Cloud Run**, no incorpora el token del dispositivo ni habilita controles remotos por Internet. No uses la cuenta publica de GitHub para almacenar claves o tokens. El modo AP+STA puede cambiar el canal de `Carrito_CAM` al canal utilizado por el router.
