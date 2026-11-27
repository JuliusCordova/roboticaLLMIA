package pe.edu.upn.llmia

import android.app.Activity
import android.app.Application
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import pe.edu.upn.llmia.command.CameraSearch
import pe.edu.upn.llmia.command.VisualMatch
import pe.edu.upn.llmia.command.ConversationContext
import kotlin.coroutines.coroutineContext
import pe.edu.upn.llmia.auth.OperatorAuth
import pe.edu.upn.llmia.command.Command
import pe.edu.upn.llmia.command.CommandParser
import pe.edu.upn.llmia.command.Direction
import pe.edu.upn.llmia.command.ObservationImages
import pe.edu.upn.llmia.net.CloudApi
import pe.edu.upn.llmia.net.CloudHttpException
import pe.edu.upn.llmia.net.Links
import pe.edu.upn.llmia.net.MoveReply
import pe.edu.upn.llmia.net.Observation
import pe.edu.upn.llmia.net.Poll
import pe.edu.upn.llmia.net.RobotApi
import pe.edu.upn.llmia.net.short
import pe.edu.upn.llmia.voice.Voice

enum class LinkState { OFF, CONNECTING, ON }
enum class Speaker { USER, LLMIA, SYSTEM }
enum class Source { VOICE, TEXT, BUTTON, PANEL }

sealed interface CloudState {
    data object Unknown : CloudState
    data object Checking : CloudState
    data class Ok(val mode: String, val operatorVerified: Boolean = false) : CloudState
    data class Fail(val reason: String) : CloudState
}

data class ChatMessage(
    val id: Long,
    val from: Speaker,
    val text: String,
    val source: Source? = null,
    val image: ImageBitmap? = null,
    val stage: String? = null,     // texto de progreso mientras Gemini analiza
    val alert: Boolean = false,
)

data class UiState(
    val robot: LinkState = LinkState.OFF,
    val cloud: CloudState = CloudState.Unknown,
    val bridgeOn: Boolean = false,
    val testCloudNetwork: Boolean = BuildConfig.DEBUG && BuildConfig.ALLOW_DEFAULT_CLOUD_NETWORK,
    val operator: String? = null,
    val operatorConfigured: Boolean = false,
    val voiceAvailable: Boolean = true,
    val live: Boolean = true,
    val frame: ImageBitmap? = null,
    val frameCount: Long = 0,
    val distance: Int? = null,
    val servoAngle: Int = 90,
    val sweeping: Boolean = true,      // el firmware inicia con barrido activo
    val moving: Direction? = null,
    val progress: Float = -1f,         // 0..1 en maniobras por texto/voz; -1 al mantener pulsado
    val listening: Boolean = false,
    val level: Float = 0f,
    val partial: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val obstacleAt: Long = 0L,
    val analyzing: Boolean = false,
    val searching: Boolean = false,
    val presenting: Boolean = false,
    val teamPhoto: ImageBitmap? = null,
)

class LlmiaViewModel(app: Application) : AndroidViewModel(app) {
    private val links = Links(app)
    private val robot = RobotApi { links.robotNetwork }
    private val cloud = CloudApi(BuildConfig.CLOUD_ORIGIN, BuildConfig.ROBOT_ID) { links.cloudNetwork() }
    private val auth = OperatorAuth(app)
    private val voice = Voice(app)

    private val _ui = MutableStateFlow(
        UiState(operatorConfigured = auth.configured, operator = auth.displayName, voiceAvailable = voice.available)
    )
    val ui: StateFlow<UiState> = _ui

    private var motionJob: Job? = null
    private var bridgeJob: Job? = null
    private var sensorsJob: Job? = null
    private var visualJob: Job? = null
    private var presentationJob: Job? = null
    private var bridgeCaptureActive = false
    private var intentJob: Job? = null
    private val conversation = ConversationContext()
    private val bridgeTransition = Mutex()
    private var foreground = false
    private val framesById = ObservationImages<ImageBitmap>()
    @Volatile private var pendingDescribe: String? = null
    private val delivered = mutableSetOf<String>()
    private var nextId = 1L
    private var lastObstacleSpeech = 0L
    @Volatile private var holding = false                // dedo apoyado en una flecha

