package pe.edu.upn.llmia.command

import org.junit.Assert.*
import org.junit.Test

class VisualQuestionTest {
    @Test fun visualQuestionsKeepOriginalTextAndNeverMove() {
        listOf("Busca un perro y descríbelo", "¿De qué color es esa silla?", "¿Hay un perro o un gato?",
            "Describe lo que está a la izquierda", "Encuentra una botella", "Busca un perro y avanza").forEach {
            assertEquals(Command.VisualQuestion(it), CommandParser.parse(it))
        }
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
