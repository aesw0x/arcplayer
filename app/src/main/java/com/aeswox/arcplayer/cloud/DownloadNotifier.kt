package com.aeswox.arcplayer.cloud

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aeswox.arcplayer.MainActivity
import com.aeswox.arcplayer.formatFileSize

/**
 * The notifications this app posts about films it is keeping, and the channels they arrive on.
 *
 * Two rows rather than one because they answer different questions at different times. The running row is
 * a status the viewer reads off the shade and then ignores: it is ongoing, silent, and carries a bar, so
 * it must never beep or it would do so for every packet group Telegram pushes. The finished row is a piece
 * of news: it makes a sound once, it can be dismissed, and it stays useful after the download row has
 * stopped existing — which is why it is a separate id on a separate channel instead of the same row
 * rewritten.
 *
 * Nothing here decides *what* the progress means; that is [DownloadNotices]. This only turns a number into
 * something the notification shade can draw.
 */
object DownloadNotifier {

    /** Id of the ongoing row, which doubles as the foreground service's notification. */
    const val RUNNING_ID = 7001

    private const val CHANNEL_RUNNING = "arc_downloads_running"
    private const val CHANNEL_FINISHED = "arc_downloads_finished"
    private const val FIRST_FINISHED_ID = 7100

    /**
     * A download row that says something even before the first byte is counted.
     *
     * The service has to name a notification the moment it is started, and it can be started in the beat
     * between the viewer's tap and Telegram's answer.
     */
    val WAITING = DownloadNotices.Running(films = 1, leadingTitle = "Download", doneBytes = 0L, totalBytes = 0L)

    /** Make both channels, once per install. Safe to call from anywhere that is about to post. */
    fun ensureChannels(context: Context) {
        val manager = manager(context) ?: return
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            "Keeping films",
            // Low rather than min: min silences the channel for good on some builds, and a silent
            // download row is the point — but it should still be a row the viewer can raise.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "How far a download has come"
            setShowBadge(false)
        }
        val finished = NotificationChannel(
            CHANNEL_FINISHED,
            "Finished downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A film that is now on this device"
        }
        manager.createNotificationChannel(running)
        manager.createNotificationChannel(finished)
    }

    /** Replace the ongoing row, or draw it for the first time. */
    fun buildRunning(context: Context, running: DownloadNotices.Running): Notification {
        val readout = if (running.totalBytes > 0L) {
            "${running.percent}% · ${formatFileSize(running.doneBytes)} of ${formatFileSize(running.totalBytes)}"
        } else {
            "Waiting for Telegram"
        }
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (running.films == 1) running.leadingTitle else "${running.films} films")
            .setContentText(readout)
            // Names the film the bar is mostly made of, so a second request does not hide which one is
            // nearly done.
            .setSubText(if (running.films == 1) null else running.leadingTitle)
            .setProgress(100, running.percent, running.totalBytes <= 0L)
            .setContentIntent(openTheApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    fun postRunning(context: Context, running: DownloadNotices.Running) {
        val manager = manager(context) ?: return
        manager.notify(RUNNING_ID, buildRunning(context, running))
    }

    /** Take the ongoing row back, for the moment nothing is being fetched any more. */
    fun clearRunning(context: Context) {
        manager(context)?.cancel(RUNNING_ID)
    }

    /**
     * Say a film landed, and where it landed.
     *
     * The folder is in the body on purpose: the whole point of filing a download in the media catalog is
     * that the rest of the phone can reach it, and a person who was told "it's on the device" without
     * being told where is the person who goes looking for an hour.
     */
    fun postFinished(context: Context, title: String, sizeBytes: Long) {
        val manager = manager(context) ?: return
        val notice = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("$title is available offline")
            .setContentText("${formatFileSize(sizeBytes)} · in ${KeptMedia.FOLDER.trimEnd('/')}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$title is on this device and needs no internet. It is in ${KeptMedia.FOLDER.trimEnd('/')}, " +
                        "which the gallery and any other player can open."
                )
            )
            .setContentIntent(openTheApp(context))
            .setAutoCancel(true)
            .build()
        // One row per film rather than one reused row: two films finishing ten minutes apart are two
        // pieces of news, and overwriting the first would hide a download the viewer is still looking for.
        manager.notify(FIRST_FINISHED_ID + finishedIdSlot(title), notice)
    }

    /** Say a film is not coming, and why in the words a person would use. */
    fun postFailed(context: Context, title: String, reason: String) {
        val manager = manager(context) ?: return
        val notice = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("$title couldn't be kept")
            .setContentText(reason)
            .setContentIntent(openTheApp(context))
            .setAutoCancel(true)
            .build()
        manager.notify(FIRST_FINISHED_ID + finishedIdSlot(title), notice)
    }

    /**
     * Whether the viewer is still going to see any of this.
     *
     * Android 13 asks, and a download started before the answer arrives still runs — the service and its
     * row are what keep the process alive, and neither needs the permission to do that. This is for the
     * two places that decide whether to nag.
     *
     * Below 13 the answer is always yes, and it matters that the question is not asked: the permission
     * does not exist on those installs, so checking it reports a refusal and the app would spend its one
     * ask on a dialog the system answers no to without drawing anything.
     */
    fun canNotify(context: Context): Boolean =
        !needsPermissionAsk(context) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether this install is one of the ones that has to be asked. */
    fun needsPermissionAsk(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /**
     * Bring the app back if it is already running, and start it if it is not.
     *
     * Clear-top and single-top together because the shelf, the info page and the player are all states
     * inside one activity: a second instance would mean a second libVLC holding the same film, which is
     * the failure this app has already been debugged for once.
     */
    private fun openTheApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Keeps one finished film in one slot, so a re-download refreshes its row instead of adding a second. */
    private fun finishedIdSlot(title: String): Int = (title.hashCode() and 0x3FFF) + 1

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
}
