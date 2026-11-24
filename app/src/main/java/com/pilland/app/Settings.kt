package com.pilland.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ενέργειες που μπορούν να δεθούν σε χειρονομίες. */
object GestureAction {
    const val NONE = 0
    const val TOGGLE_EXPAND = 1
    const val OPEN_APP = 2
    const val QUICK_PANEL = 3
    const val PLAY_PAUSE = 4
    const val DISMISS = 5
    const val NEXT_TRACK = 6
    const val PREV_TRACK = 7

    val labels = listOf(
        NONE to "Τίποτα",
        TOGGLE_EXPAND to "Άνοιγμα / κλείσιμο",
        OPEN_APP to "Άνοιγμα εφαρμογής",
        QUICK_PANEL to "Γρήγορο πάνελ",
        PLAY_PAUSE to "Play / Pause",
        DISMISS to "Απόκρυψη",
        NEXT_TRACK to "Επόμενο τραγούδι",
        PREV_TRACK to "Προηγούμενο τραγούδι",
    )
    fun label(v: Int) = labels.firstOrNull { it.first == v }?.second ?: "—"
}

/** Λειτουργίες που μπορούν να ενεργοποιηθούν/απενεργοποιηθούν. */
object Feature {
    const val MEDIA = "media"
    const val CALL = "call"
    const val TIMER = "timer"
    const val PROGRESS = "progress"
    const val LIVE = "live"
    const val CHARGING = "charging"
    const val LOW_BATTERY = "low_battery"
    const val TORCH = "torch"
    const val NOTIFICATION = "notification"
    const val RINGER = "ringer"
    const val DND = "dnd"
    const val BLUETOOTH = "bluetooth"
    const val UNLOCK = "unlock"

    val all = listOf(
        MEDIA to "Μουσική & media",
        CALL to "Κλήσεις",
        TIMER to "Χρονόμετρα / αντίστροφη μέτρηση",
        PROGRESS to "Πρόοδος (λήψεις, αποστολές)",
        LIVE to "Live Updates (παραγγελίες, πλοήγηση)",
        CHARGING to "Φόρτιση",
        LOW_BATTERY to "Χαμηλή μπαταρία",
        TORCH to "Φακός",
        NOTIFICATION to "Ειδοποιήσεις",
        RINGER to "Ήχος / Δόνηση / Σίγαση",
        DND to "Μην ενοχλείτε",
        BLUETOOTH to "Ακουστικά Bluetooth",
        UNLOCK to "Ξεκλείδωμα",
    )
}

