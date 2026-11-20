package pe.edu.upn.llmia.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import pe.edu.upn.llmia.CloudState
import pe.edu.upn.llmia.LinkState
import pe.edu.upn.llmia.LlmiaViewModel
import pe.edu.upn.llmia.Speaker
import pe.edu.upn.llmia.UiState

private enum class Mode(val label: String) { DRIVE("Conducir"), TALK("Conversar") }

@Composable
fun LlmiaScreen(vm: LlmiaViewModel, state: UiState) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(Mode.DRIVE) }
    var settings by rememberSaveable { mutableStateOf(state.robot == LinkState.OFF) }
    var draft by rememberSaveable { mutableStateOf("") }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleListening()
    }
    fun talk() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.toggleListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun send(text: String) { vm.submit(text); draft = "" }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF15130C), Llm.Night, Llm.Night)))
            .systemBarsPadding().imePadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Header(onSettings = { settings = true })
            StatusRow(state)
            Spacer(Modifier.height(10.dp))

            val caption = state.messages.lastOrNull { it.from == Speaker.LLMIA }.takeIf { mode == Mode.DRIVE }
            LiveView(
                frame = state.frame, frameCount = state.frameCount, live = state.live, distance = state.distance,
                obstacleAt = state.obstacleAt, moving = state.moving, progress = state.progress, caption = caption,
                onToggleLive = vm::toggleLive, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 11f),
            )
            Spacer(Modifier.height(10.dp))
            ModeSwitch(mode) { mode = it }
            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { m ->
                    when (m) {
                        Mode.DRIVE -> DrivePanel(vm, state)
                        Mode.TALK -> ChatPanel(state)
                    }
                }
            }

            if (state.listening) {
                Text(
                    state.partial.ifBlank { "Te escucho… di «avanza un poco», «gira a la derecha» o «¿qué ves?»" },
                    color = Llm.Yellow, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip("👁 ¿Qué ves?") { send("¿Qué ves?") }
                QuickChip("Busca un perro") { send("Busca un perro y descríbelo") }
                QuickChip("Avanza un poco") { send("Avanza un poco") }
                QuickChip("Gira a la izquierda") { send("Gira a la izquierda") }
                QuickChip("Gira a la derecha") { send("Gira a la derecha") }
                QuickChip("Retrocede") { send("Retrocede") }
                QuickChip("Barrido") { send(if (state.sweeping) "Para el barrido" else "Activa el barrido") }
            }
            InputBar(
                draft = draft, onDraft = { draft = it }, onSend = { send(draft) },
                listening = state.listening, level = state.level, voice = state.voiceAvailable,
                onMic = ::talk, onStop = vm::emergencyStop, moving = state.moving != null,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (settings) {
            SettingsSheet(
                state = state,
                onDismiss = { settings = false },
                onConnect = vm::connectRobot,
                onCheckCloud = { vm.checkCloud() },
                onStartBridge = vm::startBridge,
                onStopBridge = vm::stopBridge,
                onSignIn = { (context as? Activity)?.let { vm.signIn(it) } },
                onSignOut = vm::signOut,
            )
        }
    }
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Llm.Yellow)) { append("LLM") }
                    withStyle(SpanStyle(color = Llm.Text)) { append("ia") }
                },
                fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp,
            )
            Text("la exploradora · UPN Taller de Robótica", color = Llm.Muted, fontSize = 12.sp)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, "Ajustes y conexión", tint = Llm.Yellow)
        }
    }
}

