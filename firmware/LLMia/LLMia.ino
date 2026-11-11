#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>
#include <ESP32Servo.h>

// Archivo local opcional, nunca subir claves a GitHub.
#if __has_include("wifi_credentials.h")
#include "wifi_credentials.h"
#endif

// ======================================
//              WIFI
// ======================================

const char* ssid = "Carrito_CAM";
const char* password = "REEMPLAZAR_CLAVE_WIFI";

WebServer server(80);
bool camaraOK = false;

// ======================================
//       GALERIA: FOTOS EN PSRAM
// ======================================
// Se conservan las 6 fotos mas recientes en la memoria PSRAM.
// No requiere microSD. Al reiniciar el ESP32-CAM se borran.
const int MAX_FOTOS = 6;
struct FotoGuardada {
  uint8_t *datos;
  size_t tamano;
  bool disponible;
  unsigned long numero;
};
FotoGuardada fotos[MAX_FOTOS] = {};
int siguienteFoto = 0;
unsigned long contadorFotos = 0;
unsigned long ultimaFotoAutomatica = 0;
const unsigned long INTERVALO_FOTOS = 10000;

// ======================================
//          CAMARA AI THINKER
// ======================================

#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM   -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5

#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// ======================================
//              L298N
// ======================================

#define IN1 12
#define IN2 13
#define IN3 14
#define IN4 15

// ======================================
//             ULTRASONICO
// ======================================

#define TRIG_PIN 2
#define ECHO_PIN 3

#define DISTANCIA_SEGURIDAD 20

// ======================================
//                 SERVO
// ======================================

#define SERVO_PIN 4

Servo servoCamara;

int posicionServo = 90;
int objetivoServo = 90;

unsigned long tiempoServo = 0;

// Velocidad intermedia
const unsigned long INTERVALO_SERVO = 15;

// Barrido automatico
bool barridoAutomatico = true;

int pasoBarrido = 0;

const int posicionesBarrido[] = {
  180,  // Derecha
  90,   // Centro
  0,    // Izquierda
  90    // Centro
};

// ======================================
//              SEGURIDAD
// ======================================

unsigned long ultimoComando = 0;

const unsigned long TIEMPO_SEGURIDAD = 700;

// ======================================
//              PAGINA WEB
// ======================================

const char pagina[] PROGMEM = R"rawliteral(

<!DOCTYPE html>
<html lang="es">

<head>

<meta charset="UTF-8">

<meta name="viewport"
content="width=device-width, initial-scale=1">

<title>Carrito Robot</title>

<style>

*{box-sizing:border-box}

body{
background:#101827;
color:white;
font-family:Arial;
text-align:center;
padding:12px;
margin:0;
}

h2{color:#38bdf8}

.panel{
background:#1e293b;
padding:14px;
border-radius:15px;
margin:12px auto;
max-width:500px;
}

#video{
width:100%;
border-radius:10px;
background:black;
min-height:150px;
}

.estado{
color:#4ade80;
font-size:13px;
}

.controles{
display:grid;
grid-template-columns:repeat(3,1fr);
gap:10px;
max-width:280px;
margin:auto;
}

button{
height:70px;
border:0;
border-radius:12px;
background:#2563eb;
color:white;
font-size:27px;
font-weight:bold;
touch-action:none;
user-select:none;
}

button:active{
background:#38bdf8;
}

.vacio{
visibility:hidden;
}

.stop{
background:#dc2626;
}

.distancia{
font-size:35px;
color:#4ade80;
font-weight:bold;
}

input[type=range]{
width:90%;
accent-color:#38bdf8;
}

.servo-btn{
height:48px;
width:85px;
font-size:15px;
background:#475569;
margin:5px;
}

.auto-btn{
height:48px;
width:90%;
font-size:15px;
background:#0891b2;
margin-top:10px;
}

</style>

</head>

<body>

<h2>🚗 CARRITO ROBOT</h2>

