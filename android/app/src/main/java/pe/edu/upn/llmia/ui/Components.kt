package pe.edu.upn.llmia.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pe.edu.upn.llmia.ChatMessage
import pe.edu.upn.llmia.Source
import pe.edu.upn.llmia.Speaker
import pe.edu.upn.llmia.command.Direction
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ chips de estado
@Composable
fun StatusChip(label: String, color: Color, pulsing: Boolean = false) {
    val pulse = rememberInfiniteTransition(label = "chip")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Llm.Panel2).border(1.dp, Llm.Line, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = if (pulsing) alpha else 1f)))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Llm.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------ vista en vivo con HUD
@Composable
fun LiveView(
    frame: ImageBitmap?,
    frameCount: Long,
    live: Boolean,
    distance: Int?,
    obstacleAt: Long,
    moving: Direction?,
    progress: Float,
    caption: ChatMessage?,
    onToggleLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scan = rememberInfiniteTransition(label = "scan")
    val scanY by scan.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "y")
    val rec by scan.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "rec")

    // Destello rojo al detectar un obstáculo
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(obstacleAt) {
        while (SystemClock.elapsedRealtime() - obstacleAt < 1_500) { now = SystemClock.elapsedRealtime(); delay(32) }
        now = SystemClock.elapsedRealtime()
    }
    val flash = ((1_200 - (now - obstacleAt)).coerceAtLeast(0) / 1_200f).coerceIn(0f, 1f)

    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(Llm.Panel)
            .border(1.dp, if (flash > 0f) Llm.Danger else Llm.Line, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Vista en vivo de la cámara del robot" },
    ) {
        if (frame != null) {
            AnimatedContent(targetState = frame, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) }, label = "frame") { shownFrame ->
                Image(shownFrame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sin señal de cámara", color = Llm.Muted, fontSize = 14.sp)
                Text("Conecta Carrito_CAM en Ajustes", color = Llm.Muted.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        // HUD: esquinas, mira central, línea de barrido y viñeta
        Canvas(Modifier.fillMaxSize()) {
            val c = Llm.Yellow.copy(alpha = 0.85f)
            val l = 22.dp.toPx(); val m = 12.dp.toPx(); val w = 2.dp.toPx()
            listOf(
                Offset(m, m) to Pair(1f, 1f), Offset(size.width - m, m) to Pair(-1f, 1f),
                Offset(m, size.height - m) to Pair(1f, -1f), Offset(size.width - m, size.height - m) to Pair(-1f, -1f),
            ).forEach { (p, d) ->
                drawLine(c, p, Offset(p.x + l * d.first, p.y), w, StrokeCap.Round)
                drawLine(c, p, Offset(p.x, p.y + l * d.second), w, StrokeCap.Round)
            }
            val ctr = center
            drawCircle(c.copy(alpha = 0.5f), 14.dp.toPx(), ctr, style = Stroke(1.5.dp.toPx()))
            drawLine(c.copy(alpha = 0.5f), Offset(ctr.x - 24.dp.toPx(), ctr.y), Offset(ctr.x - 8.dp.toPx(), ctr.y), 1.5.dp.toPx())
            drawLine(c.copy(alpha = 0.5f), Offset(ctr.x + 8.dp.toPx(), ctr.y), Offset(ctr.x + 24.dp.toPx(), ctr.y), 1.5.dp.toPx())
            if (live && frame != null) {
                val y = size.height * scanY
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Llm.Yellow.copy(alpha = 0.10f), Color.Transparent), y - 30f, y + 30f),
                    Offset(0f, y - 30f), Size(size.width, 60f))
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), ctr, size.maxDimension * 0.75f))
            if (flash > 0f) drawRect(Brush.radialGradient(listOf(Color.Transparent, Llm.Danger.copy(alpha = 0.55f * flash)), ctr, size.maxDimension * 0.6f))
        }

        // EN VIVO
        Row(
            Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onToggleLive)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) Llm.Danger.copy(alpha = rec) else Llm.Muted))
            Spacer(Modifier.width(6.dp))
            Text(if (live) "EN VIVO" else "PAUSA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        // Distancia
        val danger = distance != null && distance <= 20
        Column(
            Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(distance?.let { "$it cm" } ?: "-- cm", color = if (danger) Llm.Danger else Llm.Yellow,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(if (danger) "BLOQUEADO" else "FRENTE", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 1.sp)
        }

        // Movimiento en curso + barra de progreso
        if (moving != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = if (caption != null) 64.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▶ ${moving.label.uppercase()}", color = Llm.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp)
                if (progress >= 0f) {
                    Box(Modifier.padding(top = 4.dp).width(140.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Llm.Yellow))
                    }
                }
            }
        }

        // Subtítulo con la última respuesta de LLMia
        AnimatedContent(
            targetState = caption, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() }, label = "caption",
        ) { msg ->
            if (msg != null) {
                Text(
                    msg.stage?.let { "$it…" } ?: msg.text, maxLines = 2, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ radar de distancia
@Composable
fun Radar(distance: Int?, servoAngle: Int, sweeping: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val shown = distance?.coerceIn(0, 100)
    val blip by animateFloatAsState((shown ?: 100) / 100f, tween(300), label = "blip")
    val danger = distance != null && distance <= 20
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val r = minOf(size.width / 2f, size.height) * 0.95f
            val o = Offset(size.width / 2f, size.height - 4.dp.toPx())
            // anillos: 20 cm (zona de bloqueo), 50 cm y 100 cm
            listOf(0.2f to Llm.Danger, 0.5f to Llm.Line, 1f to Llm.Line).forEach { (k, col) ->
                drawArc(col, 180f, 180f, false, Offset(o.x - r * k, o.y - r * k), Size(2 * r * k, 2 * r * k), style = Stroke(1.5.dp.toPx()))
            }
            drawArc(Llm.Danger.copy(alpha = 0.12f), 180f, 180f, true, Offset(o.x - r * 0.2f, o.y - r * 0.2f), Size(r * 0.4f, r * 0.4f))
            // haz: servo (0 = izquierda, 180 = derecha) o barrido animado
            val deg = if (sweeping) 180f + sweep * 180f else 180f + servoAngle.toFloat()
            val rad = Math.toRadians(deg.toDouble())
            val tip = Offset(o.x + (r * cos(rad)).toFloat(), o.y + (r * sin(rad)).toFloat())
            drawArc(Brush.radialGradient(listOf(Llm.Yellow.copy(alpha = 0.35f), Color.Transparent), o, r), deg - 14f, 14f, true,
                Offset(o.x - r, o.y - r), Size(2 * r, 2 * r))
            drawLine(Llm.Yellow, o, tip, 2.dp.toPx(), StrokeCap.Round)
            // eco frontal del sensor
            if (shown != null) {
                val p = Offset(o.x, o.y - r * blip)
                drawCircle((if (danger) Llm.Danger else Llm.Yellow).copy(alpha = 0.25f), 10.dp.toPx(), p)
                drawCircle(if (danger) Llm.Danger else Llm.Yellow, 4.dp.toPx(), p)
            }
            drawCircle(Llm.Yellow, 4.dp.toPx(), o)
        }
        Text(distance?.let { "$it cm" } ?: "sin lectura", color = if (danger) Llm.Danger else Llm.Text,
            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(y = 16.dp))
    }
}

// ------------------------------------------------------------------ cruceta de desplazamiento
@Composable
fun DPad(
    moving: Direction?,
    enabled: Boolean,
    onStart: (Direction) -> Unit,
    onEnd: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(CircleShape).background(Brush.radialGradient(listOf(Llm.Panel2, Llm.Panel))).border(1.dp, Llm.Line, CircleShape)) {
        ArrowKey(Icons.Rounded.KeyboardArrowUp, Direction.FORWARD, moving, enabled, onStart, onEnd, Modifier.align(Alignment.TopCenter))
        ArrowKey(Icons.Rounded.KeyboardArrowDown, Direction.BACK, moving, enabled, onStart, onEnd, Modifier.align(Alignment.BottomCenter))
        ArrowKey(Icons.Rounded.KeyboardArrowLeft, Direction.LEFT, moving, enabled, onStart, onEnd, Modifier.align(Alignment.CenterStart))
        ArrowKey(Icons.Rounded.KeyboardArrowRight, Direction.RIGHT, moving, enabled, onStart, onEnd, Modifier.align(Alignment.CenterEnd))
        Box(
            Modifier.align(Alignment.Center).size(62.dp).clip(CircleShape).background(Llm.Danger)
                .clickable(onClickLabel = "Detener el robot", onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Text("STOP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp) }
    }
}

@Composable
private fun ArrowKey(
    icon: ImageVector, direction: Direction, moving: Direction?, enabled: Boolean,
    onStart: (Direction) -> Unit, onEnd: () -> Unit, modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val active = moving == direction
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(150), label = "glow")
    Box(
        modifier.padding(6.dp).size(56.dp).scale(1f + 0.08f * glow).clip(CircleShape)
            .background(if (active) Llm.Yellow else Llm.Panel2)
            .border(1.dp, if (active) Llm.Yellow else Llm.Line, CircleShape)
            .semantics { contentDescription = "${direction.label}: mantener pulsado" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart(direction)
                    try {
                        tryAwaitRelease()
                    } finally {
                        onEnd()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Llm.Night else if (enabled) Llm.Yellow else Llm.Muted, modifier = Modifier.size(34.dp))
    }
}

// ------------------------------------------------------------------ micrófono
@Composable
fun MicOrb(listening: Boolean, level: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mic")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring")
    val lvl by animateFloatAsState(level, tween(90), label = "lvl")
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(ring, (ring + 0.5f) % 1f).forEach { k ->
                    drawCircle(Llm.Yellow.copy(alpha = (1f - k) * 0.5f), size.minDimension / 2f * (0.6f + 0.6f * k), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Box(
            Modifier.size(46.dp).scale(1f + 0.18f * lvl).clip(CircleShape)
                .background(if (listening) Llm.Yellow else Llm.Panel2)
                .border(1.dp, Llm.Yellow, CircleShape)
                .clickable(onClickLabel = if (listening) "Dejar de escuchar" else "Hablar con LLMia", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, null, tint = if (listening) Llm.Night else Llm.Yellow)
        }
    }
}

// ------------------------------------------------------------------ conversación
@Composable
fun ChatBubble(msg: ChatMessage) {
    when (msg.from) {
        Speaker.SYSTEM -> Text(
            msg.text, color = if (msg.alert) Llm.Warn else Llm.Muted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Speaker.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(Llm.Yellow)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                if (msg.source == Source.VOICE) Text("🎙 por voz", color = Llm.Night.copy(alpha = 0.6f), fontSize = 10.sp)
                Text(msg.text, color = Llm.Night, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Speaker.LLMIA -> Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(Llm.Panel2)
                    .border(1.dp, if (msg.alert) Llm.Danger.copy(alpha = 0.6f) else Llm.Line, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                    .padding(10.dp),
            ) {
                Text(if (msg.source == Source.PANEL) "LLMia · pedido del panel web" else "LLMia",
                    color = Llm.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                msg.image?.let {
                    Image(it, "Foto que analizó Gemini", Modifier.padding(top = 6.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop)
                }
                if (msg.stage != null) ThinkingLine(msg.stage) else
                    Text(msg.text, color = Llm.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ThinkingLine(stage: String) {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "p")
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(Modifier.padding(end = 4.dp).size(7.dp).scale(if (on) 1.3f else 1f).clip(CircleShape)
                .background(if (on) Llm.Yellow else Llm.Muted))
        }
        Spacer(Modifier.width(6.dp))
        Text(stage, color = Llm.Muted, fontSize = 13.sp)
    }
}

@Composable
fun QuickChip(text: String, onClick: () -> Unit) {
    Text(
        text, color = Llm.Text, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Llm.Panel2).border(1.dp, Llm.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
