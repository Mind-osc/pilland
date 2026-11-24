package com.pilland.app

import android.app.PendingIntent
import android.app.RemoteInput
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

enum class Kind {
    MEDIA, CALL, TIMER, STOPWATCH, PROGRESS, LIVE, CHARGING, LOW_BATTERY, TORCH,
    NOTIFICATION, RINGER, DND, BLUETOOTH, UNLOCK, INFO
}

data class IslandAction(
    val label: String,
    val intent: PendingIntent? = null,
    val remoteInput: RemoteInput? = null,
    /** Για εσωτερικές ενέργειες (π.χ. "timer:+60"). */
    val key: String = "",
)

data class IslandItem(
    val id: String,
    val kind: Kind,
    val feature: String,
    val priority: Int = 10,
    val title: String = "",
    val subtitle: String = "",
    val appName: String = "",
    val icon: ImageBitmap? = null,
    /** Εικονίδιο της εφαρμογής που έστειλε την ειδοποίηση. */
    val appIcon: ImageBitmap? = null,
    val accent: Color? = null,
    /** 0..1 ή null. */
    val progress: Float? = null,
    val indeterminate: Boolean = false,
    /** Wall-clock ms: αρχή (count up) ή τέλος (count down). */
    val chronoBase: Long? = null,
    val countDown: Boolean = false,
    val chronoPausedAt: Long? = null,
    val value: Int? = null,
    val flag: Boolean = false,
    val actions: List<IslandAction> = emptyList(),
    val pkg: String? = null,
    val contentIntent: PendingIntent? = null,
    val notifKey: String? = null,
    val autoExpand: Boolean = false,
    val postedAt: Long = System.currentTimeMillis(),
)

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val accent: Color?,
    val playing: Boolean,
    val duration: Long,
    val position: Long,
    val updateTime: Long,
    val speed: Float,
    val pkg: String,
    val appName: String,
) {
    fun currentPosition(): Long {
        if (!playing) return position
        val p = position + ((SystemClock.elapsedRealtime() - updateTime) * speed).toLong()
        return if (duration > 0) p.coerceIn(0, duration) else p
    }
}

enum class Mode { HIDDEN, IDLE, COMPACT, WIDE, PEEK, EXPANDED, PANEL }

data class Display(val mode: Mode, val item: IslandItem?, val secondary: IslandItem?)

data class IslandState(
    val items: List<IslandItem> = emptyList(),
    val moment: IslandItem? = null,
    val expandedId: String? = null,
    val panel: Boolean = false,
    val replyKey: String? = null,
    val dismissed: Set<String> = emptySet(),
    val landscape: Boolean = false,
    val locked: Boolean = false,
    val battery: Int = 100,
    val charging: Boolean = false,
)

object Island {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state = MutableStateFlow(IslandState())
    val media = MutableStateFlow<MediaInfo?>(null)
    /** true όταν τρέχει το πραγματικό overlay. */
    val serviceActive = MutableStateFlow(false)

    @Volatile var mediaController: MediaController? = null
    var serviceRef: WeakReference<IslandService>? = null
    val service: IslandService? get() = serviceRef?.get()

    private var momentJob: Job? = null
    private var collapseJob: Job? = null
    private var timerJob: Job? = null

    // ---------- επίλυση του τι δείχνουμε ----------

    fun resolve(s: IslandState, cfg: Settings): Display {
        if (!cfg.enabled) return Display(Mode.HIDDEN, null, null)
        if ((s.landscape && cfg.hideLandscape) || (s.locked && cfg.hideLockscreen)) return Display(Mode.HIDDEN, null, null)
        if (s.panel) return Display(Mode.PANEL, null, null)
        val visible = s.items
            .filter { cfg.has(it.feature) && dismissKey(it) !in s.dismissed }
            .sortedWith(compareByDescending<IslandItem> { it.priority }.thenByDescending { it.postedAt })
        val m = s.moment
        if (m != null && cfg.has(m.feature)) {
            val mode = when {
                s.expandedId == m.id -> Mode.EXPANDED
                m.kind == Kind.NOTIFICATION || m.kind == Kind.CALL -> Mode.PEEK
                else -> Mode.WIDE
            }
            return Display(mode, m, visible.firstOrNull { it.id != m.id })
        }
        val main = visible.firstOrNull { it.id == s.expandedId } ?: visible.firstOrNull() ?: return Display(Mode.IDLE, null, null)
        val mode = if (s.expandedId == main.id) Mode.EXPANDED else Mode.COMPACT
        return Display(mode, main, visible.firstOrNull { it.id != main.id })
    }

