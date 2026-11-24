package com.pilland.app

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PillNotificationService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var pauseJob: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pickController(list ?: emptyList())
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            main.post { publishMedia() }
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            main.post { publishMedia() }
        }
        override fun onSessionDestroyed() {
            main.post { setController(null) }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        try {
            msm = getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(this, PillNotificationService::class.java)
            msm?.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            pickController(msm?.getActiveSessions(cn) ?: emptyList())
        } catch (_: Throwable) {
        }
        try {
            activeNotifications?.forEach { handle(it, initial = true) }
        } catch (_: Throwable) {
        }
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) {}
        setController(null)
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        main.post { handle(sbn, initial = false) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        main.post { Island.removeByNotifKey(sbn.key) }
    }

    // ---------------- media ----------------

    private fun pickController(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) setController(best)
        else publishMedia()
    }

    private fun setController(c: MediaController?) {
        try { controller?.unregisterCallback(mediaCallback) } catch (_: Throwable) {}
        controller = c
        Island.mediaController = c
        try { c?.registerCallback(mediaCallback, main) } catch (_: Throwable) {}
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val md = c?.metadata
        val ps = c?.playbackState
        val st = ps?.state ?: PlaybackState.STATE_NONE
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || md == null || title.isNullOrBlank() || st == PlaybackState.STATE_NONE || st == PlaybackState.STATE_STOPPED || st == PlaybackState.STATE_ERROR) {
            if (Island.media.value?.pkg != "demo") {
                Island.media.value = null
                Island.remove("media")
            }
            return
        }
        val playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val prev = Island.media.value
        val sameTrack = prev != null && prev.title == title && prev.artist == artist && prev.pkg == c.packageName
        var artBmp = if (sameTrack) prev?.art else null
        var accent = if (sameTrack) prev?.accent else null
        if (!sameTrack) {
            val raw: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (raw != null) {
                val sc = Util.scaled(raw, 256)
                artBmp = sc.asImageBitmap()
                accent = Util.dominantColor(sc)
            } else {
                Util.appIcon(c.packageName)?.let { accent = it.second }
            }
        }
        val info = MediaInfo(
            title = title, artist = artist, art = artBmp, accent = accent, playing = playing,
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = ps?.position ?: 0L, updateTime = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            pkg = c.packageName, appName = Util.appName(c.packageName),
        )
        Island.media.value = info
        val cfg = Prefs.value
        if (c.packageName in cfg.excludedApps) { Island.remove("media"); return }

        pauseJob?.cancel()
        val item = IslandItem(
            id = "media", kind = Kind.MEDIA, feature = Feature.MEDIA,
            priority = if (playing) 50 else 30, title = title, subtitle = artist,
            appName = info.appName, icon = artBmp, accent = accent, flag = playing,
            pkg = c.packageName, contentIntent = c.sessionActivity,
        )
        Island.upsert(item)
        if (!playing && cfg.hidePausedMedia) {
            pauseJob = Island.scope.launch {
                delay(8_000)
                if (Island.media.value?.playing == false) Island.remove("media")
            }
        }
    }

    // ---------------- notifications ----------------

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val n = sbn.notification ?: return
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val cfg = Prefs.value
        if (pkg in cfg.excludedApps) return
        val ex = n.extras ?: return
        // Το media το χειρίζεται το MediaSession.
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val title = (ex.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim() ?: ""
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val promoted = (n.flags and FLAG_PROMOTED_ONGOING) != 0
        val iconPair = Util.appIcon(pkg)
        val large = loadLargeIcon(n)
        val accent = when {
            n.color != 0 && n.color != android.graphics.Color.BLACK && n.color != android.graphics.Color.WHITE ->
                androidx.compose.ui.graphics.Color(n.color or 0xFF000000.toInt())
            else -> iconPair?.second
        }
        val appName = Util.appName(pkg)
        val actions = n.actions?.take(3)?.map { a ->
            IslandAction(label = a.title?.toString() ?: "", intent = a.actionIntent, remoteInput = a.remoteInputs?.firstOrNull { it.allowFreeFormInput })
        } ?: emptyList()
        val icon = (large ?: iconPair?.first)?.asImageBitmap()
        val id = "n:" + sbn.key

        val showChrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val pCur = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val pInd = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val isCall = n.category == Notification.CATEGORY_CALL || ex.containsKey("android.callType")

        val base = IslandItem(
            id = id, kind = Kind.INFO, feature = Feature.NOTIFICATION, title = title.ifEmpty { appName },
            subtitle = text.ifEmpty { sub }, appName = appName, icon = icon, accent = accent, actions = actions,
            pkg = pkg, contentIntent = n.contentIntent, notifKey = sbn.key, postedAt = sbn.postTime,
        )

        when {
            isCall -> {
                val incoming = n.fullScreenIntent != null && !showChrono
                Island.upsert(
                    base.copy(
                        kind = Kind.CALL, feature = Feature.CALL, priority = 100,
                        subtitle = if (incoming) "Εισερχόμενη κλήση" else base.subtitle,
                        chronoBase = if (showChrono) n.`when` else null,
                        accent = androidx.compose.ui.graphics.Color(0xFF34C759),
                        autoExpand = incoming && cfg.autoExpandCalls && !initial,
                    )
                )
            }
            showChrono && ongoing -> Island.upsert(
                base.copy(kind = Kind.TIMER, feature = Feature.TIMER, priority = 60, chronoBase = n.`when`, countDown = countDown)
            )
            (pMax > 0 && (ongoing || promoted)) || (pInd && ongoing) -> Island.upsert(
                base.copy(
                    kind = if (promoted) Kind.LIVE else Kind.PROGRESS,
                    feature = if (promoted) Feature.LIVE else Feature.PROGRESS,
                    priority = if (promoted) 55 else 45,
                    progress = if (pMax > 0) (pCur.toFloat() / pMax).coerceIn(0f, 1f) else null,
                    indeterminate = pInd,
                )
            )
            promoted -> Island.upsert(base.copy(kind = Kind.LIVE, feature = Feature.LIVE, priority = 55))
            ongoing -> {}
            initial -> {}
            else -> {
                if ((n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0 && Island.state.value.moment?.notifKey == sbn.key) {
                    // Ενημέρωση ίδιας ειδοποίησης χωρίς ξανά alert.
                } else {
                    val importance = try {
                        val r = Ranking()
                        if (currentRanking.getRanking(sbn.key, r)) r.importance else NotificationManager.IMPORTANCE_DEFAULT
                    } catch (_: Throwable) { NotificationManager.IMPORTANCE_DEFAULT }
                    if (importance >= NotificationManager.IMPORTANCE_DEFAULT && (title.isNotEmpty() || text.isNotEmpty())) {
                        Island.flash(base.copy(id = id + ":" + sbn.postTime, kind = Kind.NOTIFICATION))
                    }
                }
            }
        }
    }

    private fun loadLargeIcon(n: Notification): Bitmap? {
        return try {
            val ic: Icon? = n.getLargeIcon()
            val d = ic?.loadDrawable(this) ?: return null
            val w = d.intrinsicWidth.coerceIn(1, 256)
            val h = d.intrinsicHeight.coerceIn(1, 256)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            d.setBounds(0, 0, w, h)
            d.draw(cv)
            Util.scaled(b, 160)
        } catch (_: Throwable) {
            null
        }
    }

    /** Κάνει αποστολή απάντησης μέσω RemoteInput. */
    fun reply(item: IslandItem, text: String): Boolean {
        val a = item.actions.firstOrNull { it.remoteInput != null } ?: return false
        val ri = a.remoteInput ?: return false
        val pi = a.intent ?: return false
        return try {
            val intent = android.content.Intent()
            val bundle = android.os.Bundle()
            bundle.putCharSequence(ri.resultKey, text)
            android.app.RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
            Util.sendPending(pi, intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        /** Notification.FLAG_PROMOTED_ONGOING (Android 16 Live Updates). */
        const val FLAG_PROMOTED_ONGOING = 0x00040000
        var instance: PillNotificationService? = null

        fun cancelRequest(key: String) {
            try { instance?.cancelNotification(key) } catch (_: Throwable) {}
        }
    }
}
