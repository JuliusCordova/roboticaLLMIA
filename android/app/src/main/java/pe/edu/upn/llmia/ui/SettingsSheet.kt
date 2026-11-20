package pe.edu.upn.llmia.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import pe.edu.upn.llmia.CloudState
import pe.edu.upn.llmia.LinkState
import pe.edu.upn.llmia.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
    onCheckCloud: () -> Unit,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    // Las claves viven solo en esta hoja: no se guardan ni sobreviven al cierre de la app.
    var ssid by remember { mutableStateOf("Carrito_CAM") }
    var password by remember { mutableStateOf("") }

    val wifiPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    val askWifi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) onConnect(ssid, password)
    }
    fun connect() {
        val granted = wifiPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) onConnect(ssid, password) else askWifi.launch(wifiPermissions.toTypedArray())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Llm.Panel,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Conexión de LLMia", color = Llm.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)

            Section("1 · Robot (Wi-Fi local)", when (state.robot) {
                LinkState.ON -> "Conectado a Carrito_CAM"
                LinkState.CONNECTING -> "Conectando… acepta el diálogo de Android"
                LinkState.OFF -> "Sin conexión"
            })
            Field(ssid, { ssid = it }, "Red Wi-Fi del robot")
            Field(password, { password = it }, "Clave Wi-Fi (no se guarda)", secret = true)
            Primary("Conectar al robot", enabled = state.robot != LinkState.CONNECTING) { connect() }

            Section(if (state.testCloudNetwork) "2 · Nube (Internet de prueba)" else "2 · Nube (datos móviles)", when (val c = state.cloud) {
                is CloudState.Ok -> "Cloud Run responde · modo ${c.mode}" +
                    if (c.operatorVerified) " · Operador autorizado" else " · Operador sin comprobar"
                CloudState.Checking -> "Comprobando…"
                is CloudState.Fail -> "Sin acceso: ${c.reason}"
                CloudState.Unknown -> if (state.testCloudNetwork) "Modo prueba debug: usa Internet del emulador y comprueba"
                    else "Activa los datos móviles y comprueba"
            })
            Secondary("Comprobar Cloud Run", onCheckCloud)

            Section("3 · Puente de la cámara", if (state.bridgeOn) "Activo: atiende «¿Qué ves?»" else "Apagado")
            if (!state.bridgeOn) {
                Text("Usa tu sesión de Google automáticamente. No necesitas copiar un token.", color = Llm.Muted, fontSize = 13.sp)
                Primary("Iniciar puente", enabled = state.robot == LinkState.ON && state.operator != null, onClick = onStartBridge)
            } else {
                Secondary("Detener puente", onStopBridge)
            }

            Section("4 · Operador", when {
                !state.operatorConfigured -> "Falta configurar Firebase en local.properties (ver README)"
                state.operator != null -> "Sesión: ${state.operator}"
                else -> "Inicia sesión para preguntar «¿Qué ves?» desde el teléfono"
            })
            if (state.operatorConfigured) {
                if (state.operator == null) Primary("Iniciar sesión con Google", true, onSignIn)
                else Secondary("Cerrar sesión", onSignOut)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Los motores solo se mueven por la red local del robot. La placa bloquea el avance a 20 cm, " +
                    "se detiene sola si deja de recibir órdenes y Gemini nunca decide un movimiento.",
                color = Llm.Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Section(title: String, status: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title.uppercase(), color = Llm.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(status, color = Llm.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Llm.Yellow, unfocusedBorderColor = Llm.Line,
            focusedLabelColor = Llm.Yellow, cursorColor = Llm.Yellow),
    )
}

@Composable
private fun Primary(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Llm.Yellow, contentColor = Llm.Night)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Secondary(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text, color = Llm.Yellow, fontWeight = FontWeight.Bold)
    }
}


