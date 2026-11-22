package com.aeswox.arcplayer

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aeswox.arcplayer.cloud.CloudAuthStage
import com.aeswox.arcplayer.cloud.CloudRepository
import com.aeswox.arcplayer.cloud.CloudSession
import com.aeswox.arcplayer.cloud.CloudVideoItem
import com.aeswox.arcplayer.metadata.MediaCard
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.ui.animations.JellySkeleton
import com.aeswox.arcplayer.ui.animations.StaggeredListItem
import com.aeswox.arcplayer.ui.animations.jellyTabCrossSlide
import com.aeswox.arcplayer.ui.components.FrostedDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class TvPage(val label: String) {
    HOME("Home"), SEARCH("Search"), LIBRARY("Library"), SETTINGS("Settings")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArcTvApp() {
    val context = LocalContext.current
    val prefs = remember { ArcPreferencesStore(context) }
    val repository = remember { CloudSession.repositoryFor(context, prefs.effectiveTelegramApiId(), prefs.effectiveTelegramApiHash()) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var scanTick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf(MediaLibrarySnapshot()) }
    var canRead by remember { mutableStateOf(hasVideoReadPermission(context)) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(!prefs.telegramWelcomeSeen) }
    var page by rememberSaveable { mutableStateOf(TvPage.HOME) }
    var titleKey by rememberSaveable { mutableStateOf<String?>(null) }
    var playback by remember { mutableStateOf<ActiveVideoPlayback?>(null) }
    val auth by repository.authState.collectAsState()
    val shelfHandle = rememberCollectionShelf(repository, prefs, snapshot)
    val shelf by shelfHandle.shelf
    val savedState = rememberSaveableStateHolder()
    val releases = remember { mutableMapOf<Int, Job>() }
    var lastCloud by remember { mutableStateOf<CloudVideoItem?>(null) }

    fun releaseLater(file: CloudVideoItem) {
        releases[file.fileId]?.cancel()
        releases[file.fileId] = scope.launch {
            repository.breakOffFetch(file)
            delay(1_500)
            repository.forgetCopy(file)
            releases.remove(file.fileId)
        }
    }
    fun hold(file: Playable?) {
        val incoming = file as? CloudVideoItem
        lastCloud?.takeIf { it.fileId != incoming?.fileId }?.let(::releaseLater)
        incoming?.let { releases.remove(it.fileId)?.cancel() }
        lastCloud = incoming
    }
    fun play(file: Playable, files: List<Playable>, slots: List<List<Long>> = emptyList(), card: MediaCard? = null) {
        hold(file)
        prefs.rememberWatchedAt(file.stableId)
        if (slots.isNotEmpty()) {
            val resume = PlayOrder.positionByCopy(slots, prefs.resumePositions)[file.stableId] ?: 0L
            prefs.rememberResumePosition(file.stableId, resume)
        }
        playback = ActiveVideoPlayback(file.toPlayerRow(), files.map { it.toPlayerRow() }, "tv_${file.stableId}",
            files.filterIsInstance<CloudVideoItem>(), slots, card)
    }
    fun playTitle(title: ShelfTitle) {
        val choice = PlayOrder.nextCopy(title.copySlots, prefs.resumePositions, title.lengths, prefs.watchedAt, shelf.savedIds)
        val file = title.parts.firstOrNull { it.stableId == choice?.partId } ?: title.representative
        play(file, title.parts, title.copySlots, title.card)
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            notice = null
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "Couldn't finish that request. Check your connection and try again." }
            finally { busy = false }
        }
    }
    fun refresh() {
        scanTick++
        if (auth.stage == CloudAuthStage.AUTHORIZED) perform {
            when (val result = repository.readShelf(prefs.movieFolderChatId)) {
                is CloudRepository.ShelfRead.Ready -> prefs.rememberMovieFolder(result.chatId)
                CloudRepository.ShelfRead.Gone -> notice = "No collection found. Add your videos from Settings."
            }
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canRead = hasVideoReadPermission(context)
        scanTick++
        if (!canRead) notice = "Video access wasn't granted. You can still use your Telegram library."
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: "Local video"
            val file = VideoItem(id = uri.toString().hashCode().toLong().and(0x7fffffffL) + Long.MAX_VALUE / 2,
                uri = uri, title = name, bucketId = "document", folderName = "On this TV", durationMs = 0L,
                durationFormatted = "", sizeBytes = 0L, dateAddedSec = 0L)
            play(file, listOf(file))
        }
    }
    LaunchedEffect(repository) { repository.begin() }
    LaunchedEffect(repository, prefs.movieFolderChatId) { repository.restoreManifest(prefs.movieFolderChatId) }
    LaunchedEffect(auth.stage) {
        if (auth.stage == CloudAuthStage.AUTHORIZED) { showLogin = false; prefs.rememberTelegramWelcome() }
    }
    LaunchedEffect(scanTick, canRead) {
        if (canRead) {
            try { snapshot = scanDeviceVideos(context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "Couldn't read videos on this TV. Try granting video access again." }
        } else snapshot = MediaLibrarySnapshot()
    }
    LaunchedEffect(playback) { if (playback == null) hold(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { canRead = hasVideoReadPermission(context); scanTick++ }
        }
        owner.lifecycle.addObserver(observer)
        val window = context.findActivity()?.window
        window?.let { WindowCompat.getInsetsController(it, it.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(showLogin) {
        val window = context.findActivity()?.window
        if (showLogin) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (showLogin) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(7_000); notice = null } }
    BackHandler(playback == null && (titleKey != null || showLogin || page != TvPage.HOME)) {
        when {
            showLogin -> { showLogin = false; prefs.rememberTelegramWelcome() }
            titleKey != null -> titleKey = null
            else -> page = TvPage.HOME
        }
    }

    SharedTransitionLayout {
        val sharedScope = this
        AnimatedContent(targetState = playback, contentKey = { it?.video?.id ?: "browse" },
            transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "TV playback") { current ->
            if (current != null) {
                HeroVlcVideoPlayerScreen(
                    playback = current, prefsStore = prefs, sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                    cloudRepository = repository.takeIf { current.cloudSources.isNotEmpty() },
                    onSelectAdjacentVideo = { next ->
                        hold(current.cloudSources.firstOrNull { it.stableId == next.id } ?: next)
                        prefs.rememberWatchedAt(next.id)
                        playback = current.copy(video = next, switchReason = null, refusedCopies = emptySet())
                    },
                    onFallToAnotherCopy = { next, reason ->
                        hold(current.cloudSources.firstOrNull { it.stableId == next.id } ?: next)
                        prefs.rememberWatchedAt(next.id)
                        playback = current.copy(video = next, switchReason = reason, refusedCopies = current.refusedCopies + current.video.id)
                    }, onDismiss = { playback = null }, tvMode = true,
                )
            } else {
                Box(Modifier.fillMaxSize().background(TvColors.Background)) {
                    val selected = shelf.resolved.firstOrNull { it.group.key == titleKey }
                    when {
                        showLogin -> TvTelegramLogin(repository) { showLogin = false; prefs.rememberTelegramWelcome() }
                        selected != null -> savedState.SaveableStateProvider("title_${selected.group.key}") {
                            TvTitleDetails(selected, prefs, shelf.authorized,
                                onPlay = { play(it, selected.parts, selected.copySlots, selected.card) },
                                onBack = { titleKey = null },
                                onNavigate = { titleKey = null; page = it })
                        }
                        else -> savedState.SaveableStateProvider("browse") {
                            TvBrowseShell(page, { page = it }, shelf, prefs, snapshot,
                                canRead, busy || shelfHandle.refreshing, onTitle = { titleKey = it.group.key }, onPlayTitle = ::playTitle,
                                onLocal = { play(it, snapshot.videos.ifEmpty { listOf(it) }) },
                                onResume = { item ->
                                    val title = item.title
                                    if (title != null) playTitle(title)
                                    else item.local?.let { play(it, snapshot.videos) }
                                },
                                onSignIn = { showLogin = true }, onRefresh = ::refresh,
                                onPermission = { permissions.launch(requiredVideoPermissions()) },
                                onOpenFile = {
                                    runCatching { openFile.launch(arrayOf("video/*")) }.onFailure {
                                        notice = "This TV has no file picker. Grant video access to browse indexed videos instead."
                                    }
                                },
                                onCreateShelf = { perform {
                                    val id = repository.ensureShelf(prefs.movieFolderChatId)
                                    prefs.rememberMovieFolder(id)
                                    notice = "Forward your videos to Arc Player Server in Telegram, then refresh this library."
                                } },
                            )
                        }
                    }
                    notice?.let {
                        TvReveal(Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                            Text(it, color = TvColors.Text, fontSize = 16.sp,
                                modifier = Modifier.tvGlass(CircleShape, .78f).padding(horizontal = 24.dp, vertical = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvBrowseShell(
    page: TvPage, onPage: (TvPage) -> Unit, shelf: CollectionShelf, prefs: ArcPreferencesStore,
    snapshot: MediaLibrarySnapshot,
    canRead: Boolean, busy: Boolean, onTitle: (ShelfTitle) -> Unit, onPlayTitle: (ShelfTitle) -> Unit, onLocal: (VideoItem) -> Unit,
    onResume: (CollectionResume) -> Unit, onSignIn: () -> Unit, onRefresh: () -> Unit,
    onPermission: () -> Unit, onOpenFile: () -> Unit, onCreateShelf: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    Row(Modifier.fillMaxSize()) {
        TvRail(page, onPage, shelf.authorized, Modifier.padding(start = 12.dp), initialFocus)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // The reference home sits on a dimmed wash of its own featured artwork; the glass
            // chips and cards need something behind them to read against.
            if (page == TvPage.HOME) {
                val hero = shelf.featured.firstOrNull() ?: shelf.titles.firstOrNull()
                val art = hero?.let { shelfArtUrl(it, prefs.titleDetail(it.providerId, it.isSeries)) }
                if (art != null) {
                    TvArtwork(art, null, Modifier.fillMaxSize().graphicsLayer { alpha = .22f }, 1280)
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(TvColors.Background.copy(alpha = .5f), TvColors.Background.copy(alpha = .82f), TvColors.Background))))
                }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val (enter, exit) = jellyTabCrossSlide(targetState.ordinal > initialState.ordinal)
                    enter togetherWith exit
                },
                label = "tv_page",
            ) { current ->
                when (current) {
                    TvPage.HOME -> TvHome(shelf, prefs, busy, onTitle, onPlayTitle, onResume, onSignIn, onRefresh, { onPage(TvPage.SETTINGS) })
                    TvPage.SEARCH, TvPage.LIBRARY -> TvLibrary(current == TvPage.SEARCH, shelf, prefs, onTitle, onSignIn)
                    TvPage.SETTINGS -> TvSettings(shelf, prefs, snapshot, canRead, busy, onSignIn, onRefresh,
                        onPermission, onOpenFile, onCreateShelf, onLocal)
                }
            }
        }
    }
}

private fun shelfMatchesFilter(title: ShelfTitle, filter: String, prefs: ArcPreferencesStore): Boolean = when (filter) {
    "Movies" -> !title.isSeries
    "Series" -> title.isSeries
    "Favourites" -> title.parts.any { it.stableId in prefs.favoriteIds }
    else -> true
}

@Composable
private fun TvHome(shelf: CollectionShelf, prefs: ArcPreferencesStore, busy: Boolean, onTitle: (ShelfTitle) -> Unit,
    onPlayTitle: (ShelfTitle) -> Unit, onResume: (CollectionResume) -> Unit, onSignIn: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val featured = (shelf.featured.ifEmpty { shelf.titles }).filter { shelfMatchesFilter(it, filter, prefs) }
    val movies = shelf.movies.filter { shelfMatchesFilter(it, filter, prefs) }
    val series = shelf.series.filter { shelfMatchesFilter(it, filter, prefs) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 32.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Home", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (shelf.authorized) "Telegram connected" else "Local library", fontSize = 13.sp, color = TvColors.Muted)
                TvAction(if (busy) "Refreshing…" else "Refresh", onRefresh, enabled = !busy)
            }
        }
        item("chips") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("All", "Movies", "Series", "Favourites").forEach { label ->
                    TvChip(label, filter == label, { filter = label })
                }
            }
        }
        if (shelf.readFailed) item { TvEmpty("Your connection needs a moment", "Saved titles are still available. Refresh to reconnect to Telegram.") }
        if (featured.isNotEmpty()) item("featured") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Featured", color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(featured.take(10), key = { it.group.key }) { title ->
                        StaggeredListItem(index = featured.indexOf(title), arrivalKey = "tv_feat_${title.group.key}") {
                            TvFeaturedCard(title, prefs) { onTitle(title) }
                        }
                    }
                }
            }
        }
        if (shelf.continuing.isNotEmpty()) item("continue") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Continue watching", color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(shelf.continuing, key = { it.key }) { item ->
                        StaggeredListItem(index = shelf.continuing.indexOf(item), arrivalKey = "tv_cont_${item.key}") {
                            val title = item.title ?: item.keptOf
                            TvPoster(item.name, title?.let { shelfArtUrl(it, prefs.titleDetail(it.providerId, it.isSeries)) },
                                Modifier.width(232.dp), true, { onResume(item) }) {
                                val ratio = if (item.progress.lengthMs > 0) item.progress.resumeMs.toFloat() / item.progress.lengthMs else 0f
                                LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = TvColors.Accent, trackColor = Color.White.copy(alpha = .15f))
                            }
                        }
                    }
                }
            }
        }
        if (shelf.loading && shelf.titles.isEmpty()) item { TvShelfSkeleton() }
        if (movies.isNotEmpty()) item("movies") { TvTitleRow("Movies for tonight", movies, onTitle) }
        if (series.isNotEmpty()) item("series") { TvTitleRow("One more episode", series, onTitle) }
        if (shelf.titles.isEmpty() && !shelf.loading) item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TvEmpty("Make yourself at home", if (shelf.authorized) "Your existing Arc Player Server collection will appear here. Add videos from Settings, then refresh."
                    else "Scan the Telegram QR code to bring your collection to this screen, or use Settings to browse local videos.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!shelf.authorized) TvAction("Connect Telegram", onSignIn)
                    else TvAction("Add videos", onSettings)
                }
            }
        }
    }
}

