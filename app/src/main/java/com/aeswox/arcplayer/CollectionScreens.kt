package com.aeswox.arcplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.aeswox.arcplayer.metadata.MediaCard
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.metadata.TitleCandidate
import com.aeswox.arcplayer.metadata.TitleGroups
import com.aeswox.arcplayer.metadata.formatRuntime
import com.aeswox.arcplayer.ui.animations.JellySkeleton
import com.aeswox.arcplayer.ui.animations.StaggeredListItem
import com.aeswox.arcplayer.ui.animations.jellyClick
import com.aeswox.arcplayer.ui.animations.physicsBounceOverscroll
import com.aeswox.arcplayer.ui.haptics.AppHaptics
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
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class CollectionFilter(val label: String) {
    ALL("All"), MOVIES("Movies"), SERIES("Series"), FAVOURITES("Favourites")
}

private data class CollectionResume(
    val title: ShelfTitle?,
    val local: VideoItem?,
    val progress: PlayOrder.Progress
) {
    val key: String get() = title?.let { "cloud:${it.group.key}" } ?: "local:${local!!.id}"
    val name: String get() = title?.name ?: local!!.title
}

@Composable
internal fun CollectionScreens(
    tab: BottomNavTab,
    repository: CloudRepository,
    prefsStore: ArcPreferencesStore,
    snapshot: MediaLibrarySnapshot,
    topInset: Dp,
    bottomInset: Dp,
    onOpenTitle: (ShelfTitle) -> Unit,
    onCloudPlay: (CloudVideoItem, List<CloudVideoItem>, List<List<Long>>, MediaCard?) -> Unit,
    onLocalPlay: (VideoItem, List<VideoItem>) -> Unit,
    onOpenDevice: () -> Unit,
    onOpenAllVideos: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onManageCollection: () -> Unit
) {
    if (tab == BottomNavTab.SETTINGS) return
    val videosByChat by repository.videos.collectAsState()
    val auth by repository.authState.collectAsState()
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

    val files = if (authorized && !gone && shelfId != 0L) videosByChat[shelfId].orEmpty()
        .filter { it.chatId == shelfId } else emptyList()
    val lengths = prefsStore.knownLengths.toMap()
    val cards = prefsStore.mediaCards.toMap()
    val positions = prefsStore.resumePositions.toMap()
    val watched = prefsStore.cloudWatchedAt.toMap()
    val favourites = prefsStore.favoriteIds
    val history = prefsStore.watchHistoryIds
    val groups = remember(files) {
        TitleGroups.group(files.map { TitleCandidate(it.stableId, it.title, it.sizeBytes) })
    }
    val byId = remember(files, lengths) {
        files.associate { it.stableId to it.copy(durationMs = maxOf(it.durationMs, lengths[it.stableId] ?: 0L)) }
    }
    val representatives = remember(groups, byId) { groups.mapNotNull { byId[it.representative.id] } }
    TitleSearchPass(representatives, prefsStore)
    val titles = remember(groups, byId, cards) {
        groups.mapNotNull { group ->
            val representative = byId[group.representative.id] ?: return@mapNotNull null
            ShelfTitle(group, group.parts.mapNotNull { byId[it.id] }, representative, cards[representative.stableId])
        }.sortedByDescending { title -> title.parts.maxOfOrNull { it.messageId } ?: 0L }
    }
    val movies = titles.filterNot { it.isSeries }
    val series = titles.filter { it.isSeries }
    val featured = titles.filter {
        it.name.isNotBlank() && !it.group.key.startsWith("u:") && !it.posterUrl.isNullOrBlank()
    }.take(5)
    val cloudResume = titles.mapNotNull { title ->
        PlayOrder.progress(title.copySlots, PlayOrder.positionByCopy(title.copySlots, positions), title.lengths, watched)
            ?.let { CollectionResume(title, null, it) }
    }.sortedByDescending { watched[it.progress.partId] ?: 0L }
    val localResume = snapshot.videos.mapNotNull { video ->
        val position = positions[video.id] ?: 0L
        val length = maxOf(video.durationMs, lengths[video.id] ?: 0L)
        val finished = com.aeswox.arcplayer.playback.ResumeRules.decide(position, length) ==
            com.aeswox.arcplayer.playback.ResumeOutcome.DROP
        if (position < com.aeswox.arcplayer.playback.ResumeRules.MIN_RESUME_POSITION_MS || finished) null else CollectionResume(
            null, video, PlayOrder.Progress(video.id, position, length, (length - position).coerceAtLeast(0L))
        )
    }
    val continuing = (cloudResume + localResume).sortedBy { entry ->
        history.indexOf(entry.progress.partId).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }.take(12)
    val playResume: (CollectionResume) -> Unit = { entry ->
        val title = entry.title
        if (title != null) {
            val byCopy = PlayOrder.positionByCopy(title.copySlots, prefsStore.resumePositions.toMap())
            val currentLengths = title.partIds.associateWith { maxOf(title.lengths[it] ?: 0L, prefsStore.knownLengths[it] ?: 0L) }
            // Continue must not jump to an earlier, unstarted episode selected by nextCopy.
            val progress = PlayOrder.progress(title.copySlots, byCopy, currentLengths, prefsStore.cloudWatchedAt)
            val choice = progress?.let { PlayOrder.Play(it.partId, it.resumeMs) }
                ?: PlayOrder.nextCopy(title.copySlots, byCopy, currentLengths, prefsStore.cloudWatchedAt)
            title.parts.firstOrNull { it.stableId == choice?.partId }?.let { file ->
                prefsStore.rememberResumePosition(file.stableId, choice!!.resumeMs)
                onCloudPlay(file, title.parts, title.copySlots, title.card)
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
    val localMatches = snapshot.videos.filter { collectionMatches(terms, "${it.title} ${it.folderName}") }
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
                            CollectionHeading(if (browse != null) when (browse) {
                                CollectionFilter.MOVIES -> "Your Movies"
                                CollectionFilter.SERIES -> "Your Series"
                                CollectionFilter.FAVOURITES -> "Favourites"
                                else -> "Your collection"
                            } else "Library")
                            Text("${titles.size} ${if (titles.size == 1) "title" else "titles"} · Your Telegram collection", color = JellyOnSurfaceVariant)
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
                        StaggeredListItem(index) { CollectionPoster(title, Modifier.fillMaxWidth(), onOpenTitle) }
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
                        item { CollectionHeading("Telegram collection · ${cloudMatches.size}", small = true) }
                        if (cloudMatches.isEmpty()) item { Text("No matching collection titles.", color = JellyOnSurfaceVariant) }
                        itemsIndexed(cloudMatches, key = { _, title -> "cloud:${title.group.key}" }) { index, title ->
                            StaggeredListItem(index) {
                                CollectionSearchRow(title.name, if (title.isSeries) "Series · Telegram" else "Movie · Telegram",
                                    onClick = { onOpenTitle(title) }, art = { CollectionArtwork(title.posterUrl) })
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)); CollectionHeading("On Device · ${localMatches.size}", small = true) }
                        if (localMatches.isEmpty()) item { Text("No matching device videos or folders.", color = JellyOnSurfaceVariant) }
                        itemsIndexed(localMatches, key = { _, video -> "local:${video.id}" }) { index, video ->
                            StaggeredListItem(index) {
                                CollectionSearchRow(video.title, video.folderName,
                                    onClick = { onLocalPlay(video, localMatches) }, art = { CollectionLocalArtwork(video) })
                            }
                        }
                    }
                }
            }
            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val heroHeight = (maxWidth * 1.25f).coerceIn(440.dp, 500.dp)
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
                                        StaggeredListItem(index) { CollectionContinueCard(entry) { playResume(entry) } }
                                    }
                                }
                            }
                        }
                        if (titles.isEmpty()) item(key = "empty") {
                            Box(Modifier.padding(horizontal = 24.dp)) { CollectionEmpty(emptyMessage, onManageCollection, onOpenDevice) }
                        }
                        if (titles.isNotEmpty()) item(key = "recent") {
                            CollectionRail("Recently Added", titles.take(16), { browse = CollectionFilter.ALL }, onOpenTitle)
                        }
                        if (movies.isNotEmpty()) item(key = "movies") {
                            CollectionRail("Your Movies", movies.take(16), { browse = CollectionFilter.MOVIES }, onOpenTitle)
                        }
                        if (series.isNotEmpty()) item(key = "series") {
                            CollectionRail("Your Series", series.take(16), { browse = CollectionFilter.SERIES }, onOpenTitle)
                        }
                    }
                }
            }
        }
    }
}

