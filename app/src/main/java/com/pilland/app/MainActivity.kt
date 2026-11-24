package com.pilland.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoSizeSelectSmall
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val cfg by Prefs.state.collectAsState()
            val accent = Color(cfg.accentColor)
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = accent,
                    secondary = accent,
                    background = Bg,
                    surface = Card,
                    onPrimary = Color.White,
                )
            ) {
                SettingsScreen(resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

private val Bg = Color(0xFF0B0B0F)
private val Card = Color(0xFF16161C)
private val Muted = Color(0xFF9A9AA6)

// =====================================================================
// Άδειες
// =====================================================================

private fun a11yEnabled(ctx: Context): Boolean {
    val s = AndroidSettings.Secure.getString(ctx.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.split(':').any { it.startsWith(ctx.packageName + "/") }
}

private fun listenerEnabled(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun batteryOk(ctx: Context) = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

private fun btOk(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun open(ctx: Context, i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {
        try { ctx.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
    }
}

// =====================================================================
// Οθόνη ρυθμίσεων
// =====================================================================

@Composable
private fun SettingsScreen(resumeTick: Int) {
    val cfg by Prefs.state.collectAsState()
    val ctx = LocalContext.current
    var showApps by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Header(cfg) }
        item { DemoChips() }
        item { PermissionsCard(resumeTick) }

        item {
            Section("Θέματα", Icons.Rounded.AutoAwesome) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Themes.all.forEach { t ->
                        val preview = t.transform(Settings())
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF202028))
                                .clickable { Prefs.update(t.transform); Island.demo(Kind.TORCH) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(74.dp, 24.dp)
                                    .clip(CircleShape)
                                    .background(Color(preview.bgColor).copy(alpha = preview.bgAlpha))
                                    .border(if (preview.glow) 1.6.dp else 0.dp, if (preview.glow) Color(preview.accentColor) else Color.Transparent, CircleShape)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(t.name, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Section("Θέση & μέγεθος", Icons.Rounded.PhotoSizeSelectSmall) {
                SliderRow("Οριζόντια μετατόπιση", cfg.offsetX, -80f..80f, "%.0f dp") { v -> Prefs.update { it.copy(offsetX = v) } }
                SliderRow("Κάθετη μετατόπιση", cfg.offsetY, -30f..80f, "%.0f dp") { v -> Prefs.update { it.copy(offsetY = v) } }
                SliderRow("Απόσταση από την κάμερα", cfg.cameraGap, 8f..40f, "%.0f dp") { v -> Prefs.update { it.copy(cameraGap = v) } }
                SliderRow("Ύψος", cfg.pillHeight, 24f..56f, "%.0f dp") { v -> Prefs.update { it.copy(pillHeight = v) } }
                SwitchRow("Αυτόματο πλάτος (ανάλογα με το περιεχόμενο)", cfg.autoWidth) { v -> Prefs.update { it.copy(autoWidth = v) } }
                if (cfg.autoWidth) SwitchRow("Μεγαλώνει μόνο προς τα δεξιά (χωρίς κενό αριστερά)", cfg.asymCompact) { v -> Prefs.update { it.copy(asymCompact = v) } }
                if (!cfg.autoWidth) SliderRow("Πλάτος (compact)", cfg.compactWidth, 110f..330f, "%.0f dp") { v -> Prefs.update { it.copy(compactWidth = v) } }
                SliderRow("Πλάτος (ανοιχτό)", cfg.expandedWidth, 260f..400f, "%.0f dp") { v -> Prefs.update { it.copy(expandedWidth = v) } }
                SliderRow("Στρογγύλεμα γωνιών", cfg.cornerPercent, 10f..100f, "%.0f%%") { v -> Prefs.update { it.copy(cornerPercent = v) } }
                SmallButton("Επαναφορά θέσης στην κάμερα") { Prefs.update { it.copy(offsetX = 0f, offsetY = 0f) } }
            }
        }

        item {
            Section("Εμφάνιση", Icons.Rounded.Palette) {
                Label("Σε αδράνεια")
                Choice(listOf("Αόρατο", "Κουκκίδα", "Δαχτυλίδι μπαταρίας"), cfg.idleStyle) { v -> Prefs.update { it.copy(idleStyle = v) } }
                Spacer(Modifier.height(10.dp))
                Label("Χρώμα φόντου")
                ColorPicker(Color(cfg.bgColor), listOf(0xFF000000, 0xFF111318, 0xFF1C1C24, 0xFF0D1B2A, 0xFF1A0B2E, 0xFF2B0A0A, 0xFFF2F2F7)) { c -> Prefs.update { it.copy(bgColor = c.toArgb().toLong() and 0xFFFFFFFFL) } }
                SliderRow("Αδιαφάνεια", cfg.bgAlpha, 0.4f..1f, "%.2f") { v -> Prefs.update { it.copy(bgAlpha = v) } }
                Spacer(Modifier.height(6.dp))
                SwitchRow("Αυτόματο χρώμα (από εφαρμογή / εξώφυλλο)", cfg.accentAuto) { v -> Prefs.update { it.copy(accentAuto = v) } }
                Label("Χρώμα έμφασης")
                ColorPicker(Color(cfg.accentColor), listOf(0xFF3D8BFF, 0xFF00E5FF, 0xFF39FF88, 0xFFFFD60A, 0xFFFF7A45, 0xFFFF375F, 0xFFBF5AF2, 0xFFFFFFFF)) { c -> Prefs.update { it.copy(accentColor = c.toArgb().toLong() and 0xFFFFFFFFL) } }
                SwitchRow("Φωτεινό περίγραμμα (glow)", cfg.glow) { v -> Prefs.update { it.copy(glow = v) } }
                if (cfg.glow) {
                    SwitchRow("Κινούμενο glow", cfg.glowAnimated) { v -> Prefs.update { it.copy(glowAnimated = v) } }
                    SliderRow("Πάχος glow", cfg.glowWidth, 0.5f..4f, "%.1f dp") { v -> Prefs.update { it.copy(glowWidth = v) } }
                }
                SwitchRow("Σκιά", cfg.shadow) { v -> Prefs.update { it.copy(shadow = v) } }
                SwitchRow("Δεύτερη φούσκα (2 δραστηριότητες μαζί)", cfg.splitBubble) { v -> Prefs.update { it.copy(splitBubble = v) } }
                SwitchRow("Visualizer μουσικής", cfg.visualizer) { v -> Prefs.update { it.copy(visualizer = v) } }
                SliderRow("Μέγεθος κειμένου", cfg.textScale, 0.8f..1.3f, "%.2fx") { v -> Prefs.update { it.copy(textScale = v) } }
            }
        }

        item {
            Section("Animations", Icons.Rounded.Animation) {
                Label("Ελατήριο")
                Choice(listOf("Απαλό", "Ελαστικό", "Γρήγορο", "Ομαλό", "Custom"), cfg.springPreset) { v -> Prefs.update { it.copy(springPreset = v) } }
                if (cfg.springPreset == 4) {
                    SliderRow("Σκληρότητα", cfg.stiffness, 60f..1500f, "%.0f") { v -> Prefs.update { it.copy(stiffness = v) } }
                    SliderRow("Απόσβεση (μικρό = πιο ελαστικό)", cfg.damping, 0.25f..1f, "%.2f") { v -> Prefs.update { it.copy(damping = v) } }
                }
                SliderRow("Ταχύτητα", cfg.animSpeed, 0.5f..2f, "%.2fx") { v -> Prefs.update { it.copy(animSpeed = v) } }
                Spacer(Modifier.height(6.dp))
                Label("Μετάβαση περιεχομένου")
                Choice(listOf("Zoom", "Slide", "Fade", "Pop"), cfg.transitionStyle) { v -> Prefs.update { it.copy(transitionStyle = v) } }
                SwitchRow("Blur στις μεταβάσεις", cfg.blurTransitions) { v -> Prefs.update { it.copy(blurTransitions = v) } }
                SwitchRow("Δόνηση (haptics)", cfg.haptics) { v -> Prefs.update { it.copy(haptics = v) } }
                SmallButton("Δοκίμασε την κίνηση") { Island.demo(Kind.NOTIFICATION) }
            }
        }

        item {
            Section("Συμπεριφορά", Icons.Rounded.Tune) {
                SliderRow("Διάρκεια «στιγμής»", cfg.momentSeconds, 1f..8f, "%.1f s") { v -> Prefs.update { it.copy(momentSeconds = v) } }
                SliderRow("Αυτόματο κλείσιμο ανοιχτής κάρτας", cfg.autoCollapseSeconds, 2f..30f, "%.0f s") { v -> Prefs.update { it.copy(autoCollapseSeconds = v) } }
                SliderRow("Όριο χαμηλής μπαταρίας", cfg.lowBatteryLevel.toFloat(), 5f..30f, "%.0f%%") { v -> Prefs.update { it.copy(lowBatteryLevel = v.roundToInt()) } }
                SwitchRow("Απόκρυψη σε landscape (παιχνίδια, βίντεο)", cfg.hideLandscape) { v -> Prefs.update { it.copy(hideLandscape = v) } }
                SwitchRow("Απόκρυψη στην οθόνη κλειδώματος", cfg.hideLockscreen) { v -> Prefs.update { it.copy(hideLockscreen = v) } }
                SwitchRow("Κρύψε τη μουσική όταν είναι σε παύση", cfg.hidePausedMedia) { v -> Prefs.update { it.copy(hidePausedMedia = v) } }
                SwitchRow("Αυτόματο άνοιγμα σε εισερχόμενη κλήση", cfg.autoExpandCalls) { v -> Prefs.update { it.copy(autoExpandCalls = v) } }
            }
        }

        item {
            Section("Χειρονομίες", Icons.Rounded.Gesture) {
                GestureRow("Πάτημα", cfg.gestureTap) { v -> Prefs.update { it.copy(gestureTap = v) } }
                GestureRow("Διπλό πάτημα", cfg.gestureDoubleTap) { v -> Prefs.update { it.copy(gestureDoubleTap = v) } }
                GestureRow("Παρατεταμένο πάτημα", cfg.gestureLong) { v -> Prefs.update { it.copy(gestureLong = v) } }
                GestureRow("Σύρσιμο κάτω", cfg.gestureDown) { v -> Prefs.update { it.copy(gestureDown = v) } }
                GestureRow("Σύρσιμο πλάγια", cfg.gestureSide) { v -> Prefs.update { it.copy(gestureSide = v) } }
                Text("Σύρσιμο πάνω: κλείνει / κρύβει πάντα.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            Section("Λειτουργίες", Icons.Rounded.Widgets) {
                Feature.all.forEach { (key, label) ->
                    SwitchRow(label, key !in cfg.disabledFeatures) { on ->
                        Prefs.update { it.copy(disabledFeatures = if (on) it.disabledFeatures - key else it.disabledFeatures + key) }
                    }
                }
            }
        }

        item {
            Section("Εξαιρέσεις εφαρμογών", Icons.Rounded.Apps) {
                Text(
                    if (cfg.excludedApps.isEmpty()) "Καμία εξαίρεση" else "${cfg.excludedApps.size} εφαρμογές δεν εμφανίζονται στο νησάκι",
                    color = Muted, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                SmallButton("Επιλογή εφαρμογών") { showApps = true }
            }
        }

        item {
            Section("Γενικά", Icons.Rounded.RestartAlt) {
                SwitchRow("Pilland ενεργό", cfg.enabled) { v -> Prefs.update { it.copy(enabled = v) } }
                SmallButton("Επαναφορά όλων των ρυθμίσεων") { showReset = true }
                Text("Pilland 1.0 · για Galaxy S24 Ultra", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }

    if (showApps) AppPickerDialog(cfg.excludedApps, onDismiss = { showApps = false }) { set -> Prefs.update { it.copy(excludedApps = set) } }
    if (showReset) AlertDialog(
        onDismissRequest = { showReset = false },
        confirmButton = { TextButton(onClick = { Prefs.reset(); showReset = false }) { Text("Επαναφορά") } },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("Άκυρο") } },
        title = { Text("Επαναφορά;") },
        text = { Text("Όλες οι ρυθμίσεις θα γυρίσουν στις αρχικές.") },
        containerColor = Card
    )
}

// =====================================================================
// Header με ζωντανή προεπισκόπηση
// =====================================================================

@Composable
private fun Header(cfg: Settings) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF9CC9F5), Color(0xFF5B8DEF), Color(0xFF3A5BD9), Color(0xFF7FC4F0))
                    )
                )
        ) {
            // ψεύτικο status bar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("07:39", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                val bat by Island.state.collectAsState()
                Text("${bat.battery}%", color = Color.White, fontSize = 12.sp)
            }
            // η "κάμερα"
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = ((cfg.pillHeight - 12f) / 2f).dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A))
            )
            IslandPreview(Modifier.fillMaxWidth().statusBarsPadding())
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("Pilland", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Moment Effects για το S24 Ultra · πάτα, κράτα, σύρε το νησάκι", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoChips() {
    val demos = listOf(
        "🎵 Μουσική" to { Island.demo(Kind.MEDIA) },
        "🔦 Φακός" to { Island.demo(Kind.TORCH) },
        "⏳ Αντίστροφη" to { Island.demo(Kind.TIMER) },
        "⏱ Χρονόμετρο" to { Island.demo(Kind.STOPWATCH) },
        "⚡ Φόρτιση" to { Island.demo(Kind.CHARGING) },
        "💬 Ειδοποίηση" to { Island.demo(Kind.NOTIFICATION) },
        "📞 Κλήση" to { Island.demo(Kind.CALL) },
        "⬇ Λήψη" to { Island.demo(Kind.PROGRESS) },
        "🛵 Live" to { Island.demo(Kind.LIVE) },
        "🔕 Σίγαση" to { Island.demo(Kind.RINGER) },
        "🌙 Μην ενοχλείτε" to { Island.demo(Kind.DND) },
        "🎧 Bluetooth" to { Island.demo(Kind.BLUETOOTH) },
        "🔓 Ξεκλείδωμα" to { Island.demo(Kind.UNLOCK) },
        "🪫 Μπαταρία" to { Island.demo(Kind.LOW_BATTERY) },
        "▦ Πάνελ" to { Island.openPanel() },
        "✕ Καθάρισμα" to { Island.clearDemos(); Island.closePanel() },
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Δοκιμή", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            demos.forEach { (label, act) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Card)
                        .clickable { Util.tick(); act() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(label, color = Color.White, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun PermissionsCard(resumeTick: Int) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val key = resumeTick + tick
    val a11y = remember(key) { a11yEnabled(ctx) }
    val listener = remember(key) { listenerEnabled(ctx) }
    val battery = remember(key) { batteryOk(ctx) }
    val bt = remember(key) { btOk(ctx) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    Section("Άδειες", Icons.Rounded.Security) {
        PermRow("Προσβασιμότητα", "Για να ζωγραφίζει το νησάκι πάνω από όλα και για τις γρήγορες ενέργειες", a11y) {
            open(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (!a11y) {
            Text(
                "Αν η επιλογή είναι γκρι: Ρυθμίσεις → Εφαρμογές → Pilland → ⋮ (πάνω δεξιά) → «Να επιτρέπονται οι περιορισμένες ρυθμίσεις», και ξαναδοκίμασε.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            SmallButton("Άνοιγμα πληροφοριών εφαρμογής") {
                open(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            }
        }
        PermRow("Πρόσβαση ειδοποιήσεων", "Μουσική, κλήσεις, χρονόμετρα, ειδοποιήσεις, Live Updates", listener) {
            open(ctx, Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermRow("Χωρίς περιορισμό μπαταρίας", "Για να μη το «σκοτώνει» το One UI", battery) {
            open(ctx, Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
        }
        PermRow("Συσκευές κοντά (Bluetooth)", "Όνομα & μπαταρία ακουστικών", bt) {
            btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
    val col by animateColorAsState(if (ok) Color(0xFF34C759) else Color(0xFFFF9F0A), label = "pc")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !ok) { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = col, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
        if (!ok) Text("Ενεργοποίηση", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// =====================================================================
// Δομικά στοιχεία
// =====================================================================

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(t: String) = Text(t, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 6.dp, top = 2.dp))

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(fmt.format(value), color = Muted, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0xFF2C2C34)
            ),
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun Choice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF202028)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, label = "c")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).clickable { Util.tick(); onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) { Text(o, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF24242C))
            .clickable { Util.tick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun GestureRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = true }.padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box {
            Text(GestureAction.label(value), color = MaterialTheme.colorScheme.primary, fontSize = 13.5.sp)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                GestureAction.labels.forEach { (v, l) ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { onChange(v); open = false })
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(current: Color, swatches: List<Long>, onPick: (Color) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        swatches.forEach { sw ->
            val c = Color(sw)
            val sel = c.toArgb() == current.toArgb()
            val sc by animateFloatAsState(if (sel) 1.15f else 1f, spring(0.5f, 600f), label = "sw")
            Box(
                Modifier
                    .size((30 * sc).dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (sel) 2.5.dp else 1.dp, if (sel) Color.White else Color(0x33FFFFFF), CircleShape)
                    .clickable { Util.tick(); onPick(c) }
            )
        }
    }
    // Ελεύθερη επιλογή απόχρωσης
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(current.toArgb(), hsv)
    val rainbow = remember { (0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 0.85f, 1f))) } }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(rainbow))
            .pointerInput(Unit) {
                fun pick(x: Float) {
                    val hue = (x / size.width).coerceIn(0f, 1f) * 360f
                    onPick(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))))
                }
                detectTapGestures { pick(it.x) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, _ ->
                    ch.consume()
                    val hue = (ch.position.x / size.width).coerceIn(0f, 1f) * 360f
                    onPick(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (hsv[1] > 0.2f) {
                val x = hsv[0] / 360f * size.width
                drawCircle(Color.White, radius = size.height / 2.6f, center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
                drawCircle(current, radius = size.height / 3.6f, center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
            }
        }
    }
}

// =====================================================================
// Επιλογή εφαρμογών
// =====================================================================

private data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun AppPickerDialog(initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var sel by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .map { p -> AppEntry(p, Util.appName(p), Util.appIcon(p)?.first?.asImageBitmap()) }
                .sortedBy { it.label.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(sel); onDismiss() }) { Text("Αποθήκευση") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } },
        title = { Text("Εξαίρεση εφαρμογών") },
        containerColor = Card,
        text = {
            val list = apps
            if (list == null) Text("Φόρτωση…", color = Muted)
            else LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(list, key = { it.pkg }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (a.pkg in sel) sel - a.pkg else sel + a.pkg }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ic = a.icon
                        if (ic != null) androidx.compose.foundation.Image(ic, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                        else Box(Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Checkbox(checked = a.pkg in sel, onCheckedChange = { c -> sel = if (c) sel + a.pkg else sel - a.pkg })
                    }
                }
            }
        }
    )
}