@Composable
private fun TvFeaturedCard(title: ShelfTitle, prefs: ArcPreferencesStore, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.width(320.dp).aspectRatio(16f / 9f)
            .onFocusChanged { focused = it.isFocused }
            .tvJellyFocus(focused, 1.03f)
            .border(2.dp, if (focused) TvColors.Focus else Color.Transparent, TvCardShape)
            .clip(TvCardShape)
            .clickable(onClick = onClick),
    ) {
        TvArtwork(shelfArtUrl(title, prefs.titleDetail(title.providerId, title.isSeries)), title.name, Modifier.fillMaxSize(), 600)
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title.name, color = TvColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(listOfNotNull(title.group.year?.toString(), if (title.isSeries) "Series" else "Movie").joinToString(" · "),
                color = TvColors.Muted, fontSize = 13.sp, maxLines = 1)
        }
        if (focused) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(34.dp)
                .background(TvColors.Focus, CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(com.aeswox.arcplayer.ui.components.HugeIcons.Play, null,
                    tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TvTitleRow(label: String, titles: List<ShelfTitle>, onTitle: (ShelfTitle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(titles, key = { it.group.key }) { title ->
                StaggeredListItem(index = titles.indexOf(title), arrivalKey = "tv_row_${title.group.key}") {
                    TvPoster(title.name, title.posterUrl, Modifier.width(142.dp), false, { onTitle(title) })
                }
            }
        }
    }
}

@Composable
private fun TvPoster(name: String, url: String?, modifier: Modifier, wide: Boolean, onClick: () -> Unit, extra: @Composable () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.onFocusChanged { focused = it.isFocused }
        .tvJellyFocus(focused, 1.04f)
        .border(2.dp, if (focused) TvColors.Focus else Color.Transparent, TvCardShape)
        .clip(TvCardShape).background(if (focused) Color.White.copy(alpha = .08f) else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(12.dp))) {
            TvArtwork(url, name, Modifier.fillMaxSize(), if (wide) 600 else 360)
            if (url == null) Text(name, color = TvColors.Muted, fontSize = 18.sp, maxLines = 4,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(16.dp))
        }
        extra()
        Text(name, color = TvColors.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun TvLibrary(search: Boolean, shelf: CollectionShelf, prefs: ArcPreferencesStore, onTitle: (ShelfTitle) -> Unit, onSignIn: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val titles = shelf.titles.filter { title ->
        (!search || title.name.contains(query.trim(), ignoreCase = true)) && shelfMatchesFilter(title, filter, prefs)
    }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 32.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (search) "Find your next watch" else "Your library", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (search) OutlinedTextField(query, { query = it }, label = { Text("Search your collection") }, singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().tvGlass(CircleShape, .5f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TvColors.Text, unfocusedTextColor = TvColors.Text,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedLabelColor = TvColors.Muted, unfocusedLabelColor = TvColors.Muted,
                cursorColor = TvColors.Focus))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("All", "Movies", "Series", "Favourites").forEach { label ->
                TvChip(label, filter == label, { filter = label })
            }
        }
        if (shelf.loading && titles.isEmpty()) TvShelfSkeleton()
        else if (titles.isEmpty()) {
            TvEmpty(if (search && query.isNotBlank()) "No matching titles" else "Nothing here yet",
                if (search && query.isNotBlank()) "Try another title or switch the filter." else "Your saved films and series will appear here.")
            if (!shelf.authorized) TvAction("Connect Telegram", onSignIn)
        } else LazyVerticalGrid(columns = GridCells.Adaptive(138.dp), contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(titles, key = { it.group.key }) { title ->
                StaggeredListItem(index = titles.indexOf(title), arrivalKey = "tv_lib_${title.group.key}") {
                    TvPoster(title.name, title.posterUrl, Modifier.fillMaxWidth(), false, { onTitle(title) })
                }
            }
        }
    }
}

