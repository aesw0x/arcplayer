package com.aeswox.arcplayer.cloud

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * One Telegram session, owned by the process instead of by the screen that shows it.
 *
 * Two TDLib rules force this shape: the binding has no close, so a client outlives whoever created
 * it, and `setTdlibParameters` is accepted exactly once per client. A client kept inside a
 * `remember` block would be rebuilt on every recomposition that changes a credential and then
 * silently leak, so the pair is built here and reused.
 */
object CloudSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var repository: CloudRepository? = null
    private var appliedCredentials: Credentials? = null

    /**
     * The repository for these credentials, creating or replacing it only when they actually move.
     *
     * Empty credentials still produce a repository: the UI needs somewhere to report
     * `MISSING_CREDENTIALS` and to accept an api_id typed in by hand.
     */
    @Synchronized
    fun repositoryFor(context: Context, apiId: Int, apiHash: String): CloudRepository {
        val trimmedHash = apiHash.trim()
        val hasCredentials = apiId > 0 && trimmedHash.isNotEmpty()
        val wanted = Credentials(apiId, trimmedHash)

        repository?.let { existing ->
            if (!hasCredentials || appliedCredentials == wanted) return existing
        }

        val (databaseDir, filesDir) = storageDirs(context)
        val bridge = TdBridge(
            apiId = if (hasCredentials) apiId else 0,
            apiHash = if (hasCredentials) trimmedHash else "",
            databaseDirectory = databaseDir.absolutePath,
            filesDirectory = filesDir.absolutePath,
        )
        val created = CloudRepository(
            bridge = bridge,
            scope = scope,
            hasCredentials = hasCredentials,
            // Beside Telegram's own file folder rather than inside it, so a shelf list read off disk
            // can never be mistaken for one of its video files.
            manifestPath = File(rootDir(context), "shelf_manifest.txt").absolutePath,
            subtitleManifestPath = File(rootDir(context), "shelf_subtitles.txt").absolutePath,
            // Beside the two shelf lists rather than inside Telegram's file folder: this one names files
            // the app promised to keep, and a sweep that walks the folder mistaking one for a video is
            // the failure this whole ledger exists to prevent.
            ledgerPath = File(rootDir(context), "download_ledger.txt").absolutePath,
            // Under the system's cache rather than the app's files: a caption copied out of Telegram's
            // hands is worth nothing to anyone if the system wants its room back, and it is a few
            // kilobytes rather than a film.
            captionDirectory = File(context.cacheDir, "captions").absolutePath,
            // The device's own media catalog, which is the only place on a phone that satisfies both
            // halves of a download: Telegram cannot empty it, and the gallery can see what is in it.
            keptMedia = KeptMedia(context.applicationContext),
            // Held as the application context because this object is kept by the process, and the work
            // it now hosts outlives the screen that asked for it: the download service is started from
            // here, and is meant to keep running after every activity is gone.
            appContext = context.applicationContext,
        )
        repository = created
        appliedCredentials = wanted
        return created
    }

    /**
     * The repository this process is already using, if there is one.
     *
     * [DownloadService] reads its whole picture through this, and must not ask for one the way the
     * interface does: `setTdlibParameters` is accepted once per client, so a service that passed empty or
     * different credentials would replace the live repository with a second client and leak the first —
     * and the viewer's shelf would go quiet on a phone that only wanted a progress bar. Null means no
     * session was ever built, which is a service with nothing to protect.
     */
    @Synchronized
    fun peekRepository(): CloudRepository? = repository

    private fun storageDirs(context: Context): Pair<File, File> {
        val root = rootDir(context)
        val database = File(root, "database").apply { mkdirs() }
        val files = File(root, "files").apply { mkdirs() }
        return database to files
    }

    /** The corner of internal storage this app owns; TDLib fills it below with its database and files. */
    private fun rootDir(context: Context): File = File(context.applicationContext.filesDir, "telegram")

    private data class Credentials(val apiId: Int, val apiHash: String)
}