<div class="panel">
  <button class="auto-btn" onclick="mostrarPrincipal()">🎮 CONTROL / TRANSMISIÓN</button>
  <button class="auto-btn" onclick="mostrarGaleria()">🖼️ GALERÍA DE FOTOS</button>
</div>

<div id="vistaPrincipal">

<!-- CAMARA -->

<div class="panel">

<h3>📷 CAMARA EN VIVO</h3>

<img id="video" src="/capture">

<p id="estado" class="estado">
Conectando camara...
</p>

</div>

<!-- MOVIMIENTO -->

<div class="panel">

<h3>🎮 CONTROL DE MOVIMIENTO</h3>

<div class="controles">

<div class="vacio"></div>

<button
onpointerdown="iniciar('adelante',event)"
onpointerup="detener(event)"
onpointerleave="detener(event)"
onpointercancel="detener(event)">⬆️</button>

<div class="vacio"></div>

<button
onpointerdown="iniciar('izquierda',event)"
onpointerup="detener(event)"
onpointerleave="detener(event)"
onpointercancel="detener(event)">⬅️</button>

<button class="stop" onclick="parada()">■</button>

<button
onpointerdown="iniciar('derecha',event)"
onpointerup="detener(event)"
onpointerleave="detener(event)"
onpointercancel="detener(event)">➡️</button>

<div class="vacio"></div>

<button
onpointerdown="iniciar('atras',event)"
onpointerup="detener(event)"
onpointerleave="detener(event)"
onpointercancel="detener(event)">⬇️</button>

<div class="vacio"></div>

</div>

<p class="estado">
Mantener presionado para moverse.
</p>

</div>

<!-- ULTRASONICO -->

<div class="panel">

<h3>📡 SENSOR ULTRASONICO</h3>

<p>Distancia detectada</p>

<div class="distancia">
<span id="distancia">--</span> cm
</div>

<p id="obstaculo" class="estado">
Midiendo...
</p>

</div>

<!-- SERVO -->

<div class="panel">

<h3>🎥 ORIENTACION DE CAMARA</h3>

<p>Posicion: <span id="valorServo">90</span>°</p>

<button class="servo-btn"
onclick="moverServo(180)">Derecha</button>

<button class="servo-btn"
onclick="moverServo(90)">Centro</button>

<button class="servo-btn"
onclick="moverServo(0)">Izquierda</button>

<br>

<button class="auto-btn" id="auto"
onclick="alternarBarrido()">
DETENER BARRIDO
</button>

</div>
</div> <!-- fin vistaPrincipal -->

<div id="vistaGaleria" style="display:none">
  <div class="panel">
    <h3>🖼️ GALERÍA DE FOTOS</h3>
    <p class="estado">La cámara guarda una foto automáticamente cada 10 segundos.</p>
    <button class="auto-btn" onclick="cargarGaleria()">🔄 ACTUALIZAR FOTOS</button>
    <div id="fotos" style="display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-top:15px"></div>
    <p id="sinFotos" class="estado">Todavía no hay fotografías guardadas.</p>
    <button class="auto-btn" onclick="mostrarPrincipal()">⬅️ REGRESAR A TRANSMISIÓN Y CONTROL</button>
  </div>
</div>

<script>

function mostrarPrincipal(){
  document.getElementById('vistaGaleria').style.display='none';
  document.getElementById('vistaPrincipal').style.display='block';
  actualizarCamara();
}
function mostrarGaleria(){
  document.getElementById('vistaPrincipal').style.display='none';
  document.getElementById('vistaGaleria').style.display='block';
  cargarGaleria();
}
function cargarGaleria(){
  fetch('/photos?t='+Date.now()).then(r=>r.json()).then(lista=>{
    const cont=document.getElementById('fotos');
    cont.innerHTML='';
    document.getElementById('sinFotos').style.display=lista.length?'none':'block';
    lista.forEach(id=>{
      const tarjeta=document.createElement('div');
      tarjeta.style.background='#0f172a';
      tarjeta.style.padding='6px';
      tarjeta.style.borderRadius='10px';
      const img=document.createElement('img');
      img.src='/photo?id='+id+'&t='+Date.now();
      img.style.width='100%'; img.style.borderRadius='8px';
      const texto=document.createElement('p');
      texto.textContent='Fotografía guardada'; texto.style.fontSize='12px';
      tarjeta.appendChild(img); tarjeta.appendChild(texto); cont.appendChild(tarjeta);
    });
  }).catch(()=>{});
}

