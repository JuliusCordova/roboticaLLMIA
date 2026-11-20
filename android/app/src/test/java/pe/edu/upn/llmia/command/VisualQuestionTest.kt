package pe.edu.upn.llmia.command

import org.junit.Assert.*
import org.junit.Test

class VisualQuestionTest {
    @Test fun visualQuestionsKeepOriginalTextAndNeverMove() {
        listOf("¿De qué color es esa silla?", "¿Hay un perro o un gato?",
            "Describe lo que está a la izquierda", "¿Y de qué color es?").forEach {
            assertEquals(Command.VisualQuestion(it), CommandParser.parse(it))
        }
    }
    @Test fun searchUsesOnlyExplicitTargetsAndRejectsCompoundMovement() {
        assertEquals(Command.Search("un perro"), CommandParser.parse("Busca un perro y descríbelo"))
        assertEquals(Command.Search("una botella"), CommandParser.parse("Encuentra una botella con la cámara"))
        assertTrue(CommandParser.parse("busca un perro y avanza") is Command.Unknown)
        assertTrue(CommandParser.parse("busca un perro o un gato") is Command.Unknown)
        assertEquals(Command.NewConversation, CommandParser.parse("Nueva conversación"))
    }
    @Test fun stopStillHasPriority() {
        assertEquals(Command.Stop, CommandParser.parse("para y busca un perro"))
    }
    @Test fun oversizedQuestionsAreRejected() {
        assertTrue(CommandParser.parse("busca " + "a".repeat(500)) is Command.Unknown)
    }
    @Test fun existingMotionGrammarIsPreserved() {
        assertTrue(CommandParser.parse("gira a la izquierda") is Command.Move)
        assertTrue(CommandParser.parse("no gires a la izquierda") is Command.Unknown)
    }
}
