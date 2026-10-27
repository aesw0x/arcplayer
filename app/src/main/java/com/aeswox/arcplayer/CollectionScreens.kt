package com.aeswox.arcplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aeswox.arcplayer.cloud.CloudAuthStage
import com.aeswox.arcplayer.cloud.CloudRepository
import com.aeswox.arcplayer.cloud.CloudVideoItem
import com.aeswox.arcplayer.cloud.DownloadEntry
import com.aeswox.arcplayer.cloud.DownloadLedger
import com.aeswox.arcplayer.metadata.MediaCard
import com.aeswox.arcplayer.metadata.PhoneVideoVerdict
import com.aeswox.arcplayer.metadata.PhoneVideos
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.metadata.TitleCandidate
import com.aeswox.arcplayer.metadata.TitleGroups
import com.aeswox.arcplayer.metadata.formatRuntime
import com.aeswox.arcplayer.ui.animations.JellySkeleton
import com.aeswox.arcplayer.ui.animations.StaggeredListItem
import com.aeswox.arcplayer.ui.animations.jellyClick
import com.aeswox.arcplayer.ui.animations.physicsBounceOverscroll
import com.aeswox.arcplayer.ui.components.HugeIcons
import com.aeswox.arcplayer.ui.haptics.AppHaptics
import com.aeswox.arcplayer.ui.theme.Accent as JellyAccent
import com.aeswox.arcplayer.ui.theme.Background as JellyBackground
import com.aeswox.arcplayer.ui.theme.OnPrimary as JellyOnPrimary
import com.aeswox.arcplayer.ui.theme.OnSurface as JellyOnSurface
import com.aeswox.arcplayer.ui.theme.OnSurfaceVariant as JellyOnSurfaceVariant
import com.aeswox.arcplayer.ui.theme.OutlineVariant as JellyOutlineVariant
import com.aeswox.arcplayer.ui.theme.Primary as JellyPrimary
import com.aeswox.arcplayer.ui.theme.Surface as JellySurface
import com.aeswox.arcplayer.ui.theme.SurfaceContainer as JellySurfaceContainer
import com.aeswox.arcplayer.ui.theme.SurfaceContainerHigh as JellySurfaceHigh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlin.math.abs

private enum class CollectionFilter(val label: String) {
    ALL("All"), MOVIES("Movies"), SERIES("Series"), FAVOURITES("Favourites")
}

internal data class CollectionResume(
    val title: ShelfTitle?,
    val local: VideoItem?,
    val progress: PlayOrder.Progress,
    /**
     * The shelf work this device file is a kept copy of, for a file the grid itself never shows.
     *
     * A keep is filed in this app's own corner of `Movies`, which the shelf skips on purpose so a download
     * never starts a second poster beside the film it copied. The skip is right for the grid and wrong for
     * a card that has to say what film is worth resuming: the ledger knows both ids of the same bytes, and
     * this is the work those ids name.
     */
    val keptOf: ShelfTitle? = null
) {
    val key: String get() = title?.let { "cloud:${it.group.key}" } ?: "local:${local!!.id}"
    val name: String get() = title?.name ?: keptOf?.name ?: local!!.title
}

/**
 * Everything the collection tabs draw, worked out once per library change instead of once per composition.
 *
 * A tab switch throws the leaving page away and builds the entering one from nothing, so anything a page
 * derives inside itself is derived again in the very frame the transition is supposed to start in — and
 * this derives every file name the library holds through the catalogue parser, which is where the
 * viewer's half second goes. Built above the transition it is already there when the finger lifts.
 */
internal class CollectionShelf(
    val authorized: Boolean,
    val loading: Boolean,
    val gone: Boolean,
    val readFailed: Boolean,
    val resolved: List<ShelfTitle>,
    val titles: List<ShelfTitle>,
    val hiddenTitles: Int,
    val movies: List<ShelfTitle>,
    val series: List<ShelfTitle>,
    val featured: List<ShelfTitle>,
    val onShelf: Set<Long>,
    /**
     * The phone's own files that belong to a film or an episode, and nothing else it holds.
     *
     * Not a second guess about what counts as a movie. The shelf is the one pass that reads a device
     * file's name against a catalogue, so its answer is reused here: the positive ids among the parts of
     * a card, plus the catalog row of every keep made from one of those parts — a keep is the same film
     * as the copy it was made from, filed under a row the grid never sees. Everything else in MediaStore
     * is a camera roll, a screen recording or a clip, and the viewer has asked not to be shown those
     * anywhere, including on the device screens.
     */
    val deviceIds: Set<Long>,
    val savedIds: Set<Long>,
    val continuing: List<CollectionResume>
)

/**
 * The whole shelf from one pass over what the library holds right now.
 *
 * The preference maps are read here, not passed in, because a card landing late has to rebuild this
 * rather than be quietly ignored by a caller that already looked. The Telegram list and the two read
 * flags arrive as values the caller read through a state delegate, so the derivation observes them at
 * the moment it runs.
 */
