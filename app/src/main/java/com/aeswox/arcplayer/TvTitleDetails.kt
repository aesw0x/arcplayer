package com.aeswox.arcplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcplayer.metadata.AbsoluteSeasons
import com.aeswox.arcplayer.metadata.CastMember
import com.aeswox.arcplayer.metadata.EpisodeDetail
import com.aeswox.arcplayer.metadata.MediaLibrary
import com.aeswox.arcplayer.metadata.PersonCredits
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.metadata.TitleDetail
import com.aeswox.arcplayer.metadata.TmdbDetails
import com.aeswox.arcplayer.metadata.TmdbPeople
import com.aeswox.arcplayer.metadata.formatRuntime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun TvTitleDetails(
    title: ShelfTitle,
    prefsStore: ArcPreferencesStore,
    authorized: Boolean,
    onPlay: (Playable) -> Unit,
    onBack: () -> Unit,
    onNavigate: (TvPage) -> Unit,
) {
    key(title.group.key, title.cardKey) {
        TvTitleDetailContent(title, prefsStore, authorized, onPlay, onBack, onNavigate)
    }
}

@Composable
private fun TvTitleDetailContent(
    title: ShelfTitle,
    prefsStore: ArcPreferencesStore,
    authorized: Boolean,
    onPlay: (Playable) -> Unit,
    onBack: () -> Unit,
    onNavigate: (TvPage) -> Unit,
) {
    val storedCard = prefsStore.mediaCards[title.cardKey]
    val shown = remember(title, storedCard) { title.withCard(storedCard ?: title.card) }
    var attempt by remember { mutableIntStateOf(0) }
    val detail = key(attempt) { TitleDetailFetch(shown.providerId, shown.isSeries, prefsStore) }
    var waitExpired by remember(shown.providerId, shown.isSeries, attempt) { mutableStateOf(false) }
    val canFetch = shown.providerId != null && BuildConfig.TMDB_API_KEY.isNotBlank()
    LaunchedEffect(shown.providerId, shown.isSeries, detail, attempt) {
        if (detail == null && canFetch) {
            delay(20_000)
            waitExpired = true
        }
    }
    val loading = detail == null && canFetch && !waitExpired
    LaunchedEffect(title.cardKey, prefsStore) {
        val card = prefsStore.mediaCards[title.cardKey]
        if (card == null || card.posterUrl.isNullOrBlank()) {
            MediaLibrary.askNow(
                videoId = title.cardKey,
                fileName = title.asker.title,
                apiKey = BuildConfig.TMDB_API_KEY,
                onFound = { id, found -> prefsStore.rememberMediaCard(id, found) },
            )
        }
    }

    val resumeByCopy = PlayOrder.positionByCopy(
        title.copySlots, title.partIds.associateWith { prefsStore.resumePositions[it] ?: 0L },
    )
    val choice = PlayOrder.nextCopy(
        slots = title.copySlots,
        resumeMs = resumeByCopy,
        lengthsMs = title.lengths,
        lastPlayedMs = prefsStore.watchedAt,
    )
    val playFile = title.parts.firstOrNull { it.stableId == choice?.partId } ?: title.representative
    val playLabel = if ((choice?.resumeMs ?: 0L) > 0) "Resume" else "Play"
    val favorite = title.representative.stableId in prefsStore.favoriteIds
    val episodes = remember(title, detail) { tvLibraryEpisodes(title, detail) }
    val seasons = episodes.map { it.season }.distinct().sortedWith(compareBy<Int?> { it == null }.thenBy { it })
    val suggestedSeason = episodes.firstOrNull { row -> row.files.any { it.stableId == playFile.stableId } }?.season
    var selectedSeason by remember { mutableStateOf(suggestedSeason ?: seasons.firstOrNull()) }
    var seasonChosen by remember { mutableStateOf(false) }
    LaunchedEffect(seasons, suggestedSeason) {
        if (!seasonChosen || selectedSeason !in seasons) {
            selectedSeason = suggestedSeason ?: seasons.firstOrNull()
        }
    }
    val catalogued = tvEpisodeDetails(shown.providerId.takeIf { shown.isSeries }, selectedSeason, prefsStore)
    val visibleEpisodes = episodes.filter { it.season == selectedSeason }
    val listState = rememberLazyListState()
    val castState = rememberLazyListState()
    val seasonState = rememberLazyListState()
    var person by remember { mutableStateOf<CastMember?>(null) }
    var returningCast by remember { mutableIntStateOf(-1) }
    val entryFocus = remember { FocusRequester() }
    val cast = detail?.cast.orEmpty()
    val overview = detail?.overview.orEmpty()
    val restoreCast = returningCast in cast.indices
    val member = person
    if (member != null) {
        TvDetailPerson(member, prefsStore, authorized, onNavigate) { person = null }
        return
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(member) {
        withFrameNanos { }
        // The restore target can sit below the lazy list's composed window; focus falls back to the rail then.
        runCatching { entryFocus.requestFocus() }
    }

    TvDetailFrame(detail?.backdropUrl ?: shown.posterUrl, onNavigate, authorized) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (shown.isSeries) "SERIES IN YOUR LIBRARY" else "MOVIE IN YOUR LIBRARY",
                        color = TvColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp,
                    )
                    Text(
                        shown.name, color = TvColors.Text, fontSize = 36.sp, lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(.85f).semantics { heading() },
                    )
                    Text(tvTitleMeta(shown, detail), color = TvColors.Muted, fontSize = 16.sp)
                    if (loading) {
                        TvDetailSkeleton(Modifier.fillMaxWidth(.72f))
                    } else {
                        Text(
                            detail?.overview?.takeIf { it.isNotBlank() }
                                ?: "More details are unavailable right now. Your library files are still playable.",
                            color = TvColors.Text, fontSize = 16.sp, lineHeight = 23.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(.78f),
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            TvAction(
                                playLabel, { onPlay(playFile) },
                                Modifier.width(132.dp).then(if (!restoreCast) Modifier.focusRequester(entryFocus) else Modifier),
                            )
                        }
                        item {
                            TvAction(
                                if (favorite) "Remove favorite" else "Add favorite",
                                { prefsStore.toggleFavorite(title.representative.stableId) },
                            )
                        }
                        if (detail == null && !loading && canFetch) {
                            item { TvAction("Retry details", { attempt++ }) }
                        }
                    }
                    Text(
                        "$playLabel · ${playFile.title}", color = TvColors.Muted, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item(key = "library-heading") {
                TvDetailHeading(if (shown.isSeries) "Episodes & copies" else "Available copies")
            }
            if (shown.isSeries && seasons.isNotEmpty()) {
                item(key = "seasons") {
                    LazyRow(
                        state = seasonState,
                        contentPadding = PaddingValues(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(seasons, key = { it ?: -1 }) { season ->
                            val active = season == selectedSeason
                            val label = when (season) {
                                null -> "Other files"
                                0 -> "Specials"
                                else -> detail?.seasons?.firstOrNull { it.number == season }?.title
                                    ?.takeIf { it.isNotBlank() } ?: "Season $season"
                            }
                            TvChip(label, active, { selectedSeason = season; seasonChosen = true })
                        }
                    }
                }
            }
            if (shown.isSeries) {
                visibleEpisodes.forEach { episode ->
                    val info = catalogued.firstOrNull { it.number == episode.number }
                    item(key = "episode-${episode.key}") {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (info?.stillUrl != null) {
                                TvArtwork(info.stillUrl, null, Modifier.width(144.dp).height(81.dp).clip(RoundedCornerShape(8.dp)), 300)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                val name = info?.title?.takeIf { it.isNotBlank() } ?: episode.name
                                Text(
                                    listOfNotNull(episode.number?.let { "Episode $it" }, name).joinToString(" · ").ifBlank { "Library file" },
                                    color = TvColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                if (!info?.overview.isNullOrBlank()) {
                                    Text(info!!.overview, color = TvColors.Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    items(episode.files, key = { "file-${it.stableId}" }) { file ->
                        TvDetailFile(file, resumeByCopy, prefsStore, onPlay)
                    }
                }
            } else {
                items(title.parts.distinctBy { it.stableId }, key = { "file-${it.stableId}" }) { file ->
                    TvDetailFile(file, resumeByCopy, prefsStore, onPlay)
                }
            }
            if (cast.isNotEmpty()) {
                item(key = "cast-heading") { TvDetailHeading("Cast") }
                item(key = "cast") {
                    LazyRow(
                        state = castState,
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(cast.size, key = { index -> "cast-$index" }) { index ->
                            val actor = cast[index]
                            TvDetailTile(
                                onClick = { returningCast = index; person = actor },
                                modifier = Modifier.width(136.dp).then(if (returningCast == index) Modifier.focusRequester(entryFocus) else Modifier),
                            ) {
                                TvArtwork(actor.photoUrl, actor.name, Modifier.fillMaxWidth().height(158.dp), 185)
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(actor.name, color = TvColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(actor.role.ifBlank { "View person" }, color = TvColors.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            if (overview.isNotBlank()) {
                item(key = "overview-heading") { TvDetailHeading("Overview") }
                items(tvReadableParagraphs(overview)) { paragraph -> TvDetailParagraph(paragraph) }
            }
            if (!detail?.directors.isNullOrEmpty() || !detail?.writers.isNullOrEmpty()) {
                item(key = "credits") {
                    TvDetailParagraph(
                        listOfNotNull(
                            detail?.directors?.takeIf { it.isNotEmpty() }?.joinToString()?.let { "${if (shown.isSeries) "Created by" else "Directed by"}: $it" },
                            detail?.writers?.takeIf { it.isNotEmpty() }?.joinToString()?.let { "Written by: $it" },
                        ).joinToString("\n"),
                    )
                }
            }
        }
    }
}

private data class TvLibraryEpisode(
    val season: Int?,
    val number: Int?,
    val name: String?,
    val files: List<Playable>,
) {
    val key: Long get() = files.first().stableId
}

private fun tvLibraryEpisodes(title: ShelfTitle, detail: TitleDetail?): List<TvLibraryEpisode> {
    val result = mutableListOf<TvLibraryEpisode>()
    val filed = title.filesByEpisode.mapValues { it.value.toMutableMap() }.toMutableMap()
    title.filesByAbsolute.forEach { (number, files) ->
        val season = AbsoluteSeasons.seasonFor(number, detail?.seasons.orEmpty())
        if (season != null) {
            val row = filed.getOrPut(season) { mutableMapOf() }
            row[number] = (row[number].orEmpty() + files).distinctBy { it.stableId }
        } else {
            result += TvLibraryEpisode(null, number, null, files)
        }
    }
    filed.toSortedMap().forEach { (season, episodes) ->
        episodes.toSortedMap().forEach { (number, files) ->
            val name = title.group.parts.firstOrNull { it.id == files.first().stableId }?.episodeTitle
            result += TvLibraryEpisode(season, number, name, files)
        }
    }
    val assigned = result.flatMap { it.files }.mapTo(mutableSetOf()) { it.stableId }
    title.parts.distinctBy { it.stableId }.filter { it.stableId !in assigned }.forEach { file ->
        result += TvLibraryEpisode(null, null, file.title, listOf(file))
    }
    return result.sortedWith(compareBy<TvLibraryEpisode> { it.season == null }.thenBy { it.season }.thenBy { it.number })
}

@Composable
private fun tvEpisodeDetails(showId: Int?, season: Int?, prefsStore: ArcPreferencesStore): List<EpisodeDetail> {
    LaunchedEffect(showId, season, prefsStore) {
        if (showId == null || season == null || prefsStore.episodesOf(showId, season) != null) return@LaunchedEffect
        val fetched = withContext(Dispatchers.IO) {
            try {
                TmdbDetails.episodes(BuildConfig.TMDB_API_KEY, showId, season)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (fetched != null) prefsStore.rememberEpisodesOf(showId, season, fetched)
    }
    return if (season == null) emptyList() else prefsStore.episodesOf(showId, season).orEmpty()
}

@Composable
private fun TvDetailFile(
    file: Playable,
    resumeByCopy: Map<Long, Long>,
    prefsStore: ArcPreferencesStore,
    onPlay: (Playable) -> Unit,
) {
    val resume = PlayOrder.nextCopy(listOf(listOf(file.stableId)), resumeByCopy, mapOf(file.stableId to file.durationMs))?.resumeMs ?: 0L
    val meta = listOfNotNull(
        file.frameHeight.takeIf { it > 0 }?.let { "${it}p" },
        file.sizeFormatted.takeIf { file.sizeBytes > 0L },
        formatRuntime((file.durationMs / 60_000).toInt()).takeIf { it.isNotBlank() },
        if (file.isOnPhone) "Offline" else "Cloud",
        when {
            resume > 0L -> "Resume at ${tvPosition(resume)}"
            (prefsStore.finishedAt[file.stableId] ?: 0L) > 0L -> "Watched"
            else -> null
        },
    ).joinToString(" · ")
    TvDetailTile(onClick = { onPlay(file) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (resume > 0L) "Resume" else "Play", color = TvColors.Focus, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(66.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(file.title, color = TvColors.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, color = TvColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TvDetailPerson(
    member: CastMember,
    prefsStore: ArcPreferencesStore,
    authorized: Boolean,
    onNavigate: (TvPage) -> Unit,
    onBack: () -> Unit,
) {
    val cached = member.personId?.let { prefsStore.personDetail(it) }
        ?: prefsStore.personDetails.values.firstOrNull { it.name.equals(member.name, ignoreCase = true) }
    var settled by remember(member) { mutableStateOf(cached != null) }
    var attempt by remember(member) { mutableIntStateOf(0) }
    LaunchedEffect(member, attempt, prefsStore) {
        if (cached != null) { settled = true; return@LaunchedEffect }
        settled = false
        val fetched = withContext(Dispatchers.IO) {
            try {
                val id = member.personId ?: TmdbPeople.idByName(BuildConfig.TMDB_API_KEY, member.name)
                id?.let { TmdbPeople.person(BuildConfig.TMDB_API_KEY, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (fetched != null) prefsStore.rememberPersonDetail(fetched)
        settled = true
    }
    val credits = remember(cached) { PersonCredits.rank(cached?.credits.orEmpty()) }
    BackHandler(onBack = onBack)
    TvDetailFrame(null, onNavigate, authorized) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    TvArtwork(cached?.photoUrl ?: member.photoUrl, member.name, Modifier.width(148.dp).height(198.dp).clip(RoundedCornerShape(12.dp)), 632)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("CAST & CREW", color = TvColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text(cached?.name ?: member.name, color = TvColors.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        val dates = listOfNotNull(cached?.birthday?.let { "Born $it" }, cached?.deathday?.let { "Died $it" }, cached?.placeOfBirth)
                        if (dates.isNotEmpty()) Text(dates.joinToString(" · "), color = TvColors.Muted, fontSize = 15.sp)
                        if (!settled && cached == null) {
                            TvDetailSkeleton(Modifier.fillMaxWidth())
                        } else if (cached == null) {
                            Text("Person details are unavailable right now.", color = TvColors.Muted, fontSize = 16.sp)
                            TvAction("Retry", { attempt++ })
                        } else {
                            val knownFor = PersonCredits.knownFor(cached.credits)
                            if (knownFor.isNotEmpty()) Text("Known for ${knownFor.joinToString(", ")}", color = TvColors.Text, fontSize = 16.sp, lineHeight = 23.sp)
                        }
                    }
                }
            }
            if (cached != null) {
                item { TvDetailHeading("Biography") }
                items(tvReadableParagraphs(cached.biography.ifBlank { "No biography is available for this person." })) { TvDetailParagraph(it) }
                if (credits.isNotEmpty()) {
                    item { TvDetailHeading("Filmography") }
                    item { Text("Catalogue credits · Availability is not checked here", color = TvColors.Muted, fontSize = 14.sp) }
                    item {
                        LazyRow(contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(credits, key = { "${it.id}/${it.isSeries}" }) { credit ->
                                TvDetailTile(modifier = Modifier.width(154.dp)) {
                                    TvArtwork(credit.posterUrl, credit.title, Modifier.fillMaxWidth().height(206.dp), 342)
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(credit.title, color = TvColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(listOfNotNull(credit.year?.toString(), if (credit.isSeries) "Series" else "Movie").joinToString(" · "), color = TvColors.Muted, fontSize = 13.sp)
                                        if (credit.character.isNotBlank()) Text(credit.character, color = TvColors.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDetailFrame(artUrl: String?, onNavigate: (TvPage) -> Unit, authorized: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        if (artUrl != null) {
            TvArtwork(artUrl, null, Modifier.fillMaxSize(), 1280)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(TvColors.Background.copy(alpha = .96f), TvColors.Background.copy(alpha = .72f), TvColors.Background.copy(alpha = .3f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TvColors.Background.copy(alpha = .2f), TvColors.Background.copy(alpha = .7f), TvColors.Background))))
        }
        Row(Modifier.fillMaxSize()) {
            TvRail(TvPage.LIBRARY, onNavigate, authorized, Modifier.padding(start = 12.dp))
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 40.dp)) { content() }
        }
    }
}

@Composable
private fun TvDetailTile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.onFocusChanged { focused = it.hasFocus }
            .tvJellyFocus(focused, 1.02f)
            .border(2.dp, if (focused) TvColors.Focus else Color.Transparent, TvCardShape)
            .clip(TvCardShape)
            .tvGlass(TvCardShape, .6f)
            .then(if (onClick == null) Modifier.focusable() else Modifier.clickable(onClick = onClick)),
    ) { content() }
}

@Composable
private fun TvDetailHeading(text: String) {
    Text(text, color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp).semantics { heading() })
}

@Composable
private fun TvDetailParagraph(text: String) {
    TvDetailTile(Modifier.fillMaxWidth()) {
        Text(text, color = TvColors.Text, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun TvDetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Loading details…", color = TvColors.Muted, fontSize = 14.sp)
        listOf(1f, .92f, .64f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(12.dp).clip(RoundedCornerShape(4.dp)).background(TvColors.Panel))
        }
    }
}

private fun tvTitleMeta(title: ShelfTitle, detail: TitleDetail?): String {
    val start = detail?.startYear ?: title.group.year
    val end = detail?.endYear
    val year = start?.let { if (title.isSeries && end != null && end != it) "$it–$end" else it.toString() }
    val runtime = detail?.runtimeMinutes?.takeIf { it > 0 } ?: (title.representative.durationMs / 60_000).toInt()
    return listOfNotNull(
        year,
        if (title.isSeries) "Series" else "Movie",
        formatRuntime(runtime).takeIf { it.isNotBlank() },
        detail?.maturity?.takeIf { it.isNotBlank() },
        detail?.voteScore?.takeIf { it > 0 }?.let { "TMDB ${String.format(Locale.US, "%.1f", it)}/10" },
        detail?.genres?.take(3)?.joinToString(" / ")?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

private fun tvPosition(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}

private fun tvReadableParagraphs(text: String): List<String> = buildList {
    text.split(Regex("\\n\\s*\\n")).forEach { paragraph ->
        var remaining = paragraph.trim()
        while (remaining.length > 420) {
            val boundary = remaining.lastIndexOf(' ', 420).takeIf { it >= 200 } ?: 420
            add(remaining.take(boundary))
            remaining = remaining.drop(boundary).trimStart()
        }
        if (remaining.isNotBlank()) add(remaining)
    }
}