// ======================================
//              CAMARA
// ======================================

const video=document.getElementById("video");

function actualizarCamara(){
video.src="/capture?t="+Date.now();
}

video.onload=()=>{
document.getElementById("estado").innerText=
"CAMARA TRANSMITIENDO";

setTimeout(actualizarCamara,120);
};

video.onerror=()=>{
document.getElementById("estado").innerText=
"ERROR DE CAMARA";

setTimeout(actualizarCamara,1000);
};

// ======================================
//             MOTORES
// ======================================

let movimiento=null;
let repeticion=null;

function enviar(dir){

fetch("/move?dir="+dir)
.then(r=>r.text())
.then(t=>{

if(t==="OBSTACULO"){
document.getElementById("obstaculo").innerText=
"⚠️ OBSTACULO: AVANCE BLOQUEADO";
}

})
.catch(()=>{});

}

function iniciar(dir,event){

event.preventDefault();

try{
event.currentTarget.setPointerCapture(event.pointerId);
}catch(e){}

clearInterval(repeticion);

movimiento=dir;

enviar(dir);

repeticion=setInterval(()=>{

if(movimiento!==null){
enviar(movimiento);
}

},250);

}

function detener(event){

if(event)event.preventDefault();

if(movimiento===null)return;

movimiento=null;

clearInterval(repeticion);

enviar("stop");

}

function parada(){

movimiento=null;

clearInterval(repeticion);

enviar("stop");

}

window.addEventListener("blur",parada);

document.addEventListener("visibilitychange",()=>{

if(document.hidden)parada();

});

// ======================================
//             DISTANCIA
// ======================================

function actualizarDistancia(){

fetch("/distance?t="+Date.now())

.then(r=>r.json())

.then(data=>{

let d=data.distancia;

document.getElementById("distancia").innerText=
d<0?"--":d;

if(d>=0 && d<=20){

document.getElementById("obstaculo").innerText=
"⚠️ OBSTACULO DETECTADO - AVANCE BLOQUEADO";

}else if(d>20){

document.getElementById("obstaculo").innerText=
"CAMINO LIBRE";

}else{

document.getElementById("obstaculo").innerText=
"SIN LECTURA";

}

})

.catch(()=>{});

}

setInterval(actualizarDistancia,500);

// ======================================
//                SERVO
// ======================================

function moverServo(a){

fetch("/servo?angulo="+a)
.catch(()=>{});

document.getElementById("valorServo").innerText=a;

}

function alternarBarrido(){

fetch("/sweep")
.then(r=>r.json())
.then(data=>{

let boton=document.getElementById("auto");

if(data.activo){

boton.innerText="DETENER BARRIDO";

}else{

boton.innerText="ACTIVAR BARRIDO";

}

})
.catch(()=>{});

}

</script>

</body>
</html>

)rawliteral";

// ======================================
//             INICIAR CAMARA
// ======================================

