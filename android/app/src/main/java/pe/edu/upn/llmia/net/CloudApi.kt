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
 * Contrato de Cloud Run:
 *  operador:    POST /api/v1/robots/{id}/observations, GET /api/v1/observations/{id}
 *  puente Android: rutas /api/v1/robots/{id}/bridge con sesión Firebase
 * Las llamadas usan red celular; debug puede habilitar Internet predeterminado.
 */
class CloudApi(
    private val origin: String,
    private val robotId: String,
    private val network: () -> Network?,
) {
    private val idPattern = Regex("^[a-f0-9]{32}$")
    init {
        require(origin == "https://llmia-demo-913797829604.us-central1.run.app") { "Origen Cloud Run no aprobado" }
        require(robotId == "llmia-01") { "Robot no aprobado para esta demo" }
    }
    private fun net() = network() ?: throw CloudOffline()

    suspend fun health(): String = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/health", connectMs = 8_000, readMs = 10_000)
        if (code != 200) throw CloudHttpException(code, "health")
        JSONObject(body.toString(Charsets.UTF_8)).optString("mode", "?")
    }

    /** Prueba de autorización sin crear capturas ni requerir robot conectado. */
    suspend fun verifyOperator(operatorToken: String): Unit = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/api/v1/robots/$robotId/status",
            auth = "Bearer $operatorToken", maxResponse = 4096, readMs = 10_000)
        if (code != 200) throw CloudHttpException(code, "operator_status")
        check(JSONObject(body.toString(Charsets.UTF_8)).getString("robot_id") == robotId) {
            "Respuesta de otro robot"
        }
    }

    suspend fun poll(operatorToken: String): Poll = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/api/v1/robots/$robotId/bridge/poll", "POST", "Bearer $operatorToken", ByteArray(0))
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

    suspend fun upload(operatorToken: String, id: String, jpeg: ByteArray): Observation = withContext(Dispatchers.IO) {
        require(idPattern.matches(id))
        val (code, body) = Http.exchange(net(), "$origin/api/v1/robots/$robotId/bridge/observations/$id/capture", "PUT",
            "Bearer $operatorToken", jpeg, "image/jpeg", readMs = 45_000)
        if (code != 200) throw CloudHttpException(code, body.toString(Charsets.UTF_8).take(80))
        Observation.parse(body)
    }

    suspend fun requestObservation(operatorToken: String, question: String = "¿Qué ves?"): Observation = withContext(Dispatchers.IO) {
        val (code, body) = Http.exchange(net(), "$origin/api/v1/robots/$robotId/observations", "POST",
            "Bearer $operatorToken", JSONObject().put("question", question).toString().toByteArray(Charsets.UTF_8), "application/json")
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