private fun collectionMatches(terms: List<String>, text: String): Boolean = terms.all { text.contains(it, ignoreCase = true) }

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
    val scope = rememberCoroutineScope()
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
                Text("IN YOUR COLLECTION", color = Color.White, fontSize = 11.sp, letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 24.dp, top = topInset + 20.dp))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 72.dp)
                    .graphicsLayer { alpha = 1f - recede },
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
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            titles.forEachIndexed { index, _ ->
                Box(Modifier.size(48.dp).jellyClick(role = Role.Button, onClickLabel = "Show featured title ${index + 1}") {
                    AppHaptics.tick()
                    scope.launch { pager.animateScrollToPage(index) }
                }.semantics { contentDescription = "Featured title ${index + 1} of ${titles.size}${if (index == pager.currentPage) ", selected" else ""}" },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = if (index == pager.currentPage) 24.dp else 6.dp, height = 6.dp).background(
                        if (index == pager.currentPage) JellyOnSurface else JellyOutlineVariant, CircleShape))
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
 * real scroll offset, which leaves the picture pinned while the title, pills and dots riding the
 * column give way, exactly as the stretch intends.
 */
@Composable
private fun PinnedHeroArtwork(titles: List<ShelfTitle>, prefs: ArcPreferencesStore, pager: PagerState,
                              scroll: LazyListState, heroHeight: Dp, pull: Animatable<Float, *>) {
    val layerHeightPx = with(LocalDensity.current) { heroHeight.toPx() }
    // The layer comes first because a modifier draws in its own node: with `background` ahead of
    // `graphicsLayer` the rectangle is painted before the transform exists, so the sheet never moves
    // and blankets the screen in page colour instead of sliding off the picture.
    Box(Modifier.graphicsLayer {
        val offset = if (scroll.firstVisibleItemIndex == 0) scroll.firstVisibleItemScrollOffset else 0
        // 0.6 rather than 1: the picture already recedes at 40% of the scroll, as before.
        translationY = if (scroll.firstVisibleItemIndex == 0) -offset * 0.6f else -layerHeightPx * 2f
    }.fillMaxWidth().height(heroHeight).clipToBounds()) {
        val offsetFraction = pager.currentPageOffsetFraction
        for (index in (pager.currentPage - 1)..(pager.currentPage + 1)) {
            if (index !in titles.indices) continue
            HeroArtPage(titles[index], prefs, index - pager.currentPage - offsetFraction)
        }
    }
    // The layer's clip is static while the list's hero edge scrolls and stretches away, so the
    // picture's slower parallax would overdraw the rails below; this cover rides that edge —
    // real scroll plus the overscroll stretch — and hides the overdraw behind the page colour.
    Box(Modifier.graphicsLayer {
        val offset = if (scroll.firstVisibleItemIndex == 0) scroll.firstVisibleItemScrollOffset else layerHeightPx.toInt()
        translationY = layerHeightPx - offset + pull.value
    }.fillMaxSize().background(JellyBackground))
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
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.32f), 0.35f to Color.Transparent,
            0.67f to Color.Black.copy(alpha = 0.68f), 0.84f to Color.Black.copy(alpha = 0.75f),
            1f to JellyBackground)))
        // The reference recedes an adjacent card by scaling it down. Here the card is the whole
        // screen, so shrinking one would pull the background up between two pages; it recedes
        // into shadow instead, which reads the same way over a full-bleed picture.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * abs(slide).coerceIn(0f, 1f))))
    }
}

