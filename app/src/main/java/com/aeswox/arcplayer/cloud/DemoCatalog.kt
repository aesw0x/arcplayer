package com.aeswox.arcplayer.cloud

import com.aeswox.arcplayer.ArcPreferencesStore
import com.aeswox.arcplayer.metadata.EpisodeDetail
import com.aeswox.arcplayer.metadata.MediaCard
import com.aeswox.arcplayer.metadata.SeasonCard
import com.aeswox.arcplayer.metadata.TitleDetail
import com.aeswox.arcplayer.metadata.titleDetailKey

/**
 * The shelf a Play reviewer signs into.
 *
 * The review team cannot create Telegram accounts or receive SMS codes, so one fixed number and one
 * fixed code open a library that needs neither Telegram nor the metadata catalogues: the films are
 * the Blender Foundation's open movies and the Caminandes shorts (CC-BY), streamed from their public
 * mirrors, and every poster, synopsis and episode row is bundled with the app. Nothing here is a
 * secret; the credentials are printed in the Play Console declaration on purpose.
 */
object DemoCatalog {

    const val PHONE = "+12025550143"
    const val CODE = "12345"
    const val CHAT_ID = -777000001L
    const val CHAT_TITLE = "Arc Player Demo"

    /** Artwork ships in assets so a demo shelf draws its banners with no network at all. */
    fun art(name: String): String = "asset:demo/$name"

    private data class Entry(
        val messageId: Long,
        val sceneTitle: String,
        val name: String,
        val year: Int,
        val series: Boolean,
        val episode: Int,
        val episodeTitle: String,
        val overview: String,
        val runtimeMinutes: Int,
        val director: String,
        val poster: String,
        val backdrop: String,
        val url: String,
        val sizeBytes: Long,
        val durationMs: Long,
        val height: Int,
        val mime: String,
    )

    private val entries = listOf(
        Entry(
            1, "Big.Buck.Bunny.2008.1080p.WEBRip.x264-DEMO.mp4", "Big Buck Bunny", 2008, false, 0, "",
            "A gentle giant of a rabbit answers three bullying rodents with a day of elaborate, " +
                "good-natured payback in the Blender Foundation's open comedy short.",
            10, "Sacha Goedegebure", art("poster_bbb.jpg"), art("backdrop_bbb.jpg"),
            "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
            61_878_609L, 596_000L, 720, "video/mp4",
        ),
        Entry(
            2, "Sintel.2010.1080p.BluRay.x264-DEMO.mkv", "Sintel", 2010, false, 0, "",
            "A lonely young woman crosses mountains and desert in search of the dragon she once " +
                "raised and lost, in the Blender Foundation's open fantasy short.",
            15, "Colin Levy", art("poster_sintel.jpg"), art("backdrop_sintel.jpg"),
            "https://download.blender.org/durian/movies/Sintel.2010.1080p.mkv",
            1_180_090_590L, 888_000L, 1080, "video/x-matroska",
        ),
        Entry(
            3, "Elephants.Dream.2006.1080p.WEBRip.x264-DEMO.mp4", "Elephants Dream", 2006, false, 0, "",
            "Two travellers argue their way through a vast machine that builds itself around them, " +
                "in the first open movie ever made.",
            11, "Bassam Kurdali", art("poster_ed.jpg"), art("backdrop_ed.jpg"),
            "https://archive.org/download/ElephantsDream/ed_hd.mp4",
            67_801_890L, 654_000L, 720, "video/mp4",
        ),
        Entry(
            4, "Caminandes.S01E01.Llama.Drama.1080p.WEBRip.x264-DEMO.mp4", "Caminandes", 2013, true, 1,
            "Llama Drama",
            "A llama's attempt to cross a quiet Patagonian road meets one very persistent car.",
            3, "Pablo Vazquez", art("poster_caminandes.jpg"), art("backdrop_caminandes.jpg"),
            "https://archive.org/download/Caminandes1LlamaDrama/01_llama_drama_1080p.mp4",
            35_103_580L, 180_000L, 1080, "video/mp4",
        ),
        Entry(
            5, "Caminandes.S01E02.Gran.Dillama.1080p.WEBRip.x264-DEMO.mp4", "Caminandes", 2013, true, 2,
            "Gran Dillama",
            "The same llama, the same road, and a roadside cactus that refuses to be ignored.",
            3, "Pablo Vazquez", art("poster_caminandes.jpg"), art("backdrop_caminandes.jpg"),
            "https://archive.org/download/Caminandes2GranDillama/02_gran_dillama_1080p.mp4",
            125_974_946L, 160_000L, 1080, "video/mp4",
        ),
    )

