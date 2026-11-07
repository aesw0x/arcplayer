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
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.metadata.TitleCandidate
import com.aeswox.arcplayer.metadata.TitleDetail
import com.aeswox.arcplayer.metadata.TitleGroups
import com.aeswox.arcplayer.metadata.TitleKind
import com.aeswox.arcplayer.metadata.nullIfBlank
import com.aeswox.arcplayer.metadata.timingRequestFor
import com.aeswox.arcplayer.metadata.titleDetailKey
import com.aeswox.arcplayer.metadata.toTimingAnswer
import com.aeswox.arcplayer.playback.ResumeBlob
import com.aeswox.arcplayer.playback.TimingTrust
import com.aeswox.arcplayer.playback.WatchedStamps
import com.aeswox.arcplayer.subtitle.SubtitleMatch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * One file the TV is allowed to ask for.
 *
 * A file is not always a work: two uploads of one film and ninety of one show are the same card, and
 * [groups] is what says so.
 *
 * [cloudVideo] names the Telegram message wherever the bytes come from. [keptHandle] is present when a
 * complete offline copy exists and is always preferred; otherwise the stream server opens a cloud read
 * session rather than hiding the film from the shelf.
 */
data class TvShelfItem(
    val stableId: Long,
    val fileName: String,
    /**
     * The name the catalogue gave this copy, or null when it never answered for it.
     *
     * A card takes its name from whichever of its copies has one of these: [fileName] is what an uploader
     * typed, and "The Rookie · Episode 4" names a part, which belongs beneath a card rather than on it.
     */
    val cardTitle: String?,
    /** Which part of the work this file is, off its own name. Both null for a film. */
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    val overview: String,
    val genres: List<String>,
    val sizeBytes: Long,
    val durationMs: Long,
    val introEndMs: Long?,
    val outroStartMs: Long?,
    val outroEndMs: Long?,
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
    val logoSource: String?,
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

/**
 * One card on the television's shelf: a work, with every copy of it the phone holds filed underneath.
 *
 * [items] is a file list, and a file list is what made a viewer scroll past nine identical posters to
 * reach the same film twice. This is the same shelf read the phone's own grid uses, so a numbered run
 * of one show and two uploads of one film each get one card here too.
 *
 * The card speaks for the work — [title], [year], [overview], [genres] and the two pictures all come
 * from whichever copy the catalogue actually answered for, which for a series is rarely the one whose
 * file is first in the list. [playId] then names the part the card's button opens and [resumeId] the
 * part still underway, so the television never has to work out on its own which of [items] a "Resume"
 * belongs to; the byte routes keep taking a file id, because those are the bytes being fetched.
 */
data class TvShelfGroup(
    val key: String,
    val isSeries: Boolean,
    val title: String,
    val year: Int?,
    val overview: String,
    val genres: List<String>,
    /** Best copy first inside each part, parts in watch order — the phone's own order, unchanged. */
    val items: List<TvShelfItem>,
    val playId: Long,
    val playResumeMs: Long,
    /** The part being watched, or null when every part here is either untouched or finished. */
    val resumeId: Long?,
    val resumeMs: Long,
    val resumeLengthMs: Long,
    /** The newest "opened" stamp anywhere in the group; the figure the hero is ranked on. */
    val watchedAtMs: Long,
    /** Distinct slots, not copies: every copy of a film belongs to its single slot. */
    val partCount: Int,
    val seasonCount: Int,
    val posterItem: TvShelfItem?,
    val backdropItem: TvShelfItem?,
    val logoItem: TvShelfItem?,
    val episodes: List<TvShelfEpisode>,
)

data class TvShelfEpisode(
    val season: Int,
    val episode: Int,
    val playId: Long,
    val resumeMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val copyIds: List<Long>,
)

/**
 * Which gate of [ShelfLibrary.groups] closed, in the order the reads happen.
 *
 * [served] counts cards, not files: a shelf that groups 193 files into one card has a grouping bug, and a
 * count of what [items] handed over would call that healthy.
 */
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
        val timings = jsonObject(prefs.getString(KEY_TIMINGS, "{}"))
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
            val episodeGuess = guess as? MediaGuess.Episode
            val guessedYear = when (guess) {
                is MediaGuess.Movie -> guess.year
                is MediaGuess.Episode -> guess.year
                is MediaGuess.Unnamed -> null
            }
            val measuredDuration = maxOf(video.durationMs, lengths[video.stableId] ?: 0L)
            val audio = AudioTag.of(video.title)
            val showCard = episodeGuess
                ?.series?.trim()?.lowercase(Locale.ROOT)?.let(showCards::get)
            val showDetail = showCard?.optInt("providerId")?.takeIf { it > 0 }?.let { id ->
                details.optJSONObject(titleDetailKey(id, true))?.let(TitleDetail::fromJson)
            }
            val posterSource = card?.optString("poster")?.takeIf { it.isNotBlank() }
                ?: showCard?.optString("poster")?.takeIf { it.isNotBlank() }
            val backdropSource = detail?.backdropUrl?.takeIf { it.isNotBlank() }
                ?: showDetail?.backdropUrl?.takeIf { it.isNotBlank() }
            val logoSource = rasterLogo(card) ?: rasterLogo(showCard)
            val durationMs = if (measuredDuration > 0L) {
                measuredDuration
            } else {
                ((detail ?: showDetail)?.runtimeMinutes ?: 0) * 60_000L
            }
            val timingRequest = timingRequestFor(
                providerId ?: showCard?.optInt("providerId")?.takeIf { it > 0 },
                guess,
                (detail ?: showDetail)?.seasons.orEmpty(),
            )
            val trustedTimings = timingRequest?.let { timings.optJSONObject(it.key)?.toTimingAnswer() }
                ?.let { TimingTrust.believe(it, durationMs) }

            TvShelfItem(
                stableId = video.stableId,
                fileName = video.title,
                cardTitle = card?.optString("title")?.takeIf { it.isNotBlank() },
                season = episodeGuess?.season,
                episode = episodeGuess?.episode,
                year = detail?.startYear ?: guessedYear,
                overview = detail?.overview.orEmpty(),
                genres = detail?.genres.orEmpty(),
                sizeBytes = video.sizeBytes,
                durationMs = durationMs,
                introEndMs = trustedTimings?.introEndMs,
                outroStartMs = trustedTimings?.outroStartMs,
                outroEndMs = trustedTimings?.outroEndMs,
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
                logoSource = logoSource,
            )
        }
    }

    /**
     * The shelf as the television should draw it: one card per work, not one per file.
     *
     * [items] answers with a file for every row the manifest carries, so a show uploaded episode by
     * episode arrived as ninety cards and a film uploaded twice arrived twice — which is what the viewer
     * saw as nine identical posters running down one row. The grouping is the phone's own [TitleGroups]
     * asked of the same names its grid reads, so the two shelves cannot disagree about what one work is,
     * and the part a card plays is [PlayOrder]'s answer, the same one the phone's Play button gets, so a
     * television resumes the episode the viewer is actually inside rather than the lowest number the
     * channel still owes.
     */
    fun groups(context: Context): List<TvShelfGroup> {
        val files = items(context)
        if (files.isEmpty()) return emptyList()

        val byId = files.associateBy { it.stableId }
        val lengths = files.associate { it.stableId to it.durationMs }
        val resumes = files.associate { it.stableId to it.resumeMs }
        val lastPlayed = files.associate { it.stableId to it.watchedAtMs }
        val savedIds = files.filter { it.keptHandle != null }.map { it.stableId }.toSet()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val finishedMs = numberMap(prefs.getString(KEY_FINISHED, "{}"))

        return TitleGroups.group(
            files.map { TitleCandidate(it.stableId, it.fileName, it.sizeBytes, it.height, it.keptHandle != null) }
        ).mapNotNull { work ->
            val parts = work.parts.mapNotNull { byId[it.id] }
            if (parts.isEmpty()) return@mapNotNull null

            // One slot per part, its copies best first, the slots in watch order — the shape PlayOrder reads.
            val slots = work.parts
                .groupBy { (it.season ?: 0) to (it.episode ?: 0) }
                .values
                .map { part -> part.map { it.id } }
            val positionByCopy = PlayOrder.positionByCopy(slots, resumes)
            val play = PlayOrder.nextCopy(slots, positionByCopy, lengths, lastPlayed, savedIds)
            val underway = PlayOrder.progress(slots, positionByCopy, lengths, lastPlayed, savedIds)
            val episodes = if (work.kind == TitleKind.SERIES) {
                // Normalize browsing slots separately so the existing work-level play choice stays unchanged.
                val episodeSlots = work.parts
                    .groupBy { (it.season ?: 1) to (it.episode ?: 0) }
                    .mapValues { (_, copies) -> copies.map { it.id } }
                val episodePositions = PlayOrder.positionByCopy(episodeSlots.values.toList(), resumes)
                episodeSlots.map { (number, slot) ->
                    val episodePlay = PlayOrder.nextCopy(listOf(slot), episodePositions, lengths, lastPlayed, savedIds)
                    val playId = episodePlay?.partId ?: slot.first()
                    val standing = PlayOrder.standing(
                        episodes = listOf(number.second to slot),
                        resumeMs = episodePositions,
                        lengthsMs = lengths,
                        finishedMs = finishedMs,
                    ).episodes.single()
                    TvShelfEpisode(
                        season = number.first,
                        episode = number.second,
                        playId = playId,
                        resumeMs = episodePlay?.resumeMs ?: 0L,
                        durationMs = lengths[playId]?.takeIf { it > 0L }
                            ?: (slot.maxOfOrNull { lengths[it] ?: 0L } ?: 0L),
                        watched = standing.watch == PlayOrder.EpisodeWatch.WATCHED,
                        copyIds = slot,
                    )
                }.sortedWith(compareBy(TvShelfEpisode::season, TvShelfEpisode::episode))
            } else {
                emptyList()
            }

            TvShelfGroup(
                key = work.key,
                isSeries = work.kind == TitleKind.SERIES,
                // A catalogue name beats the parser's whenever one of the copies carries it, because the
                // parser reads what an uploader typed and gets the work's name right only by luck.
                title = parts.firstOrNull { it.cardTitle != null }?.cardTitle ?: work.displayTitle,
                year = parts.firstOrNull { it.year != null }?.year ?: work.year,
                overview = parts.firstOrNull { it.overview.isNotBlank() }?.overview.orEmpty(),
                genres = parts.firstOrNull { it.genres.isNotEmpty() }?.genres.orEmpty(),
                items = parts,
                playId = play?.partId ?: parts.first().stableId,
                playResumeMs = play?.resumeMs ?: 0L,
                resumeId = underway?.partId,
                resumeMs = underway?.resumeMs ?: 0L,
                resumeLengthMs = underway?.lengthMs ?: 0L,
                watchedAtMs = parts.maxOfOrNull { it.watchedAtMs } ?: 0L,
                partCount = slots.size,
                seasonCount = work.seasons.size,
                posterItem = parts.firstOrNull { it.posterSource != null },
                backdropItem = parts.firstOrNull { it.backdropSource != null },
                logoItem = parts.firstOrNull { it.logoSource != null },
                episodes = episodes,
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
        "logo" -> cachedArtwork(context, item.logoSource) ?: PosterCache.diskImage(context, item.logoSource)
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
            served = groups(context).size,
        )
    }

    private fun read(file: File): Result<String> = runCatching { file.readText() }

    private fun jsonObject(text: String?): JSONObject =
        runCatching { JSONObject(text ?: "{}") }.getOrElse { JSONObject() }

    private fun rasterLogo(card: JSONObject?): String? =
        card?.optString("logo").nullIfBlank()?.takeIf { url ->
            url.substringBefore('?').substringBefore('#').substringAfterLast('.', "")
                .lowercase(Locale.ROOT) in RASTER_LOGO_EXTENSIONS
        }

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
    private const val KEY_TIMINGS = "episode_timings"
    private const val KEY_FINISHED = "film_finished_at"
    private val RASTER_LOGO_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
}
