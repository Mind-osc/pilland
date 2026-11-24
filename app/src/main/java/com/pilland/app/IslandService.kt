package com.pilland.app

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/** Lifecycle για Compose μέσα σε service. */
class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    fun start() {
        ssc.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/** Φακός: κατάσταση, εναλλαγή, ένταση. */
object Torch {
    private var cm: CameraManager? = null
    var camId: String? = null
    var on = false
    var maxLevel = 1
    var level = 1
    private var cb: CameraManager.TorchCallback? = null

    fun register(ctx: Context) {
        try {
            val m = ctx.getSystemService(CameraManager::class.java) ?: return
            cm = m
            camId = m.cameraIdList.firstOrNull { id ->
                val c = m.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            val id = camId ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                maxLevel = m.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            }
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId != camId) return
                    on = enabled
                    publish()
                }

                override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                    if (cameraId != camId) return
                    level = newStrengthLevel
                    if (on) publish()
                }
            }
            cb = callback
            m.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
        }
    }

    fun unregister() {
        try { cb?.let { cm?.unregisterTorchCallback(it) } } catch (_: Throwable) {}
        cb = null
    }

    private fun publish() {
        if (on) {
            Island.upsert(
                IslandItem(
                    id = "torch", kind = Kind.TORCH, feature = Feature.TORCH, priority = 40,
                    title = "Φακός", value = level, accent = Color(0xFF2F80FF), flag = true,
                    actions = listOf(IslandAction("Σβήσε", key = "torch:toggle")),
                )
            )
        } else Island.remove("torch")
    }

    fun toggle(ctx: Context) {
        try {
            val m = cm ?: ctx.getSystemService(CameraManager::class.java)
            val id = camId ?: return
            m?.setTorchMode(id, !on)
        } catch (_: Throwable) {
        }
    }

    fun applyLevel(l: Int) {
        try {
            val id = camId ?: return
            if (Build.VERSION.SDK_INT >= 33 && maxLevel > 1) cm?.turnOnTorchWithStrengthLevel(id, l.coerceIn(1, maxLevel))
        } catch (_: Throwable) {
        }
    }
}

class IslandService : AccessibilityService() {

    private var wm: WindowManager? = null
    /** Παράθυρο εικόνας: σταθερό, όλο το πλάτος, δεν δέχεται αγγίγματα. */
    private var visual: FrameLayout? = null
    private var vParams: WindowManager.LayoutParams? = null
    /** Αόρατο παράθυρο αφής, όσο το νησάκι· προωθεί τα αγγίγματα στο παράθυρο εικόνας. */
    private var touch: View? = null
    private var tParams: WindowManager.LayoutParams? = null
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private var shrinkJob: Job? = null
    private var curRect = Rect()
    private var cutout = Rect()
    private var screenW = 0
    private var screenH = 0
    private var lastSpec: WindowSpec? = null
    private var lastLowWarn = 101

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            onSystemEvent(i, isInitialStickyBroadcast)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Island.serviceRef = WeakReference(this)
        Island.serviceActive.value = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Island.setLandscape(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        computeCutout()
        createOverlay()
        registerEvents()
        Torch.register(this)

        scope.launch {
            combine(Island.state, Prefs.state) { s, cfg ->
                val d = Island.resolve(s, cfg)
                WindowSpec(
                    rect = islandTouchRect(d, cfg, s.replyKey != null),
                    touchable = d.mode != Mode.HIDDEN,
                    focusable = s.replyKey != null,
                    offX = cfg.offsetX, offY = cfg.offsetY, pill = cfg.pillHeight,
                    speed = cfg.animSpeed,
                )
            }.distinctUntilChanged().collect { applySpec(it) }
        }
    }

    private data class WindowSpec(
        /** (αριστερά, δεξιά, ύψος) σε dp, σε σχέση με το κέντρο της κάμερας. */
        val rect: Triple<Float, Float, Float>,
        val touchable: Boolean,
        val focusable: Boolean,
        val offX: Float,
        val offY: Float,
        val pill: Float,
        val speed: Float,
    )