    private fun dismissKey(i: IslandItem) = i.id + "|" + i.title

    // ---------- items ----------

    fun upsert(item: IslandItem) {
        state.update { s ->
            val list = s.items.filterNot { it.id == item.id } + item
            s.copy(items = list)
        }
        if (item.autoExpand && Prefs.value.has(item.feature)) expand(item.id)
    }

    fun remove(id: String) {
        state.update { s ->
            s.copy(
                items = s.items.filterNot { it.id == id },
                expandedId = if (s.expandedId == id) null else s.expandedId,
                replyKey = if (s.items.any { it.id == id && it.notifKey == s.replyKey }) null else s.replyKey,
            )
        }
        if (state.value.moment?.id == id) clearMoment()
    }

    fun removeByNotifKey(key: String) {
        val ids = state.value.items.filter { it.notifKey == key }.map { it.id }
        ids.forEach { remove(it) }
        if (state.value.moment?.notifKey == key && state.value.expandedId != state.value.moment?.id) clearMoment()
    }

    /** Παροδική "στιγμή". */
    fun flash(item: IslandItem, seconds: Float = Prefs.value.momentSeconds) {
        if (!Prefs.value.has(item.feature)) return
        state.update { it.copy(moment = item, panel = false) }
        momentJob?.cancel()
        momentJob = scope.launch {
            delay((seconds * 1000).toLong())
            if (state.value.expandedId != item.id && state.value.replyKey == null) clearMoment()
        }
        if (item.autoExpand) expand(item.id, seconds)
    }

    fun clearMoment() {
        momentJob?.cancel()
        state.update { s ->
            s.copy(moment = null, expandedId = if (s.expandedId == s.moment?.id) null else s.expandedId, replyKey = null)
        }
    }

    // ---------- αλληλεπίδραση ----------

    fun current(): Display = resolve(state.value, Prefs.value)

    fun expand(id: String, holdSeconds: Float? = null) {
        state.update { it.copy(expandedId = id, panel = false) }
        armCollapse(holdSeconds)
    }

    fun toggleExpand() {
        val d = current()
        when (d.mode) {
            Mode.EXPANDED -> collapse()
            Mode.PANEL -> closePanel()
            Mode.IDLE -> openPanel()
            else -> d.item?.let { expand(it.id) }
        }
    }

    fun collapse() {
        collapseJob?.cancel()
        val m = state.value.moment
        val wasMomentExpanded = m != null && state.value.expandedId == m.id
        state.update { it.copy(expandedId = null, replyKey = null) }
        if (wasMomentExpanded) clearMoment()
    }

    fun openPanel() {
        state.update { it.copy(panel = true, expandedId = null, replyKey = null) }
        armCollapse()
    }

    fun closePanel() {
        collapseJob?.cancel()
        state.update { it.copy(panel = false) }
    }

    /** Κάθε άγγιγμα ανανεώνει το χρονόμετρο αυτόματου κλεισίματος. */
    fun poke() {
        val s = state.value
        if (s.expandedId != null || s.panel) armCollapse()
    }

