package pe.edu.upn.llmia

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
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
                if (state.presenting) {
                    BackHandler { vm.emergencyStop() }
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        val photo = state.teamPhoto
                        if (photo != null) {
                            Image(photo, contentDescription = "Equipo LLMia",
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        } else {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                        Button(onClick = { vm.emergencyStop() },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                            Text("STOP")
                        }
                    }
                } else {
                    LlmiaScreen(vm, state)
                }
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
