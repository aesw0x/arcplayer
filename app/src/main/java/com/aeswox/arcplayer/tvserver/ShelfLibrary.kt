package com.aeswox.arcplayer.tvserver

import android.content.Context
import com.aeswox.arcplayer.cloud.CloudSession
import com.aeswox.arcplayer.cloud.CloudSubtitleItem
import com.aeswox.arcplayer.cloud.CloudVideoItem
import com.aeswox.arcplayer.cloud.DownloadLedger
import com.aeswox.arcplayer.cloud.ShelfManifest
import com.aeswox.arcplayer.cloud.SubtitleManifest
import com.aeswox.arcplayer.metadata.AudioTag
import com.aeswox.arcplayer.metadata.MediaGuess
import com.aeswox.arcplayer.metadata.MediaNameParser
import com.aeswox.arcplayer.metadata.PosterCache
import com.aeswox.arcplayer.metadata.TitleDetail
import com.aeswox.arcplayer.metadata.titleDetailKey
import com.aeswox.arcplayer.playback.ResumeBlob
import com.aeswox.arcplayer.playback.WatchedStamps
import com.aeswox.arcplayer.subtitle.SubtitleMatch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * One film the TV is allowed to ask for.
 *
 * [cloudVideo] names the Telegram message wherever the bytes come from. [keptHandle] is present when a
 * complete offline copy exists and is always preferred; otherwise the stream server opens a cloud read
 * session rather than hiding the film from the shelf.
 */
data class TvShelfItem(
    val stableId: Long,
    val fileName: String,
    val title: String,
    val subtitle: String?,
    val year: Int?,
    val overview: String,
    val genres: List<String>,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val contentType: String,
    val resumeMs: Long,
    /** When this copy was last opened, here or on the television; zero when it never has been. */
    val watchedAtMs: Long,
    /** What the copy's own name says its audio is, or null when it says nothing. */
    val audioCodec: String?,
    /** The TV cannot decode any track this copy names, so its picture will run with nothing to hear. */
    val needsRemux: Boolean,
    val cloudVideo: CloudVideoItem,
    val keptHandle: String?,
    val posterFile: File?,
    val backdropFile: File?,
    /**
     * Where the catalogue keeps these two pictures, so [artworkFile] can go and get one the phone has
     * never drawn. A shelf that only ever served cached files showed four posters out of 193 films.
     */
    val posterSource: String?,
    val backdropSource: String?,
) {
    /**
     * Average bits per second, or zero when the length is unknown.
     *
     * Zero rather than absent: the companion app calls `.toFixed()` on this straight off the response,
     * and a missing number is an exception on the TV that reads as a blank film page.
     */
    val bitrateMbps: Double
        get() = if (durationMs <= 0L || sizeBytes <= 0L) 0.0 else sizeBytes * 8.0 / durationMs / 1000.0
}

/** Which gate of [ShelfLibrary.items] closed, in the order the reads happen. */
data class ShelfCounts(
    val chatId: Long,
    val manifestHeaderChatId: String,
    val manifestRows: Int,
    val ledgerRows: Int,
    val keptComplete: Int,
    val keptInCatalog: Int,
    val served: Int,
)

/**
 * The shelf as a file list, read off the records the app already writes.
 *
 * Browsing deliberately does not go through [com.aeswox.arcplayer.cloud.CloudRepository]: a shelf read
 * should neither walk the channel nor claim a download window. The repository becomes involved only
 * when a real stream GET opens a film that has no complete offline copy.
 *
 * Every call is file I/O and runs on the server's own worker thread, never the main one.
 */
object ShelfLibrary {

    /** Every film on the shelf, in the channel's own order. */
    fun items(context: Context): List<TvShelfItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chatId = prefs.getLong(KEY_CHAT_ID, 0L)
        if (chatId == 0L) return emptyList()

        val manifest = read(CloudSession.manifestFile(context))
            .map { ShelfManifest.decode(it, chatId) }
            .getOrElse { return emptyList() }
        if (manifest.isEmpty()) return emptyList()

        val kept = read(CloudSession.ledgerFile(context))
            .map { DownloadLedger.decode(it) }
            .getOrElse { emptyMap() }

        // The player measures a length off the container when no upload carries one, and Telegram reports
        // nothing for a file sent as a document, so most of a shelf's durations live only in this map.
        val lengths = numberMap(prefs.getString(KEY_LENGTHS, "{}"))
        val resumes = ResumeBlob.read(context)
        val watched = WatchedStamps.read(context)
        val cards = jsonObject(prefs.getString(KEY_CARDS, "{}"))
        val details = jsonObject(prefs.getString(KEY_DETAILS, "{}"))
        val showCards = manifest.mapNotNull { video ->
            val showCard = cards.optJSONObject(video.stableId.toString())
                ?.takeIf { it.optBoolean("isSeries") }
                ?: return@mapNotNull null
            val title = showCard.optString("title").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            title.trim().lowercase(Locale.ROOT) to showCard
        }.toMap()

