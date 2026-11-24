package com.pilland.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// =====================================================================
// Μεγέθη (dp) – κοινά για UI και για το παράθυρο του service
// =====================================================================

fun islandSize(d: Display, cfg: Settings, reply: Boolean): Pair<Float, Float> {
    val h = cfg.pillHeight
    val w = cfg.expandedWidth
    val item = d.item
    return when (d.mode) {
        Mode.HIDDEN, Mode.IDLE -> h to h
        Mode.COMPACT, Mode.WIDE -> (if (item == null) h else compactWidthFor(item, cfg)) to h
        Mode.PEEK -> w to (h / 2f + 4f + 62f)
        Mode.PANEL -> w to (h + 172f)
        Mode.SHORTCUTS -> shortcutsCardWidth(cfg) to (h + max(1, visibleShortcuts(cfg).size) * SC_ROW + 8f)
        Mode.EXPANDED -> w to (h + when (item?.kind) {
            Kind.MEDIA -> 156f
            Kind.CALL -> 106f
            Kind.TIMER, Kind.STOPWATCH -> 106f
            Kind.TORCH -> 96f
            Kind.NOTIFICATION -> 108f + (if (reply) 52f else 0f)
            Kind.PROGRESS, Kind.LIVE -> 102f + (if (reply) 52f else 0f)
            else -> 80f
        })
    }
}

/** Πλάτος (dp) του περιεχομένου δεξιά της κάμερας. */
private fun rightContentWidth(item: IslandItem, cfg: Settings): Float {
    val h = cfg.pillHeight
    val ts = cfg.textScale
    fun txt(chars: Int, sp: Float) = chars * sp * 0.62f * ts
    return when (item.kind) {
        Kind.MEDIA -> if (cfg.visualizer) 30f else 20f
        Kind.TIMER, Kind.STOPWATCH -> {
            val base = item.chronoBase ?: 0L
            val ms = if (item.countDown) base - System.currentTimeMillis() else System.currentTimeMillis() - base
            txt(if (ms >= 3_600_000L) 7 else 5, 15f)
        }
        Kind.CALL -> txt(5, 15f)
        Kind.TORCH -> txt(5, 16f)
        Kind.PROGRESS, Kind.LIVE -> h - 12f
        Kind.CHARGING, Kind.LOW_BATTERY -> txt(4, 14f) + 6f + 24f
        Kind.RINGER -> txt(item.title.length, 14f)
        Kind.DND -> txt(item.subtitle.length, 13f)
        Kind.BLUETOOTH -> if (item.value != null) txt(4, 13f) + 6f + 24f else txt(item.subtitle.length, 12f)
        Kind.UNLOCK -> 0f
        else -> min(txt(item.title.length, 13f), 120f)
    }
}

fun isAsym(cfg: Settings) = cfg.asymCompact

/** Μισό κενό γύρω από την τρύπα της κάμερας στο ασύμμετρο νησάκι. */
fun camGapHalf(cfg: Settings) = cfg.cameraGap

/** Απόσταση (dp) αριστερής / δεξιάς άκρης από το κέντρο της κάμερας. */
fun compactExtents(item: IslandItem, cfg: Settings): Pair<Float, Float> {
    val h = cfg.pillHeight
    val left = h - 10f
    val right = rightContentWidth(item, cfg) + 6f
    if (isAsym(cfg)) {
        val g = camGapHalf(cfg)
        val l = 5f + left + g
        val r = if (cfg.autoWidth) max(g + right + 5f, h / 2f + 4f) else max(cfg.compactWidth - l, h / 2f + 4f)
        return l to r
    }
    val w = if (cfg.autoWidth) (2 * max(left, right) + cfg.cameraGap * 2f + 10f).coerceIn(h * 2.4f, 380f) else cfg.compactWidth
    return w / 2f to w / 2f
}

fun compactWidthFor(item: IslandItem, cfg: Settings): Float = compactExtents(item, cfg).let { it.first + it.second }

/** Μετατόπιση (dp) του κέντρου του νησιού σε σχέση με την κάμερα. */
fun islandOffsetX(d: Display, cfg: Settings): Float {
    val it = d.item ?: return 0f
    if (d.mode != Mode.COMPACT && d.mode != Mode.WIDE) return 0f
    val (l, r) = compactExtents(it, cfg)
    return (r - l) / 2f
}

/** Περιοχή αφής (dp, ως προς το κέντρο της κάμερας): αριστερά, δεξιά, ύψος. */
fun islandTouchRect(d: Display, cfg: Settings, reply: Boolean): Triple<Float, Float, Float> {
    val (w, h) = islandSize(d, cfg, reply)
    val ox = islandOffsetX(d, cfg)
    val sec = if (d.secondary != null && cfg.splitBubble && (d.mode == Mode.COMPACT || d.mode == Mode.WIDE)) cfg.pillHeight + 6f else 0f
    return Triple(ox - w / 2f - 6f, ox + w / 2f + 6f + sec, h)
}

fun islandWindowSize(d: Display, cfg: Settings, reply: Boolean): Pair<Float, Float> {
    val (w, h) = islandSize(d, cfg, reply)
    val pad = IslandService.WINDOW_PAD
    val side = if (d.secondary != null && cfg.splitBubble) (cfg.pillHeight + 10f) * 2 else 0f
    val shift = abs(islandOffsetX(d, cfg)) * 2f
    return (w + shift + side + pad * 2) to (h + pad * 2)
}

fun accentFor(item: IslandItem?, cfg: Settings, media: MediaInfo?): Color {
    if (!cfg.accentAuto) return Color(cfg.accentColor)
    if (item?.kind == Kind.MEDIA) {
        val a = media?.accent
        if (a != null) return a
    }
    return item?.accent ?: Color(cfg.accentColor)
}

private fun Settings.ts(v: Float): TextUnit = (v * textScale).sp

// =====================================================================
// Ρίζα overlay
// =====================================================================

