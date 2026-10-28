package com.aeswox.arcplayer.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.aeswox.arcplayer.BuildConfig
import com.aeswox.arcplayer.metadata.HttpJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the fetch of the new binary has got to. */
sealed class UpdateDownload {
    object Idle : UpdateDownload()
    class Running(val fraction: Float) : UpdateDownload()
    object Ready : UpdateDownload()
    object Failed : UpdateDownload()
}

/** What a question asked of GitHub came back with. */
sealed class UpdateCheck {
    /** Nothing this phone could install is out. */
    object Current : UpdateCheck()

    /** GitHub could not be reached, or answered with something that is not a release. */
    object Unreachable : UpdateCheck()

    /** A newer release, with an APK this architecture can actually run. */
    class Available(val release: Release) : UpdateCheck()
}

/**
 * Asks GitHub whether a newer signed release exists, and fetches it when the viewer says yes.
 *
 * The asking is one GET over the app's existing hand-rolled JSON channel — an updater is not worth a
 * networking dependency.
 */
internal class UpdateManager(private val context: Context) {

    private var downloadId = -1L

    /**
     * What GitHub said.
     *
     * [UpdateCheck.Unreachable] is kept apart from [UpdateCheck.Current] because the two mean different
     * things to someone who pressed a button, even though a card nobody asked for says neither.
     */
    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val payload = HttpJson.get(LATEST_RELEASE) ?: return@withContext UpdateCheck.Unreachable
        val assets = payload.optJSONArray("assets") ?: return@withContext UpdateCheck.Unreachable
        val tag = payload.optString("tag_name")
        if (tag.isEmpty()) return@withContext UpdateCheck.Unreachable
        val files = buildList {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.isNotEmpty() && url.isNotEmpty()) add(ReleaseAsset(name, url))
            }
        }
        val release = Release(
            version = UpdateRules.versionFromTag(tag),
            notes = UpdateRules.notes(payload.optString("body", "")),
            assets = files
        )
        when {
            release.version.isEmpty() -> UpdateCheck.Unreachable
            !UpdateRules.isNewer(release.version, BuildConfig.VERSION_NAME) -> UpdateCheck.Current
            // Newer, but nothing in it this phone can run — so there is nothing to offer.
            UpdateRules.apkFor(release.assets, Build.SUPPORTED_ABIS.toList()) == null -> UpdateCheck.Current
            else -> UpdateCheck.Available(release)
        }
    }

    /**
     * Pulls the release's own APK into the app's private downloads folder.
     *
     * The system's downloader does the transfer because it is the one component that survives this app
     * going away mid-fetch, and it is the same one that puts a film on the phone. Progress is read back
     * out of it rather than tracked here, so a paused or retried transfer reports what it really is.
     */
    fun download(scope: CoroutineScope, release: Release, onState: (UpdateDownload) -> Unit) {
        val asset = UpdateRules.apkFor(release.assets, Build.SUPPORTED_ABIS.toList())
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (asset == null || directory == null) {
            onState(UpdateDownload.Failed)
            return
        }
        // A half-written file from an abandoned attempt must not be the thing the installer is handed.
        File(directory, fileName(release.version)).delete()
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Arc Player ${release.version}")
            .setDescription("Downloading the update")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName(release.version))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val manager = context.getSystemService(DownloadManager::class.java)
        downloadId = runCatching { manager.enqueue(request) }.getOrDefault(-1L)
        if (downloadId == -1L) {
            onState(UpdateDownload.Failed)
            return
        }
        scope.launch(Dispatchers.IO) {
            while (true) {
                val state = query(manager)
                withContext(Dispatchers.Main) { onState(state) }
                if (state !is UpdateDownload.Running) break
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    /**
     * Hands the finished file to the installer.
     *
     * False means the viewer has not let this app install packages — a setting only they can change, and
     * [installPermissionRequest] is the way there.
     */
    fun install(): Boolean {
        if (downloadId == -1L || !canInstallPackages()) return false
        val manager = context.getSystemService(DownloadManager::class.java)
        val uri = runCatching { manager.getUriForDownloadedFile(downloadId) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PACKAGE_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * The system's "allow installing other apps" screen, pre-aimed at this app.
     *
     * There is no probe for whether the device has one: under Android 11+ package visibility, asking the
     * package manager what resolves is a zero-result scan of an activity the app is not permitted to see.
     * So the caller starts it and takes the exception if the settings screen does not exist.
     */
    fun installPermissionRequest(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun query(manager: DownloadManager): UpdateDownload = runCatching {
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return@runCatching UpdateDownload.Failed
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> UpdateDownload.Ready
                DownloadManager.STATUS_FAILED -> UpdateDownload.Failed
                else -> {
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val fraction = if (total > 0L) done.toFloat() / total.toFloat() else 0f
                    UpdateDownload.Running(fraction.coerceIn(0f, 1f))
                }
            }
        }
    }.getOrDefault(UpdateDownload.Failed)

    private fun fileName(version: String): String = "ArcPlayer-$version.apk"

    private companion object {
        private const val LATEST_RELEASE = "https://api.github.com/repos/aesw0x/arcplayer/releases/latest"
        private const val PACKAGE_MIME = "application/vnd.android.package-archive"
        private const val PROGRESS_POLL_MS = 400L
    }
}