    val items: List<CloudVideoItem> = entries.mapIndexed { index, entry ->
        CloudVideoItem(
            stableId = CloudIds.forVideo(CHAT_ID, entry.messageId, "demo-${entry.messageId}"),
            chatId = CHAT_ID,
            chatTitle = CHAT_TITLE,
            messageId = entry.messageId,
            fileId = -1000 - index,
            title = entry.sceneTitle,
            sizeBytes = entry.sizeBytes,
            durationMs = entry.durationMs,
            width = 1920,
            height = entry.height,
            mimeType = entry.mime,
            supportsStreaming = true,
            dateSec = 1_700_000_000L + index,
        )
    }

    fun isDemo(video: CloudVideoItem): Boolean = video.chatId == CHAT_ID

    fun streamUrlFor(video: CloudVideoItem): String? =
        entries.firstOrNull { it.messageId == video.messageId }?.url

    /** Provider ids are the demo's own, far above any catalogue's, so nothing real is overwritten. */
    private fun providerId(entry: Entry): Int = 990_000 + entry.messageId.toInt()

    fun cards(): Map<Long, MediaCard> = items.mapIndexed { index, item ->
        val entry = entries[index]
        item.stableId to MediaCard(
            title = entry.name,
            subtitle = if (entry.series) "Series" else entry.year.toString(),
            posterUrl = entry.poster,
            source = "demo",
            logoKnown = true,
            providerId = providerId(entry),
            isSeries = entry.series,
        )
    }.toMap()

    fun details(): Map<String, TitleDetail> = entries
        .filter { it.episode <= 1 }
        .associate { entry ->
            titleDetailKey(providerId(entry), entry.series) to TitleDetail(
                isSeries = entry.series,
                overview = entry.overview,
                genres = listOf("Animation", "Short", "Family"),
                startYear = entry.year,
                endYear = if (entry.series) entry.year else null,
                runtimeMinutes = entry.runtimeMinutes,
                directors = listOf(entry.director),
                writers = emptyList(),
                cast = emptyList(),
                seasons = if (entry.series) {
                    listOf(SeasonCard(1, "Season 1", entry.poster, entries.count { it.series }))
                } else {
                    emptyList()
                },
                backdropUrl = entry.backdrop,
                maturity = null,
                voteScore = null,
                seasonsFetched = true,
            )
        }

    fun episodes(): Map<String, List<EpisodeDetail>> {
        val show = entries.first { it.series }
        val key = titleDetailKey(providerId(show), true) + "/1"
        return mapOf(
            key to entries.filter { it.series }.map { entry ->
                EpisodeDetail(
                    season = 1,
                    number = entry.episode,
                    title = entry.episodeTitle,
                    overview = entry.overview,
                    stillUrl = entry.backdrop,
                    runtimeMinutes = entry.runtimeMinutes,
                )
            }
        )
    }

    /** File the bundled answers once per demo session so the shelf and info pages draw offline. */
    fun seedInto(prefsStore: ArcPreferencesStore) {
        prefsStore.mediaCards.putAll(cards())
        prefsStore.titleDetails.putAll(details())
        prefsStore.seasonEpisodes.putAll(episodes())
    }
}