@Composable
fun OverlayRoot() {
    val s by Island.state.collectAsState()
    val cfg by Prefs.state.collectAsState()
    val media by Island.media.collectAsState()
    val d = Island.resolve(s, cfg)
    val shift by Island.windowShift.collectAsState()
    Box(
        Modifier.fillMaxSize().padding(top = IslandService.WINDOW_PAD.dp).offset(x = shift.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        IslandView(d, cfg, s, media, interactive = true)
    }
}

// =====================================================================
// Το νησάκι
// =====================================================================

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun IslandView(
    d: Display,
    cfg: Settings,
    s: IslandState,
    media: MediaInfo?,
    interactive: Boolean,
) {
    val (stiff, damp) = cfg.spring()
    val replyOpen = s.replyKey != null && (s.replyKey == d.item?.notifKey || s.replyKey == d.item?.id)
    val (tw, th) = islandSize(d, cfg, replyOpen)
    val small = d.mode == Mode.COMPACT || d.mode == Mode.WIDE || d.mode == Mode.IDLE || d.mode == Mode.HIDDEN
    val targetRadius = if (small) th / 2f else min(34f, th / 2f)
    val radiusPct = (cfg.cornerPercent / 100f).coerceIn(0.08f, 1f)

    val w by animateDpAsState(tw.dp, spring(damp, stiff), label = "w")
    val h by animateDpAsState(th.dp, spring(damp, stiff), label = "h")
    val r by animateDpAsState((targetRadius * radiusPct).dp, spring(damp, stiff), label = "r")
    // Χωρίς υπέρβαση: το κέντρο πάει ομαλά στην κάμερα, δεν «πετάγεται» αριστερά/δεξιά
    val ox by animateDpAsState(islandOffsetX(d, cfg).dp, spring(1f, stiff), label = "ox")
    val invisible = d.mode == Mode.HIDDEN || (d.mode == Mode.IDLE && cfg.idleStyle == 0)
    val alpha by animateFloatAsState(if (invisible) 0f else 1f, tween((260 / cfg.animSpeed).toInt()), label = "a")
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.5f, 700f), label = "p")
    val elev by animateDpAsState(if (small) 4.dp else 18.dp, label = "e")

    val accent = accentFor(d.item, cfg, media)
    val bg = Color(cfg.bgColor).copy(alpha = cfg.bgAlpha)
    val fg = if (Color(cfg.bgColor).luminance() > 0.5f) Color.Black else Color.White
    val shape = RoundedCornerShape(r)

    // Παλμός του glow σε κάθε νέα "στιγμή"
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(d.item?.id, d.mode) {
        if (d.mode != Mode.IDLE && d.mode != Mode.HIDDEN) {
            pulse.snapTo(2.2f)
            pulse.animateTo(1f, tween((900 / cfg.animSpeed).toInt()))
        }
    }

    Box(contentAlignment = Alignment.TopCenter) {
        // δευτερεύουσα φούσκα (split)
        val sec = d.secondary
        val showSec = sec != null && cfg.splitBubble && (d.mode == Mode.COMPACT || d.mode == Mode.WIDE)
        val secOffset by animateDpAsState(if (showSec) (islandOffsetX(d, cfg) + tw / 2f + 6f + cfg.pillHeight / 2f).dp else 0.dp, spring(1f, stiff), label = "so")
        AnimatedVisibility(
            visible = showSec,
            enter = scaleIn(spring(damp, stiff), initialScale = 0.2f) + fadeIn(),
            exit = scaleOut(targetScale = 0.2f) + fadeOut(),
            modifier = Modifier.offset(x = secOffset)
        ) {
            val si = sec
            Box(
                Modifier
                    .size(cfg.pillHeight.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .islandGlow(cfg.glow, accentFor(si, cfg, media), cfg.glowWidth.dp, false, (cfg.pillHeight / 2).dp, 1f)
                    .then(if (interactive && si != null) Modifier.clickable { Util.tick(); Island.expand(si.id) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (si != null) SmallBadge(si, media, accentFor(si, cfg, media), fg, (cfg.pillHeight - 10f).dp)
            }
        }

        Box(
            Modifier
                .offset(x = ox)
                .graphicsLayer {
                    scaleX = pressScale; scaleY = pressScale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .then(if (cfg.shadow && !invisible) Modifier.shadow(elev, shape, ambientColor = accent, spotColor = accent) else Modifier)
                .size(w, h)
                .clip(shape)
                .background(bg)
                .islandGlow(cfg.glow && d.mode != Mode.IDLE, accent, cfg.glowWidth.dp, cfg.glowAnimated, r, min(1f, 0.55f + pulse.value / 2.5f))
                .then(if (interactive) Modifier.islandGestures(d, cfg) { pressed = it } else Modifier)
        ) {
            AnimatedContent(
                targetState = d,
                contentKey = { "${it.mode}:${it.item?.id}" },
                transitionSpec = { islandTransition(cfg) },
                contentAlignment = Alignment.TopCenter,
                label = "content",
                modifier = Modifier.fillMaxSize()
            ) { target ->
                val blur by transition.animateDp(label = "blur") { st -> if (st == EnterExitState.Visible) 0.dp else 10.dp }
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (cfg.blurTransitions) Modifier.blur(blur) else Modifier)
                ) {
                    IslandContent(target, cfg, s, media, accent, fg)
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Display>.islandTransition(cfg: Settings): ContentTransform {
    val (stiff, damp) = cfg.spring()
    val sp = cfg.animSpeed.coerceAtLeast(0.3f)
    val inMs = (260 / sp).toInt()
    val outMs = (140 / sp).toInt()
    val delayMs = (70 / sp).toInt()
    // Όταν αλλάζει το μέγεθος (μικρό ↔ μεγάλο), κινείται ήδη το ίδιο το νησάκι:
    // το περιεχόμενο απλώς ξεπροβάλλει από την κάμερα, χωρίς ζουμ που ξεχειλίζει.
    fun big(m: Mode) = m == Mode.EXPANDED || m == Mode.PEEK || m == Mode.PANEL || m == Mode.SHORTCUTS
    if (big(initialState.mode) != big(targetState.mode)) {
        val top = TransformOrigin(0.5f, 0f)
        return (fadeIn(tween((240 / sp).toInt(), (90 / sp).toInt())) +
            scaleIn(tween((320 / sp).toInt(), (60 / sp).toInt(), FastOutSlowInEasing), initialScale = 0.9f, transformOrigin = top)) togetherWith
            fadeOut(tween((90 / sp).toInt())) using SizeTransform(clip = true) { _, _ -> snap() }
    }
    return when (cfg.transitionStyle) {
        // Slide: το νέο έρχεται από πάνω, το παλιό φεύγει προς τα κάτω
        1 -> (slideInVertically(spring(0.8f, (stiff * 0.8f).coerceAtLeast(120f))) { -it } + fadeIn(tween(inMs))) togetherWith
            (slideOutVertically(tween((outMs * 1.8f).toInt())) { it } + fadeOut(tween((outMs * 1.8f).toInt())))
        // Fade: αργό, απαλό crossfade χωρίς κίνηση
        2 -> fadeIn(tween((520 / sp).toInt(), (120 / sp).toInt())) togetherWith fadeOut(tween((380 / sp).toInt()))
        // Pop: «σκάει» από μεγάλο με έντονο ελατήριο
        3 -> (scaleIn(spring(0.42f, (stiff * 1.2f).coerceAtLeast(200f)), initialScale = 1.6f) + fadeIn(tween(inMs / 2))) togetherWith
            (scaleOut(tween(outMs), targetScale = 0.4f) + fadeOut(tween(outMs)))
        // Zoom: μεγαλώνει από το κέντρο
        else -> (scaleIn(spring(damp, stiff), initialScale = 0.45f) + fadeIn(tween(inMs, delayMs))) togetherWith
            (scaleOut(tween(outMs), targetScale = 1.2f) + fadeOut(tween(outMs)))
    }.using(SizeTransform(clip = true) { _, _ -> snap() })
}

// =====================================================================
// Χειρονομίες
// =====================================================================

private fun runGesture(action: Int, d: Display) {
    when (action) {
        GestureAction.TOGGLE_EXPAND -> Island.toggleExpand()
        GestureAction.OPEN_APP -> { Util.openApp(d.item); Island.collapse() }
        GestureAction.QUICK_PANEL -> if (d.mode == Mode.PANEL) Island.closePanel() else Island.openPanel()
        GestureAction.SHORTCUTS -> if (d.mode == Mode.SHORTCUTS) Island.closePanel() else Island.openShortcuts()
        GestureAction.PLAY_PAUSE -> Island.playPause()
        GestureAction.DISMISS -> Island.dismissCurrent()
        GestureAction.NEXT_TRACK -> Island.next()
        GestureAction.PREV_TRACK -> Island.prev()
    }
}

private fun Modifier.islandGestures(d: Display, cfg: Settings, onPressed: (Boolean) -> Unit): Modifier = composed {
    val dRef = remember { mutableStateOf(d) }
    dRef.value = d
    val cRef = remember { mutableStateOf(cfg) }
    cRef.value = cfg
    this
        .pointerInput(cfg.gestureDoubleTap) {
            val dblFn: (Offset) -> Unit = {
                Util.tick(); runGesture(cRef.value.gestureDoubleTap, dRef.value)
            }
            val dbl: ((Offset) -> Unit)? = if (cfg.gestureDoubleTap != GestureAction.NONE) dblFn else null
            detectTapGestures(
                onPress = {
                    onPressed(true)
                    tryAwaitRelease()
                    onPressed(false)
                },
                onDoubleTap = dbl,
                onLongPress = {
                    Util.vibrate(longArrayOf(0, 18))
                    runGesture(cRef.value.gestureLong, dRef.value)
                },
                onTap = {
                    Util.tick()
                    val dd = dRef.value
                    // Σε ανοιχτή κάρτα, το tap ανοίγει την εφαρμογή
                    if (dd.mode == Mode.EXPANDED || dd.mode == Mode.PEEK) {
                        if (dd.mode == Mode.PEEK) Island.expand(dd.item?.id ?: return@detectTapGestures)
                        else { Util.openApp(dd.item); Island.collapse() }
                    } else runGesture(cRef.value.gestureTap, dd)
                }
            )
        }
        .pointerInput(Unit) {
            var dx = 0f
            var dy = 0f
            detectDragGestures(
                onDragStart = { dx = 0f; dy = 0f },
                onDragEnd = {
                    val dd = dRef.value
                    val c = cRef.value
                    val th = 36.dp.toPx()
                    when {
                        abs(dy) > abs(dx) && dy > th -> {
                            Util.tick()
                            if (dd.mode == Mode.COMPACT || dd.mode == Mode.WIDE || dd.mode == Mode.PEEK || dd.mode == Mode.IDLE) runGesture(c.gestureDown, dd)
                        }
                        abs(dy) > abs(dx) && dy < -th -> {
                            Util.tick()
                            when (dd.mode) {
                                Mode.EXPANDED -> Island.collapse()
                                Mode.PANEL, Mode.SHORTCUTS -> Island.closePanel()
                                else -> Island.dismissCurrent()
                            }
                        }
                        abs(dx) > th -> { Util.tick(); runGesture(c.gestureSide, dd) }
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    dx += amount.x; dy += amount.y
                }
            )
        }
}

// =====================================================================
// Glow
// =====================================================================

fun Modifier.islandGlow(enabled: Boolean, color: Color, width: Dp, animated: Boolean, radius: Dp, intensity: Float): Modifier = composed {
    if (!enabled || width.value <= 0f) return@composed this
    val phase = if (animated) {
        val inf = rememberInfiniteTransition(label = "glow")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "ph").value
    } else -1f
    this.drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val brush = if (phase >= 0f) {
            val colors = (0..12).map { i ->
                val t = i / 12f
                val a = (0.12f + 0.88f * ((cos(2 * PI * (t - phase)).toFloat() + 1f) / 2f).pow(3)) * intensity
                color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f))
            }
            Brush.sweepGradient(colors, center)
        } else SolidColor(color.copy(alpha = 0.85f * intensity))
        val rr = (radius.toPx() - stroke / 2).coerceAtLeast(0f)
        drawRoundRect(
            brush = brush,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(rr, rr),
            style = Stroke(stroke)
        )
    }
}

// =====================================================================
// Περιεχόμενο
// =====================================================================

@Composable
private fun IslandContent(d: Display, cfg: Settings, s: IslandState, media: MediaInfo?, accent: Color, fg: Color) {
    val item = d.item
    when (d.mode) {
        Mode.HIDDEN -> {}
        Mode.IDLE -> IdleContent(cfg, s, fg)
        Mode.COMPACT, Mode.WIDE -> if (item != null) CompactContent(item, cfg, media, accent, fg, wide = d.mode == Mode.WIDE)
        Mode.PEEK -> if (item != null) PeekContent(item, cfg, accent, fg)
        Mode.PANEL -> PanelContent(cfg, s, accent, fg)
        Mode.SHORTCUTS -> ShortcutsContent(cfg, fg, accent)
        Mode.EXPANDED -> if (item != null) ExpandedContent(item, cfg, s, media, accent, fg)
    }
}

@Composable
private fun IdleContent(cfg: Settings, s: IslandState, fg: Color) {
    if (cfg.idleStyle != 2) return
    val col = when {
        s.charging -> Color(0xFF34C759)
        s.battery <= cfg.lowBatteryLevel -> Color(0xFFFF453A)
        else -> fg.copy(alpha = 0.85f)
    }
    val sweep by animateFloatAsState(s.battery / 100f * 360f, tween(900), label = "bat")
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val st = 2.4.dp.toPx()
        drawArc(fg.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(st))
        drawArc(col, -90f, sweep, false, style = Stroke(st, cap = StrokeCap.Round))
    }
}

/** Απόσταση περιεχομένου από τις άκρες στις μεγάλες κάρτες (ακολουθεί τη γωνία). */
private val EDGE = 24.dp

/** Ζώνη στο ύψος της κάμερας: αριστερά & δεξιά από την τρύπα. */
@Composable
private fun CameraBand(
    height: Dp,
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
    side: Dp = EDGE,
) {
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = side),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start, content = left)
        Spacer(Modifier.width((Prefs.value.cameraGap * 2f).dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = right)
    }
}