bool iniciarCamara(){

camera_config_t config;

config.ledc_channel=LEDC_CHANNEL_0;
config.ledc_timer=LEDC_TIMER_0;

config.pin_d0=Y2_GPIO_NUM;
config.pin_d1=Y3_GPIO_NUM;
config.pin_d2=Y4_GPIO_NUM;
config.pin_d3=Y5_GPIO_NUM;
config.pin_d4=Y6_GPIO_NUM;
config.pin_d5=Y7_GPIO_NUM;
config.pin_d6=Y8_GPIO_NUM;
config.pin_d7=Y9_GPIO_NUM;

config.pin_xclk=XCLK_GPIO_NUM;
config.pin_pclk=PCLK_GPIO_NUM;
config.pin_vsync=VSYNC_GPIO_NUM;
config.pin_href=HREF_GPIO_NUM;

config.pin_sccb_sda=SIOD_GPIO_NUM;
config.pin_sccb_scl=SIOC_GPIO_NUM;

config.pin_pwdn=PWDN_GPIO_NUM;
config.pin_reset=RESET_GPIO_NUM;

config.xclk_freq_hz=20000000;
config.pixel_format=PIXFORMAT_JPEG;

if(psramFound()){

config.frame_size=FRAMESIZE_VGA;
config.jpeg_quality=12;
config.fb_count=2;
config.fb_location=CAMERA_FB_IN_PSRAM;

}else{

config.frame_size=FRAMESIZE_QVGA;
config.jpeg_quality=15;
config.fb_count=1;
config.fb_location=CAMERA_FB_IN_DRAM;

}

esp_err_t err=esp_camera_init(&config);

if(err!=ESP_OK){

Serial.printf("ERROR CAMARA: 0x%x\n",err);

return false;

}

sensor_t *s=esp_camera_sensor_get();

if(s){

if(psramFound()){
s->set_framesize(s,FRAMESIZE_VGA);
s->set_quality(s,12);
}else{
s->set_framesize(s,FRAMESIZE_QVGA);
s->set_quality(s,15);
}

}

Serial.println("CAMARA INICIADA");

return true;

}

// ======================================
//             MOTORES
// ======================================

void detenerMotores(){

digitalWrite(IN1,LOW);
digitalWrite(IN2,LOW);
digitalWrite(IN3,LOW);
digitalWrite(IN4,LOW);

}

void moverMotores(String dir){

if(dir=="adelante"){

digitalWrite(IN1,HIGH);
digitalWrite(IN2,LOW);

digitalWrite(IN3,HIGH);
digitalWrite(IN4,LOW);

}

else if(dir=="atras"){

digitalWrite(IN1,LOW);
digitalWrite(IN2,HIGH);

digitalWrite(IN3,LOW);
digitalWrite(IN4,HIGH);

}

else if(dir=="izquierda"){

digitalWrite(IN1,LOW);
digitalWrite(IN2,HIGH);

digitalWrite(IN3,HIGH);
digitalWrite(IN4,LOW);

}

else if(dir=="derecha"){

digitalWrite(IN1,HIGH);
digitalWrite(IN2,LOW);

digitalWrite(IN3,LOW);
digitalWrite(IN4,HIGH);

}

else{

detenerMotores();

}

}

// ======================================
//           ULTRASONICO
// ======================================

long medirDistancia(){

digitalWrite(TRIG_PIN,LOW);
delayMicroseconds(3);

digitalWrite(TRIG_PIN,HIGH);
delayMicroseconds(10);

digitalWrite(TRIG_PIN,LOW);

long duracion=pulseIn(ECHO_PIN,HIGH,25000);

if(duracion==0)return -1;

return duracion*0.0343/2;

}

// ======================================
//           MOVIMIENTO SUAVE SERVO
// ======================================

void actualizarServo(){

if(posicionServo==objetivoServo)return;

if(millis()-tiempoServo<INTERVALO_SERVO)return;

tiempoServo=millis();

if(posicionServo<objetivoServo){

posicionServo++;

}else if(posicionServo>objetivoServo){

posicionServo--;

}

servoCamara.write(posicionServo);

}

// ======================================
//           BARRIDO AUTOMATICO
// ======================================

void iniciarBarrido(){

barridoAutomatico=true;

pasoBarrido=0;

objetivoServo=posicionesBarrido[pasoBarrido];

}

void actualizarBarrido(){

if(!barridoAutomatico)return;

if(posicionServo==objetivoServo){

pasoBarrido++;

if(pasoBarrido>=4){
pasoBarrido=0;
}

objetivoServo=posicionesBarrido[pasoBarrido];

}

}

// ======================================
//             CAPTURA CAMARA
// ======================================