        return manifest.map { video ->
            val keptHandle = kept[video.stableId]
                ?.takeIf { it.isComplete && DownloadLedger.isCatalogHandle(it.path) }
                ?.path

            val card = cards.optJSONObject(video.stableId.toString())
            val providerId = card?.optInt("providerId")?.takeIf { it > 0 }
            val isSeries = card?.optBoolean("isSeries") ?: false
            val detail = providerId?.let { id ->
                details.optJSONObject(titleDetailKey(id, isSeries))?.let(TitleDetail::fromJson)
            }
            val guess = MediaNameParser.parse(video.title)
            val guessedYear = when (guess) {
                is MediaGuess.Movie -> guess.year
                is MediaGuess.Episode -> guess.year
                is MediaGuess.Unnamed -> null
            }
            val measuredDuration = maxOf(video.durationMs, lengths[video.stableId] ?: 0L)
            val audio = AudioTag.of(video.title)
            val showCard = (guess as? MediaGuess.Episode)
                ?.series?.trim()?.lowercase(Locale.ROOT)?.let(showCards::get)
            val showDetail = showCard?.optInt("providerId")?.takeIf { it > 0 }?.let { id ->
                details.optJSONObject(titleDetailKey(id, true))?.let(TitleDetail::fromJson)
            }
            val posterSource = card?.optString("poster")?.takeIf { it.isNotBlank() }
                ?: showCard?.optString("poster")?.takeIf { it.isNotBlank() }
            val backdropSource = detail?.backdropUrl?.takeIf { it.isNotBlank() }
                ?: showDetail?.backdropUrl?.takeIf { it.isNotBlank() }

            TvShelfItem(
                stableId = video.stableId,
                fileName = video.title,
                title = card?.optString("title")?.takeIf { it.isNotBlank() } ?: guess.fallbackTitle,
                subtitle = card?.optString("subtitle")?.takeIf { it.isNotBlank() },
                year = detail?.startYear ?: guessedYear,
                overview = detail?.overview.orEmpty(),
                genres = detail?.genres.orEmpty(),
                sizeBytes = video.sizeBytes,
                durationMs = if (measuredDuration > 0L) {
                    measuredDuration
                } else {
                    (detail?.runtimeMinutes ?: 0) * 60_000L
                },
                width = video.width,
                height = video.height,
                contentType = contentTypeFor(video.title, video.mimeType),
                resumeMs = resumes[video.stableId] ?: 0L,
                watchedAtMs = watched[video.stableId] ?: 0L,
                audioCodec = audio.codec,
                needsRemux = audio.unsupported,
                cloudVideo = video,
                keptHandle = keptHandle,
                posterFile = cachedArtwork(context, posterSource),
                backdropFile = cachedArtwork(context, backdropSource),
                posterSource = posterSource,
                backdropSource = backdropSource,
            )
        }
    }

    /**
     * The picture for one film, fetching it from the catalogue when the phone has no copy.
     *
     * Called only by the artwork route, never by [items]: a shelf read is file I/O, and 193 posters of
     * network would sit between the viewer and their film list. A missing picture costs one image request
     * on the server's own worker, and the next viewer of the same card gets the file.
     */
    fun artworkFile(context: Context, item: TvShelfItem, kind: String): File? = when (kind) {
        "poster" -> item.posterFile ?: PosterCache.diskImage(context, item.posterSource)
        "backdrop" -> item.backdropFile ?: PosterCache.diskImage(context, item.backdropSource)
        else -> null
    }

    /** The one film this id names, or nothing when the id is not a film the phone can hand over. */
    fun find(context: Context, id: String): TvShelfItem? {
        val stableId = id.toLongOrNull() ?: return null
        return items(context).firstOrNull { it.stableId == stableId }
    }

    /**
     * The caption for [item] that is already on this device, or nothing.
     *
     * Disk only, on purpose. [com.aeswox.arcplayer.cloud.CloudRepository.captionFileFor] will walk the
     * channel and then ask Telegram for a file it has never fetched, and a request that arrives a second
     * after the last one belongs to a viewer sitting two rooms away, waiting on a socket the phone is
     * also using to feed their film. This reads the two records the app keeps and answers from them: if
     * the bytes are not here, the caption is not here.
     *
     * The name decides which caption belongs to which picture, exactly as it does for the phone's own
     * player, and the viewer's language wins when the channel holds the same episode in three.
     */
    fun captionFileFor(context: Context, item: TvShelfItem): File? {
        val chatId = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_CHAT_ID, 0L)
        if (chatId == 0L) return null

        val captions = read(CloudSession.subtitleManifestFile(context))
            .map { SubtitleManifest.decode(it, chatId) }
            .getOrNull()
            .orEmpty()
        if (captions.isEmpty()) return null

        val wanted = SubtitleMatch.pick(
            video = item.fileName,
            subtitles = captions.map { it.fileName },
            language = Locale.getDefault().language,
        )
        val ranked = captions.filter { SubtitleMatch.matches(item.fileName, it.fileName) }
            .sortedByDescending { it.fileName == wanted }
        return ranked.asSequence()
            .mapNotNull { cachedCaption(context, it) }
            .firstOrNull()
    }

    /**
     * How far [items] got before it ran out of films.
     *
     * An empty library is otherwise silent: the four gates above all answer `0` the same way, and the
     * release build strips logging, so nothing on the phone says which one closed. These counts say — one
     * number per gate, read straight off the same two files, from the authenticated `/api/shelf` route.
     */
    fun counts(context: Context): ShelfCounts {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chatId = prefs.getLong(KEY_CHAT_ID, 0L)

        val manifestText = read(CloudSession.manifestFile(context)).getOrNull()
        val headerChatId = manifestText?.lineSequence()?.firstOrNull()?.split('\t')?.getOrNull(2) ?: ""
        val manifestRows = manifestText?.let { ShelfManifest.decode(it, chatId) }?.size ?: 0

        val kept = read(CloudSession.ledgerFile(context)).map { DownloadLedger.decode(it) }.getOrElse { emptyMap() }
        return ShelfCounts(
            chatId = chatId,
            manifestHeaderChatId = headerChatId,
            manifestRows = manifestRows,
            ledgerRows = kept.size,
            keptComplete = kept.values.count { it.isComplete },
            keptInCatalog = kept.values.count { it.isComplete && DownloadLedger.isCatalogHandle(it.path) },
            served = items(context).size,
        )
    }

    private fun read(file: File): Result<String> = runCatching { file.readText() }

    private fun jsonObject(text: String?): JSONObject =
        runCatching { JSONObject(text ?: "{}") }.getOrElse { JSONObject() }

    private fun cachedArtwork(context: Context, url: String?): File? {
        if (url.isNullOrBlank()) return null
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.applicationContext.cacheDir, "posters"), "$hash.img")
            .takeIf { it.isFile && it.length() >= 2048L }
    }

    /**
     * The copy the player left behind, if this caption is one.
     *
     * A caption is filed under its own name's id rather than the film's, because the id is worked out
     * from the message the subtitle arrived in — so the manifest answers "which file is the Spanish one"
     * and this answers "has anybody watched the film that needed it" without either one guessing.
     */
    private fun cachedCaption(context: Context, caption: CloudSubtitleItem): File? {
        val extension = caption.fileName.substringAfterLast('.', "srt").lowercase()
        return File(CloudSession.captionDirectory(context), "${caption.stableId}.$extension")
            .takeIf { it.isFile && it.length() > 0L }
    }

    /** A `{"-1001234567890": 4215000}` map of id to milliseconds, which is how prefs hold both. */
    private fun numberMap(text: String?): Map<Long, Long> {
        val json = runCatching { JSONObject(text ?: "{}") }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val value = json.optLong(key, 0L)
            if (value > 0L) id to value else null
        }.toMap()
    }

    /**
     * Which container these bytes are in.
     *
     * The name answers first, because that is what a file manager and AVPlay both go by. A document sent
     * to Telegram carries a MIME type the uploader's client invented, and `application/octet-stream` in a
     * `Content-Type` is a stream no player will open, so the type only wins when it is already a video.
     */
    fun contentTypeFor(title: String, mimeType: String): String {
        val byExtension = when (title.substringAfterLast('.', "").lowercase()) {
            "mkv" -> "video/x-matroska"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> null
        }
        if (byExtension != null) return byExtension
        return if (mimeType.startsWith("video/")) mimeType else "video/x-matroska"
    }

    private const val PREFS = "arc_player_prefs"
    private const val KEY_CHAT_ID = "movie_folder_chat_id"
    private const val KEY_LENGTHS = "known_lengths"
    private const val KEY_CARDS = "media_cards"
    private const val KEY_DETAILS = "title_details"
}
