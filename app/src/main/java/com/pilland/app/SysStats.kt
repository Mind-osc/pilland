package com.pilland.app

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** RAM & CPU για το πάνελ. */
object SysStats {
    /** Ποσοστό χρησιμοποιημένης RAM. */
    fun ramPct(ctx: Context): Int {
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return -1
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            if (mi.totalMem <= 0) -1 else (((mi.totalMem - mi.availMem) * 100) / mi.totalMem).toInt()
        } catch (_: Throwable) { -1 }
    }

    fun availMb(ctx: Context): Long {
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return -1
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.availMem / (1024 * 1024)
        } catch (_: Throwable) { -1 }
    }

    /**
     * Φόρτος CPU κατά προσέγγιση: τρέχουσα / μέγιστη συχνότητα ανά πυρήνα.
     * (Το /proc/stat δεν διαβάζεται από εφαρμογές στο νέο Android.) -1 αν δεν επιτρέπεται.
     */
    fun cpuPct(): Int {
        return try {
            var sum = 0.0
            var n = 0
            for (i in 0 until 12) {
                val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
                val cur = File(base + "scaling_cur_freq")
                if (!cur.exists()) continue
                val c = cur.readText().trim().toDoubleOrNull() ?: continue
                val maxF = File(base + "cpuinfo_max_freq").takeIf { it.exists() }?.readText()?.trim()?.toDoubleOrNull()
                    ?: File(base + "scaling_max_freq").readText().trim().toDoubleOrNull() ?: continue
                val minF = File(base + "cpuinfo_min_freq").takeIf { it.exists() }?.readText()?.trim()?.toDoubleOrNull() ?: 0.0
                if (maxF <= minF) continue
                sum += ((c - minF) / (maxF - minF)).coerceIn(0.0, 1.0)
                n++
            }
            if (n == 0) -1 else (sum / n * 100).toInt()
        } catch (_: Throwable) { -1 }
    }
}