    init {
        say(Speaker.SYSTEM, "Hola, soy LLMia. Conéctame en Ajustes y luego háblame, escríbeme o usa las flechas.")
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) {
            voice.cancelListening()
            _ui.update { it.copy(listening = false, partial = "", level = 0f) }
            emergencyStop()
            sensorsJob?.cancel()
            stopBridge()
        } else if (_ui.value.robot == LinkState.ON) startSensors()
    }

    // ------------------------------------------------------------------ conexión
    fun connectRobot(ssid: String, password: String) {
        if (ssid.isBlank() || password.length < 8) {
            say(Speaker.SYSTEM, "La red debe ser Carrito_CAM y la clave tener al menos 8 caracteres.", alert = true)
            return
        }
        _ui.update { it.copy(robot = LinkState.CONNECTING) }
        runCatching {
            links.connectRobot(ssid.trim(), password) { network, message ->
                // los callbacks de red llegan en otro hilo: se procesan en el principal
                viewModelScope.launch {
                    _ui.update { it.copy(robot = if (network != null) LinkState.ON else LinkState.OFF) }
                    say(Speaker.SYSTEM, message, alert = network == null)
                    if (network != null && foreground) startSensors() else {
                        sensorsJob?.cancel()
                        _ui.update { it.copy(frame = null, distance = null) }
                        stopMotion("sin conexión")
                    }
                }
            }
        }.onFailure {
            _ui.update { s -> s.copy(robot = LinkState.OFF) }
            say(Speaker.SYSTEM, "No se pudo solicitar el Wi-Fi: ${it.short()}", alert = true)
        }
    }

    fun checkCloud() = viewModelScope.launch {
        _ui.update { it.copy(cloud = CloudState.Checking) }
        try {
            val mode = cloud.health()
            val signedIn = _ui.value.operator != null
            if (signedIn) cloud.verifyOperator(auth.idToken())
            _ui.update { it.copy(cloud = CloudState.Ok(mode, signedIn)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudHttpException) {
            val message = when (e.code) {
                401 -> "Sesion rechazada. Cierra sesion e inicia nuevamente."
                403 -> "Usuario sin permiso para llmia-01 en el backend."
                else -> "Cloud Run respondio HTTP ${e.code}."
            }
            _ui.update { it.copy(cloud = CloudState.Fail(message)) }
        } catch (e: Exception) {
            _ui.update { it.copy(cloud = CloudState.Fail(e.short())) }
        }
    }

    fun signIn(activity: Activity) = viewModelScope.launch {
        resetConversation()
        runCatching { auth.signIn(activity) }
            .onSuccess { name -> _ui.update { it.copy(operator = name) }; say(Speaker.SYSTEM, "Sesión iniciada: $name") }
            .onFailure { say(Speaker.SYSTEM, "No se pudo iniciar sesión: ${it.short()}", alert = true) }
    }

    fun signOut() {
        intentJob?.cancel()
        presentationJob?.cancel()
        resetConversation()
        stopBridge()
        auth.signOut()
        _ui.update { it.copy(operator = null, cloud = CloudState.Unknown) }
    }

    // ------------------------------------------------------------------ vista en vivo y distancia
    private fun startSensors() {
        sensorsJob?.cancel()
        sensorsJob = viewModelScope.launch {
            var lastFrame = 0L
            var lastDistance = 0L
            while (isActive && links.robotNetwork != null) {
                val now = SystemClock.elapsedRealtime()
                val s = _ui.value
                // Durante una maniobra no se reserva la red para fotos ni sensor.
                val frameEvery = 800L
                if (s.moving == null && s.live && now - lastFrame >= frameEvery) {
                    lastFrame = now
                    runCatching { robot.capture() }.onSuccess { jpeg ->
                        if (foreground && links.robotNetwork != null) {
                            decode(jpeg)?.let { bmp -> _ui.update { it.copy(frame = bmp, frameCount = it.frameCount + 1) } }
                        }
                    }
                }
                if (s.moving == null && now - lastDistance >= 500L) {
                    lastDistance = now
                    runCatching { robot.distance() }.onSuccess { d -> _ui.update { it.copy(distance = d) } }
                }
                delay(120)
            }
        }
    }

    fun toggleLive() = _ui.update { it.copy(live = !it.live) }

    // ------------------------------------------------------------------ movimiento
    /** Flechas: se mueve mientras el dedo siga apoyado. */
    fun holdStart(direction: Direction) {
        if (_ui.value.presenting) return
        if (_ui.value.searching) { reply("La búsqueda usa solo la cámara. Pulsa STOP para cancelarla."); return }
        if (!foreground || _ui.value.robot != LinkState.ON) { say(Speaker.SYSTEM, "Conecta el robot y mantén la app visible.", alert = true); return }
        holding = true
        startMotion { drive(direction, Long.MAX_VALUE) }
    }

    /** Espera a que la maniobra anterior termine (y envíe su STOP) antes de empezar otra. */
    private fun startMotion(block: suspend () -> Unit) {
        intentJob?.cancel()
        robot.invalidateMotion()
        val previous = motionJob
        previous?.cancel()
        motionJob = viewModelScope.launch {
            withContext(NonCancellable) { previous?.cancelAndJoin() }
            coroutineContext.ensureActive()
            if (!foreground || _ui.value.searching || _ui.value.presenting) return@launch
            block()
        }
    }

    fun holdEnd() {
        if (holding) {
            holding = false
            motionJob?.cancel()
        }
    }

    fun emergencyStop() {
        intentJob?.cancel()
        presentationJob?.cancel()
        cancelVisual()
        stopWheels()
    }

    private fun stopWheels() {
        voice.cancelListening()
        _ui.update { it.copy(listening = false, partial = "", level = 0f) }
        holding = false
        robot.invalidateMotion() // revoca movimientos en cola antes de suspender
        val previous = motionJob
        previous?.cancel()
        motionJob = viewModelScope.launch {
            withContext(NonCancellable) {
                previous?.cancelAndJoin()
                if (!robot.stop() && foreground) say(Speaker.SYSTEM, "STOP sin confirmación. Comprueba la parada física.", alert = true)
            }
        }
        _ui.update { it.copy(moving = null, progress = -1f) }
    }

    private fun stopMotion(@Suppress("UNUSED_PARAMETER") reason: String) = emergencyStop()

    /**
     * Espera 200 ms entre respuestas MOVE. Duraciones objetivo, no tiempo real.
     * Revoca órdenes en cola y termina con STOP; el watchdog físico requiere pruebas.
     */
    private suspend fun drive(direction: Direction, millis: Long) {
        val timed = millis != Long.MAX_VALUE
        val limit = if (timed) millis else CommandParser.MAX_MS.getValue(direction)
        val lease = robot.beginMotion()
        val start = SystemClock.elapsedRealtime()
        _ui.update { it.copy(moving = direction, progress = if (timed) 0f else -1f) }
        try {
            var failures = 0
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - start
                if (!foreground || elapsed >= limit || (!timed && !holding)) break
                val reply = try { robot.move(direction, lease, start + limit) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { MoveReply.ERROR }
                when (reply) {
                    MoveReply.OK -> failures = 0
                    MoveReply.BLOCKED -> { onObstacle(); break }
                    MoveReply.ERROR -> if (++failures >= 3) {
                        say(Speaker.SYSTEM, "El robot no responde; se detiene por seguridad.", alert = true)
                        break
                    }
                }
                if (timed) _ui.update { it.copy(progress = (elapsed.toFloat() / millis).coerceIn(0f, 1f)) }
                delay(200)
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            robot.invalidateMotion()
            withContext(NonCancellable) {
                if (!robot.stop() && foreground) say(Speaker.SYSTEM, "STOP sin confirmación. Comprueba la parada física.", alert = true)
            }
            _ui.update { it.copy(moving = null, progress = -1f) }
        }
    }

    private fun onObstacle() {
        _ui.update { it.copy(obstacleAt = SystemClock.elapsedRealtime()) }
        val now = SystemClock.elapsedRealtime()
        if (now - lastObstacleSpeech > 2_500) {
            lastObstacleSpeech = now
            val d = _ui.value.distance
            val text = if (d != null) "Obstáculo a $d centímetros. No avanzo." else "Obstáculo o sensor sin lectura. No avanzo."
            say(Speaker.LLMIA, text, alert = true)
            voice.speak(text)
        }
    }

    // ------------------------------------------------------------------ servo
    fun setServo(angle: Int) {
        if (!foreground || _ui.value.moving != null || _ui.value.searching || _ui.value.presenting) return
        _ui.update { it.copy(servoAngle = angle, sweeping = false) }
        viewModelScope.launch { if (!_ui.value.searching) runCatching { robot.servo(angle) } }
    }

    fun toggleSweep(want: Boolean? = null) = viewModelScope.launch {
        if (!foreground || _ui.value.moving != null || _ui.value.searching || _ui.value.presenting) return@launch
        if (want != null && want == _ui.value.sweeping) return@launch
        runCatching { robot.toggleSweep() }
            .onSuccess { on -> _ui.update { it.copy(sweeping = on) } }
            .onFailure { say(Speaker.SYSTEM, "No se pudo cambiar el barrido: ${it.short()}", alert = true) }
    }

    // ------------------------------------------------------------------ órdenes por texto y voz
    fun submit(text: String, source: Source = Source.TEXT) {
        val clean = text.trim()
        if (clean.isEmpty() || !foreground) return
        say(Speaker.USER, clean, source = source)
        intentJob?.cancel()
        val parsed = CommandParser.parse(clean)
        if (_ui.value.presenting && parsed != Command.Stop) return
        if (_ui.value.searching && parsed != Command.Stop && parsed != Command.NewConversation) {
            reply("Estoy buscando con la cámara. Pulsa STOP para cancelar antes de otra orden.")
            return
        }
        when (val cmd = parsed) {
            Command.Stop -> { emergencyStop(); reply("Solicito detenerme.") }
            Command.PresentTeam -> presentTeam()
            Command.Describe -> describe(clean)
            is Command.Search -> startVisual("Busca ${cmd.target} y descríbelo", cmd.target)
            Command.NewConversation -> { resetConversation(); reply("Empezamos una nueva conversación.") }
            is Command.VisualQuestion -> describe(cmd.question)
            is Command.Look -> { setServo(cmd.angle); reply(lookText(cmd.angle)) }
            is Command.Sweep -> { if (!cmd.on) emergencyStop(); toggleSweep(cmd.on); reply(if (cmd.on) "Activo el barrido de la cámara." else "Detengo el barrido.") }
            is Command.Move -> {
                if (_ui.value.searching) { reply("Pulsa STOP para terminar la búsqueda antes de conducir."); return }
                if (_ui.value.robot != LinkState.ON) { reply("Necesito la conexión con el robot para moverme."); return }
                val extra = if (cmd.direction == Direction.BACK) " Ojo: atrás no tengo sensor." else ""
                reply("${cmd.direction.spoken} ${"%.1f".format(cmd.millis / 1000.0)} s.$extra")
                holding = false
                startMotion { drive(cmd.direction, cmd.millis) }
            }
            is Command.Unknown -> interpretPresentation(clean)
        }
    }

    private fun interpretPresentation(text: String) {
        val s = _ui.value
        if (!foreground || s.operator == null || s.moving != null || s.searching ||
            s.analyzing || s.presenting || text.length !in 1..500) {
            reply("No entendí la orden.")
            return
        }
        intentJob = viewModelScope.launch {
            try {
                val present = cloud.presentationIntent(auth.idToken(), text)
                ensureActive()
                val current = _ui.value
                if (!foreground || current.operator != s.operator || current.moving != null ||
                    current.searching || current.analyzing || current.presenting) return@launch
                if (present) presentTeam() else reply("No entendí la orden.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reply("No pude interpretar la solicitud.")
            }
        }
    }

    private fun presentTeam() {
        val s = _ui.value
        if (!foreground || s.presenting || s.analyzing || s.searching) return
        if (bridgeCaptureActive) {
            reply("Espera a que termine la captura antes de presentar al equipo.")
            return
        }
        if (s.operator == null || s.robot != LinkState.ON) {
            reply("Conecta el robot e inicia sesión para presentar al equipo.")
            return
        }
        _ui.update { it.copy(presenting = true) }
        stopWheels()
        presentationJob = viewModelScope.launch {
            try {
                motionJob?.join()
                check(robot.stop()) { "STOP sin confirmación" }
                val team = cloud.teamPresentation(auth.idToken())
                val photo = withContext(Dispatchers.Default) { decode(team.jpeg) }
                    ?: error("Fotografía inválida")
                _ui.update { it.copy(teamPhoto = photo) }
                val narration = "Les presento a mis creadores: " + team.members.joinToString(". ") + "."
                say(Speaker.LLMIA, narration)
                check(voice.narrate(narration)) { "Narración no completada" }
                delay(3_000)
            } catch (e: TimeoutCancellationException) {
                say(Speaker.SYSTEM, "Tiempo de presentación agotado.", alert = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(Speaker.SYSTEM, "Presentación interrumpida: ${e.short()}", alert = true)
            } finally {
                _ui.update { it.copy(presenting = false, teamPhoto = null) }
            }
        }
    }

    private fun lookText(angle: Int) = when {
        angle < 45 -> "Miro a la izquierda."
        angle > 135 -> "Miro a la derecha."
        else -> "Miro al frente."
    }

    fun toggleListening() {
        if (!foreground || _ui.value.presenting) return
        if (_ui.value.listening) { voice.cancelListening(); _ui.update { it.copy(listening = false, level = 0f) }; return }
        _ui.update { it.copy(listening = true, partial = "") }
        voice.listen(
            onLevel = { l -> _ui.update { it.copy(level = l) } },
            onPartial = { p ->
                _ui.update { it.copy(partial = p) }
                // «para» se ejecuta apenas se escucha, sin esperar el final de la frase
                if (CommandParser.parse(p) == Command.Stop && (_ui.value.moving != null || _ui.value.searching)) emergencyStop()
            },
            onResult = { text -> submit(text, Source.VOICE) },
            onError = { msg -> say(Speaker.SYSTEM, msg, alert = true) },
            onEnd = { _ui.update { it.copy(listening = false, level = 0f, partial = "") } },
        )
    }

    // ------------------------------------------------------------------ «¿Qué ves?» con Gemini
    fun describe(question: String = "¿Qué ves?") = startVisual(question)

    private fun cancelVisual() {
        pendingDescribe?.let { delivered.add(it) }
        visualJob?.cancel()
    }

    fun resetConversation() {
        cancelVisual()
        conversation.reset()
    }

    fun newConversation() {
        resetConversation()
        say(Speaker.SYSTEM, "Nueva conversación: las próximas preguntas empiezan sin contexto anterior.")
    }

    private fun startVisual(question: String, target: String? = null) {
        val s = _ui.value
        when {
            !foreground || s.presenting -> return
            s.robot != LinkState.ON -> { reply("Conecta el robot para tomar la foto."); return }
            !s.bridgeOn -> { reply("Activa el puente en Ajustes para enviar la foto a Gemini."); return }
            s.operator == null -> { reply("Inicia sesión de operador en Ajustes."); return }
            s.analyzing -> { reply("Sigo analizando. Pulsa STOP para cancelar."); return }
        }
        stopWheels()
        val version = conversation.version
        _ui.update { it.copy(analyzing = true, searching = target != null) }
        visualJob = viewModelScope.launch {
            var pausedSensors = false
            try {
                if (target == null) {
                    observe(question, version)
                } else {
                    pausedSensors = true
                    sensorsJob?.cancelAndJoin()
                    var last: Observation? = null
                    val result = withTimeout(CameraSearch.TIMEOUT_MS) {
                        CameraSearch.run(
                            confirmStop = {
                                motionJob?.join()
                                robot.stop()
                            },
                            pointAndSettle = { angle ->
                                robot.servo(angle) // además desactiva el barrido en el firmware existente
                                _ui.update { it.copy(servoAngle = angle, sweeping = false) }
                                say(Speaker.SYSTEM, "Buscando $target: cámara ${lookText(angle).lowercase()} (sin mover ruedas).")
                                delay(CameraSearch.SETTLE_MS)
                                // Vaciar los dos buffers de cámara que pueden preceder al cambio de ángulo.
                                repeat(2) { robot.capture() }
                            },
                            inspect = { angle ->
                                last = observe("$question. Cámara orientada hacia ${angleLabel(angle)}.", version, target)
                                when (last?.match) {
                                    "FOUND" -> VisualMatch.FOUND
                                    "NOT_FOUND" -> VisualMatch.NOT_FOUND
                                    else -> VisualMatch.UNCERTAIN
                                }
                            },
                        )
                    }
                    when (result.match) {
                        VisualMatch.FOUND -> reply("Encontré $target mirando hacia ${angleLabel(result.angle!!)}. ${last?.description.orEmpty()}")
                        VisualMatch.NOT_FOUND -> reply("No vi $target en las tres vistas revisadas. Puede estar fuera del campo de la cámara.")
                        VisualMatch.UNCERTAIN -> reply("No pude confirmar si hay $target. Revisa la iluminación o acerca el objeto a la cámara.")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                pendingDescribe?.let { delivered.add(it) }
                say(Speaker.SYSTEM, "Búsqueda detenida: se alcanzó el límite de tiempo.", alert = true)
            } catch (e: CancellationException) {
                pendingDescribe?.let { delivered.add(it) }
                throw e
            } catch (e: Exception) {
                val message = if (e is CloudHttpException) when (e.code) {
                    401 -> "Sesión rechazada. Inicia sesión nuevamente."
                    403 -> "Tu cuenta no tiene permiso sobre este robot."
                    404 -> "El contexto ya no está disponible. Pulsa Nueva conversación e intenta otra vez."
                    409 -> "Hay una solicitud activa o contexto sin terminar. Espera y vuelve a intentar."
                    else -> "La nube respondió HTTP ${e.code}."
                } else "Consulta interrumpida: ${e.short()}"
                say(Speaker.SYSTEM, message, alert = true)
            } finally {
                pendingDescribe = null
                _ui.update { it.copy(analyzing = false, searching = false) }
                if (pausedSensors && foreground && _ui.value.robot == LinkState.ON) startSensors()
            }
        }
    }

    private fun angleLabel(angle: Int) = when {
        angle < 60 -> "la izquierda"
        angle > 120 -> "la derecha"
        else -> "el centro"
    }

    private suspend fun observe(question: String, version: Long, target: String? = null): Observation {
        val bubble = say(Speaker.LLMIA, "Mirando…", stage = "Creando la solicitud")
        try {
            val obs = cloud.requestObservation(auth.idToken(), question, conversation.parentId, target)
            pendingDescribe = obs.id
            val deadline = SystemClock.elapsedRealtime() + 35_000
            while (SystemClock.elapsedRealtime() < deadline) {
                coroutineContext.ensureActive()
                val current = cloud.observation(auth.idToken(), obs.id)
                check(current.id == obs.id) { "Respuesta de otra observación" }
                updateMessage(bubble) { it.copy(stage = stageText(current.status)) }
                if (current.terminal) {
                    check(foreground && conversation.version == version) { "Conversación reiniciada" }
                    if (current.status == "LISTA") conversation.remember(current.id, version)
                    finish(bubble, current, null, speak = target == null)
                    check(current.status == "LISTA") { "No se completó la captura: ${current.error ?: current.status}" }
                    return current
                }
                delay(900)
            }
            error("La solicitud venció. Intenta de nuevo.")
        } catch (e: CancellationException) {
            updateMessage(bubble) { it.copy(text = "Consulta cancelada.", stage = null) }
            throw e
        } catch (e: Exception) {
            updateMessage(bubble) { it.copy(text = "No se completó la consulta.", stage = null, alert = true) }
            throw e
        } finally {
            // Cancelar no borra ni relanza el trabajo ya aceptado por el servidor.
            pendingDescribe?.let { delivered.add(it) }
            pendingDescribe = null
        }
    }

    private fun stageText(status: String) = when (status) {
        "PENDIENTE" -> "Esperando al robot"
        "RECLAMADA" -> "Tomando la fotografía"
        "ANALIZANDO" -> "Gemini está describiendo la escena"
        else -> null
    }

    /** Publica el resultado una sola vez por ID (puede llegar por el puente o por la consulta). */
    private fun finish(bubbleId: Long, obs: Observation?, failure: String?, speak: Boolean = true) {
        val id = obs?.id
        val speakOnce = id != null && delivered.add(id)
        val text = when {
            failure != null -> failure
            obs?.status == "LISTA" && obs.description != null -> obs.description
            obs?.status == "VENCIDA" -> "La solicitud venció antes de recibir la foto."
            else -> "No se pudo describir la escena (${obs?.error ?: "error"})."
        }
        val ok = obs?.status == "LISTA"
        updateMessage(bubbleId) { it.copy(text = text, stage = null, alert = !ok, image = framesById.get(id)) }
        if (ok && speakOnce && speak) voice.speak(text)
    }

    // ------------------------------------------------------------------ puente (dispositivo)
    fun startBridge() {
        if (_ui.value.operator == null) {
            say(Speaker.SYSTEM, "Inicia sesión con Google para activar el puente.", alert = true); return
        }
        if (!foreground || _ui.value.robot != LinkState.ON) { say(Speaker.SYSTEM, "Conecta el robot y mantén la app visible.", alert = true); return }
        viewModelScope.launch {
            bridgeTransition.withLock {
                bridgeJob?.cancelAndJoin()
                if (!foreground || _ui.value.operator == null) return@withLock
                _ui.update { it.copy(bridgeOn = true) }
                say(Speaker.SYSTEM, "Puente activo: solicitudes de la app y del panel web.")
                bridgeJob = viewModelScope.launch {
                    try {
                        while (isActive) {
                            if (_ui.value.presenting) {
                                delay(400)
                                continue
                            }
                            when (val p = cloud.poll(auth.idToken())) {
                                Poll.NoWork -> Unit
                                is Poll.Job -> try {
                                    while (_ui.value.presenting && p.expiresAt.isAfter(java.time.Instant.now())) delay(200)
                                    if (p.expiresAt.isAfter(java.time.Instant.now())) {
                                        bridgeCaptureActive = true
                                        try { handleJob(p.id, p.expiresAt) }
                                        finally { bridgeCaptureActive = false }
                                    }
                                } catch (e: CancellationException) { throw e }
                                  catch (e: Exception) { say(Speaker.SYSTEM, "No se pudo enviar la foto: ${e.short()}", alert = true) }
                                Poll.Unauthorized -> {
                                    say(Speaker.SYSTEM, "Sesión sin autorización. Comprueba tu acceso a Cloud Run. Puente detenido.", alert = true)
                                    break
                                }
                                is Poll.Failed -> say(Speaker.SYSTEM, "Consulta HTTP ${p.code}.", alert = true)
                            }
                            delay(if (pendingDescribe != null) 400 else 1_500)
                        }
                    } catch (e: CancellationException) { throw e }
                      catch (e: Exception) { say(Speaker.SYSTEM, "Puente detenido: ${e.short()}", alert = true) }
                    finally {
                        cancelVisual()
                        _ui.update { it.copy(bridgeOn = false) }
                    }
                }
            }
        }
    }

    fun stopBridge() {
        cancelVisual()
        viewModelScope.launch {
            bridgeTransition.withLock {
                bridgeJob?.cancelAndJoin()
                bridgeJob = null
                _ui.update { it.copy(bridgeOn = false) }
            }
        }
    }

    private suspend fun handleJob(id: String, expiresAt: java.time.Instant) {
        // La observación detiene la maniobra antes de reservar la cámara.
        stopWheels()
        motionJob?.join()
        coroutineContext.ensureActive()
        check(robot.stop()) { "STOP sin confirmación; no se captura" }
        check(expiresAt.isAfter(java.time.Instant.now())) { "Solicitud vencida antes de capturar" }
        val jpeg = robot.capture()
        check(expiresAt.isAfter(java.time.Instant.now())) { "Solicitud vencida antes de enviar" }
        val bmp = decode(jpeg)
        if (bmp != null) {
            framesById.put(id, bmp)
            _ui.update { it.copy(frame = bmp, frameCount = it.frameCount + 1) }
        }
        val result = cloud.upload(auth.idToken(), id, jpeg)
        check(result.id == id) { "La respuesta no corresponde a la captura" }
        if (pendingDescribe != id && result.terminal && delivered.add(id)) {
            val text = result.description ?: "No se pudo describir la escena (${result.error ?: "error"})."
            say(Speaker.LLMIA, text, source = Source.PANEL, image = framesById.get(id), alert = result.status != "LISTA")
            if (result.status == "LISTA" && !_ui.value.presenting) voice.speak(text)
        }
    }

    // ------------------------------------------------------------------ utilidades
    private fun decode(jpeg: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()

    private fun reply(text: String) {
        say(Speaker.LLMIA, text)
        voice.speak(text)
    }

    private fun say(
        from: Speaker, text: String, source: Source? = null, image: ImageBitmap? = null,
        stage: String? = null, alert: Boolean = false,
    ): Long {
        val id = nextId++
        _ui.update { s -> s.copy(messages = (s.messages + ChatMessage(id, from, text, source, image, stage, alert)).takeLast(60)) }
        return id
    }

    private fun updateMessage(id: Long, change: (ChatMessage) -> ChatMessage) {
        _ui.update { s -> s.copy(messages = s.messages.map { if (it.id == id) change(it) else it }) }
    }

    override fun onCleared() {
        foreground = false
        robot.invalidateMotion()
        motionJob?.cancel()
        visualJob?.cancel()
        intentJob?.cancel()
        presentationJob?.cancel()
        bridgeJob?.cancel()
        sensorsJob?.cancel()
        // Mantener la red hasta terminar la limpieza de la maniobra y pedir STOP.
        // Este alcance solo vive durante la limpieza; viewModelScope ya se cancela.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                motionJob?.join()
                robot.stop()
            } finally {
                links.disconnectRobot()
            }
        }
        voice.shutdown()
        super.onCleared()
    }
}