private fun buildCollectionShelf(
    authorized: Boolean,
    shelfId: Long,
    videosByChat: Map<Long, List<CloudVideoItem>>,
    loading: Boolean,
    gone: Boolean,
    readFailed: Boolean,
    prefsStore: ArcPreferencesStore,
    snapshot: MediaLibrarySnapshot,
    downloads: Map<Long, DownloadEntry>
): CollectionShelf {
    val files = if (authorized && !gone && shelfId != 0L) videosByChat[shelfId].orEmpty()
        .filter { it.chatId == shelfId } else emptyList()
    val lengths = prefsStore.knownLengths.toMap()
    val cards = prefsStore.mediaCards.toMap()
    val positions = prefsStore.resumePositions.toMap()
    val watched = prefsStore.watchedAt.toMap()
    val history = prefsStore.watchHistoryIds
    val savedIds = DownloadLedger.completeIds(downloads)
    // The two piles as one, newest file first, because a card's place in the grid is the place of the
    // newest copy it holds. A Telegram file arrives with the date of the message that carries it and a
    // device file with the moment MediaStore took it in, which are the only two figures either has.
    //
    // Only what the phone's own folders say is a *kept* video reaches the shelf. A camera roll runs
    // through this list otherwise, and every holiday clip in it would cost a catalogue request to be told
    // it is not a film — and one that parses by accident would sit on a poster card beside the collection.
    val onChannel: List<Playable> = files.map { it.withLength(lengths[it.stableId] ?: 0L) }
    val onPhone: List<Playable> = snapshot.videos
        .map { it.withLength(lengths[it.stableId] ?: 0L) }
        .filter { PhoneVideos.verdictOf(it.relativePath, it.durationMs) == PhoneVideoVerdict.COLLECTION }
    val shelfFiles = (onChannel + onPhone).sortedByDescending { it.arrivedAtSec }
    val groups = TitleGroups.group(
        shelfFiles.map { TitleCandidate(it.stableId, it.title, it.sizeBytes, it.frameHeight, it.isOnPhone) }
    )
    val resolved = shelfCards(shelfFiles, groups, cards)
    // A work with nothing but its own file name to show is a grey box on a poster wall, so a card only
    // earns its place once the catalogue has said what it is. That test is asked of a shelf the phone
    // alone fills, and never of one holding a channel copy: those files really are on the shelf, and an
    // airplane would otherwise empty the whole collection.
    val titles = resolved.filter { it.heldOnChannel || !it.posterUrl.isNullOrBlank() || it.providerId != null }
    // The device files that earned a card, in the phone's own numbers, plus the rows holding a keep of
    // one. A card the grid hides — no name, no picture — hides its files here too, or the two screens
    // would disagree about what this app contains.
    val cardParts = titles.flatMap { it.partIds }.toSet()
    val deviceIds = cardParts.filter { it > 0L }.toSet() +
        DownloadLedger.keptRows(downloads).filter { (_, part) -> part in cardParts }.keys
    val shelfResume = resolved.mapNotNull { title ->
        PlayOrder.progress(
            title.copySlots, PlayOrder.positionByCopy(title.copySlots, positions), title.lengths, watched, savedIds
        )?.let { CollectionResume(title, null, it) }
    }.sortedByDescending { watched[it.progress.partId] ?: 0L }
    val namedFromShelf = shelfResume.mapNotNull { it.title?.group?.key }.toSet()
    // A watched file the shelf already carries is in the rail above, under its own title — which is the
    // whole point of merging the two piles, so a film played from `Movies/` moves the poster rather than
    // starting a second row beside it. What is left here is a keep of a film whose card has nothing left
    // to resume, and nothing else: the phone's clips and camera roll are not a film's worth of progress.
    val onShelf = resolved.flatMap { it.partIds }.toSet()
    val workOfRow = DownloadLedger.keptRows(downloads).mapNotNull { (row, part) ->
        resolved.firstOrNull { title -> part in title.partIds }?.let { row to it }
    }.toMap()
    val localResume = snapshot.videos.mapNotNull { video ->
        // A clip, a screen recording or a camera roll file has no business in a rail about films, and
        // this is the one place the phone's whole video table was still being read straight.
        if (video.id !in deviceIds) return@mapNotNull null
        if (video.id in onShelf) return@mapNotNull null
        // A keep is the same bytes as the channel copy the shelf already carries, filed under the row its
        // bytes landed in. So the shelf's card is the one this rail wants, and it wins for the same reason
        // a film played from `Movies/` wins there: one work, one card. Only a work that earned no card —
        // watched through, so nothing is left to resume — lets the keep speak for it, and then it is named
        // by the work rather than by the name a channel typed onto a file.
        val keptOf = workOfRow[video.id]
        if (keptOf != null && keptOf.group.key in namedFromShelf) return@mapNotNull null
        val position = positions[video.id] ?: 0L
        val length = maxOf(video.durationMs, lengths[video.id] ?: 0L)
        val finished = com.aeswox.arcplayer.playback.ResumeRules.decide(position, length) ==
            com.aeswox.arcplayer.playback.ResumeOutcome.DROP
        if (position < com.aeswox.arcplayer.playback.ResumeRules.MIN_RESUME_POSITION_MS || finished) null
        else CollectionResume(
            null, video,
            PlayOrder.Progress(video.id, position, length, (length - position).coerceAtLeast(0L)),
            keptOf
        )
    }
    val continuing = (shelfResume + localResume).sortedBy { entry ->
        history.indexOf(entry.progress.partId).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }.take(12)
    return CollectionShelf(
        authorized = authorized,
        loading = loading,
        gone = gone,
        readFailed = readFailed,
        resolved = resolved,
        titles = titles,
        hiddenTitles = resolved.size - titles.size,
        movies = titles.filterNot { it.isSeries },
        series = titles.filter { it.isSeries },
        featured = titles.filter {
            it.name.isNotBlank() && !it.group.key.startsWith("u:") && !it.posterUrl.isNullOrBlank()
        }.take(5),
        onShelf = onShelf,
        deviceIds = deviceIds,
        savedIds = savedIds,
        continuing = continuing
    )
}

