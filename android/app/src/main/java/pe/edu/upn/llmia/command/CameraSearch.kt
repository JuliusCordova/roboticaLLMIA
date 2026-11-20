package pe.edu.upn.llmia.command

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class VisualMatch { FOUND, NOT_FOUND, UNCERTAIN }
data class SearchResult(val match: VisualMatch, val angle: Int? = null)

/** Secuencia finita sin acceso a MOVE: primero exige STOP confirmado. */
object CameraSearch {
    val angles = listOf(90, 30, 150)
    const val SETTLE_MS = 3_200L // firmware: hasta 180 pasos de 15 ms, más margen
    const val TIMEOUT_MS = 120_000L

    suspend fun run(
        confirmStop: suspend () -> Boolean,
        pointAndSettle: suspend (Int) -> Unit,
        inspect: suspend (Int) -> VisualMatch,
    ): SearchResult {
        check(confirmStop()) { "No se confirmó STOP; búsqueda cancelada" }
        var uncertain = false
        for (angle in angles) {
            currentCoroutineContext().ensureActive()
            pointAndSettle(angle)
            currentCoroutineContext().ensureActive()
            when (inspect(angle)) {
                VisualMatch.FOUND -> return SearchResult(VisualMatch.FOUND, angle)
                VisualMatch.UNCERTAIN -> uncertain = true
                VisualMatch.NOT_FOUND -> Unit
            }
        }
        return SearchResult(if (uncertain) VisualMatch.UNCERTAIN else VisualMatch.NOT_FOUND)
    }
}
