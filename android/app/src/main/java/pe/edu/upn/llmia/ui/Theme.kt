package pe.edu.upn.llmia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta LLMia: noche de laboratorio con el amarillo UPN como única luz. */
object Llm {
    val Night = Color(0xFF0B0B0D)
    val Panel = Color(0xFF151517)
    val Panel2 = Color(0xFF1E1E22)
    val Line = Color(0xFF2C2C31)
    val Yellow = Color(0xFFFFC000)
    val YellowSoft = Color(0x33FFC000)
    val Text = Color(0xFFF4F1E8)
    val Muted = Color(0xFF9E9A90)
    val Danger = Color(0xFFFF4D4D)
    val Ok = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFA726)
}

@Composable
fun LlmiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Llm.Yellow, onPrimary = Llm.Night,
            secondary = Llm.Yellow, onSecondary = Llm.Night,
            background = Llm.Night, onBackground = Llm.Text,
            surface = Llm.Panel, onSurface = Llm.Text,
            surfaceVariant = Llm.Panel2, onSurfaceVariant = Llm.Muted,
            outline = Llm.Line, error = Llm.Danger,
        ),
        content = content,
    )
}