/**
 * The shelf, kept alive across a tab switch and rebuilt only when the library actually moved.
 *
 * [snapshot], [authorized] and the collection's chat id are plain values by the time they get here, so
 * they are keys of the block holding the derived value; everything the shelf reads through
 * [prefsStore] or the two Telegram flows is observed inside the derivation instead. Both catalogue
 * passes run here too, which is the other half of the fix: they key their effects on the list they are
 * given, and a list rebuilt by every composition restarted every catalogue ask in the middle of the
 * transition. What comes back is the state, not its contents, so each tab reads the shelf in its own
 * scope and a late catalogue answer redraws the page showing it rather than only the page that asked.
 */
@Composable
internal fun rememberCollectionShelf(
    repository: CloudRepository,
    prefsStore: ArcPreferencesStore,
    snapshot: MediaLibrarySnapshot
): State<CollectionShelf> {
    val videosByChat by repository.videos.collectAsState()
    val auth by repository.authState.collectAsState()
    val downloads by repository.downloads.collectAsState()
    val authorized = auth.stage == CloudAuthStage.AUTHORIZED
    val shelfId = prefsStore.movieFolderChatId
    val owner = LocalLifecycleOwner.current
    var loading by remember(repository, authorized, shelfId) { mutableStateOf(false) }
    var gone by remember(repository, authorized, shelfId) { mutableStateOf(false) }
    var readFailed by remember(repository, authorized, shelfId) { mutableStateOf(false) }
    LaunchedEffect(repository, authorized, shelfId, owner) {
        if (!authorized) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loading = true
            readFailed = false
            try {
                when (val result = repository.readShelf(shelfId)) {
                    is CloudRepository.ShelfRead.Ready -> {
                        gone = false
                        if (result.chatId != shelfId) prefsStore.rememberMovieFolder(result.chatId)
                    }
                    CloudRepository.ShelfRead.Gone -> gone = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                readFailed = true
            } finally {
                loading = false
            }
            awaitCancellation()
        }
    }

    // Handed back as the state rather than its contents: the tabs read it in their own scope, so a late
    // catalogue answer redraws the page that shows it instead of only the page that asked.
    val shelf = remember(repository, prefsStore, snapshot, authorized, shelfId) {
        derivedStateOf {
            buildCollectionShelf(
                authorized, shelfId, videosByChat, loading, gone, readFailed, prefsStore, snapshot,
                downloads
            )
        }
    }
    TitleSearchPass(shelf.value.resolved, prefsStore)
    // Every card on this shelf is about to wear the same picture its page will, and the wide shot only
    // comes with the title's detail. Asked here rather than when a page opens, because a card that
    // swaps its own artwork at the moment of the tap is a visible flicker behind the incoming page.
    ShelfArtworkWarmPass(shelf.value.titles, prefsStore)
    return shelf
}

