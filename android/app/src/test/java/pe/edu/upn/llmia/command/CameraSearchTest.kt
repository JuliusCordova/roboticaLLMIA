package pe.edu.upn.llmia.command

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CameraSearchTest {
    @Test fun requiresStopBeforeAnyServoOrImage() = runBlocking {
        val events = mutableListOf<String>()
        try {
            CameraSearch.run({ events.add("stop"); false }, { events.add("servo") }, { events.add("image"); VisualMatch.FOUND })
            fail("STOP failure must cancel search")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("stop"), events)
    }
    @Test fun stopsAtFirstPositiveAndDoesNotInferAbsenceFromUncertainty() = runBlocking {
        val points = mutableListOf<Int>()
        val result = CameraSearch.run({ true }, { points.add(it) }, { if (it == 30) VisualMatch.FOUND else VisualMatch.NOT_FOUND })
        assertEquals(listOf(90, 30), points)
        assertEquals(SearchResult(VisualMatch.FOUND, 30), result)
        assertEquals(VisualMatch.UNCERTAIN, CameraSearch.run({ true }, {}, { VisualMatch.UNCERTAIN }).match)
    }
    @Test fun atMostThreeViewsAndNegativeDoesNotMeanEverywhere() = runBlocking {
        var count = 0
        val result = CameraSearch.run({ true }, {}, { count++; VisualMatch.NOT_FOUND })
        assertEquals(3, count)
        assertEquals(VisualMatch.NOT_FOUND, result.match)
        assertNull(result.angle)
    }
    @Test fun cancellationDuringSettlingPreventsCaptureAndFurtherServoCommands() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            CameraSearch.run({ events.add("stop"); true }, {
                events.add("servo"); started.complete(Unit); awaitCancellation()
            }, { events.add("image"); VisualMatch.FOUND })
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf("stop", "servo"), events)
    }
}