    private fun armCollapse(holdSeconds: Float? = null) {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(((holdSeconds ?: Prefs.value.autoCollapseSeconds) * 1000).toLong())
            if (state.value.replyKey == null) {
                if (state.value.panel) closePanel() else collapse()
            }
        }
    }

    fun dismissCurrent() {
        val d = current()
        val it = d.item
        when {
            d.mode == Mode.PANEL -> closePanel()
            it == null -> {}
            state.value.moment?.id == it.id -> clearMoment()
            else -> state.update { s -> s.copy(dismissed = s.dismissed + dismissKey(it), expandedId = null) }
        }
    }

    fun openReply(key: String?) {
        state.update { it.copy(replyKey = key) }
        momentJob?.cancel()
        collapseJob?.cancel()
    }

    fun setLandscape(v: Boolean) = state.update { it.copy(landscape = v) }
    fun setLocked(v: Boolean) = state.update { it.copy(locked = v) }
    fun setBattery(level: Int, charging: Boolean) = state.update { it.copy(battery = level, charging = charging) }

    // ---------- media ----------

    fun playPause() {
        val mc = mediaController ?: return
        val st = mc.playbackState?.state
        if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) mc.transportControls.pause()
        else mc.transportControls.play()
    }
    fun next() { mediaController?.transportControls?.skipToNext() }
    fun prev() { mediaController?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { mediaController?.transportControls?.seekTo(ms) }

    // ---------- εσωτερικό χρονόμετρο ----------

    const val INT_TIMER = "internal_timer"
    const val INT_WATCH = "internal_stopwatch"

    fun startTimer(ms: Long) {
        val end = System.currentTimeMillis() + ms
        upsert(
            IslandItem(
                id = INT_TIMER, kind = Kind.TIMER, feature = Feature.TIMER, priority = 62,
                title = "Χρονόμετρο", appName = "Pilland", chronoBase = end, countDown = true,
                accent = Color(0xFF8E7CFF),
                actions = listOf(
                    IslandAction("+1′", key = "timer:+60"),
                    IslandAction("Παύση", key = "timer:pause"),
                    IslandAction("Τέλος", key = "timer:stop"),
                ),
            )
        )
        scheduleTimerEnd(end)
    }

    private fun scheduleTimerEnd(end: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay((end - System.currentTimeMillis()).coerceAtLeast(0))
            remove(INT_TIMER)
            Util.vibrate(longArrayOf(0, 220, 120, 220, 120, 400))
            flash(
                IslandItem(
                    id = "timer_done", kind = Kind.INFO, feature = Feature.TIMER, title = "Ο χρόνος τελείωσε",
                    subtitle = "Χρονόμετρο", accent = Color(0xFF8E7CFF), flag = true,
                ), 6f
            )
        }
    }

    fun startStopwatch() {
        upsert(
            IslandItem(
                id = INT_WATCH, kind = Kind.STOPWATCH, feature = Feature.TIMER, priority = 61,
                title = "Χρονόμετρο", appName = "Pilland", chronoBase = System.currentTimeMillis(),
                accent = Color(0xFFFFB020),
                actions = listOf(IslandAction("Παύση", key = "watch:pause"), IslandAction("Τέλος", key = "watch:stop")),
            )
        )
    }

    fun handleInternal(key: String) {
        val items = state.value.items
        when (key) {
            "timer:+60" -> items.firstOrNull { it.id == INT_TIMER }?.let { t ->
                if (t.chronoPausedAt != null) upsert(t.copy(chronoBase = t.chronoBase!! + 60_000))
                else { val end = t.chronoBase!! + 60_000; upsert(t.copy(chronoBase = end)); scheduleTimerEnd(end) }
            }
            "timer:pause" -> items.firstOrNull { it.id == INT_TIMER }?.let { t ->
                if (t.chronoPausedAt == null) {
                    timerJob?.cancel()
                    upsert(t.copy(chronoPausedAt = System.currentTimeMillis(), actions = t.actions.map { a -> if (a.key == "timer:pause") a.copy(label = "Συνέχεια") else a }))
                } else {
                    val end = t.chronoBase!! + (System.currentTimeMillis() - t.chronoPausedAt)
                    upsert(t.copy(chronoBase = end, chronoPausedAt = null, actions = t.actions.map { a -> if (a.key == "timer:pause") a.copy(label = "Παύση") else a }))
                    scheduleTimerEnd(end)
                }
            }
            "timer:stop" -> { timerJob?.cancel(); remove(INT_TIMER) }
            "watch:pause" -> items.firstOrNull { it.id == INT_WATCH }?.let { t ->
                if (t.chronoPausedAt == null) upsert(t.copy(chronoPausedAt = System.currentTimeMillis(), actions = t.actions.map { a -> if (a.key == "watch:pause") a.copy(label = "Συνέχεια") else a }))
                else upsert(t.copy(chronoBase = t.chronoBase!! + (System.currentTimeMillis() - t.chronoPausedAt), chronoPausedAt = null, actions = t.actions.map { a -> if (a.key == "watch:pause") a.copy(label = "Παύση") else a }))
            }
            "watch:stop" -> remove(INT_WATCH)
            "torch:toggle" -> service?.let { Torch.toggle(it) }
            "moment:dismiss" -> { collapse(); clearMoment() }
            "media:playpause" -> playPause()
        }
    }

    // ---------- demo ----------

    private var demoJob: Job? = null

    /** Οι δοκιμές κρατάνε λίγα δευτερόλεπτα και μετά καθαρίζουν μόνες τους. */
    private fun autoClearDemo(vararg ids: String) {
        demoJob?.cancel()
        demoJob = scope.launch {
            delay(7_000)
            ids.forEach { id ->
                if (id == INT_TIMER) timerJob?.cancel()
                if (id == "media" && media.value?.pkg == "demo") media.value = null
                if (id != "media" || media.value == null) remove(id)
            }
        }
    }

    fun demo(kind: Kind) {
        val now = System.currentTimeMillis()
        when (kind) {
            Kind.TORCH -> autoClearDemo("demo_torch")
            Kind.TIMER -> autoClearDemo(INT_TIMER)
            Kind.STOPWATCH -> autoClearDemo(INT_WATCH)
            Kind.CALL -> autoClearDemo("demo_call")
            Kind.PROGRESS -> autoClearDemo("demo_prog")
            Kind.LIVE -> autoClearDemo("demo_live")
            Kind.MEDIA -> autoClearDemo("media")
            else -> {}
        }
        when (kind) {
            Kind.TORCH -> upsert(IslandItem("demo_torch", Kind.TORCH, Feature.TORCH, 40, title = "Φακός", accent = Color(0xFF2F80FF), actions = listOf(IslandAction("Σβήσε", key = "demo:off"))))
            Kind.TIMER -> startTimer(10 * 60_000L + 1_000)
            Kind.STOPWATCH -> startStopwatch()
            Kind.CHARGING -> flash(IslandItem("charging", Kind.CHARGING, Feature.CHARGING, title = "Φόρτιση", value = state.value.battery.coerceAtLeast(1), flag = true, accent = Color(0xFF34C759)))
            Kind.LOW_BATTERY -> flash(IslandItem("lowbat", Kind.LOW_BATTERY, Feature.LOW_BATTERY, title = "Χαμηλή μπαταρία", value = 12, accent = Color(0xFFFF453A)))
            Kind.RINGER -> flash(IslandItem("ringer", Kind.RINGER, Feature.RINGER, title = "Σίγαση", value = 0, accent = Color(0xFFFF453A)))
            Kind.DND -> flash(IslandItem("dnd", Kind.DND, Feature.DND, title = "Μην ενοχλείτε", subtitle = "Ενεργό", flag = true, accent = Color(0xFF7D6BFF)))
            Kind.BLUETOOTH -> flash(IslandItem("bt", Kind.BLUETOOTH, Feature.BLUETOOTH, title = "Galaxy Buds", subtitle = "Συνδέθηκε", accent = Color(0xFF64D2FF)))
            Kind.UNLOCK -> flash(IslandItem("unlock", Kind.UNLOCK, Feature.UNLOCK, title = "Ξεκλείδωμα", accent = Color(0xFFFFFFFF)), 1.6f)
            Kind.NOTIFICATION -> {
                val pkg = listOf("com.viber.voip", "com.facebook.orca", "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.whatsapp")
                    .firstOrNull { Util.appIcon(it) != null }
                val ic = pkg?.let { Util.appIcon(it) }
                flash(
                    IslandItem(
                        "demo_n$now", Kind.NOTIFICATION, Feature.NOTIFICATION, title = "Μαρία", subtitle = "Πάμε για καφέ στις 6; ☕",
                        appName = pkg?.let { Util.appName(it) } ?: "Messages", appIcon = ic?.first?.asImageBitmap(),
                        accent = ic?.second ?: Color(0xFF34C759), pkg = pkg,
                    )
                )
            }
            Kind.CALL -> upsert(IslandItem("demo_call", Kind.CALL, Feature.CALL, 100, title = "Γιάννης", subtitle = "Εισερχόμενη κλήση", appName = "Τηλέφωνο", accent = Color(0xFF34C759), autoExpand = true, actions = listOf(IslandAction("Απόρριψη", key = "demo:call_off"), IslandAction("Απάντηση", key = "demo:call_off"))))
            Kind.PROGRESS -> {
                upsert(IslandItem("demo_prog", Kind.PROGRESS, Feature.PROGRESS, 45, title = "Λήψη αρχείου", subtitle = "video.mp4", appName = "Chrome", progress = 0.42f, accent = Color(0xFF64D2FF)))
                scope.launch {
                    for (p in listOf(0.6f, 0.8f, 0.95f, 1f)) {
                        delay(700)
                        val cur = state.value.items.firstOrNull { it.id == "demo_prog" } ?: return@launch
                        upsert(cur.copy(progress = p))
                    }
                    delay(400)
                    remove("demo_prog")
                    flash(
                        IslandItem(
                            "demo_done", Kind.NOTIFICATION, Feature.PROGRESS, title = "Η λήψη ολοκληρώθηκε", subtitle = "video.mp4",
                            appName = "Chrome", accent = Color(0xFF64D2FF), autoExpand = true,
                            actions = listOf(IslandAction("Όχι", key = "moment:dismiss"), IslandAction("Άνοιγμα", key = "moment:dismiss")),
                        ), 10f
                    )
                }
            }
            Kind.LIVE -> upsert(IslandItem("demo_live", Kind.LIVE, Feature.LIVE, 55, title = "Η παραγγελία έρχεται", subtitle = "Σε 12 λεπτά", appName = "efood", progress = 0.65f, accent = Color(0xFFFF2D55)))
            Kind.MEDIA -> {
                media.value = MediaInfo("Blinding Lights", "The Weeknd", null, Color(0xFFFF375F), true, 200_000, 64_000, SystemClock.elapsedRealtime(), 1f, "demo", "Spotify")
                upsert(IslandItem("media", Kind.MEDIA, Feature.MEDIA, 50, title = "Blinding Lights", subtitle = "The Weeknd", appName = "Spotify", accent = Color(0xFFFF375F), flag = true))
            }
            Kind.INFO -> {}
        }
    }

    private var seqJob: Job? = null

    /** Δείχνει 3 διαδοχικές στιγμές για να φαίνεται καθαρά η μετάβαση. */
    fun demoSequence() {
        seqJob?.cancel()
        seqJob = scope.launch {
            demo(Kind.NOTIFICATION)
            delay(1600)
            flash(IslandItem("seq_bt", Kind.BLUETOOTH, Feature.BLUETOOTH, title = "Galaxy Buds", subtitle = "Συνδέθηκε", value = 80, accent = Color(0xFF64D2FF)), 1.8f)
            delay(1600)
            flash(IslandItem("seq_ch", Kind.CHARGING, Feature.CHARGING, title = "Φόρτιση", value = state.value.battery.coerceAtLeast(1), flag = true, accent = Color(0xFF34C759)), 2f)
        }
    }

    fun clearDemos() {
        state.update { s -> s.copy(items = s.items.filterNot { it.id.startsWith("demo") || it.id == INT_TIMER || it.id == INT_WATCH || (it.id == "media" && media.value?.pkg == "demo") }) }
        if (media.value?.pkg == "demo") media.value = null
        timerJob?.cancel()
        clearMoment()
    }

    fun handleAction(a: IslandAction, item: IslandItem?) {
        when {
            a.key == "demo:off" -> remove("demo_torch")
            a.key == "demo:call_off" -> remove("demo_call")
            a.key.isNotEmpty() -> handleInternal(a.key)
            a.remoteInput != null -> openReply(item?.notifKey ?: item?.id)
            a.intent != null -> {
                Util.sendPending(a.intent)
                if (item?.kind == Kind.NOTIFICATION) clearMoment()
                if (item?.kind == Kind.CALL) {
                    collapse()
                    if (!Prefs.value.showOngoingCall) remove(item.id)
                }
            }
        }
    }
}