@Composable
internal fun CollectionScreens(
    tab: BottomNavTab,
    shelfState: State<CollectionShelf>,
    prefsStore: ArcPreferencesStore,
    snapshot: MediaLibrarySnapshot,
    topInset: Dp,
    bottomInset: Dp,
    onOpenTitle: (ShelfTitle) -> Unit,
    onShelfPlay: (Playable, List<Playable>, List<List<Long>>, MediaCard?) -> Unit,
    onLocalPlay: (VideoItem, List<VideoItem>) -> Unit,
    onOpenDevice: () -> Unit,
    onOpenAllVideos: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onManageCollection: () -> Unit,
    activeDownloads: Int,
    onOpenDownloads: () -> Unit
) {
    if (tab == BottomNavTab.SETTINGS) return
    val shelf = shelfState.value
    val authorized = shelf.authorized
    val loading = shelf.loading
    val gone = shelf.gone
    val readFailed = shelf.readFailed
    val titles = shelf.titles
    val hiddenTitles = shelf.hiddenTitles
    val movies = shelf.movies
    val series = shelf.series
    val featured = shelf.featured
    val onShelf = shelf.onShelf
    val continuing = shelf.continuing
    val savedIds = shelf.savedIds
    val favourites = prefsStore.favoriteIds
    val playResume: (CollectionResume) -> Unit = { entry ->
        val title = entry.title
        if (title != null) {
            val byCopy = PlayOrder.positionByCopy(title.copySlots, prefsStore.resumePositions.toMap())
            val currentLengths = title.partIds.associateWith { maxOf(title.lengths[it] ?: 0L, prefsStore.knownLengths[it] ?: 0L) }
            // Continue must not jump to an earlier, unstarted episode selected by nextCopy.
            val progress = PlayOrder.progress(
                title.copySlots, byCopy, currentLengths, prefsStore.watchedAt, shelf.savedIds
            )
            val choice = progress?.let { PlayOrder.Play(it.partId, it.resumeMs) }
                ?: PlayOrder.nextCopy(
                    title.copySlots, byCopy, currentLengths, prefsStore.watchedAt, shelf.savedIds
                )
            title.parts.firstOrNull { it.stableId == choice?.partId }?.let { file ->
                prefsStore.rememberResumePosition(file.stableId, choice!!.resumeMs)
                onShelfPlay(file, title.parts, title.copySlots, title.card)
            }
        } else entry.local?.let { onLocalPlay(it, snapshot.videos) }
    }
    var browse by rememberSaveable(tab) { mutableStateOf<CollectionFilter?>(null) }
    var filter by rememberSaveable { mutableStateOf(CollectionFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val homeScroll = rememberLazyListState()
    BackHandler(enabled = browse != null) { browse = null }
    val selectedFilter = browse ?: filter
    val filtered = titles.filter {
        when (selectedFilter) {
            CollectionFilter.ALL -> true
            CollectionFilter.MOVIES -> !it.isSeries
            CollectionFilter.SERIES -> it.isSeries
            CollectionFilter.FAVOURITES -> it.partIds.any { id -> id in favourites }
        }
    }
    val terms = remember(query) { query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val cloudMatches = titles.filter { title ->
        collectionMatches(terms, buildString {
            append(title.name).append(' ').append(title.group.displayTitle).append(' ')
            title.parts.forEach { append(it.title).append(' ') }
            title.group.parts.forEach { append(it.episodeTitle.orEmpty()).append(' ') }
        })
    }
    // A device file the shelf already carries is searched as a title, not twice, so it drops out of the
    // pile below — which is left holding exactly what the collection has no card for.
    val localMatches = snapshot.videos.filter {
        it.id !in onShelf && collectionMatches(terms, "${it.title} ${it.folderName}")
    }
    val emptyMessage = when {
        !authorized -> "Connect Telegram to see your own movies and series. Your on-device videos are still available."
        loading -> "Reading your Telegram collection…"
        readFailed -> "Your collection could not be refreshed. Check your connection or manage your collection."
        gone -> "This Telegram collection is unavailable. Choose your collection to get started again."
        else -> "Your next favourite is already yours. Add movies or episodes to your Telegram collection to see them here."
    }
    val shortcuts = listOf("On Device" to onOpenDevice, "All videos" to onOpenAllVideos,
        "Playlists" to onOpenPlaylists, "Manage collection" to onManageCollection)

    Box(Modifier.fillMaxSize().background(JellyBackground)) {
        when {
            browse != null || tab == BottomNavTab.LIBRARY -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(128.dp), modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topInset + 24.dp, bottom = bottomInset + 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (browse != null) CollectionBack { browse = null }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CollectionHeading(
                                    if (browse != null) when (browse) {
                                        CollectionFilter.MOVIES -> "Your Movies"
                                        CollectionFilter.SERIES -> "Your Series"
                                        CollectionFilter.FAVOURITES -> "Favourites"
                                        else -> "Your collection"
                                    } else "Library",
                                    modifier = Modifier.weight(1f)
                                )
                                // The Library screen's own way into the Downloads page. It is a circle
                                // rather than a pill because it stands beside a 32 sp word, and any
                                // taller control would push the heading off the top of the grid.
                                if (browse == null && tab == BottomNavTab.LIBRARY) {
                                    CollectionDownloadsBadge(activeDownloads, onOpenDownloads)
                                }
                            }
                            Text(
                                buildString {
                                    append("${titles.size} ${if (titles.size == 1) "title" else "titles"} · Telegram and this phone")
                                    // Said out loud because a device file the catalogue would not name is filed here
                                    // and then not drawn, and a grid that quietly shrinks looks like a bug.
                                    if (hiddenTitles > 0) {
                                        append(" · $hiddenTitles ${if (hiddenTitles == 1) "has" else "have"} no artwork yet")
                                    }
                                },
                                color = JellyOnSurfaceVariant
                            )
                        }
                    }
                    if (browse == null) item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            shortcuts.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { (label, action) -> CollectionPill(label, Modifier.weight(1f), onClick = action) }
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.physicsBounceOverscroll(isHorizontal = true)
                        ) {
                            items(CollectionFilter.entries, key = { it.name }) { option ->
                                CollectionPill(option.label, selected = option == selectedFilter) {
                                    AppHaptics.tick()
                                    if (browse != null) browse = option else filter = option
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        CollectionEmpty(if (titles.isEmpty()) emptyMessage else "No ${selectedFilter.label.lowercase()} yet.", onManageCollection, onOpenDevice)
                    }
                    itemsIndexed(filtered, key = { _, title -> title.group.key }) { index, title ->
                        StaggeredListItem(index, "shelf:${title.group.key}") {
                            CollectionPoster(title, Modifier.fillMaxWidth(), prefsStore, savedIds, onOpenTitle)
                        }
                    }
                }
            }
            tab == BottomNavTab.SEARCH -> {
                LazyColumn(
                    modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topInset + 24.dp, bottom = bottomInset + 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { CollectionHeading("Search") }
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            label = { Text("Search your collection & device") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Default.Close, "Clear search",
                                Modifier.size(48.dp).jellyClick(role = Role.Button) { query = "" }.padding(12.dp)) },
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JellyOnSurface,
                                unfocusedBorderColor = JellyOutlineVariant, focusedTextColor = JellyOnSurface,
                                unfocusedTextColor = JellyOnSurface, cursorColor = JellyOnSurface)
                        )
                    }
                    if (terms.isEmpty()) item {
                        Text("Find your titles, episode files and on-device folders. Only your own videos — no catalogue search.", color = JellyOnSurfaceVariant)
                    } else {
                        item { CollectionHeading("Your collection · ${cloudMatches.size}", small = true) }
                        if (cloudMatches.isEmpty()) item { Text("No matching collection titles.", color = JellyOnSurfaceVariant) }
                        itemsIndexed(cloudMatches, key = { _, title -> "cloud:${title.group.key}" }) { index, title ->
                            StaggeredListItem(index, "cloud:${title.group.key}") {
                                CollectionSearchRow(title.name,
                                    "${if (title.isSeries) "Series" else "Movie"} · ${title.whereItLives()}",
                                    onClick = { onOpenTitle(title) }, art = { CollectionShelfArt(title, prefsStore) })
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)); CollectionHeading("On this phone · ${localMatches.size}", small = true) }
                        if (localMatches.isEmpty()) item { Text("No matching device videos or folders.", color = JellyOnSurfaceVariant) }
                        itemsIndexed(localMatches, key = { _, video -> "local:${video.id}" }) { index, video ->
                            StaggeredListItem(index, "local:${video.id}") {
                                CollectionSearchRow(video.title, video.folderName,
                                    onClick = { onLocalPlay(video, localMatches) }, art = { CollectionLocalArtwork(video) })
                            }
                        }
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    val heroHeight = rememberCollectionHeroHeight()
                    val heroPager = rememberPagerState(pageCount = { featured.size })
                    val heroPull = remember { Animatable(0f) }
                    // The backdrop lives outside the scrolling column on purpose. The bounce
                    // overscroll translates the whole column — title, pills and dots ride it down —
                    // while this layer follows only the real scroll, so mid-stretch the picture
                    // holds the top edge and no page background opens up above it.
                    if (featured.isNotEmpty()) PinnedHeroArtwork(featured, prefsStore, heroPager, homeScroll, heroHeight, heroPull)
                    LazyColumn(
                        state = homeScroll, modifier = Modifier.physicsBounceOverscroll(displacement = heroPull).fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        item(key = "hero") {
                            CollectionHero(featured, prefsStore, topInset, onOpenTitle, heroPager, heroHeight)
                        }
                        if (readFailed && titles.isNotEmpty()) item {
                            Text("Couldn't refresh. Showing your saved collection.", color = JellyOnSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                        if (continuing.isNotEmpty()) item(key = "continue") {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CollectionHeading("Continue Watching", Modifier.padding(horizontal = 24.dp), small = true)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.physicsBounceOverscroll(isHorizontal = true)
                                ) {
                                    itemsIndexed(continuing, key = { _, entry -> entry.key }) { index, entry ->
                                        StaggeredListItem(index, "continue:${entry.key}") { CollectionContinueCard(entry, prefsStore, savedIds) { playResume(entry) } }
                                    }
                                }
                            }
                        }
                        if (titles.isEmpty()) item(key = "empty") {
                            Box(Modifier.padding(horizontal = 24.dp)) { CollectionEmpty(emptyMessage, onManageCollection, onOpenDevice) }
                        }
                        if (titles.isNotEmpty()) item(key = "recent") {
                            CollectionRail("Recently Added", titles.take(16), prefsStore, savedIds, { browse = CollectionFilter.ALL }, onOpenTitle)
                        }
                        if (movies.isNotEmpty()) item(key = "movies") {
                            CollectionRail("Your Movies", movies.take(16), prefsStore, savedIds, { browse = CollectionFilter.MOVIES }, onOpenTitle)
                        }
                        if (series.isNotEmpty()) item(key = "series") {
                            CollectionRail("Your Series", series.take(16), prefsStore, savedIds, { browse = CollectionFilter.SERIES }, onOpenTitle)
                        }
                    }
                }
            }
        }
    }
}

