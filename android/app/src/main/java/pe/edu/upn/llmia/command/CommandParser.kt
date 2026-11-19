package pe.edu.upn.llmia.command

import java.text.Normalizer

/** Direcciones que entiende el firmware en /move?dir=… */
enum class Direction(val wire: String, val label: String, val spoken: String) {
    FORWARD("adelante", "Avanzar", "Avanzo"),
    BACK("atras", "Retroceder", "Retrocedo"),
    LEFT("izquierda", "Izquierda", "Giro a la izquierda"),
    RIGHT("derecha", "Derecha", "Giro a la derecha"),
}

sealed interface Command {
    data class Move(val direction: Direction, val millis: Long) : Command
    data object Stop : Command
    data object Describe : Command
    data class Look(val angle: Int) : Command      // servo: 0 = izquierda, 90 = centro, 180 = derecha
    data class Sweep(val on: Boolean) : Command
    data class Unknown(val text: String) : Command
}

/**
 * Intérprete local y determinístico de órdenes en español (texto o voz).
 * No usa la nube: funciona sin Internet y Gemini nunca decide un movimiento.
 * Las duraciones son cortas y tienen tope; la placa además bloquea el avance a 20 cm
 * y se detiene sola si deja de recibir órdenes.
 */
object CommandParser {
    // Tiempos base en ms: (corto, normal, largo). Calibrar con el robot en el piso.
    private val TIMES = mapOf(
        Direction.FORWARD to longArrayOf(450, 900, 1_600),
        Direction.BACK to longArrayOf(300, 500, 800),
        Direction.LEFT to longArrayOf(220, 400, 700),
        Direction.RIGHT to longArrayOf(220, 400, 700),
    )
    val MAX_MS = mapOf(
        Direction.FORWARD to 3_000L,
        Direction.BACK to 1_000L,   // sin sensor trasero
        Direction.LEFT to 1_500L,
        Direction.RIGHT to 1_500L,
    )

    private val STOP = setOf("para", "parate", "alto", "stop", "detente", "deten", "frena", "frenar", "quieto", "quieta", "basta")
    private val SMALL = setOf("poco", "poquito", "poquitito", "ligeramente", "suave", "despacio")
    private val LARGE = setOf("mucho", "bastante", "harto", "mas", "largo")
    private val NUMBERS = mapOf("un" to 1.0, "uno" to 1.0, "una" to 1.0, "medio" to 0.5, "dos" to 2.0, "tres" to 3.0)


    fun normalize(raw: String): String {
        val noAccents = Normalizer.normalize(raw.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.replace(Regex("[^a-z0-9., ]"), " ").replace(Regex("\\s+"), " ").trim(' ', '.', ',')
    }

    fun parse(raw: String): Command {
        val text = normalize(raw)
        if (text.isEmpty()) return Command.Unknown(raw)
        val words = text.split(' ').map { it.trim(',', '.') }.filter { it.isNotEmpty() }
        val has = { set: Set<String> -> words.any { it in set } }

        // Solo una orden explícita. Nunca inferir movimiento de una pregunta,
        // negación, alternativa o secuencia de instrucciones.
        if (text in setOf("para el barrido", "deten el barrido", "desactiva el barrido", "apaga el barrido")) {
            return Command.Sweep(false)
        }
        if (has(STOP)) return Command.Stop
        if (words.any { it in setOf("no", "nunca", "sin", "tampoco", "ni", "y", "o", "luego", "despues", "si") } ||
            raw.any { it in "-−–—" }) return Command.Unknown(raw)

        val phrase = text.replaceFirst(Regex("^llmia[,.]?\\s+"), "").removePrefix("por favor ").removeSuffix(" por favor").trim()
        if (phrase in setOf("activa el barrido", "inicia el barrido", "escanea", "escanear")) return Command.Sweep(true)
        if (Regex("(?:mira|gira la camara|camara) (?:a la |al |a |hacia la )?(izquierda|derecha|centro|frente|adelante)").matches(phrase)) {
            return Command.Look(when {
                "izquierda" in words -> 0
                "derecha" in words -> 180
                else -> 90
            })
        }
        if (phrase in setOf("que ves", "que observas", "que hay", "que hay adelante", "que estas viendo",
                "describe la escena", "describe el entorno", "describeme la escena", "que se ve", "toma una foto", "foto", "que es eso")) {
            return Command.Describe
        }
        val suffix = "(?: un (?:poco|poquito)| mucho| bastante| ligeramente| despacio| (?:[0-9]+(?:[.,][0-9]+)?|un|uno|una|medio|dos|tres) (?:segundos?|seg|s))?"
        val direction = when {
            Regex("(?:avanza|avanzar|adelante|sigue derecho|sigue recto)" + suffix).matches(phrase) -> Direction.FORWARD
            Regex("(?:retrocede|retroceder|atras|reversa|regresa)" + suffix).matches(phrase) -> Direction.BACK
            Regex("(?:(?:gira|dobla) a la izquierda|izquierda)" + suffix).matches(phrase) || phrase == "da media vuelta a la izquierda" -> Direction.LEFT
            Regex("(?:(?:gira|dobla) a la derecha|derecha)" + suffix).matches(phrase) || phrase == "da media vuelta a la derecha" -> Direction.RIGHT
            else -> return Command.Unknown(raw)
        }
        val numericSeconds = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(?:segundos?|seg|s)\\b").find(phrase)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        if (numericSeconds != null && (!numericSeconds.isFinite() || numericSeconds <= 0)) return Command.Unknown(raw)
        return Command.Move(direction, duration(direction, text, words))
    }

    private fun duration(direction: Direction, text: String, words: List<String>): Long {
        val times = TIMES.getValue(direction)
        val max = MAX_MS.getValue(direction)
        // «media vuelta» / «una vuelta»: solo para giros
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            if (text.contains("media vuelta")) return 900L.coerceAtMost(max)
            if (text.contains("vuelta")) return max
        }
        // «2 segundos», «1,5 s», «dos segundos», «medio segundo»
        val seconds = Regex("(\\d+(?:[.,]\\d+)?)\\s*(segundos?|seg|s)\\b").find(text)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()
            ?: words.withIndex().firstNotNullOfOrNull { (i, w) ->
                val next = words.getOrNull(i + 1) ?: ""
                if (next.startsWith("segundo")) NUMBERS[w] else null
            }
        if (seconds != null) return (seconds * 1000).toLong().coerceIn(150L, max)
        return when {
            words.any { it in SMALL } -> times[0]
            words.any { it in LARGE } -> times[2]
            else -> times[1]
        }.coerceAtMost(max)
    }
}