private fun iconFor(item: IslandItem, media: MediaInfo?): ImageVector = when (item.kind) {
    Kind.MEDIA -> Icons.Rounded.MusicNote
    Kind.CALL -> Icons.Rounded.Call
    Kind.TIMER -> Icons.Rounded.HourglassTop
    Kind.STOPWATCH -> Icons.Rounded.Timer
    Kind.TORCH -> if (item.flag) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff
    Kind.CHARGING -> Icons.Rounded.Bolt
    Kind.LOW_BATTERY -> Icons.Rounded.BatteryAlert
    Kind.RINGER -> when (item.value) {
        0 -> Icons.Rounded.NotificationsOff
        1 -> Icons.Rounded.Vibration
        else -> Icons.Rounded.NotificationsActive
    }
    Kind.DND -> Icons.Rounded.Bedtime
    Kind.BLUETOOTH -> Icons.Rounded.Headphones
    Kind.UNLOCK -> Icons.Rounded.LockOpen
    Kind.NOTIFICATION, Kind.PROGRESS, Kind.LIVE -> Icons.Rounded.Notifications
    Kind.INFO -> Icons.Rounded.CheckCircle
}

/** Κυκλικό εικονίδιο (σαν το σχέδιο: μπλε φακός, μωβ κλεψύδρα…). */
@Composable
private fun SmallBadge(item: IslandItem, media: MediaInfo?, accent: Color, fg: Color, size: Dp) {
    val art = if (item.kind == Kind.MEDIA) media?.art ?: item.icon else (item.icon ?: item.appIcon)
    if (art != null && (item.kind == Kind.MEDIA || item.kind == Kind.NOTIFICATION || item.kind == Kind.PROGRESS || item.kind == Kind.LIVE || item.kind == Kind.INFO || item.kind == Kind.CALL && item.icon != null)) {
        Image(
            art, null,
            modifier = Modifier.size(size).clip(if (item.kind == Kind.MEDIA) RoundedCornerShape(size * 0.28f) else CircleShape),
            contentScale = ContentScale.Crop
        )
        return
    }
    when (item.kind) {
        Kind.RINGER -> {
            val rot = remember { Animatable(0f) }
            LaunchedEffect(item.id, item.value, item.postedAt) {
                rot.snapTo(0f)
                for (a in listOf(-22f, 20f, -16f, 12f, -6f, 0f)) rot.animateTo(a, tween(70))
            }
            BadgeCircle(accent, size) { Icon(iconFor(item, media), null, tint = Color.White, modifier = Modifier.size(size * 0.62f).rotate(rot.value)) }
        }
        Kind.UNLOCK -> {
            var open by remember { mutableStateOf(false) }
            LaunchedEffect(item.postedAt) { open = false; delay(220); open = true }
            val sc by animateFloatAsState(if (open) 1f else 0.82f, spring(0.4f, 500f), label = "u")
            Icon(if (open) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null, tint = fg, modifier = Modifier.size(size * 0.8f).scale(sc))
        }
        Kind.CHARGING -> {
            val inf = rememberInfiniteTransition(label = "chg")
            val k by inf.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
            BadgeCircle(if (item.flag) accent else Color(0xFF636366), size) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(size * 0.66f).scale(if (item.flag) k else 1f)) }
        }
        Kind.CALL -> {
            val inf = rememberInfiniteTransition(label = "call")
            val k by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
            BadgeCircle(accent, size) { Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(size * 0.58f).scale(k)) }
        }
        else -> BadgeCircle(accent, size) { Icon(iconFor(item, media), null, tint = Color.White, modifier = Modifier.size(size * 0.6f)) }
    }
}