private fun collectionMatches(terms: List<String>, text: String): Boolean = terms.all { text.contains(it, ignoreCase = true) }

/**
 * Which piles a card's copies came out of, in the words a viewer would use for them.
 *
 * A merged film has to say both: the channel copy is what put it on the shelf and the phone copy is
 * what Play will open, so a label that picked one of the two would be a lie either way.
 */
private fun ShelfTitle.whereItLives(): String {
    val onChannel = heldOnChannel
    val onPhone = parts.any { it.isOnPhone }
    return if (onChannel && onPhone) "Telegram and this phone"
    else if (onChannel) "Telegram" else "On this phone"
}

/**
 * The height both heroes wear — the collection home's carousel and a title page's opening picture.
 *
 * 1.25 viewports tall puts the picture's bottom just past the middle of the screen on the phones this
 * app is shaped for, and the clamp keeps a tablet from getting a wall of artwork. Both screens ask the
 * same question, so both ask it here: a hero that changes size between the card and the page it opens
 * is the flicker the push was built to avoid.
 */
@Composable
internal fun rememberCollectionHeroHeight(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return remember(width) { (width * 1.25f).coerceIn(440.dp, 500.dp) }
}

/**
 * The one hero scrim: shade for the status bar, clear across the picture, then the page closing over
 * its own bottom edge. Shared because the two heroes should hand over to the page the same way.
 */
internal val HeroScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.32f), 0.35f to Color.Transparent,
    0.67f to Color.Black.copy(alpha = 0.68f), 0.84f to Color.Black.copy(alpha = 0.75f),
    1f to JellyBackground
)

/**
 * The hero's last 64 dp: page colour closing over the picture, so its bottom edge dissolves instead
 * of being cut. The scrim's own tail is only 75% black where the picture is brightest, and a bright
 * poster bottom prints straight through it.
 */
internal val HeroFeather = Brush.verticalGradient(listOf(JellyBackground.copy(alpha = 0f), JellyBackground))