data class Settings(
    val enabled: Boolean = true,
    // Θέση & μέγεθος (dp)
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val pillHeight: Float = 36f,
    val compactWidth: Float = 210f,
    val expandedWidth: Float = 360f,
    val cornerPercent: Float = 100f,
    // Εμφάνιση
    val idleStyle: Int = 1, // 0 αόρατο, 1 κουκκίδα, 2 δαχτυλίδι μπαταρίας
    val bgColor: Long = 0xFF000000,
    val bgAlpha: Float = 1f,
    val accentAuto: Boolean = true,
    val accentColor: Long = 0xFF3D8BFF,
    val glow: Boolean = true,
    val glowWidth: Float = 1.6f,
    val glowAnimated: Boolean = true,
    val shadow: Boolean = true,
    val textScale: Float = 1f,
    val splitBubble: Boolean = true,
    val visualizer: Boolean = true,
    // Animations
    val springPreset: Int = 1, // 0 απαλό, 1 ελαστικό, 2 γρήγορο, 3 ομαλό, 4 custom
    val stiffness: Float = 420f,
    val damping: Float = 0.62f,
    val animSpeed: Float = 1f,
    val blurTransitions: Boolean = true,
    val transitionStyle: Int = 0, // 0 scale, 1 slide, 2 fade, 3 flip
    val haptics: Boolean = true,
    // Συμπεριφορά
    val momentSeconds: Float = 3.5f,
    val autoCollapseSeconds: Float = 8f,
    val hideLandscape: Boolean = true,
    val hideLockscreen: Boolean = false,
    val hidePausedMedia: Boolean = true,
    val lowBatteryLevel: Int = 15,
    val autoExpandCalls: Boolean = true,
    // Χειρονομίες
    val gestureTap: Int = GestureAction.TOGGLE_EXPAND,
    val gestureDoubleTap: Int = GestureAction.NONE,
    val gestureLong: Int = GestureAction.QUICK_PANEL,
    val gestureDown: Int = GestureAction.TOGGLE_EXPAND,
    val gestureSide: Int = GestureAction.DISMISS,
    // Λειτουργίες / εξαιρέσεις
    val disabledFeatures: Set<String> = emptySet(),
    val excludedApps: Set<String> = emptySet(),
) {
    fun has(feature: String) = enabled && feature !in disabledFeatures

    /** (stiffness, dampingRatio) ανάλογα με το preset. */
    fun spring(): Pair<Float, Float> = when (springPreset) {
        0 -> 180f to 0.85f
        1 -> 420f to 0.62f
        2 -> 900f to 0.78f
        3 -> 260f to 1f
        else -> stiffness to damping
    }.let { (s, d) -> (s * animSpeed * animSpeed).coerceIn(30f, 4000f) to d }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("pilland", Context.MODE_PRIVATE)
        _state.value = load()
    }

    fun update(block: (Settings) -> Settings) {
        val n = block(_state.value)
        _state.value = n
        save(n)
    }

    fun reset() = update { Settings() }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            enabled = sp.getBoolean("enabled", d.enabled),
            offsetX = sp.getFloat("offsetX", d.offsetX),
            offsetY = sp.getFloat("offsetY", d.offsetY),
            pillHeight = sp.getFloat("pillHeight", d.pillHeight),
            compactWidth = sp.getFloat("compactWidth", d.compactWidth),
            expandedWidth = sp.getFloat("expandedWidth", d.expandedWidth),
            cornerPercent = sp.getFloat("cornerPercent", d.cornerPercent),
            idleStyle = sp.getInt("idleStyle", d.idleStyle),
            bgColor = sp.getLong("bgColor", d.bgColor),
            bgAlpha = sp.getFloat("bgAlpha", d.bgAlpha),
            accentAuto = sp.getBoolean("accentAuto", d.accentAuto),
            accentColor = sp.getLong("accentColor", d.accentColor),
            glow = sp.getBoolean("glow", d.glow),
            glowWidth = sp.getFloat("glowWidth", d.glowWidth),
            glowAnimated = sp.getBoolean("glowAnimated", d.glowAnimated),
            shadow = sp.getBoolean("shadow", d.shadow),
            textScale = sp.getFloat("textScale", d.textScale),
            splitBubble = sp.getBoolean("splitBubble", d.splitBubble),
            visualizer = sp.getBoolean("visualizer", d.visualizer),
            springPreset = sp.getInt("springPreset", d.springPreset),
            stiffness = sp.getFloat("stiffness", d.stiffness),
            damping = sp.getFloat("damping", d.damping),
            animSpeed = sp.getFloat("animSpeed", d.animSpeed),
            blurTransitions = sp.getBoolean("blurTransitions", d.blurTransitions),
            transitionStyle = sp.getInt("transitionStyle", d.transitionStyle),
            haptics = sp.getBoolean("haptics", d.haptics),
            momentSeconds = sp.getFloat("momentSeconds", d.momentSeconds),
            autoCollapseSeconds = sp.getFloat("autoCollapseSeconds", d.autoCollapseSeconds),
            hideLandscape = sp.getBoolean("hideLandscape", d.hideLandscape),
            hideLockscreen = sp.getBoolean("hideLockscreen", d.hideLockscreen),
            hidePausedMedia = sp.getBoolean("hidePausedMedia", d.hidePausedMedia),
            lowBatteryLevel = sp.getInt("lowBatteryLevel", d.lowBatteryLevel),
            autoExpandCalls = sp.getBoolean("autoExpandCalls", d.autoExpandCalls),
            gestureTap = sp.getInt("gestureTap", d.gestureTap),
            gestureDoubleTap = sp.getInt("gestureDoubleTap", d.gestureDoubleTap),
            gestureLong = sp.getInt("gestureLong", d.gestureLong),
            gestureDown = sp.getInt("gestureDown", d.gestureDown),
            gestureSide = sp.getInt("gestureSide", d.gestureSide),
            disabledFeatures = sp.getStringSet("disabledFeatures", d.disabledFeatures)?.toSet() ?: d.disabledFeatures,
            excludedApps = sp.getStringSet("excludedApps", d.excludedApps)?.toSet() ?: d.excludedApps,
        )
    }

    private fun save(s: Settings) {
        sp.edit()
            .putBoolean("enabled", s.enabled)
            .putFloat("offsetX", s.offsetX)
            .putFloat("offsetY", s.offsetY)
            .putFloat("pillHeight", s.pillHeight)
            .putFloat("compactWidth", s.compactWidth)
            .putFloat("expandedWidth", s.expandedWidth)
            .putFloat("cornerPercent", s.cornerPercent)
            .putInt("idleStyle", s.idleStyle)
            .putLong("bgColor", s.bgColor)
            .putFloat("bgAlpha", s.bgAlpha)
            .putBoolean("accentAuto", s.accentAuto)
            .putLong("accentColor", s.accentColor)
            .putBoolean("glow", s.glow)
            .putFloat("glowWidth", s.glowWidth)
            .putBoolean("glowAnimated", s.glowAnimated)
            .putBoolean("shadow", s.shadow)
            .putFloat("textScale", s.textScale)
            .putBoolean("splitBubble", s.splitBubble)
            .putBoolean("visualizer", s.visualizer)
            .putInt("springPreset", s.springPreset)
            .putFloat("stiffness", s.stiffness)
            .putFloat("damping", s.damping)
            .putFloat("animSpeed", s.animSpeed)
            .putBoolean("blurTransitions", s.blurTransitions)
            .putInt("transitionStyle", s.transitionStyle)
            .putBoolean("haptics", s.haptics)
            .putFloat("momentSeconds", s.momentSeconds)
            .putFloat("autoCollapseSeconds", s.autoCollapseSeconds)
            .putBoolean("hideLandscape", s.hideLandscape)
            .putBoolean("hideLockscreen", s.hideLockscreen)
            .putBoolean("hidePausedMedia", s.hidePausedMedia)
            .putInt("lowBatteryLevel", s.lowBatteryLevel)
            .putBoolean("autoExpandCalls", s.autoExpandCalls)
            .putInt("gestureTap", s.gestureTap)
            .putInt("gestureDoubleTap", s.gestureDoubleTap)
            .putInt("gestureLong", s.gestureLong)
            .putInt("gestureDown", s.gestureDown)
            .putInt("gestureSide", s.gestureSide)
            .putStringSet("disabledFeatures", s.disabledFeatures)
            .putStringSet("excludedApps", s.excludedApps)
            .apply()
    }
}

