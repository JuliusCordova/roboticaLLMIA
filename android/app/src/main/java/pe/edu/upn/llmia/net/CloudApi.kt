package pe.edu.upn.llmia.net

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

/** Observación tal como la devuelve el backend (payload() en backend/llmia/http.py). */
data class Observation(
    val id: String,
    val status: String,          // PENDIENTE | RECLAMADA | ANALIZANDO | LISTA | ERROR | VENCIDA
    val description: String?,
    val error: String?,
) {
    val terminal get() = status in setOf("LISTA", "ERROR", "VENCIDA")

    companion object {
        fun parse(bytes: ByteArray): Observation {
            val o = JSONObject(bytes.toString(Charsets.UTF_8))
            return Observation(
                id = o.getString("id"),
                status = o.optString("status"),
                description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
                error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}

sealed interface Poll {
    data object NoWork : Poll
    data class Job(val id: String, val expiresAt: Instant) : Poll
    data object Unauthorized : Poll
    data class Failed(val code: Int) : Poll
}

class CloudHttpException(val code: Int, val detail: String) : IllegalStateException("HTTP $code $detail")

/**
 * Contrato existente de Cloud Run, sin cambios en el backend:
 *  operador:    POST /api/v1/robots/{id}/observations, GET /api/v1/observations/{id}
 *  dispositivo: POST /device/v1/poll, PUT /device/v1/observations/{id}/capture
 * Todas las llamadas salen por la red celular.
 */
class CloudApi(
    private val origin: String,
    private val robotId: String,
    private val cellular: () -> Network?,
) {
    private val idPattern = Regex("^[a-f0-9]{32}$")
    init {
        require(origin == "https://llmia-demo-913797829604.us-central1.run.app") { "Origen Cloud Run no aprobado" }
        require(robotId == "llmia-01") { "Robot no aprobado para esta demo" }
    }
    private fun net() = cellular() ?: throw CellularOffline()

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/health", connectMs = 8_000, readMs = 10_000)
        if (code != 200) throw CloudHttpException(code, "health")
        JSONObject(body.toString(Charsets.UTF_8)).optString("mode", "?")
    }

    suspend fun poll(deviceToken: String): Poll = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/device/v1/poll", "POST", "Bearer $deviceToken", ByteArray(0))
        when (code) {
            204 -> Poll.NoWork
            200 -> {
                val job = JSONObject(body.toString(Charsets.UTF_8))
                val id = job.getString("id")
                require(idPattern.matches(id) && job.getString("action") == "CAPTURE") { "Solicitud no valida" }
                val expiry = Instant.parse(job.getString("expires_at"))
                if (expiry.isAfter(Instant.now())) Poll.Job(id, expiry) else Poll.NoWork
            }
            401, 403 -> Poll.Unauthorized
            else -> Poll.Failed(code)
        }
    }

    suspend fun upload(deviceToken: String, id: String, jpeg: ByteArray): Observation = withContext(Dispatchers.IO) {
        require(idPattern.matches(id))
        val (code, body) = Http.exchange(net(), "$origin/device/v1/observations/$id/capture", "PUT",
            "Bearer $deviceToken", jpeg, "image/jpeg", readMs = 45_000)
        if (code != 200) throw CloudHttpException(code, body.toString(Charsets.UTF_8).take(80))
        Observation.parse(body)
    }

    suspend fun requestObservation(operatorToken: String): Observation = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/api/v1/robots/$robotId/observations", "POST",
            "Bearer $operatorToken", ByteArray(0))
        if (code != 202) throw CloudHttpException(code, body.toString(Charsets.UTF_8).take(80))
        Observation.parse(body)
    }

    suspend fun observation(operatorToken: String, id: String): Observation = withContext(Dispatchers.IO) {
        require(idPattern.matches(id))
        val (code, body) = Http.exchange(net(), "$origin/api/v1/observations/$id", "GET", "Bearer $operatorToken")
        if (code != 200) throw CloudHttpException(code, body.toString(Charsets.UTF_8).take(80))
        Observation.parse(body)
    }
}