@Composable
private fun BadgeCircle(color: Color, size: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.42f))
            .background(Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.75f, green = color.green * 0.75f, blue = color.blue * 0.75f)))),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun rememberNow(intervalMs: Long = 250): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}

@Composable
private fun Visualizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "viz")
    val durs = listOf(430, 610, 380, 520, 470)
    val bars = durs.mapIndexed { i, d ->
        inf.animateFloat(0.15f, 1f, infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b$i")
    }
    val amp by animateFloatAsState(if (playing) 1f else 0.08f, tween(400), label = "amp")
    Canvas(modifier.size(30.dp, 18.dp)) {
        val n = bars.size
        val bw = size.width / (n * 1.8f)
        val gap = (size.width - bw * n) / (n - 1)
        for (i in 0 until n) {
            val bh = size.height * (0.18f + 0.82f * bars[i].value * amp)
            val x = i * (bw + gap)
            drawRoundRect(
                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f))),
                topLeft = Offset(x, (size.height - bh) / 2f),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2, bw / 2)
            )
        }
    }
}

@Composable
private fun ProgressRing(progress: Float?, indeterminate: Boolean, color: Color, fg: Color, size: Dp, showText: Boolean, cfg: Settings) {
    val p by animateFloatAsState(progress ?: 0f, spring(1f, 200f), label = "pr")
    val inf = rememberInfiniteTransition(label = "ind")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "r")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 2.6.dp.toPx()
            val inset = st / 2
            val arcSize = Size(this.size.width - st, this.size.height - st)
            drawArc(fg.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(st))
            if (indeterminate || progress == null) drawArc(color, rot, 90f, false, Offset(inset, inset), arcSize, style = Stroke(st, cap = StrokeCap.Round))
            else drawArc(color, -90f, p * 360f, false, Offset(inset, inset), arcSize, style = Stroke(st, cap = StrokeCap.Round))
        }
        if (showText && progress != null && !indeterminate) Text("${(p * 100).toInt()}", color = fg, fontSize = cfg.ts(8.5f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniBattery(level: Int, color: Color, fg: Color) {
    val lv by animateFloatAsState(level / 100f, tween(900, easing = FastOutSlowInEasing), label = "mb")
    Canvas(Modifier.size(24.dp, 12.dp)) {
        val st = 1.2.dp.toPx()
        val bodyW = size.width - 3.dp.toPx()
        drawRoundRect(fg.copy(alpha = 0.5f), Offset(st / 2, st / 2), Size(bodyW - st, size.height - st), CornerRadius(3.dp.toPx()), style = Stroke(st))
        drawRoundRect(fg.copy(alpha = 0.5f), Offset(bodyW + 0.5.dp.toPx(), size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1.dp.toPx()))
        val pad = 2.2.dp.toPx()
        drawRoundRect(color, Offset(pad, pad), Size((bodyW - pad * 2) * lv, size.height - pad * 2), CornerRadius(1.6.dp.toPx()))
    }
}

@Composable
private fun CompactContent(item: IslandItem, cfg: Settings, media: MediaInfo?, accent: Color, fg: Color, wide: Boolean) {
    val h = cfg.pillHeight.dp
    val badge = h - 10.dp
    val asym = isAsym(cfg)
    val g = camGapHalf(cfg).dp
    val rightContent: @Composable RowScope.() -> Unit = {
            when (item.kind) {
                Kind.MEDIA -> if (cfg.visualizer) Visualizer(media?.playing ?: item.flag, accent) else
                    Icon(if (media?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = fg, modifier = Modifier.size(20.dp))
                Kind.TIMER, Kind.STOPWATCH, Kind.CALL -> {
                    if (item.chronoBase != null) {
                        val now = rememberNow()
                        Text(
                            Util.chronoText(item, now), color = if (item.kind == Kind.CALL) accent else fg,
                            fontSize = cfg.ts(15f), fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    } else Text(if (item.kind == Kind.CALL) "Κλήση" else "", color = accent, fontSize = cfg.ts(14f), fontWeight = FontWeight.SemiBold)
                }
                Kind.TORCH -> Text("Φακός", color = fg, fontSize = cfg.ts(16f), fontWeight = FontWeight.SemiBold)
                Kind.PROGRESS, Kind.LIVE -> ProgressRing(item.progress, item.indeterminate, accent, fg, h - 12.dp, true, cfg)
                Kind.CHARGING, Kind.LOW_BATTERY -> {
                    val lv = item.value ?: 0
                    Text("$lv%", color = if (item.kind == Kind.LOW_BATTERY) accent else fg, fontSize = cfg.ts(14f), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    MiniBattery(lv, accent, fg)
                }
                Kind.RINGER -> Text(item.title, color = accent, fontSize = cfg.ts(14f), fontWeight = FontWeight.SemiBold)
                Kind.DND -> Text(item.subtitle, color = if (item.flag) accent else fg.copy(alpha = 0.7f), fontSize = cfg.ts(13f), fontWeight = FontWeight.SemiBold)
                Kind.BLUETOOTH -> {
                    val v = item.value
                    if (v != null) { Text("$v%", color = fg, fontSize = cfg.ts(13f), fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp)); MiniBattery(v, Color(0xFF34C759), fg) }
                    else Text(item.subtitle, color = accent, fontSize = cfg.ts(12f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Kind.UNLOCK -> {}
                else -> Text(item.title, color = fg, fontSize = cfg.ts(13f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
    }
    if (asym) {
        // εικονίδιο | κάμερα | περιεχόμενο → μεγαλώνει μόνο δεξιά
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(5.dp))
            SmallBadge(item, media, accent, fg, badge)
            Spacer(Modifier.width(g * 2))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start, content = rightContent)
        }
    } else {
        CameraBand(h, side = 5.dp, left = { SmallBadge(item, media, accent, fg, badge) }, right = rightContent)
    }
}

@Composable
private fun PeekContent(item: IslandItem, cfg: Settings, accent: Color, fg: Color) {
    val h = cfg.pillHeight.dp
    // Χωρίς ετικέτα: το εικονίδιο της εφαρμογής λέει από πού ήρθε.
    // Το κείμενο ξεκινά κάτω από την κάμερα, το εικονίδιο πιάνει όλο το ύψος αριστερά.
    Row(Modifier.fillMaxSize().padding(start = 18.dp, end = EDGE), verticalAlignment = Alignment.CenterVertically) {
        NotifIcon(item, accent, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = h / 2 + 4.dp)) {
            run {
                Text(item.title, color = fg, fontSize = cfg.ts(14.5f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, color = fg.copy(alpha = 0.72f), fontSize = cfg.ts(13f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NotifIcon(item: IslandItem, accent: Color, size: Dp) {
    val big = item.icon
    val app = item.appIcon
    Box(Modifier.size(size)) {
        when {
            big != null -> Image(big, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
            app != null -> Image(app, null, Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)), contentScale = ContentScale.Crop)
            else -> BadgeCircle(accent, size) { Icon(Icons.Rounded.Notifications, null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
        }
        // Φωτογραφία επαφής + μικρό σήμα εφαρμογής στη γωνία
        if (big != null && app != null) {
            val b = size * 0.42f
            Image(
                app, null,
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(b)
                    .clip(RoundedCornerShape(b * 0.3f))
                    .background(Color.Black),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ActionChips(item: IslandItem, accent: Color, fg: Color, cfg: Settings, call: Boolean = false) {
    if (item.actions.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (call) 12.dp else 8.dp)) {
        item.actions.forEachIndexed { idx, a ->
            val color = when {
                call && item.actions.size >= 2 && idx == 0 -> Color(0xFFFF453A)
                call && idx == item.actions.lastIndex -> Color(0xFF34C759)
                a.label == "Άνοιγμα" -> accent
                else -> fg.copy(alpha = 0.14f)
            }
            val strong = call || a.label == "Άνοιγμα"
            val bh = if (call) 48.dp else 36.dp
            Row(
                Modifier
                    .weight(1f)
                    .height(bh)
                    .clip(RoundedCornerShape(bh / 2))
                    .background(color)
                    .clickable { Util.tick(); Island.handleAction(a, item) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (call) {
                    val decline = item.actions.size >= 2 && idx == 0
                    Icon(if (decline) Icons.Rounded.CallEnd else Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(a.label, color = if (strong) Color.White else fg, fontSize = cfg.ts(if (call) 15f else 13f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ExpandedContent(item: IslandItem, cfg: Settings, s: IslandState, media: MediaInfo?, accent: Color, fg: Color) {
    val h = cfg.pillHeight.dp
    Column(Modifier.fillMaxSize()) {
        CameraBand(
            h,
            left = {
                val quiet = item.kind == Kind.NOTIFICATION || item.kind == Kind.CALL || item.kind == Kind.PROGRESS || item.kind == Kind.LIVE
                if (!quiet) Text(
                    if (item.kind == Kind.MEDIA) media?.appName ?: item.appName else item.appName.ifEmpty { kindLabel(item.kind) },
                    color = fg.copy(alpha = 0.65f), fontSize = cfg.ts(11.5f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            right = {
                if (item.kind == Kind.MEDIA) Visualizer(media?.playing == true, accent)
                else if (item.kind != Kind.NOTIFICATION && item.kind != Kind.CALL) Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            }
        )
        val callCard = item.kind == Kind.CALL && item.actions.isNotEmpty()
        val sideInset = if (callCard) 14.dp else EDGE
        Column(Modifier.fillMaxWidth().padding(start = sideInset, end = sideInset, bottom = 14.dp)) {
            when (item.kind) {
                Kind.MEDIA -> MediaExpanded(item, cfg, media, accent, fg)
                Kind.CALL -> {
                    Row(Modifier.padding(horizontal = EDGE - sideInset), verticalAlignment = Alignment.CenterVertically) {
                        SmallBadge(item, media, accent, fg, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = fg, fontSize = cfg.ts(16f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = if (item.chronoBase != null) Util.chronoText(item, rememberNow()) else item.subtitle
                            Text(sub, color = accent, fontSize = cfg.ts(13f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionChips(item, accent, fg, cfg, call = true)
                }
                Kind.TIMER, Kind.STOPWATCH -> {
                    val now = rememberNow(200)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallBadge(item, media, accent, fg, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title.ifEmpty { kindLabel(item.kind) }, color = fg.copy(alpha = 0.7f), fontSize = cfg.ts(12f), maxLines = 1)
                            Text(
                                Util.chronoText(item, now), color = accent, fontSize = cfg.ts(30f), fontWeight = FontWeight.Light,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ActionChips(item, accent, fg, cfg)
                }
                Kind.TORCH -> TorchExpanded(item, cfg, accent, fg)
                Kind.NOTIFICATION, Kind.PROGRESS, Kind.LIVE -> NotificationExpanded(item, cfg, s, accent, fg)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallBadge(item, media, accent, fg, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = fg, fontSize = cfg.ts(16f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = when (item.kind) {
                                Kind.CHARGING, Kind.LOW_BATTERY -> "${item.value ?: s.battery}% μπαταρία"
                                Kind.BLUETOOTH -> item.subtitle + (item.value?.let { " · $it%" } ?: "")
                                else -> item.subtitle
                            }
                            if (sub.isNotEmpty()) Text(sub, color = fg.copy(alpha = 0.7f), fontSize = cfg.ts(13f), maxLines = 2)
                        }
                        if (item.kind == Kind.CHARGING || item.kind == Kind.LOW_BATTERY) MiniBattery(item.value ?: s.battery, accent, fg)
                    }
                }
            }
        }
    }
}

private fun kindLabel(k: Kind) = when (k) {
    Kind.MEDIA -> "Μουσική"; Kind.CALL -> "Κλήση"; Kind.TIMER -> "Αντίστροφη μέτρηση"; Kind.STOPWATCH -> "Χρονόμετρο"
    Kind.PROGRESS -> "Πρόοδος"; Kind.LIVE -> "Live"; Kind.CHARGING -> "Φόρτιση"; Kind.LOW_BATTERY -> "Μπαταρία"
    Kind.TORCH -> "Φακός"; Kind.NOTIFICATION -> "Ειδοποίηση"; Kind.RINGER -> "Ήχος"; Kind.DND -> "Μην ενοχλείτε"
    Kind.BLUETOOTH -> "Bluetooth"; Kind.UNLOCK -> "Ξεκλείδωμα"; Kind.INFO -> "Pilland"
}

@Composable
private fun MediaExpanded(item: IslandItem, cfg: Settings, media: MediaInfo?, accent: Color, fg: Color) {
    val m = media
    Row(verticalAlignment = Alignment.CenterVertically) {
        val art = m?.art ?: item.icon
        if (art != null) Image(art, null, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        else BadgeCircle(accent, 58.dp) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m?.title ?: item.title, color = fg, fontSize = cfg.ts(16f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m?.artist ?: item.subtitle, color = fg.copy(alpha = 0.65f), fontSize = cfg.ts(13.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(10.dp))
    val now = rememberNow(500)
    val dur = m?.duration ?: 0L
    val pos = remember(now, m) { m?.currentPosition() ?: 0L }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val frac = if (dragging) dragFrac else if (dur > 0) pos.toFloat() / dur else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(dur) {
                detectTapGestures { o -> if (dur > 0) Island.seekTo((o.x / size.width * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { if (dur > 0) Island.seekTo((dragFrac * dur).toLong()); dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { ch, amt -> ch.consume(); dragFrac = (dragFrac + amt / size.width).coerceIn(0f, 1f) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val th by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "th")
        Box(Modifier.fillMaxWidth().height(th).clip(CircleShape).background(fg.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(th).clip(CircleShape).background(accent))
    }
    Row(Modifier.fillMaxWidth()) {
        Text(Util.formatClock(if (dragging) (dragFrac * dur).toLong() else pos), color = fg.copy(alpha = 0.55f), fontSize = cfg.ts(10.5f))
        Spacer(Modifier.weight(1f))
        Text(if (dur > 0) "-" + Util.formatClock(dur - pos) else "", color = fg.copy(alpha = 0.55f), fontSize = cfg.ts(10.5f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Rounded.SkipPrevious, fg, 34.dp) { Island.prev() }
        val playing = m?.playing == true
        RoundIconButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, fg, 44.dp) { Island.playPause() }
        RoundIconButton(Icons.Rounded.SkipNext, fg, 34.dp) { Island.next() }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, tint: Color, size: Dp, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.82f else 1f, spring(0.45f, 800f), label = "rb")
    Box(
        Modifier
            .size(size + 8.dp)
            .scale(sc)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { Util.tick(); onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size)) }
}

@Composable
private fun TorchExpanded(item: IslandItem, cfg: Settings, accent: Color, fg: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (item.flag) accent else fg.copy(alpha = 0.15f))
                .clickable { Util.tick(); Island.handleAction(IslandAction("", key = if (item.id == "demo_torch") "demo:off" else "torch:toggle"), item) },
            contentAlignment = Alignment.Center
        ) { Icon(if (item.flag) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Φακός", color = fg, fontSize = cfg.ts(16f), fontWeight = FontWeight.SemiBold)
            if (Torch.maxLevel > 1) {
                var lv by remember { mutableFloatStateOf(Torch.level.toFloat()) }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(fg.copy(alpha = 0.14f))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = { Torch.applyLevel(lv.toInt()) },
                                onHorizontalDrag = { ch, amt ->
                                    ch.consume()
                                    lv = (lv + amt / size.width * Torch.maxLevel).coerceIn(1f, Torch.maxLevel.toFloat())
                                }
                            )
                        }
                ) {
                    Box(Modifier.fillMaxWidth(lv / Torch.maxLevel).height(26.dp).clip(RoundedCornerShape(13.dp)).background(accent))
                    Text("Ένταση ${lv.toInt()}/${Torch.maxLevel}", color = Color.White, fontSize = cfg.ts(11.5f), modifier = Modifier.align(Alignment.Center))
                }
            } else Text("Πάτα για σβήσιμο", color = fg.copy(alpha = 0.6f), fontSize = cfg.ts(12.5f))
        }
    }
}

@Composable
private fun NotificationExpanded(item: IslandItem, cfg: Settings, s: IslandState, accent: Color, fg: Color) {
    Row(verticalAlignment = Alignment.Top) {
        NotifIcon(item, accent, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = fg, fontSize = cfg.ts(15f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = fg.copy(alpha = 0.75f), fontSize = cfg.ts(13f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (item.progress != null || item.indeterminate) {
            Spacer(Modifier.width(8.dp))
            ProgressRing(item.progress, item.indeterminate, accent, fg, 36.dp, true, cfg)
        }
    }
    Spacer(Modifier.height(10.dp))
    val replying = s.replyKey != null && (s.replyKey == item.notifKey || s.replyKey == item.id)
    if (replying) ReplyField(item, cfg, accent, fg)
    else ActionChips(item, accent, fg, cfg)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ReplyField(item: IslandItem, cfg: Settings, accent: Color, fg: Color) {
    var text by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { delay(150); try { fr.requestFocus(); kb?.show() } catch (_: Throwable) {} }
    val send: () -> Unit = {
        if (text.isNotBlank() && Reply.send(item, text.trim())) {
            Util.vibrate(longArrayOf(0, 20, 40, 20))
            kb?.hide()
            Island.openReply(null)
            Island.clearMoment()
            Island.collapse()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(fg.copy(alpha = 0.12f)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) Text("Απάντηση…", color = fg.copy(alpha = 0.45f), fontSize = cfg.ts(14f))
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(color = fg, fontSize = cfg.ts(14f)),
                cursorBrush = SolidColor(accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(accent).clickable { send() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)).clickable { kb?.hide(); Island.openReply(null) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Close, null, tint = fg, modifier = Modifier.size(20.dp)) }
    }
}

// =====================================================================
// Γρήγορο πάνελ
// =====================================================================

// ---------- Συντομεύσεις (σύρσιμο κάτω) ----------

private const val SC_ROW = 56f

fun shortcutsCardWidth(cfg: Settings): Float {
    val longest = visibleShortcuts(cfg).maxOfOrNull { Shortcuts.label(it).length } ?: 10
    // περιθώρια + εικονίδιο + κενό + κείμενο
    return (10f * 2 + 8f * 2 + 36f + 12f + longest * 15f * 0.6f * cfg.textScale + 18f).coerceIn(190f, cfg.expandedWidth)
}

fun visibleShortcuts(cfg: Settings): List<String> = cfg.shortcuts.filter { Shortcuts.installed(it) }

private fun actionIcon(k: String): ImageVector = when (k) {
    "action:torch" -> Icons.Rounded.FlashlightOn
    "action:screenshot" -> Icons.Rounded.Screenshot
    "action:timer5" -> Icons.Rounded.HourglassTop
    "action:stopwatch" -> Icons.Rounded.Timer
    "action:ram" -> Icons.Rounded.Memory
    "action:panel" -> Icons.Rounded.Apps
    "action:lock" -> Icons.Rounded.Lock
    else -> Icons.Rounded.Apps
}

@Composable
private fun ShortcutsContent(cfg: Settings, fg: Color, accent: Color) {
    val list = remember(cfg.shortcuts) { visibleShortcuts(cfg) }
    val cardW = shortcutsCardWidth(cfg)
    Column(
        Modifier.fillMaxSize().padding(top = cfg.pillHeight.dp, start = 10.dp, end = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (list.isEmpty()) {
            Text("Πρόσθεσε συντομεύσεις από τις ρυθμίσεις", color = fg.copy(alpha = 0.7f), fontSize = cfg.ts(13f), modifier = Modifier.padding(14.dp))
        }
        list.forEachIndexed { i, k ->
            val icon = remember(k) { if (Shortcuts.isAction(k)) null else Util.appIcon(k)?.first?.asImageBitmap() }
            val label = remember(k) { Shortcuts.label(k) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height((SC_ROW - 6f).dp)
                    .fromCamera(cfg, cardW / 2f, cfg.pillHeight + i * SC_ROW + (SC_ROW - 6f) / 2f, i, cardW)
                    .clip(RoundedCornerShape(25.dp))
                    .background(fg.copy(alpha = 0.09f))
                    .clickable { Util.tick(); Shortcuts.run(k) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) Image(icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                else BadgeCircle(accent, 36.dp) { Icon(actionIcon(k), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(label, color = fg, fontSize = cfg.ts(15f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Περίπου ύψος (dp) κάθε κουτιού του πάνελ. */
private const val TILE_H = 54f

/**
 * Το στοιχείο ξεκινά μικρό από το σημείο της κάμερας και «πετάει» στη θέση του
 * με ελατήριο. (cx, cy) = κέντρο του στοιχείου μέσα στο πάνελ, σε dp.
 */
private fun Modifier.fromCamera(cfg: Settings, cx: Float, cy: Float, index: Int, cardW: Float = cfg.expandedWidth): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Όλα μαζί, ταυτόχρονα από την κάμερα
        val (st, dmp) = cfg.spring()
        p.animateTo(1f, spring(dmp.coerceAtLeast(0.55f), (st * 0.7f).coerceIn(150f, 900f)))
    }
    val camX = cardW / 2f
    val camY = cfg.pillHeight / 2f
    this.graphicsLayer {
        val k = p.value
        translationX = (camX - cx).dp.toPx() * (1f - k)
        translationY = (camY - cy).dp.toPx() * (1f - k)
        scaleX = 0.15f + 0.85f * k
        scaleY = 0.15f + 0.85f * k
        alpha = (k * 1.6f).coerceIn(0f, 1f)
    }
}

private data class QuickTile(val key: String, val label: String, val icon: ImageVector)

private val quickTiles = listOf(
    QuickTile("torch", "Φακός", Icons.Rounded.FlashlightOn),
    QuickTile("screenshot", "Στιγμιότυπο", Icons.Rounded.Screenshot),
    QuickTile("lock", "Κλείδωμα", Icons.Rounded.Lock),
    QuickTile("notifications", "Ειδοποιήσεις", Icons.Rounded.Notifications),
    QuickTile("quicksettings", "Ρυθμίσεις", Icons.Rounded.Tune),
    QuickTile("stopwatch", "Χρονόμετρο", Icons.Rounded.Timer),
    QuickTile("recents", "Πρόσφατα", Icons.Rounded.Apps),
    QuickTile("power", "Λειτουργία", Icons.Rounded.PowerSettingsNew),
)

@Composable
private fun PanelContent(cfg: Settings, s: IslandState, accent: Color, fg: Color) {
    val h = cfg.pillHeight.dp
    Column(Modifier.fillMaxSize()) {
        CameraBand(
            h,
            left = {
                Text("Pilland", color = fg, fontSize = cfg.ts(13f), fontWeight = FontWeight.Bold)
            },
            right = {
                SysChips(cfg, fg)
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).clickable { Island.service?.quick("settings") ?: Island.closePanel() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Settings, null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(18.dp)) }
            }
        )
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            quickTiles.chunked(4).forEachIndexed { row, tiles ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.forEachIndexed { col, t ->
                        val active = t.key == "torch" && Torch.on
                        // Κέντρο του κουτιού σε σχέση με την κάμερα (dp) → ξεκινά από την κάμερα
                        val cw = (cfg.expandedWidth - 36f - 24f) / 4f
                        val cx = 18f + col * (cw + 8f) + cw / 2f
                        val cy = cfg.pillHeight + row * (TILE_H + 7f) + TILE_H / 2f
                        Column(
                            Modifier
                                .weight(1f)
                                .fromCamera(cfg, cx, cy, row * 4 + col)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) accent else fg.copy(alpha = 0.1f))
                                .clickable {
                                    Util.tick()
                                    when (t.key) {
                                        "stopwatch" -> { Island.closePanel(); Island.startStopwatch() }
                                        else -> {
                                            val svc = Island.service
                                            if (svc != null) svc.quick(t.key) else Island.closePanel()
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(t.icon, null, tint = if (active) Color.White else fg, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(t.label, color = fg.copy(alpha = 0.85f), fontSize = cfg.ts(9.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HourglassTop, null, tint = accent, modifier = Modifier.size(18.dp))
                listOf(1, 3, 5, 10, 15, 30).forEachIndexed { i, m ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(fg.copy(alpha = 0.1f))
                            .clickable { Util.tick(); Island.closePanel(); Island.startTimer(m * 60_000L) },
                        contentAlignment = Alignment.Center
                    ) { Text("$m′", color = fg, fontSize = cfg.ts(12.5f), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

private fun loadColor(p: Int): Color = when {
    p < 0 -> Color(0xFF8E8E93)
    p < 60 -> Color(0xFF30D158)
    p < 85 -> Color(0xFFFF9F0A)
    else -> Color(0xFFFF453A)
}

/** RAM & CPU με μικρά εικονίδια. Πάτημα = καθαρισμός RAM. */
@Composable
private fun SysChips(cfg: Settings, fg: Color) {
    var ram by remember { mutableStateOf(SysStats.ramPct(Util.ctx)) }
    var cpu by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SysStats.ramPct(Util.ctx) to SysStats.cpuPct() }
            ram = r.first; cpu = r.second
            delay(1500)
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fg.copy(alpha = 0.08f))
            .clickable {
                Util.tick()
                val svc = Island.service
                if (svc != null) svc.cleanRam() else Island.closePanel()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Memory, null, tint = loadColor(ram), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(if (ram >= 0) "$ram%" else "—", color = fg.copy(alpha = 0.9f), fontSize = cfg.ts(11.5f), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.Speed, null, tint = loadColor(cpu), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(if (cpu >= 0) "$cpu%" else "—", color = fg.copy(alpha = 0.9f), fontSize = cfg.ts(11.5f), fontWeight = FontWeight.SemiBold)
    }
}

/** Χρησιμοποιείται στην προεπισκόπηση των ρυθμίσεων. */
@Composable
fun IslandPreview(modifier: Modifier = Modifier) {
    val s by Island.state.collectAsState()
    val cfg by Prefs.state.collectAsState()
    val media by Island.media.collectAsState()
    val live by Island.serviceActive.collectAsState()
    if (live) return
    // Στην προεπισκόπηση δείχνουμε πάντα το νησάκι, ακόμη και σε landscape/lock.
    val d = Island.resolve(s.copy(landscape = false, locked = false), cfg.copy(enabled = true))
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.offset(x = cfg.offsetX.dp, y = cfg.offsetY.dp)) {
            IslandView(d, cfg, s, media, interactive = true)
        }
    }
}

