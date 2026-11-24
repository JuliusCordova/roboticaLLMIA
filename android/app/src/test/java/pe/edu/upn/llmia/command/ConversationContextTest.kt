package pe.edu.upn.llmia.command

import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
    @Test fun resetRejectsLateResultsFromPreviousConversationOrAccount() {
        val context = ConversationContext()
        val previous = context.version
        context.remember("first", previous)
        context.reset()
        assertNull(context.parentId)
        assertFalse(context.remember("late", previous))
        assertNull(context.parentId)
        assertTrue(context.remember("new", context.version))
        assertEquals("new", context.parentId)
    }
}