@Composable
private fun TvSettings(shelf: CollectionShelf, prefs: ArcPreferencesStore, snapshot: MediaLibrarySnapshot,
    canRead: Boolean, busy: Boolean, onSignIn: () -> Unit, onRefresh: () -> Unit, onPermission: () -> Unit,
    onOpenFile: () -> Unit, onCreateShelf: () -> Unit, onLocal: (VideoItem) -> Unit) {
    var confirmCreate by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 32.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Text("Settings", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item {
            Column(Modifier.fillMaxWidth().tvGlass(TvCardShape, .6f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvEmpty("Your Telegram collection", if (shelf.authorized)
                    "Connected. Forward videos to Arc Player Server from Telegram on your phone. Your TV reads the same collection. Watch progress is stored on this device."
                    else "Connect with a QR code, or use your phone number. You can browse local videos without an account.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!shelf.authorized) TvAction("Connect Telegram", onSignIn)
                    else TvAction("Add from Telegram", { confirmCreate = true }, enabled = !busy)
                    TvAction(if (busy) "Refreshing…" else "Refresh library", onRefresh, enabled = !busy)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().tvGlass(TvCardShape, .6f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvEmpty("On this TV", if (canRead) "Videos in the device's media library are listed below. Open a file to use an attached drive."
                    else "Allow video access to include local films, or choose a file from an attached drive.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvAction(if (canRead) "Video access" else "Allow video access", onPermission)
                    TvAction("Open a video", onOpenFile)
                }
            }
        }
        items(snapshot.videos, key = { it.id }) { file ->
            TvAction(file.title, { onLocal(file) }, Modifier.fillMaxWidth())
        }
        item {
            Column(Modifier.fillMaxWidth().tvGlass(TvCardShape, .6f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvEmpty("Subtitles", "Audio, subtitle tracks and timing are available from the player. Selecting subtitles can use software decoding, which may be slower for demanding video.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvAction("Smaller", { prefs.updateSubtitleStyle(prefs.subtitleLiftPercent, prefs.subtitleSizePercent - 10) })
                    Text("${prefs.subtitleSizePercent}%", color = TvColors.Text, fontSize = 18.sp)
                    TvAction("Larger", { prefs.updateSubtitleStyle(prefs.subtitleLiftPercent, prefs.subtitleSizePercent + 10) })
                }
            }
        }
        item { TvEmpty("Arc Player ${BuildConfig.VERSION_NAME}", "Built for your remote. Use the directional buttons to browse, OK to select, and Back to return. Playback and media processing powered by VideoLAN LibVLC; Telegram connectivity powered by TDLib.") }
        item { TvEmpty("Data sources", "This product uses the TMDB API but is not endorsed or certified by TMDB. Additional metadata comes from AniList, IMDb and Wikipedia. Artwork belongs to its respective owners.") }
    }
    if (confirmCreate) FrostedDialog(
        onDismissRequest = { confirmCreate = false },
        title = { Text("Add your videos") },
        text = { Text("Arc will reuse your existing Arc Player Server channel, or create a private one if none exists. Forward your videos to that channel from Telegram, then refresh this TV.") },
        confirmButton = { TvAction("Continue", { confirmCreate = false; onCreateShelf() }) },
        dismissButton = { TvAction("Cancel", { confirmCreate = false }) },
    )
}

@Composable
private fun TvEmpty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = TvColors.Muted, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun TvShelfSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { Box(Modifier.width(142.dp).height(210.dp).clip(TvCardShape)) { JellySkeleton() } }
    }
}