@Composable
private fun CollectionRail(label: String, titles: List<ShelfTitle>, onAll: () -> Unit, onOpen: (ShelfTitle) -> Unit) {
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
                StaggeredListItem(index) { CollectionPoster(title, Modifier.width(132.dp), onOpen) }
            }
        }
    }
}

@Composable
private fun CollectionPoster(title: ShelfTitle, modifier: Modifier, onOpen: (ShelfTitle) -> Unit) {
    Column(modifier.jellyClick(role = Role.Button, onClickLabel = "View ${title.name}") { onOpen(title) },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(22.dp))) { CollectionArtwork(title.posterUrl) }
        Text(title.name, color = JellyOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (title.isSeries) "Series" else "Movie", color = JellyOnSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun CollectionContinueCard(entry: CollectionResume, onPlay: () -> Unit) {
    val progress = entry.progress
    val part = entry.title?.group?.parts?.firstOrNull { it.id == progress.partId }
    val episode = part?.episode?.let { number -> part.season?.let { "S$it · E$number" } ?: "Episode $number" }
    val remaining = if (progress.lengthMs > 0L) "${(progress.remainingMs + 59_999L) / 60_000L}m left" else "Resume"
    val caption = listOfNotNull(if (entry.local != null) "On Device" else "Telegram", episode, remaining).joinToString(" · ")
    Column(Modifier.width(248.dp).jellyClick(role = Role.Button, onClickLabel = "Resume ${entry.name}", onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(22.dp))) {
            if (entry.title != null) CollectionArtwork(entry.title.posterUrl) else entry.local?.let { CollectionLocalArtwork(it) }
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
private fun CollectionHeading(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(text, modifier.semantics { heading() }, color = JellyOnSurface, fontSize = if (small) 21.sp else 32.sp,
        fontWeight = FontWeight.Bold, lineHeight = if (small) 26.sp else 38.sp)
}

@Composable
private fun CollectionPill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(if (selected) JellyPrimary else JellySurfaceContainer)
        .jellyClick(role = Role.Button, scaleDownTo = 0.95f, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) JellyOnPrimary else JellyOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