@Composable
private fun CollectionHero(titles: List<ShelfTitle>, prefs: ArcPreferencesStore, topInset: Dp,
                           onOpen: (ShelfTitle) -> Unit, pager: PagerState, heroHeight: Dp) {
    if (titles.isEmpty()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = topInset + 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ONLY YOURS", color = JellyOnSurfaceVariant, fontSize = 12.sp, letterSpacing = 2.sp)
            CollectionHeading("Your collection")
            Text("A little cinema, all your own.", color = JellyOnSurfaceVariant)
        }
        return
    }
    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            // The lazy key map can ask for a page index from the previous, longer list in the
            // same frame the shelf shrinks, so the key must survive an out-of-range index.
            key = { titles.getOrNull(it)?.group?.key ?: "hero:$it" },
            // Frosted Hero Carousel Glide: a released page eases into place on a soft spring
            // instead of ratcheting to the snap point.
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            ),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val title = titles.getOrNull(page) ?: return@HorizontalPager
            val nearby = abs(page - pager.currentPage) <= 1
            val detail = if (nearby) TitleDetailFetch(title.providerId, title.isSeries, prefs) else null
            val logo = rememberCachedArt(if (nearby) title.wordmarkUrl else null, 640)
            val metadata = listOfNotNull(detail?.startYear?.toString(), if (title.isSeries) "Series" else "Movie",
                detail?.runtimeMinutes?.takeIf { it > 0 }?.let(::formatRuntime), detail?.genres?.firstOrNull()).joinToString(" · ")
            // The picture and its scrim are pinned outside the column, so this page carries only
            // what the stretch is meant to move: the wordmark, the metadata and the pill.
            val recede = abs(pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize()) {
                // The group wants to sit lower than the hero box allows, so the last 3 dp hang past
                // its bottom edge into the gap above the next section.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp)
                    .graphicsLayer { alpha = 1f - recede; translationY = 3.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (logo != null) Image(logo.asImageBitmap(), title.name, contentScale = ContentScale.Fit,
                        alignment = Alignment.Center, modifier = Modifier.fillMaxWidth(0.8f).height(76.dp))
                    else Text(title.name, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(metadata, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    CollectionPill("View Details", Modifier.width(164.dp).height(56.dp), onClick = { onOpen(title) })
                }
            }
        }
    }
}

/**
 * The hero backdrop, drawn behind the scrolling column instead of inside it.
 *
 * The bounce overscroll translates the column as a whole, and everything inside the column shares
 * that moved clip, so an artwork inside the list can never hold the top edge mid-stretch — pulling
 * down drags the picture down and opens the page background above it. This layer follows only the
 * real scroll offset, which leaves the picture pinned while the title, metadata and pill riding
 * the column give way, exactly as the stretch intends.
 */
@Composable
private fun PinnedHeroArtwork(titles: List<ShelfTitle>, prefs: ArcPreferencesStore, pager: PagerState,
                              scroll: LazyListState, heroHeight: Dp, pull: Animatable<Float, *>) {
    val layerHeightPx = with(LocalDensity.current) { heroHeight.toPx() }
    val maxBlurPx = with(LocalDensity.current) { 28.dp.toPx() }
    Box(Modifier.graphicsLayer {
        val offset = if (scroll.firstVisibleItemIndex == 0) scroll.firstVisibleItemScrollOffset else 0
        // 0.6 rather than 1: the picture already recedes at 40% of the scroll, as before.
        translationY = if (scroll.firstVisibleItemIndex == 0) -offset * 0.6f else -layerHeightPx * 2f
        // A pinned picture that only gets covered reads as dead weight. Defocusing and dimming it as
        // the list comes up makes it recede instead, and the soft edge is what carries the eye across
        // the line the page-colour sheet finally closes over.
        val retreat = heroRetreat(scroll, layerHeightPx)
        // The radius guard is not tidiness: `BlurEffect(0f, 0f, ..)` returns a null native pointer on
        // API 31 and takes the process down, so the effect has to stay absent until there is blur to do.
        renderEffect = if (retreat <= 0.001f) null
            else defocusAndDim(maxBlurPx * retreat, 1f - 0.65f * retreat)
    }.fillMaxWidth().height(heroHeight).clipToBounds()) {
        val offsetFraction = pager.currentPageOffsetFraction
        for (index in (pager.currentPage - 1)..(pager.currentPage + 1)) {
            if (index !in titles.indices) continue
            HeroArtPage(titles[index], prefs, index - pager.currentPage - offsetFraction)
        }
    }
    // The cover rides that edge — real scroll plus the overscroll stretch — and hides the overdraw
    // behind the page colour. Its top edge is feathered rather than square: the cover travels at
    // 1:1 while the picture recedes at 0.6:1, so after a few hundred dp of scroll the edge is no
    // longer sitting on the scrim's opaque tail but across mid-tone artwork, and a square edge
    // slices it (measured: luma 33.8 on one row, 0.0 on the next). The feather dissolves the
    // picture under the sheet instead, and also swallows the artwork's own clip edge, which
    // surfaces for the same reason. The solid part still overlaps the picture's bottom by a few
    // pixels rather than meeting it edge to edge — both edges land on fractional pixels, and the
    // row between them is painted by neither at full strength, which prints one bright line of raw
    // artwork across the seam.
    //
    // The stretch only ever rides the cover upward. Riding it down would slide that overlap off the
    // picture's clip edge — the pinned layer holds still while the cover follows the finger — and the
    // same raw-artwork row reappears for as long as the finger is down (measured on a held pull:
    // luma 22.2 on the seam row, 0.0 on every row either side of it).
    val seamOverlapPx = with(LocalDensity.current) { 3.dp.toPx() }
    val featherPx = with(LocalDensity.current) { 64.dp.toPx() }
    Column(Modifier.graphicsLayer {
        val offset = if (scroll.firstVisibleItemIndex == 0) scroll.firstVisibleItemScrollOffset else layerHeightPx.toInt()
        translationY = layerHeightPx - offset + pull.value.coerceAtMost(0f) - seamOverlapPx - featherPx
    }.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(64.dp).background(HeroFeather))
        Box(Modifier.fillMaxWidth().weight(1f).background(JellyBackground))
    }
}