/** Έτοιμα θέματα. */
object Themes {
    data class Theme(val name: String, val transform: (Settings) -> Settings)

    val all = listOf(
        Theme("Κλασικό") { it.copy(bgColor = 0xFF000000, bgAlpha = 1f, glow = false, accentAuto = true, springPreset = 1, shadow = true) },
        Theme("Neon") { it.copy(bgColor = 0xFF000000, bgAlpha = 1f, glow = true, glowAnimated = true, glowWidth = 2f, accentAuto = false, accentColor = 0xFF00E5FF, springPreset = 1) },
        Theme("Γυαλί") { it.copy(bgColor = 0xFF1A1C22, bgAlpha = 0.78f, glow = true, glowAnimated = false, glowWidth = 1f, accentAuto = true, springPreset = 3) },
        Theme("Galaxy") { it.copy(bgColor = 0xFF000000, bgAlpha = 1f, glow = true, glowAnimated = true, glowWidth = 1.4f, accentAuto = false, accentColor = 0xFF3D8BFF, springPreset = 1) },
        Theme("Ηλιοβασίλεμα") { it.copy(bgColor = 0xFF140A0A, bgAlpha = 1f, glow = true, glowAnimated = true, glowWidth = 1.8f, accentAuto = false, accentColor = 0xFFFF7A45, springPreset = 0) },
        Theme("Σπορ") { it.copy(bgColor = 0xFF000000, bgAlpha = 1f, glow = true, glowAnimated = true, glowWidth = 2.2f, accentAuto = false, accentColor = 0xFF39FF88, springPreset = 2) },
    )
}
