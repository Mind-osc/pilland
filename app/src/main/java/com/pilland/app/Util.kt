package com.pilland.app

import android.app.ActivityOptions
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PillandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        Prefs.init(this)
    }

    companion object {
        lateinit var app: PillandApp
    }
}

object Util {
    val ctx: Context get() = PillandApp.app

    fun vibrate(pattern: LongArray) {
        if (!Prefs.value.haptics) return
        try {
            val vm = ctx.getSystemService(VibratorManager::class.java) ?: return
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Throwable) {
        }
    }

    fun tick() {
        if (!Prefs.value.haptics) return
        try {
            val vm = ctx.getSystemService(VibratorManager::class.java) ?: return
            vm.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (_: Throwable) {
        }
    }

    private fun bgOptions(): android.os.Bundle? =
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null

    fun sendPending(pi: PendingIntent, fill: Intent? = null) {
        try {
            pi.send(ctx, 0, fill, null, null, null, bgOptions())
        } catch (_: Throwable) {
        }
    }

    fun openApp(item: IslandItem?) {
        if (item == null) return
        val ci = item.contentIntent
        if (ci != null) {
            sendPending(ci); return
        }
        val pkg = item.pkg ?: (if (item.kind == Kind.MEDIA) Island.media.value?.pkg else null) ?: return
        if (pkg == "demo") return
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Throwable) {
        }
    }

    fun appName(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Throwable) {
        pkg
    }

    private val iconCache = HashMap<String, Pair<Bitmap, Color>>()

    fun appIcon(pkg: String): Pair<Bitmap, Color>? {
        iconCache[pkg]?.let { return it }
        return try {
            val d: Drawable = ctx.packageManager.getApplicationIcon(pkg)
            val b = d.toBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val r = b to dominantColor(b)
            iconCache[pkg] = r
            r
        } catch (_: Throwable) {
            null
        }
    }

    fun scaled(b: Bitmap, max: Int = 256): Bitmap {
        if (b.width <= max && b.height <= max) return b
        val s = max.toFloat() / maxOf(b.width, b.height)
        return try {
            Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
        } catch (_: Throwable) {
            b
        }
    }

    /** Βρίσκει το πιο "ζωντανό" χρώμα μιας εικόνας (για αυτόματο accent). */
    fun dominantColor(src: Bitmap): Color {
        return try {
            val b = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
            val small = Bitmap.createScaledBitmap(b, 24, 24, true)
            var x = 0.0; var y = 0.0; var wSum = 0.0; var vSum = 0.0
            val hsv = FloatArray(3)
            for (i in 0 until 24) for (j in 0 until 24) {
                val p = small.getPixel(i, j)
                if ((p ushr 24) < 128) continue
                android.graphics.Color.colorToHSV(p, hsv)
                val w = (hsv[1] * hsv[2]).toDouble().let { it * it }
                if (w < 0.02) continue
                val rad = Math.toRadians(hsv[0].toDouble())
                x += cos(rad) * w; y += sin(rad) * w; wSum += w; vSum += hsv[2] * w
            }
            if (wSum < 0.05) return Color(0xFFB0B8C8)
            var hue = Math.toDegrees(atan2(y, x)).toFloat()
            if (hue < 0) hue += 360f
            val v = (vSum / wSum).toFloat().coerceIn(0.75f, 1f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.72f, v)))
        } catch (_: Throwable) {
            Color(0xFFB0B8C8)
        }
    }

    fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun chronoText(item: IslandItem, now: Long): String {
        val base = item.chronoBase ?: return ""
        val ref = item.chronoPausedAt ?: now
        val ms = if (item.countDown) (base - ref + 999) else (ref - base)
        return formatClock(ms)
    }
}

object Reply {
    fun send(item: IslandItem, text: String): Boolean {
        val a = item.actions.firstOrNull { it.remoteInput != null } ?: return false
        val ri = a.remoteInput ?: return false
        val pi = a.intent ?: return false
        return try {
            val intent = Intent()
            val bundle = android.os.Bundle()
            bundle.putCharSequence(ri.resultKey, text)
            android.app.RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
            Util.sendPending(pi, intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
