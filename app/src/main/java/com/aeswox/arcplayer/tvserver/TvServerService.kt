package com.aeswox.arcplayer.tvserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aeswox.arcplayer.BuildConfig
import com.aeswox.arcplayer.playback.ResumeBlob
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

/**
 * Keeps a LAN port open so a TV can browse and play the films this phone is holding.
 *
 * The same argument [com.aeswox.arcplayer.cloud.DownloadService] makes for an arriving file applies to a
 * film going out: a process with no visible component is the first thing Android reclaims, and a server
 * that dies mid-film looks to a TV exactly like a network fault. `dataSync` is honest about what this is
 * doing — moving bytes, playing nothing — and Android 15's roughly six hours a day for that type is
 * longer than any single film, so it is accepted rather than worked around.
 *
 * Starts only when the viewer has opened the door. Nothing here binds a port on a phone that never asked.
 */
class TvServerService : Service() {

    companion object {
        private const val TAG = "ArcTV-Server"
        private const val CHANNEL_ID = "arctv_server"
        private const val NOTIFICATION_ID = 4712
        private const val WAKE_LOCK_TAG = "ArcPlayer:tvserver"

        /**
         * Rebind with whatever is stored now, rather than keeping whatever was stored at the start.
         *
         * The viewer needs this for one reason: a rotated access key is the whole point of rotating it, and
         * a server that carries on with the old one would make the button a lie.
         */
        private const val ACTION_RESTART = "com.aeswox.arcplayer.tvserver.RESTART"

        /** Debug only: a file `adb push` can drop where no root is needed, to prove Range by hand. */
        private const val DEMO_ID = "demo"
        private val DEMO_NAMES = listOf("demo.mkv", "demo.mp4", "demo.ts")

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TvServerService::class.java),
            )
        }

        fun restart(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TvServerService::class.java).setAction(ACTION_RESTART),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TvServerService::class.java))
        }
    }

    private var server: LocalStreamServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = TvServerConfigStore.load(this)
        if (!config.enabled) {
            // The door is shut, and the system redelivering a sticky start is not somebody opening it.
            stopSelf()
            return START_NOT_STICKY
        }

        // A sticky redelivery is not somebody asking for a new server, so only an explicit restart tears
        // the running one down; anything else that finds a bound port leaves it alone.
        val running = server
        if (running != null && intent?.action != ACTION_RESTART) return START_STICKY

        running?.stop()
        server = null
        startForegroundCompat(config)
        acquireWakeLock()
        // Opened beside the server rather than at the top of this method: an approved TV is handed
        // [config.token], so the two must come from the same read. A pairing key that the running server
        // would answer 401 to is a prompt the viewer tapped for nothing.
        TvPairing.open(this, config.token)

        val started = LocalStreamServer(
            port = config.port,
            token = config.token,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            libraryItems = { ShelfLibrary.items(this) },
            shelfCounts = { ShelfLibrary.counts(this) },
            openStream = { id -> openSource(id) },
            // The same two records the phone's own player reads, reached through the one object that owns
            // them, because a television writing this string directly is a second writer in a house where
            // the first one rewrote the whole map from memory every second and a half.
            savedPositions = { ResumeBlob.read(this) },
            savePosition = { id, positionMs, durationMs -> recordProgress(id, positionMs, durationMs) },
            openCaption = { id -> captionFileFor(id) },
        )
        try {
            started.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = started
            Log.i(TAG, "serving on 0.0.0.0:${config.port}")
        } catch (failure: IOException) {
            // The port is the one thing a viewer cannot fix from the TV, so say so in their notification.
            Log.w(TAG, "could not bind port ${config.port}", failure)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        TvPairing.close()
        releaseWakeLock()
        Log.i(TAG, "server stopped")
        super.onDestroy()
    }

    /**
     * The bytes behind one id.
     *
     * Only films whose every byte is on the device answer; everything else is a 404 rather than a stall.
     * Serving a file Telegram is still filling means aiming the phone's one download window at a second
     * viewer, and that is a negotiation with the player rather than a lookup.
     */
    private fun openSource(id: String): ByteSource? {
        if (BuildConfig.DEBUG && id == DEMO_ID) return demoSource()
        val item = ShelfLibrary.find(this, id) ?: return null
        return CatalogHandleSource.open(contentResolver, item.keptHandle, item.contentType)
    }

    /**
     * The place a television reported, kept only if the phone would have kept it itself.
     *
     * The phone's own length wins when it has one, because that figure came from a player that ran the
     * container; a television reports what it read while playing, which for a film neither side has
     * measured is the only number anybody has. An id this phone is not holding says so rather than
     * accepting a bookmark for a file the viewer cannot open.
     */
    private fun recordProgress(id: String, positionMs: Long, durationMs: Long): Boolean {
        val item = ShelfLibrary.find(this, id) ?: return false
        ResumeBlob.record(
            context = this,
            id = item.stableId,
            positionMs = positionMs,
            durationMs = if (item.durationMs > 0L) item.durationMs else maxOf(0L, durationMs),
        )
        return true
    }

    /**
     * The caption of this film that is already on the device, if the phone ever cached one.
     *
     * A television is offered what the viewer's own player would have shown and nothing more: asking
     * Telegram for a caption nobody asked for would put a LAN errand ahead of the film this phone is
     * already streaming.
     */
    private fun captionFileFor(id: String): File? =
        ShelfLibrary.find(this, id)?.let { ShelfLibrary.captionFileFor(this, it) }

    private fun demoSource(): ByteSource? {
        val configured = TvServerConfigStore.demoFile(this)?.let(::File)
        val file = if (configured?.isFile == true) {
            configured
        } else {
            val directory = getExternalFilesDir(null) ?: return null
            DEMO_NAMES.map { File(directory, it) }.firstOrNull { it.isFile } ?: return null
        }
        return CompleteFileSource.path(file, ShelfLibrary.contentTypeFor(file.name, ""))
    }

    private fun startForegroundCompat(config: TvServerConfig) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV access is on")
            .setContentText("Arc Player is serving your films on port ${config.port}")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setSilent(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV access", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the TV's library server running while it watches"
                setShowBadge(false)
            },
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
