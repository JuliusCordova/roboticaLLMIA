package pe.edu.upn.llmia.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private fun move(text: String) = CommandParser.parse(text) as Command.Move

    @Test fun forwardDefault() = assertEquals(Command.Move(Direction.FORWARD, 900), CommandParser.parse("Avanza"))
    @Test fun forwardSmall() = assertEquals(450L, move("LLMia, avanza un poco").millis)
    @Test fun forwardSeconds() = assertEquals(2_000L, move("avanza 2 segundos").millis)
    @Test fun forwardSecondsInWords() = assertEquals(2_000L, move("avanza dos segundos").millis)
    @Test fun forwardIsCapped() = assertEquals(3_000L, move("avanza 9 segundos").millis)
    @Test fun straightIsForward() = assertEquals(Direction.FORWARD, move("sigue derecho").direction)
    @Test fun turnLeft() = assertEquals(Direction.LEFT, move("gira a la izquierda").direction)
    @Test fun turnRightWithAccents() = assertEquals(Direction.RIGHT, move("Dobla a la DERECHA").direction)
    @Test fun halfTurn() = assertEquals(900L, move("da media vuelta a la derecha").millis)
    @Test fun backIsShortAndCapped() {
        assertEquals(Direction.BACK, move("retrocede").direction)
        assertTrue(move("retrocede 5 segundos").millis <= 1_000L)
    }

    @Test fun stopWins() {
        assertEquals(Command.Stop, CommandParser.parse("¡Para!"))
        assertEquals(Command.Stop, CommandParser.parse("avanza no, detente"))
        assertEquals(Command.Stop, CommandParser.parse("alto"))
    }

    @Test fun describe() {
        assertEquals(Command.Describe, CommandParser.parse("¿Qué ves?"))
        assertEquals(Command.Describe, CommandParser.parse("LLMia, ¿qué hay adelante?"))
        assertEquals(Command.Describe, CommandParser.parse("describe la escena"))
    }

    @Test fun lookWithServo() {
        assertEquals(Command.Look(0), CommandParser.parse("mira a la izquierda"))
        assertEquals(Command.Look(180), CommandParser.parse("gira la cámara a la derecha"))
        assertEquals(Command.Look(90), CommandParser.parse("cámara al centro"))
    }

    @Test fun sweep() {
        assertEquals(Command.Sweep(true), CommandParser.parse("activa el barrido"))
        assertEquals(Command.Sweep(false), CommandParser.parse("para el barrido"))
    }

    @Test fun unknown() = assertTrue(CommandParser.parse("cuéntame un chiste") is Command.Unknown)
    @Test fun rejectsNegatedCommands() {
        listOf("no avanza", "no avances", "no retrocede", "no gires a la izquierda", "nunca adelante", "no tomes foto").forEach {
            assertTrue(it, CommandParser.parse(it) is Command.Unknown)
        }
    }
    @Test fun rejectsAlternativesAndSequences() {
        listOf("izquierda o derecha", "avanza y luego retrocede", "avanza y gira a la derecha", "si puedes avanza").forEach {
            assertTrue(it, CommandParser.parse(it) is Command.Unknown)
        }
    }
    @Test fun rejectsQuestionsAboutMovement() {
        listOf("dime si debo ir a la izquierda", "puedes avanza", "que significa derecha").forEach {
            assertTrue(it, CommandParser.parse(it) is Command.Unknown)
        }
    }
    @Test fun rejectsInvalidDuration() {
        listOf("avanza -2 segundos", "avanza −2 segundos", "avanza 0 segundos", "retrocede 0,0 s").forEach {
            assertTrue(it, CommandParser.parse(it) is Command.Unknown)
        }
    }
    @Test fun explicitStopOverridesCompoundInstruction() {
        assertEquals(Command.Stop, CommandParser.parse("para y activa el barrido"))
        assertEquals(Command.Stop, CommandParser.parse("no avances detente"))
    }
    @Test fun decimalSecondsAndPoliteness() {
        assertEquals(Command.Move(Direction.FORWARD, 1500), CommandParser.parse("por favor avanza 1,5 segundos"))
        assertEquals(Command.Move(Direction.BACK, 500), CommandParser.parse("retrocede por favor"))
        assertEquals(Command.Move(Direction.FORWARD, 900), CommandParser.parse("Avanza."))
    }
}