@Composable
private fun StatusRow(s: UiState) {
    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (s.robot) {
            LinkState.ON -> StatusChip("Robot", Llm.Ok)
            LinkState.CONNECTING -> StatusChip("Conectando", Llm.Warn, pulsing = true)
            LinkState.OFF -> StatusChip("Robot desconectado", Llm.Danger)
        }
        when (val c = s.cloud) {
            is CloudState.Ok -> StatusChip(if (c.mode == "gemini") "Nube · Gemini" else "Nube · ${c.mode}", Llm.Ok)
            CloudState.Checking -> StatusChip("Nube…", Llm.Warn, pulsing = true)
            is CloudState.Fail -> StatusChip("Nube sin acceso", Llm.Danger)
            CloudState.Unknown -> StatusChip("Nube sin comprobar", Llm.Muted)
        }
        StatusChip(if (s.bridgeOn) "Puente activo" else "Puente apagado", if (s.bridgeOn) Llm.Yellow else Llm.Muted, pulsing = s.bridgeOn)
        StatusChip(s.operator ?: "Sin operador", if (s.operator != null) Llm.Ok else Llm.Muted)
    }
}

@Composable
private fun ModeSwitch(mode: Mode, onChange: (Mode) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Llm.Panel).border(1.dp, Llm.Line, RoundedCornerShape(14.dp)).padding(4.dp)) {
        Mode.entries.forEach { m ->
            val on = m == mode
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) Llm.Yellow else Color.Transparent)
                    .clickable { onChange(m) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(m.label, color = if (on) Llm.Night else Llm.Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun DrivePanel(vm: LlmiaViewModel, s: UiState) {
    val enabled = s.robot == LinkState.ON
    var angle by remember { mutableFloatStateOf(s.servoAngle.toFloat()) }
    LaunchedEffect(s.servoAngle) { angle = s.servoAngle.toFloat() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Radar(s.distance, s.servoAngle, s.sweeping, Modifier.width(132.dp).height(80.dp))
                Spacer(Modifier.height(22.dp))
                Text("Bloqueo a 20 cm", color = Llm.Muted, fontSize = 11.sp)
            }
            DPad(
                moving = s.moving, enabled = enabled, onStart = vm::holdStart, onEnd = vm::holdEnd,
                onStop = vm::emergencyStop, modifier = Modifier.size(188.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Cámara", color = Llm.Muted, fontSize = 12.sp, modifier = Modifier.width(54.dp))
            Slider(
                value = angle, onValueChange = { angle = it }, onValueChangeFinished = { vm.setServo(angle.toInt()) },
                valueRange = 0f..180f, enabled = enabled, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Llm.Yellow, activeTrackColor = Llm.Yellow, inactiveTrackColor = Llm.Line),
            )
            Spacer(Modifier.width(8.dp))
            Text("Barrido", color = Llm.Muted, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = s.sweeping, onCheckedChange = { vm.toggleSweep(it) }, enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = Llm.Night, checkedTrackColor = Llm.Yellow),
            )
        }
    }
}

@Composable
private fun ChatPanel(s: UiState) {
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size, s.messages.lastOrNull()?.text) {
        if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.size - 1)
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.messages, key = { it.id }) { ChatBubble(it) }
    }
}

@Composable
private fun InputBar(
    draft: String, onDraft: (String) -> Unit, onSend: () -> Unit,
    listening: Boolean, level: Float, voice: Boolean, onMic: () -> Unit,
    onStop: () -> Unit, moving: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (moving) Llm.Danger else Llm.Danger.copy(alpha = 0.25f))
                .border(1.dp, Llm.Danger, CircleShape).clickable(onClickLabel = "Detener el robot", onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Text("■", color = Color.White, fontSize = 18.sp) }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = draft, onValueChange = onDraft, singleLine = true, modifier = Modifier.weight(1f),
            placeholder = { Text("Pregunta u orden…", color = Llm.Muted) },
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Llm.Yellow, unfocusedBorderColor = Llm.Line, cursorColor = Llm.Yellow,
                focusedContainerColor = Llm.Panel, unfocusedContainerColor = Llm.Panel,
            ),
            trailingIcon = {
                if (draft.isNotBlank()) IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Enviar orden", tint = Llm.Yellow)
                }
            },
        )
        if (voice) {
            Spacer(Modifier.width(6.dp))
            MicOrb(listening, level, onMic)
        }
    }
}

