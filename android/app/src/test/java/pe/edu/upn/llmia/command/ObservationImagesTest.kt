package pe.edu.upn.llmia.command

import org.junit.Assert.*
import org.junit.Test

class ObservationImagesTest {
    @Test fun neverFallsBackToPreviousPhoto() {
        val images = ObservationImages<String>()
        images.put("old", "foto-anterior")
        assertNull(images.get("new"))
        assertNull(images.get(null))
        assertEquals("foto-anterior", images.get("old"))
    }
    @Test fun preservesAssociationWhenResultsArriveOutOfOrder() {
        val images = ObservationImages<String>()
        images.put("a", "botella")
        images.put("b", "caja")
        assertEquals("botella", images.get("a"))
        assertEquals("caja", images.get("b"))
    }
    @Test fun evictedPhotoIsAbsentInsteadOfReplaced() {
        val images = ObservationImages<String>(1)
        images.put("a", "botella")
        images.put("b", "caja")
        assertNull(images.get("a"))
        assertEquals("caja", images.get("b"))
    }
}