void capturarImagen(){

if(!camaraOK){

server.send(503,"text/plain","Camara no disponible");

return;

}

camera_fb_t *fb=esp_camera_fb_get();

if(!fb){

server.send(500,"text/plain","Error de imagen");

return;

}

WiFiClient cliente=server.client();

cliente.print(
"HTTP/1.1 200 OK\r\n"
"Content-Type: image/jpeg\r\n"
"Cache-Control: no-store\r\n"
"Connection: close\r\n"
);

cliente.printf(
"Content-Length: %u\r\n\r\n",
(unsigned int)fb->len
);

cliente.write(fb->buf,fb->len);

esp_camera_fb_return(fb);

cliente.stop();

}

// ======================================
//       GUARDAR FOTOS CADA 10 SEGUNDOS
// ======================================
void tomarFotoAutomatica(){
  if(!camaraOK) return;
  if(millis()-ultimaFotoAutomatica < INTERVALO_FOTOS) return;
  ultimaFotoAutomatica = millis();

  camera_fb_t *fb = esp_camera_fb_get();
  if(!fb){
    Serial.println("No se pudo capturar fotografia");
    return;
  }

  uint8_t *copia = (uint8_t *)ps_malloc(fb->len);
  if(copia){
    memcpy(copia, fb->buf, fb->len);
    if(fotos[siguienteFoto].datos) free(fotos[siguienteFoto].datos);
    fotos[siguienteFoto].datos = copia;
    fotos[siguienteFoto].tamano = fb->len;
    fotos[siguienteFoto].disponible = true;
    fotos[siguienteFoto].numero = ++contadorFotos;
    siguienteFoto = (siguienteFoto + 1) % MAX_FOTOS;
    Serial.printf("Foto guardada en galeria. Total tomadas: %lu\n", contadorFotos);
  }else{
    Serial.println("PSRAM insuficiente para guardar la foto");
  }
  esp_camera_fb_return(fb);
}

void enviarListaFotos(){
  String json = "[";
  bool primero = true;
  for(int i=0;i<MAX_FOTOS;i++){
    if(fotos[i].disponible){
      if(!primero) json += ",";
      json += String(i);
      primero = false;
    }
  }
  json += "]";
  server.send(200,"application/json",json);
}

void enviarFoto(){
  if(!server.hasArg("id")){
    server.send(400,"text/plain","Falta id");
    return;
  }
  int id = server.arg("id").toInt();
  if(id<0 || id>=MAX_FOTOS || !fotos[id].disponible || !fotos[id].datos){
    server.send(404,"text/plain","Foto no encontrada");
    return;
  }
  WiFiClient cliente = server.client();
  cliente.print("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nCache-Control: no-store\r\nConnection: close\r\n");
  cliente.printf("Content-Length: %u\r\n\r\n",(unsigned int)fotos[id].tamano);
  cliente.write(fotos[id].datos,fotos[id].tamano);
  cliente.stop();
}

// ======================================
//                 SETUP
// ======================================