    private fun dp(v: Float) = (v * resources.displayMetrics.density).roundToInt()

    private fun computeCutout() {
        val w = wm ?: return
        try {
            val metrics = w.currentWindowMetrics
            screenW = metrics.bounds.width()
            screenH = metrics.bounds.height()
            val dc = metrics.windowInsets.displayCutout
            val r = dc?.boundingRects?.minByOrNull { it.top }
            cutout = if (r != null && !r.isEmpty) Rect(r) else {
                val cx = screenW / 2
                Rect(cx - dp(14f), dp(10f), cx + dp(14f), dp(38f))
            }
        } catch (_: Throwable) {
            screenW = resources.displayMetrics.widthPixels
            screenH = resources.displayMetrics.heightPixels
            val cx = screenW / 2
            cutout = Rect(cx - dp(14f), dp(10f), cx + dp(14f), dp(38f))
        }
    }

    private fun visualFlags(focusable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return f
    }

    private fun touchFlags(touchable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun createOverlay() {
        val w = wm ?: return
        owner.start()

        // ---- εικόνα
        val frame = FrameLayout(this)
        val cv = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { OverlayRoot() }
        }
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.addView(cv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val vp = WindowManager.LayoutParams(
            screenW, (screenH * 0.75f).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            visualFlags(false),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Pilland"
        }
        vParams = vp
        positionVisual(Prefs.value.offsetX, Prefs.value.offsetY, Prefs.value.pillHeight)
        try { w.addView(frame, vp); visual = frame } catch (_: Throwable) {}

        // ---- αφή
        val t = object : View(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    val s = Island.state.value
                    if (s.panel || s.shortcuts) Island.closePanel()
                    else if (s.expandedId != null || s.replyKey != null) Island.collapse()
                    return false
                }
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) Island.poke()
                val root = visual ?: return false
                val tp = tParams ?: return false
                val v = vParams ?: return false
                val e = MotionEvent.obtain(ev)
                e.offsetLocation((tp.x - v.x).toFloat(), (tp.y - v.y).toFloat())
                root.dispatchTouchEvent(e)
                e.recycle()
                return true
            }
        }
        val tp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            touchFlags(true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Pilland-touch"
        }
        tParams = tp
        try { w.addView(t, tp); touch = t } catch (_: Throwable) {}
    }

    /** Το παράθυρο εικόνας ξεκινά πάνω από την κάμερα· το νησάκι κεντράρεται σε αυτήν. */
    private fun positionVisual(offX: Float, offY: Float, pill: Float) {
        val vp = vParams ?: return
        vp.x = 0
        vp.width = screenW
        vp.height = (screenH * 0.75f).toInt()
        vp.y = cutout.centerY() - dp(pill) / 2 - dp(WINDOW_PAD) + dp(offY)
        val camOffsetPx = cutout.centerX() - screenW / 2
        Island.windowShift.value = camOffsetPx / resources.displayMetrics.density + offX
    }

    private fun applySpec(spec: WindowSpec) {
        lastSpec = spec
        val w = wm ?: return
        val vp = vParams ?: return
        val tp = tParams ?: return

        // εικόνα: αλλάζει μόνο θέση/εστίαση, ποτέ πλάτος
        val oldY = vp.y
        val oldFlags = vp.flags
        positionVisual(spec.offX, spec.offY, spec.pill)
        vp.flags = visualFlags(spec.focusable)
        vp.softInputMode = if (spec.focusable) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE else WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        if (vp.y != oldY || vp.flags != oldFlags) try { w.updateViewLayout(visual, vp) } catch (_: Throwable) {}

        // αφή: ακριβώς πάνω στο νησάκι
        val camX = cutout.centerX() + dp(spec.offX)
        val top = vp.y + dp(WINDOW_PAD) - dp(6f)
        val (l, r, h) = spec.rect
        val target = Rect(camX + dp(l), top, camX + dp(r), top + dp(h) + dp(12f))
        tp.flags = touchFlags(spec.touchable)
        shrinkJob?.cancel()
        val grown = Rect(curRect).apply { if (isEmpty) set(target) else union(target) }
        applyTouchRect(grown)
        if (grown != target) {
            shrinkJob = scope.launch {
                delay((700 / spec.speed.coerceAtLeast(0.3f)).toLong())
                applyTouchRect(target)
            }
        }
    }

    private fun applyTouchRect(r: Rect) {
        val tp = tParams ?: return
        curRect = Rect(r)
        tp.x = r.left
        tp.y = r.top
        tp.width = r.width().coerceAtLeast(1)
        tp.height = r.height().coerceAtLeast(1)
        try { wm?.updateViewLayout(touch, tp) } catch (_: Throwable) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Island.setLandscape(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        computeCutout()
        curRect = Rect()
        val vp = vParams ?: return
        positionVisual(Prefs.value.offsetX, Prefs.value.offsetY, Prefs.value.pillHeight)
        try { wm?.updateViewLayout(visual, vp) } catch (_: Throwable) {}
        lastSpec?.let { applySpec(it) }
    }

    // ---------------- system events ----------------

    private fun registerEvents() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Throwable) {
        }
        val km = getSystemService(KeyguardManager::class.java)
        Island.setLocked(km?.isKeyguardLocked == true)
    }

    private fun onSystemEvent(i: Intent, sticky: Boolean) {
        when (i.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val pct = if (level >= 0) level * 100 / scale else 100
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                Island.setBattery(pct, charging)
                val th = Prefs.value.lowBatteryLevel
                if (!charging && pct <= th && lastLowWarn > th && !sticky) {
                    Island.flash(IslandItem("lowbat", Kind.LOW_BATTERY, Feature.LOW_BATTERY, title = "Χαμηλή μπαταρία", value = pct, accent = Color(0xFFFF453A)), 5f)
                    Util.vibrate(longArrayOf(0, 60, 80, 60))
                }
                lastLowWarn = if (charging) 101 else pct
            }
            Intent.ACTION_POWER_CONNECTED -> {
                Island.scope.launch {
                    delay(350) // περιμένουμε το νέο επίπεδο
                    val s = Island.state.value
                    Island.flash(IslandItem("charging", Kind.CHARGING, Feature.CHARGING, title = "Φόρτιση", value = s.battery, flag = true, accent = Color(0xFF34C759)))
                    Util.vibrate(longArrayOf(0, 30, 60, 30))
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Island.flash(IslandItem("charging", Kind.CHARGING, Feature.CHARGING, title = "Αποσυνδέθηκε", value = Island.state.value.battery, flag = false, accent = Color(0xFF8E8E93)), 2f)
            }
            AudioManager.RINGER_MODE_CHANGED_ACTION -> if (!sticky) {
                val mode = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                val (t, c) = when (mode) {
                    AudioManager.RINGER_MODE_SILENT -> "Σίγαση" to Color(0xFFFF453A)
                    AudioManager.RINGER_MODE_VIBRATE -> "Δόνηση" to Color(0xFFFF9F0A)
                    else -> "Ήχος" to Color(0xFF30D158)
                }
                Island.flash(IslandItem("ringer", Kind.RINGER, Feature.RINGER, title = t, value = mode, accent = c), 2.2f)
            }
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> if (!sticky) {
                val nm = getSystemService(NotificationManager::class.java)
                val on = nm?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } == true
                Island.flash(IslandItem("dnd", Kind.DND, Feature.DND, title = "Μην ενοχλείτε", subtitle = if (on) "Ενεργό" else "Ανενεργό", flag = on, accent = Color(0xFF7D6BFF)), 2.4f)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (!sticky) {
                val dev: BluetoothDevice? = btDevice(i)
                var name = "Bluetooth"
                var audio = true
                var battery = -1
                try {
                    name = dev?.name ?: name
                    val cls = dev?.bluetoothClass?.majorDeviceClass
                    audio = cls == null || cls == BluetoothClass.Device.Major.AUDIO_VIDEO || cls == BluetoothClass.Device.Major.UNCATEGORIZED || cls == BluetoothClass.Device.Major.WEARABLE
                    battery = (dev?.javaClass?.getMethod("getBatteryLevel")?.invoke(dev) as? Int) ?: -1
                } catch (_: Throwable) {
                }
                if (audio) {
                    val connected = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
                    Island.flash(
                        IslandItem(
                            "bt", Kind.BLUETOOTH, Feature.BLUETOOTH, title = name,
                            subtitle = if (connected) "Συνδέθηκε" else "Αποσυνδέθηκε",
                            value = battery.takeIf { it in 0..100 }, flag = connected, accent = Color(0xFF64D2FF),
                        ), 3f
                    )
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                Island.setLocked(false)
                Island.flash(IslandItem("unlock", Kind.UNLOCK, Feature.UNLOCK, title = "Ξεκλείδωμα", accent = Color.White), 1.4f)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Island.setLocked(true)
                Island.collapse(); Island.closePanel(); Island.clearMoment()
            }
            Intent.ACTION_SCREEN_ON -> {
                val km = getSystemService(KeyguardManager::class.java)
                Island.setLocked(km?.isKeyguardLocked == true)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun btDevice(i: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    // ---------------- quick actions ----------------

    /** «Βελτιστοποίηση»: κλείνει τις πρόσφατες εφαρμογές σαν το Device Care. */
    fun cleanRam() {
        Island.closePanel()
        val before = SysStats.availMb(this)
        Island.scope.launch {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            delay(750)
            var clicked = false
            for (attempt in 0 until 3) {
                clicked = clickByText(listOf("Κλείσιμο όλων", "Κλείσιμο όλα", "Close all", "Clear all", "Εκκαθάριση όλων"))
                if (clicked) break
                delay(350)
            }
            if (!clicked) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                openDeviceCare()
                return@launch
            }
            delay(1400)
            val after = SysStats.availMb(this@IslandService)
            val freed = (after - before).coerceAtLeast(0)
            Util.vibrate(longArrayOf(0, 25, 50, 25))
            Island.flash(
                IslandItem(
                    "ram_clean", Kind.INFO, Feature.NOTIFICATION, title = "Βελτιστοποιήθηκε",
                    subtitle = if (freed > 0) "Ελευθερώθηκαν $freed MB" else "Η RAM καθάρισε",
                    accent = Color(0xFF30D158), flag = true,
                ), 2.5f
            )
        }
    }

    private fun clickByText(labels: List<String>): Boolean {
        val root = try { rootInActiveWindow } catch (_: Throwable) { null } ?: return false
        for (l in labels) {
            val nodes = try { root.findAccessibilityNodeInfosByText(l) } catch (_: Throwable) { null } ?: continue
            for (n in nodes) {
                var x: android.view.accessibility.AccessibilityNodeInfo? = n
                var depth = 0
                while (x != null && !x.isClickable && depth < 4) { x = x.parent; depth++ }
                if (x != null && x.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }

    private fun openDeviceCare() {
        val candidates = listOf("com.samsung.android.lool", "com.samsung.android.sm_cn", "com.samsung.android.sm")
        for (p in candidates) {
            val i = packageManager.getLaunchIntentForPackage(p) ?: continue
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Throwable) {}
        }
    }

    fun quick(action: String) {
        Island.closePanel()
        when (action) {
            "ram" -> cleanRam()
            "screenshot" -> Island.scope.launch { delay(450); performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) }
            "lock" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quicksettings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "torch" -> Torch.toggle(this)
            "settings" -> try {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        Torch.unregister()
        try { visual?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        try { touch?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        visual = null; touch = null
        owner.stop()
        scope.cancel()
        if (Island.service === this) { Island.serviceRef = null; Island.serviceActive.value = false }
        super.onDestroy()
    }

    companion object {
        const val WINDOW_PAD = 16f
    }
}
