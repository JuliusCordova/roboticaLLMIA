package pe.edu.upn.llmia.voice

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import java.util.UUID

/**
 * Voz del sistema Android: reconocimiento (SpeechRecognizer) y síntesis (TextToSpeech).
 * Crear y usar en el hilo principal.
 */
class Voice(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var session = 0L

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                for (locale in listOf(Locale("es", "PE"), Locale("es", "US"), Locale("es", "ES"), Locale("es"))) {
                    val r = engine.setLanguage(locale)
                    if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                        ttsReady = true
                        break
                    }
                }
            }
        }
    }

    fun listen(
        onLevel: (Float) -> Unit,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onEnd: () -> Unit,
    ) {
        cancelListening()
        val listeningSession = session
        tts?.stop()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onEnd()
            onError("Falta el permiso de micrófono.")
            return
        }
        if (!available) {
            onEnd()
            onError("Este Android no tiene un servicio de reconocimiento disponible.")
            return
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onRmsChanged(rmsdB: Float) {
                if (session == listeningSession) onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (session != listeningSession) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                if (session != listeningSession) return
                onEnd()
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onError("No te escuché bien. Intenta de nuevo.") else onResult(text)
            }
            override fun onError(error: Int) {
                if (session != listeningSession) return
                onEnd()
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché bien. Intenta de nuevo."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocimiento de voz necesita Internet (datos móviles)."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono."
                        else -> "Error de reconocimiento de voz ($error)."
                    }
                )
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    fun cancelListening() {
        session++
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "llmia")
    }

    /** Wait for the actual utterance completion, not an estimated duration. */
    suspend fun narrate(text: String): Boolean = withTimeout(90_000L) {
        suspendCancellableCoroutine { continuation ->
            val engine = tts
            if (!ttsReady || engine == null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            val id = UUID.randomUUID().toString()
            val listener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(true)
                }
                @Deprecated("Legacy TTS callback")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(false)
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(false)
                }
            }
            if (engine.setOnUtteranceProgressListener(listener) != TextToSpeech.SUCCESS) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { engine.stop() }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS &&
                continuation.isActive) continuation.resume(false)
        }
    }

    fun shutdown() {
        cancelListening()
        tts?.shutdown(); tts = null
    }
}