/**
 * How far the list has travelled across the pinned picture, 0..1.
 *
 * Only ever read from inside a `graphicsLayer` lambda: reading it in the composition body would
 * recompose this layer, and the three artwork pages it holds, on every scroll frame.
 */
private fun heroRetreat(scroll: LazyListState, layerHeightPx: Float): Float {
    val travelled = if (scroll.firstVisibleItemIndex == 0) scroll.firstVisibleItemScrollOffset.toFloat() else layerHeightPx
    return (travelled / layerHeightPx).coerceIn(0f, 1f)
}

/**
 * Defocus the picture and scale its colours toward black, in one effect chain on one layer.
 *
 * The darkening has to ride the blur's own layer. Painted as a sibling over the artwork through a
 * nested `graphicsLayer { alpha = .. }` it never showed at all, while this layer tracked the scroll
 * frame for frame — measured: a constant `keep` of 0.35 landed as 0.350 on the pixel bands.
 * Compose 1.7.4 ships no `ColorMatrixEffect`, so the chain is assembled through the platform API.
 */
private fun defocusAndDim(radiusPx: Float, keep: Float): RenderEffect {
    val blur = BlurEffect(radiusPx, radiusPx, TileMode.Clamp).asAndroidRenderEffect()
    val dim = android.graphics.ColorMatrixColorFilter(
        floatArrayOf(keep, 0f, 0f, 0f, 0f, 0f, keep, 0f, 0f, 0f, 0f, 0f, keep, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    )
    return android.graphics.RenderEffect.createColorFilterEffect(dim, blur).asComposeRenderEffect()
}

/** One backdrop page of the pinned layer, sliding with the same offset the pager gives its page. */
@Composable
private fun HeroArtPage(title: ShelfTitle, prefs: ArcPreferencesStore, slide: Float) {
    val poster = rememberCachedArt(title.posterUrl, 1_000)
    val nearby = abs(slide) <= 1f
    val detail = if (nearby) TitleDetailFetch(title.providerId, title.isSeries, prefs) else null
    val backdrop = rememberCachedArt(detail?.backdropUrl, 1_100) ?: poster
    Box(Modifier.graphicsLayer { translationX = slide * size.width }.fillMaxSize().clipToBounds()) {
        if (backdrop == null) CollectionImage(null, awaiting = true)
        else Image(backdrop.asImageBitmap(), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.18f; scaleY = 1.18f
                translationX = -slide * size.width * 0.1f
            })
        Box(Modifier.fillMaxSize().background(HeroScrim))
        // The reference recedes an adjacent card by scaling it down. Here the card is the whole
        // screen, so shrinking one would pull the background up between two pages; it recedes
        // into shadow instead, which reads the same way over a full-bleed picture.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * abs(slide).coerceIn(0f, 1f))))
    }
}

