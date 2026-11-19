package pe.edu.upn.llmia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upn.llmia.ui.LlmiaScreen
import pe.edu.upn.llmia.ui.LlmiaTheme

/**
 * LLMia · puente Android entre la ESP32-CAM (Wi-Fi Carrito_CAM) y Cloud Run (datos móviles),
 * con control local del robot por flechas, texto y voz.
 */
class MainActivity : ComponentActivity() {
    private val vm: LlmiaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LlmiaTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                LlmiaScreen(vm, state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.setForeground(true)
    }

    // Deshabilitar nuevas órdenes y cancelar voz antes de pedir STOP.
    override fun onPause() {
        vm.setForeground(false)
        super.onPause()
    }
}
