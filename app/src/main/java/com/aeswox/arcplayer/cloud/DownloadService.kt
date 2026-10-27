package com.aeswox.arcplayer.cloud

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * The reason a download outlives the screen that started it.
 *
 * Nothing in this app cancels a running fetch — the promise and the pump both live on a scope owned by
 * the process, not by the activity ([CloudSession]). What has been killing them is Samsung: an app with
 * no visible component and no service is the first thing the system takes back when it wants room or
 * battery, and the download dies with the process and does not come back, because a fetch interrupted by
 * a death is not a fetch that is running.
 *
 * A foreground service is the announcement that says otherwise: the viewer can see the work happening,
 * and while it does, this process is not a candidate. The type is `dataSync`, which is exactly what it
 * is, and it comes with Android's own limit of six active hours a day — a film that needs more than that
 * needs the viewer to open the app again, and the row saying so is better than a silent stall.
 *
 * It holds no state of its own and reads none from the intent that started it. Every number it shows
 * comes from the live repository, so there is no second copy of the truth to drift out of date, and no
 * chance of a second TDLib client — those are process-lifetime objects, and building one here would leak
 * the one the interface is using.
 */
class DownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadNotifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = CloudSession.peekRepository()?.downloadNotice?.value
        if (state == null) {
            // Started with nothing to keep — a redelivery after a death, or a race against the last
            // byte. There is no work to protect, and a notification claiming otherwise is a lie about
            // the battery.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!goForeground(state)) {
            stopSelf()
            return START_NOT_STICKY
        }
        DownloadNotifier.postRunning(this, state)
        holdTheCpu()
        return START_NOT_STICKY
    }

    /**
     * Claim the foreground, or say plainly that the system would not grant it.
     *
     * The refusal is a live possibility on a device this app cannot predict — Android 14 audits which
     * `dataSync` starts it will honour — and an exception thrown here lands in the middle of a started
     * service, which takes the whole process and every fetch in it down with it. Stopping quietly costs
     * the protection and nothing else: the film keeps arriving, the repository keeps its row on the
     * shade, and the fetch simply runs at the mercy of whoever decides this process is no longer
     * interesting.
     */
    private fun goForeground(state: DownloadNotices.Running): Boolean =
        runCatching {
            ServiceCompat.startForeground(
                this,
                DownloadNotifier.RUNNING_ID,
                DownloadNotifier.buildRunning(this, state),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }.onFailure { failure ->
            Log.w(TAG, "download service refused the foreground; fetch runs unprotected", failure)
        }.isSuccess
    override fun onDestroy() {
        releaseTheCpu()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Keep the processor awake while bytes are moving.
     *
     * A download that starts with the phone in a pocket is the common case, and without this the link is
     * suspended as the device dozes and the fetch crawls — which is indistinguishable from a slow server
     * from the outside. The lock is released with the service, and the service stops with the last film,
     * so its lifetime is exactly the download's. The timeout is a seatbelt: a bug that failed to release
     * this would otherwise hold the phone awake through the night.
     */
    private fun holdTheCpu() {
        if (wakeLock?.isHeld == true) return
        val power = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseTheCpu() {
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    private companion object {
        const val TAG = "ArcCloud"
        const val WAKE_LOCK_TAG = "ArcPlayer:downloads"
        const val WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
    }
}