@Composable
private fun CollectionRail(label: String, titles: List<ShelfTitle>, prefsStore: ArcPreferencesStore,
                           savedIds: Set<Long>,
                           onAll: () -> Unit, onOpen: (ShelfTitle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            CollectionHeading(label, Modifier.weight(1f), small = true)
            Text("See all", color = JellyOnSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.jellyClick(role = Role.Button, onClickLabel = "See all $label", onClick = onAll).padding(vertical = 12.dp, horizontal = 8.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.physicsBounceOverscroll(isHorizontal = true)
        ) {
            itemsIndexed(titles, key = { _, title -> title.group.key }) { index, title ->
                StaggeredListItem(index, "rail:$label:${title.group.key}") {
                    CollectionPoster(title, Modifier.width(132.dp), prefsStore, savedIds, onOpen)
                }
            }
        }
    }
}

/** One shelf card: the poster the title wears, its name, and what it is. */
@Composable
private fun CollectionPoster(title: ShelfTitle, modifier: Modifier, prefsStore: ArcPreferencesStore,
                             savedIds: Set<Long>, onOpen: (ShelfTitle) -> Unit) {
    val artUrl = rememberShelfArtUrl(title, prefsStore)
    Column(modifier.jellyClick(role = Role.Button, onClickLabel = "View ${title.name}") { onOpen(title) },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            .graphicsLayer { clip = true; shape = RoundedCornerShape(22.dp) }) {
            CollectionArtwork(artUrl)
            // The mark of a film that will play with no signal at all, put on the picture rather than
            // under it so a wall of posters still reads as pictures. It is the ledger's own answer — not
            // Telegram's, which would cost one request per card to learn what a file on this device
            // already says — and it waits for one kept copy in every part of the work, because a series
            // with episode three saved is not a series you can watch offline, and a card that said
            // otherwise would be the lie that gets someone onto a plane.
            if (title.copySlots.isNotEmpty() && title.copySlots.all { slot -> slot.any { it in savedIds } }) {
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(4.dp)) {
                    Icon(
                        imageVector = HugeIcons.Download,
                        contentDescription = "Available offline",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(title.name, color = JellyOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (title.isSeries) "Series" else "Movie", color = JellyOnSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun CollectionContinueCard(entry: CollectionResume, prefsStore: ArcPreferencesStore,
                                   savedIds: Set<Long>, onPlay: () -> Unit) {
    val progress = entry.progress
    val part = entry.title?.group?.parts?.firstOrNull { it.id == progress.partId }
    val episode = part?.episode?.let { number -> part.season?.let { "S$it · E$number" } ?: "Episode $number" }
    val remaining = if (progress.lengthMs > 0L) "${(progress.remainingMs + 59_999L) / 60_000L}m left" else "Resume"
    // The rail names the copy it is about to open, not the card that carries it: a film watched from
    // `Movies/` is still a file on this phone even though its poster came off the channel.
    val resume = entry.local ?: entry.title?.parts?.firstOrNull { it.stableId == progress.partId }
        ?: entry.title?.representative
    // "Offline" is the ledger's word, not Telegram's: this part's copy was verified byte for byte on this
    // device, which is the one claim a flight cannot argue with. A channel file that says so without the
    // entry behind it would promise a film the sweep has already handed back.
    val caption = listOfNotNull(
        when {
            resume?.isOnPhone == true -> "On this phone"
            progress.partId in savedIds -> "Offline"
            else -> "On Telegram"
        },
        episode,
        remaining
    ).joinToString(" · ")
    Column(Modifier.width(248.dp).jellyClick(role = Role.Button, onClickLabel = "Resume ${entry.name}", onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(22.dp))) {
            val work = entry.title ?: entry.keptOf
            if (work != null) CollectionShelfArt(work, prefsStore) else entry.local?.let { CollectionLocalArtwork(it) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center)
                .size(44.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(8.dp))
            if (progress.lengthMs > 0L) {
                val fraction = (progress.resumeMs.toFloat() / progress.lengthMs).coerceIn(0f, 1f)
                Box(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(3.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White, CircleShape))
                }
            }
        }
        Text(entry.name, fontSize = 15.sp, color = JellyOnSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, fontSize = 12.sp, color = JellyOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One shelf title's artwork, asked through the store so the row redraws when the catalogue's wide shot
 * lands. Read here rather than in a list's content lambda, which would freeze the row on whatever the
 * first composition saw.
 */
@Composable
private fun CollectionShelfArt(title: ShelfTitle, prefsStore: ArcPreferencesStore) {
    CollectionArtwork(rememberShelfArtUrl(title, prefsStore))
}

@Composable
private fun CollectionArtwork(url: String?) {
    val bitmap = rememberCachedArt(url, 520)
    CollectionImage(bitmap?.asImageBitmap(), awaiting = url != null)
}

@Composable
private fun CollectionLocalArtwork(video: VideoItem) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, video.uri, context) {
        value = loadVideoThumbnailCancellable(context.contentResolver, video.uri, 520, 300)
    }
    CollectionImage(bitmap, awaiting = true)
}

/** A poster that has not arrived yet is a shimmering slot, not a dead grey box. */
@Composable
private fun CollectionImage(bitmap: ImageBitmap?, awaiting: Boolean = false) {
    Box(Modifier.fillMaxSize().background(JellySurfaceHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (awaiting) JellySkeleton()
        else Text("Artwork unavailable", color = JellyOnSurfaceVariant, fontSize = 11.sp,
            modifier = Modifier.padding(12.dp), maxLines = 2)
    }
}

@Composable
private fun CollectionSearchRow(name: String, source: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(JellySurfaceContainer)
        .jellyClick(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(64.dp).height(88.dp).clip(RoundedCornerShape(16.dp))) { art() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, color = JellyOnSurface, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source, color = JellyOnSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CollectionHeading(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(text, modifier.semantics { heading() }, color = JellyOnSurface, fontSize = if (small) 21.sp else 32.sp,
        fontWeight = FontWeight.Bold, lineHeight = if (small) 26.sp else 38.sp)
}

/**
 * The Library screen's way into the Downloads page, with a dot while bytes are arriving.
 *
 * The dot is the part that has to be legible at a glance: from this screen a fetch is otherwise
 * invisible, and the shade's progress row is collapsed, off, or buried under the dozen other things a
 * phone notifies about. It animates rather than appearing, so a download started while the viewer is
 * looking at the shelf reads as a change instead of as a mark that was always there.
 */
@Composable
private fun CollectionDownloadsBadge(arriving: Int, onClick: () -> Unit) {
    val live by animateFloatAsState(if (arriving > 0) 1f else 0f, label = "DownloadsBadgeLive")
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(JellySurfaceContainer)
            .jellyClick(role = Role.Button, onClickLabel = "Downloads") {
                AppHaptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(HugeIcons.Download, "Downloads", tint = JellyOnSurface, modifier = Modifier.size(22.dp))
        if (live > 0f) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(9.dp)
                    .graphicsLayer { alpha = live; scaleX = live; scaleY = live }
                    .clip(CircleShape).background(JellyAccent)
            )
        }
    }
}

@Composable
internal fun CollectionPill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    CollectionPill(modifier, selected, onClick) {
        Text(text, color = if (selected) JellyOnPrimary else JellyOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The same pill with its own contents, for a hero action that carries more than a word. */
@Composable
internal fun CollectionPill(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(if (selected) JellyPrimary else JellySurfaceContainer)
        .jellyClick(role = Role.Button, scaleDownTo = 0.95f, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun CollectionBack(onBack: () -> Unit) {
    Row(Modifier.jellyClick(role = Role.Button, onClickLabel = "Back to Home", onClick = onBack)
        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = JellyOnSurface, modifier = Modifier.size(20.dp))
        Text("Back to Home", color = JellyOnSurface, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CollectionEmpty(message: String, onManage: () -> Unit, onDevice: () -> Unit) {
    // One step above the page, not two: the pills inside are a container, and a container has to
    // sit lighter than the card it rests on or it reads as a hole punched in it.
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(JellySurface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, color = JellyOnSurfaceVariant, fontSize = 15.sp, lineHeight = 23.sp)
        CollectionPill("Manage collection", onClick = onManage)
        CollectionPill("On Device", onClick = onDevice)
    }
}
