package pe.edu.upn.llmia.net

import android.net.Network
import android.os.SystemClock
import pe.edu.upn.llmia.command.MotionPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.edu.upn.llmia.command.Direction

enum class MoveReply { OK, BLOCKED, ERROR }

/**
 * Cliente de las rutas del firmware LLMia.ino (sin reprogramar la placa):
 *  /move?dir=adelante|atras|izquierda|derecha|stop   -> "OK" | "OBSTACULO" | "STOP"
 *  /distance -> {"distancia": cm | -1}
 *  /capture  -> JPEG
 *  /servo?angulo=0..180, /sweep -> {"activo": bool}
 *
 * La placa bloquea «adelante» a 20 cm o sin lectura. El watchdog de 700 ms
 * se comprueba desde su bucle principal: la latencia real requiere medición.
 * MOVE y STOP se serializan; las órdenes revocadas no salen de la cola.
 */
class RobotApi(private val network: () -> Network?) {
    private val base = "http://192.168.4.1"
    private val io = Mutex() // el WebServer de la ESP32 atiende una petición a la vez
    private val permit = MotionPermit()
    fun beginMotion(): Long = permit.begin()
    fun invalidateMotion() = permit.revoke()

    private val maxJpeg = 512 * 1024

    private fun net() = network() ?: throw RobotOffline()

    suspend fun move(dir: Direction, lease: Long, deadline: Long): MoveReply = withContext(Dispatchers.IO) {
        io.withLock {
            if (!permit.allows(lease) || SystemClock.elapsedRealtime() >= deadline) return@withLock MoveReply.ERROR
            val (code, body) = Http.exchange(net(), "$base/move?dir=${dir.wire}",
                maxResponse = 256, connectMs = 1_500, readMs = 1_500)
            val text = body.toString(Charsets.UTF_8).trim()
            when {
                code == 200 && text == "OK" -> MoveReply.OK
                code == 200 && text == "OBSTACULO" -> MoveReply.BLOCKED
                else -> MoveReply.ERROR
            }
        }
    }

    /** Revoca órdenes en cola y envía STOP tras la petición local ya en curso. */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        permit.revoke()
        io.withLock {
            runCatching {
                val (code, body) = Http.exchange(net(), "$base/move?dir=stop", maxResponse = 256, connectMs = 500, readMs = 500)
                code == 200 && body.toString(Charsets.UTF_8).trim() == "STOP"
            }.getOrDefault(false)
        }
    }

    suspend fun distance(): Int? = withContext(Dispatchers.IO) {
        io.withLock {
            if (permit.isMoving()) return@withLock null
            val (code, body) = Http.exchange(net(), "$base/distance", maxResponse = 512, connectMs = 1_500, readMs = 2_000)
            if (code != 200) return@withLock null
            val d = JSONObject(body.toString(Charsets.UTF_8)).optInt("distancia", -1)
            if (d < 0) null else d
        }
    }

    suspend fun capture(): ByteArray = withContext(Dispatchers.IO) {
        io.withLock {
            check(!permit.isMoving()) { "Camara pausada durante el movimiento" }
            val (code, data) = Http.exchange(net(), "$base/capture", maxResponse = maxJpeg, connectMs = 2_000, readMs = 6_000)
            if (code != 200) throw IllegalStateException("La cámara respondió HTTP $code")
            val valid = data.size >= 4 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() &&
                data[data.size - 2] == 0xFF.toByte() && data.last() == 0xD9.toByte()
            if (!valid) throw IllegalStateException("La cámara no devolvió un JPEG válido")
            data
        }
    }

    suspend fun servo(angle: Int) = withContext(Dispatchers.IO) {
        io.withLock {
            check(!permit.isMoving()) { "Servo pausado durante el movimiento" }
            Http.exchange(net(), "$base/servo?angulo=${angle.coerceIn(0, 180)}", maxResponse = 256, connectMs = 1_500, readMs = 2_000)
        }
        Unit
    }

    /** Alterna el barrido automático del servo; devuelve si quedó activo. */
    suspend fun toggleSweep(): Boolean = withContext(Dispatchers.IO) {
        io.withLock {
            check(!permit.isMoving()) { "Barrido pausado durante el movimiento" }
            val (_, body) = Http.exchange(net(), "$base/sweep", maxResponse = 256, connectMs = 1_500, readMs = 2_000)
            JSONObject(body.toString(Charsets.UTF_8)).optBoolean("activo", false)
        }
    }
}
