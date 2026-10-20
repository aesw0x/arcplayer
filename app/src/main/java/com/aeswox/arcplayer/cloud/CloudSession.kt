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
            // Under the system's cache rather than the app's files: a caption copied out of Telegram's
            // hands is worth nothing to anyone if the system wants its room back, and it is a few
            // kilobytes rather than a film.
            captionDirectory = File(context.cacheDir, "captions").absolutePath,
        )
        repository = created
        appliedCredentials = wanted
        return created
    }

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