void setup(){

Serial.begin(115200);

delay(1500);

Serial.println("INICIANDO CARRITO ROBOT");

// MOTORES

pinMode(IN1,OUTPUT);
pinMode(IN2,OUTPUT);
pinMode(IN3,OUTPUT);
pinMode(IN4,OUTPUT);

detenerMotores();

// ULTRASONICO

pinMode(TRIG_PIN,OUTPUT);
pinMode(ECHO_PIN,INPUT);

digitalWrite(TRIG_PIN,LOW);

// SERVO

servoCamara.setPeriodHertz(50);

servoCamara.attach(SERVO_PIN,500,2400);

servoCamara.write(90);

posicionServo=90;

objetivoServo=90;

// CAMARA

camaraOK=iniciarCamara();
if(!psramFound()){
  Serial.println("ADVERTENCIA: no se detecto PSRAM; la galeria no podra guardar fotos.");
}

// WIFI

// Mantener el punto de acceso local incluso si el router no esta disponible.
#if defined(LLMIA_WIFI_SSID) && defined(LLMIA_WIFI_PASSWORD)
WiFi.mode(WIFI_AP_STA);
#else
WiFi.mode(WIFI_AP);
#endif
WiFi.setSleep(false);
WiFi.softAP(ssid,password);
Serial.print("IP local Carrito_CAM: ");
Serial.println(WiFi.softAPIP());

#if defined(LLMIA_WIFI_SSID) && defined(LLMIA_WIFI_PASSWORD)
Serial.print("Conectando a Wi-Fi del router: ");
Serial.println(LLMIA_WIFI_SSID);
WiFi.begin(LLMIA_WIFI_SSID, LLMIA_WIFI_PASSWORD);
const unsigned long inicioConexion = millis();
while (WiFi.status() != WL_CONNECTED && millis() - inicioConexion < 12000) {
  delay(250);
}
if (WiFi.status() == WL_CONNECTED) {
  Serial.print("IP del robot en el router: ");
  Serial.println(WiFi.localIP());
} else {
  Serial.println("Router no disponible. Se mantiene Carrito_CAM.");
}
#endif

// PAGINA PRINCIPAL

server.on("/",HTTP_GET,[](){

server.send_P(200,"text/html",pagina);

});

// CAMARA

server.on("/capture",HTTP_GET,capturarImagen);

// MOVIMIENTO

server.on("/move",HTTP_GET,[](){

if(!server.hasArg("dir")){

server.send(400,"text/plain","Falta direccion");

return;

}

String dir=server.arg("dir");

if(dir=="stop"){

detenerMotores();

ultimoComando=millis();

server.send(200,"text/plain","STOP");

return;

}

// PROTECCION DE AVANCE A 20 CM

if(dir=="adelante"){

long distancia=medirDistancia();

if(distancia<0 || distancia<=DISTANCIA_SEGURIDAD){

detenerMotores();

ultimoComando=millis();

server.send(200,"text/plain","OBSTACULO");

return;

}

}

// ATRAS Y GIROS PERMITIDOS

if(
dir=="adelante" ||
dir=="atras" ||
dir=="izquierda" ||
dir=="derecha"
){

moverMotores(dir);

ultimoComando=millis();

server.send(200,"text/plain","OK");

}else{

detenerMotores();

server.send(400,"text/plain","Comando invalido");

}

});

// DISTANCIA

server.on("/distance",HTTP_GET,[](){

long d=medirDistancia();

String respuesta="{\"distancia\":"+String(d)+"}";

server.send(200,"application/json",respuesta);

});

// CONTROL MANUAL SERVO

server.on("/servo",HTTP_GET,[](){

if(!server.hasArg("angulo")){

server.send(400,"text/plain","Falta angulo");

return;

}

int angulo=server.arg("angulo").toInt();

angulo=constrain(angulo,0,180);

barridoAutomatico=false;

objetivoServo=angulo;

server.send(200,"text/plain","SERVO OK");

});

// ACTIVAR / DESACTIVAR BARRIDO

server.on("/sweep",HTTP_GET,[](){

barridoAutomatico=!barridoAutomatico;

if(barridoAutomatico){

iniciarBarrido();

}else{

objetivoServo=posicionServo;

}

String respuesta="{\"activo\":"+
String(barridoAutomatico?"true":"false")+"}";

server.send(200,"application/json",respuesta);

});

server.on("/photos",HTTP_GET,[](){
  enviarListaFotos();
});

server.on("/photo",HTTP_GET,[](){
  enviarFoto();
});

server.begin();

Serial.println("SERVIDOR INICIADO");

Serial.println("ABRIR http://192.168.4.1");

// INICIAR BARRIDO AUTOMATICO

iniciarBarrido();

}

// ======================================
//                  LOOP
// ======================================

void loop(){

server.handleClient();

// Captura y guarda una foto cada 10 segundos
 tomarFotoAutomatica();

// MOVIMIENTO GRADUAL DEL SERVO

actualizarServo();

// CAMBIO DE DIRECCION AUTOMATICO

actualizarBarrido();

// SEGURIDAD DE MOTORES

if(millis()-ultimoComando>TIEMPO_SEGURIDAD){

detenerMotores();

}

}