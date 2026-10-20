package com.aeswox.arcplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.util.Rational
import android.util.Size
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aeswox.arcplayer.ui.animations.jelly
import com.aeswox.arcplayer.ui.animations.jellyClick
import com.aeswox.arcplayer.ui.animations.jellyCombinedClickable
import com.aeswox.arcplayer.ui.components.MorphingMenu
import com.aeswox.arcplayer.ui.components.MorphingMenuItem
import com.aeswox.arcplayer.ui.theme.Background as JellyBackground
import com.aeswox.arcplayer.ui.theme.OnBackground as JellyOnBackground
import com.aeswox.arcplayer.ui.theme.OnSurface as JellyOnSurface
import com.aeswox.arcplayer.ui.theme.OnSurfaceVariant as JellyOnSurfaceVariant
import com.aeswox.arcplayer.ui.theme.Outline as JellyOutline
import com.aeswox.arcplayer.ui.theme.SurfaceContainer as JellySurfaceContainer
import com.aeswox.arcplayer.ui.theme.SurfaceContainerHigh as JellySurfaceContainerHigh
import com.aeswox.arcplayer.ui.theme.SurfaceContainerLowest as JellySurfaceContainerLowest
import com.aeswox.arcplayer.ui.theme.SurfaceContainerLow as JellySurfaceContainerLow
import com.aeswox.arcplayer.ui.theme.ArcPlayerJellyTheme
import com.aeswox.arcplayer.ui.jelly.jellyGlass
import com.aeswox.arcplayer.ui.jelly.jellySource
import com.aeswox.arcplayer.backdrop.backdrops.LayerBackdrop
import com.aeswox.arcplayer.backdrop.backdrops.rememberLayerBackdrop
import com.aeswox.arcplayer.cloud.CloudAuthStage
import com.aeswox.arcplayer.cloud.CloudAuthState
import com.aeswox.arcplayer.cloud.CloudRepository
import com.aeswox.arcplayer.cloud.CloudSession
import com.aeswox.arcplayer.cloud.CloudVideoItem
import com.aeswox.arcplayer.cloud.CloudWindowPlanner
import com.aeswox.arcplayer.cloud.CodeHint
import com.aeswox.arcplayer.cloud.CloudProgress
import com.aeswox.arcplayer.cloud.SHELF_CHANNEL_TITLE
import com.aeswox.arcplayer.metadata.AbsoluteSeasons
import com.aeswox.arcplayer.metadata.EpisodeDetail
import com.aeswox.arcplayer.metadata.LookupRequest
import com.aeswox.arcplayer.metadata.MediaCard
import com.aeswox.arcplayer.metadata.MediaLibrary
import com.aeswox.arcplayer.metadata.PosterCache
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.metadata.ReleaseInfo
import com.aeswox.arcplayer.metadata.ShelfSearch
import com.aeswox.arcplayer.metadata.TmdbDetails
import com.aeswox.arcplayer.metadata.TitleCandidate
import com.aeswox.arcplayer.metadata.TitleDetail
import com.aeswox.arcplayer.metadata.TitleGroup
import com.aeswox.arcplayer.metadata.TitleGroups
import com.aeswox.arcplayer.metadata.TitleKind
import com.aeswox.arcplayer.metadata.formatRuntime
import com.aeswox.arcplayer.metadata.nullIfBlank
import com.aeswox.arcplayer.metadata.titleDetailKey
import com.aeswox.arcplayer.playback.FrameRateMatch
import com.aeswox.arcplayer.playback.NextUp
import com.aeswox.arcplayer.playback.ResumeOutcome
import com.aeswox.arcplayer.playback.ResumeRules
import com.aeswox.arcplayer.playback.SeekHold
import com.aeswox.arcplayer.playback.TrackChoice
import com.aeswox.arcplayer.scroll.HeaderFling
import com.aeswox.arcplayer.stream.ByteRate
import com.aeswox.arcplayer.stream.BufferGuard
import dev.chrisbanes.haze.HazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout

class MainActivity : ComponentActivity() {
    var isVideoActivelyPlaying: Boolean = false
    var autoPipEnabled: Boolean = true
    var isInPipState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            ArcPlayerTheme {
                ArcPlayerHomeScreen()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isVideoActivelyPlaying && autoPipEnabled) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState = isInPictureInPictureMode
    }
}

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ============================================================================
// THEME & SHARED CONSTANTS (Samsung One UI Light Palette)
// ============================================================================

private val OneUiBackground = JellyBackground
private val OneUiTextPrimary = JellyOnSurface
private val OneUiTextSecondary = JellyOnSurfaceVariant
private val OneUiPillSurface = JellySurfaceContainerLowest
private val OneUiPillSelected = JellySurfaceContainerHigh
private val OneUiBadgeOrange = JellySurfaceContainerHigh
private val OneUiPlaceholderGray = JellyOutline

private const val CARD_ASPECT_RATIO = 0.78f
private val CardCornerShape = RoundedCornerShape(22.dp)
private val DurationPillShape = RoundedCornerShape(10.dp)
private val TopActionPillShape = RoundedCornerShape(22.dp)

private val CardTitleTextStyle = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    color = Color.White,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 1.5f),
        blurRadius = 4f
    )
)

private val CardCountTextStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Normal,
    color = Color.White.copy(alpha = 0.86f),
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.40f),
        offset = Offset(0f, 1f),
        blurRadius = 3f
    )
)

@Composable
fun ArcPlayerTheme(content: @Composable () -> Unit) {
    ArcPlayerJellyTheme(content = content)
}

// ============================================================================
// PERSISTENT PREFERENCES, WATCH HISTORY, FAVORITES & PLAYLISTS STORE
// ============================================================================

@Immutable
enum class VideoSortOrder(val label: String) {
    DATE_DESC("Date (Newest)"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Size (Largest)"),
    DURATION_DESC("Duration (Longest)")
}

@Stable
class ArcPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("arc_player_prefs", Context.MODE_PRIVATE)

    val resumePositions = mutableStateMapOf<Long, Long>()
    var watchHistoryIds by mutableStateOf<List<Long>>(emptyList())
        private set
    var favoriteIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var playlists by mutableStateOf<Map<String, List<Long>>>(emptyMap())
        private set

    /**
     * Catalogue answers, keyed by cloud video id.
     *
     * A map rather than a per-file lookup because the grid needs to recompose a single card the
     * moment its match lands, and because a file that was searched and refused is stored as refused:
     * asking again on the next launch would only be slower.
     */
    val mediaCards = mutableStateMapOf<Long, MediaCard>()

    /**
     * Info-page detail, keyed by the catalogue's own id rather than by a cloud file id.
     *
     * A shelf of 200 files can hold three copies of one film, and all three open the same page. Keying
     * by file would fetch the cast three times and let the copies drift apart; keying by the catalogue
     * is what makes the page the same whichever card was tapped.
     */
    val titleDetails = mutableStateMapOf<String, TitleDetail>()

    /**
     * One season's episodes, keyed the same way as [titleDetails].
     *
     * Deliberately not written to disk: a season list is only ever read on the page that asked for it,
     * and a show's episode titles change more often than a film's cast does.
     */
    val seasonEpisodes = mutableStateMapOf<String, List<EpisodeDetail>>()

    fun episodesOf(showId: Int?, season: Int): List<EpisodeDetail>? =
        showId?.let { seasonEpisodes[titleDetailKey(it, true) + "/$season"] }

    fun rememberEpisodesOf(showId: Int, season: Int, episodes: List<EpisodeDetail>) {
        seasonEpisodes[titleDetailKey(showId, true) + "/$season"] = episodes
    }

    var sortOrder by mutableStateOf(VideoSortOrder.DATE_DESC)
        private set
    var gridColumns by mutableIntStateOf(3)
        private set
    var backgroundAudioEnabled by mutableStateOf(false)
        private set
    var autoPipEnabled by mutableStateOf(true)
        private set
    var defaultPlaybackSpeed by mutableFloatStateOf(1.0f)
        private set
    var telegramApiId by mutableIntStateOf(0)
        private set
    var telegramApiHash by mutableStateOf("")
        private set

    /**
     * The Telegram chat the library is built out of, or zero for nobody has made one yet.
     *
     * Stored here because [CloudRepository] has no preferences and should not acquire any. It is only
     * ever a hint: a chat id belongs to the account that was signed in when it was written, so the
     * tab asks Telegram whether the number still names a readable chat before opening it.
     */
    var movieFolderChatId by mutableLongStateOf(0L)
        private set

    /**
     * When each cloud file was last played, keyed by its stable id.
     *
     * The order the app throws its local copies away in, and the only reason it can say how many bytes
     * of watched video it is holding without asking Telegram about every file on the shelf. Keyed by
     * the server-derived id rather than by TDLib's file id, which is handed out again whenever the
     * database is rebuilt: a trim order built from that restarts from nothing on the next launch.
     */
    val cloudWatchedAt = mutableStateMapOf<Long, Long>()

    /**
     * How long each file runs, keyed by the id it was played under, as the player reported it.
     *
     * A file forwarded into the shelf as a document carries no duration in Telegram's schema — which is
     * how nearly every release arrives — so the shelf list has no length for anything in it and the app
     * cannot say how far into a film a viewer is. The player learns the real number the moment the file
     * opens, and this is where that answer is kept for the next time the shelf is drawn.
     */
    val knownLengths = mutableStateMapOf<Long, Long>()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        runCatching {
            val sortName = prefs.getString("sort_order", VideoSortOrder.DATE_DESC.name)
            sortOrder = VideoSortOrder.entries.find { it.name == sortName } ?: VideoSortOrder.DATE_DESC
            gridColumns = prefs.getInt("grid_columns", 3).coerceIn(2, 4)
            backgroundAudioEnabled = prefs.getBoolean("bg_audio", false)
            autoPipEnabled = prefs.getBoolean("auto_pip", true)
            defaultPlaybackSpeed = prefs.getFloat("default_speed", 1.0f).coerceIn(0.5f, 2.0f)

            // Credentials typed into the Cloud tab win over whatever was compiled in.
            telegramApiId = prefs.getInt("tg_api_id", 0)
            telegramApiHash = prefs.getString("tg_api_hash", "").orEmpty()

            movieFolderChatId = prefs.getLong("movie_folder_chat_id", 0L)

            val watchedJson = JSONObject(prefs.getString("cloud_watched_at", "{}") ?: "{}")
            watchedJson.keys().forEach { key ->
                val id = key.toLongOrNull()
                val at = watchedJson.optLong(key, 0L)
                if (id != null && at > 0L) cloudWatchedAt[id] = at
            }

            val posJson = JSONObject(prefs.getString("resume_positions", "{}") ?: "{}")
            posJson.keys().forEach { key ->
                val id = key.toLongOrNull()
                val pos = posJson.optLong(key, 0L)
                if (id != null && pos > 0L) {
                    resumePositions[id] = pos
                }
            }

            val lengthJson = JSONObject(prefs.getString("known_lengths", "{}") ?: "{}")
            lengthJson.keys().forEach { key ->
                val id = key.toLongOrNull()
                val ms = lengthJson.optLong(key, 0L)
                if (id != null && ms > 0L) knownLengths[id] = ms
            }

            val histArray = JSONArray(prefs.getString("watch_history", "[]") ?: "[]")
            val histList = ArrayList<Long>(histArray.length())
            for (i in 0 until histArray.length()) {
                histList.add(histArray.getLong(i))
            }
            watchHistoryIds = histList

            val favArray = JSONArray(prefs.getString("favorites", "[]") ?: "[]")
            val favSet = LinkedHashSet<Long>(favArray.length())
            for (i in 0 until favArray.length()) {
                favSet.add(favArray.getLong(i))
            }
            favoriteIds = favSet

            val plJson = JSONObject(prefs.getString("playlists", "{}") ?: "{}")
            val plMap = LinkedHashMap<String, List<Long>>()
            plJson.keys().forEach { name ->
                val arr = plJson.optJSONArray(name) ?: JSONArray()
                val ids = ArrayList<Long>(arr.length())
                for (i in 0 until arr.length()) {
                    ids.add(arr.getLong(i))
                }
                plMap[name] = ids
            }
            playlists = plMap

            val cardJson = JSONObject(prefs.getString("media_cards", "{}") ?: "{}")
            cardJson.keys().forEach { key ->
                val id = key.toLongOrNull() ?: return@forEach
                val card = cardJson.optJSONObject(key) ?: return@forEach
                val title = card.optString("title")
                if (title.isNotBlank()) {
                    mediaCards[id] = MediaCard(
                        title = title,
                        subtitle = card.optString("subtitle").nullIfBlank(),
                        posterUrl = card.optString("poster").nullIfBlank(),
                        source = card.optString("source").ifBlank { MediaCard.NONE },
                        logoUrl = card.optString("logo").nullIfBlank(),
                        logoKnown = card.optBoolean("logoKnown"),
                        providerId = card.optInt("providerId").takeIf { it > 0 },
                        isSeries = card.optBoolean("isSeries")
                    )
                }
            }

            val detailJson = JSONObject(prefs.getString("title_details", "{}") ?: "{}")
            detailJson.keys().forEach { key ->
                val obj = detailJson.optJSONObject(key) ?: return@forEach
                TitleDetail.fromJson(obj)?.let { titleDetails[key] = it }
            }
        }
    }

    /**
     * Remembers what the catalogues said about one cloud file.
     *
     * Written straight through, including the refusals, because a lookup pass costs a request per
     * file and the set of files in a chat does not change between launches.
     */
    fun rememberMediaCard(videoId: Long, card: MediaCard) {
        mediaCards[videoId] = card
        persistMediaCards()
    }

    private fun persistMediaCards() {
        val obj = JSONObject()
        mediaCards.forEach { (id, card) ->
            obj.put(
                id.toString(),
                JSONObject()
                    .put("title", card.title)
                    .put("subtitle", card.subtitle ?: JSONObject.NULL)
                    .put("poster", card.posterUrl ?: JSONObject.NULL)
                    .put("source", card.source)
                    .put("logo", card.logoUrl ?: JSONObject.NULL)
                    .put("logoKnown", card.logoKnown)
                    .put("providerId", card.providerId ?: JSONObject.NULL)
                    .put("isSeries", card.isSeries)
            )
        }
        prefs.edit().putString("media_cards", obj.toString()).apply()
    }

    /** The detail for one catalogue entry, or null when its info page has never been opened. */
    fun titleDetail(providerId: Int?, isSeries: Boolean): TitleDetail? =
        providerId?.let { titleDetails[titleDetailKey(it, isSeries)] }

    /**
     * Remembers what the catalogue said about one title.
     *
     * Only ever written when a page is opened, so the store grows with the titles actually looked at
     * rather than with every card on the shelf — and a second open of the same film costs no request.
     */
    fun rememberTitleDetail(providerId: Int, isSeries: Boolean, detail: TitleDetail) {
        titleDetails[titleDetailKey(providerId, isSeries)] = detail
        persistTitleDetails()
    }

    private fun persistTitleDetails() {
        val obj = JSONObject()
        titleDetails.forEach { (key, detail) -> obj.put(key, detail.toJson()) }
        prefs.edit().putString("title_details", obj.toString()).apply()
    }

    /**
     * Remembers which Telegram chat the library lives in.
     *
     * Written once, when the shelf is made. Read as a hint and never a fact: a chat id belongs to the
     * account that was signed in when it was saved, so the tab checks it is still readable before
     * opening it and makes a new channel if it is not.
     */
    fun rememberMovieFolder(chatId: Long) {
        movieFolderChatId = chatId
        prefs.edit().putLong("movie_folder_chat_id", chatId).apply()
    }

    /**
     * Marks a cloud file as the most recently watched thing on the device.
     *
     * Written when playback opens rather than when it closes: a film abandoned two minutes in is still
     * the film the viewer last reached for, and it is the one most likely to be reached for again
     * tonight.
     */
    fun rememberCloudWatched(videoId: Long) {
        cloudWatchedAt[videoId] = System.currentTimeMillis()
        val obj = JSONObject()
        cloudWatchedAt.forEach { (id, at) -> obj.put(id.toString(), at) }
        prefs.edit().putString("cloud_watched_at", obj.toString()).apply()
    }

    fun updateSortOrder(order: VideoSortOrder) {
        sortOrder = order
        prefs.edit().putString("sort_order", order.name).apply()
    }

    fun updateGridColumns(cols: Int) {
        val clamped = cols.coerceIn(2, 4)
        gridColumns = clamped
        prefs.edit().putInt("grid_columns", clamped).apply()
    }

    fun updateBackgroundAudio(enabled: Boolean) {
        backgroundAudioEnabled = enabled
        prefs.edit().putBoolean("bg_audio", enabled).apply()
    }

    fun updateAutoPip(enabled: Boolean) {
        autoPipEnabled = enabled
        prefs.edit().putBoolean("auto_pip", enabled).apply()
    }

    fun updateDefaultSpeed(speed: Float) {
        defaultPlaybackSpeed = speed
        prefs.edit().putFloat("default_speed", speed).apply()
    }

    fun updateTelegramCredentials(apiId: Int, apiHash: String) {
        val trimmed = apiHash.trim()
        telegramApiId = apiId
        telegramApiHash = trimmed
        prefs.edit()
            .putInt("tg_api_id", apiId)
            .putString("tg_api_hash", trimmed)
            .apply()
    }

    /** Compiled-in keys from `local.properties` unless the Cloud tab has been given its own. */
    fun effectiveTelegramApiId(): Int =
        if (telegramApiId > 0) telegramApiId else BuildConfig.TG_API_ID

    fun effectiveTelegramApiHash(): String =
        telegramApiHash.ifBlank { BuildConfig.TG_API_HASH }

    fun recordPlaybackProgress(videoId: Long, positionMs: Long, durationMs: Long) {
        rememberKnownLength(videoId, durationMs)

        val updatedHistory = listOf(videoId) + watchHistoryIds.filter { it != videoId }.take(49)
        if (updatedHistory != watchHistoryIds) {
            watchHistoryIds = updatedHistory
            val arr = JSONArray()
            updatedHistory.forEach { arr.put(it) }
            prefs.edit().putString("watch_history", arr.toString()).apply()
        }

        val stored = resumePositions[videoId]
        val changed = when (ResumeRules.decide(positionMs, durationMs)) {
            ResumeOutcome.STORE -> if (stored != positionMs) {
                resumePositions[videoId] = positionMs
                true
            } else {
                false
            }

            ResumeOutcome.DROP -> if (stored != null) {
                resumePositions.remove(videoId)
                true
            } else {
                false
            }

            ResumeOutcome.KEEP -> false
        }
        if (changed) persistResumePositions()
    }

    /**
     * Put a resume position onto a file the viewer has never opened.
     *
     * A show watched partway through in one copy keeps its minute when the page hands the same episode
     * to a different one, and the player finds a position only in the file it was given. Nothing moves
     * backwards and a file that already knows where it was left is left alone.
     */
    fun rememberResumePosition(videoId: Long, positionMs: Long) {
        if (positionMs <= 0L) return
        if ((resumePositions[videoId] ?: 0L) >= positionMs) return
        resumePositions[videoId] = positionMs
        persistResumePositions()
    }

    private fun persistResumePositions() {
        val obj = JSONObject()
        resumePositions.forEach { (id, pos) ->
            obj.put(id.toString(), pos)
        }
        prefs.edit().putString("resume_positions", obj.toString()).apply()
    }

    /**
     * Keep the length the player just read off a file.
     *
     * Written once per file rather than on every tick: a film does not change length, and the progress
     * loop that reports it runs once a second and a half.
     */
    fun rememberKnownLength(videoId: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        if (knownLengths[videoId] == durationMs) return
        knownLengths[videoId] = durationMs
        val obj = JSONObject()
        knownLengths.forEach { (id, ms) -> obj.put(id.toString(), ms) }
        prefs.edit().putString("known_lengths", obj.toString()).apply()
    }

    fun toggleFavorite(videoId: Long) {
        val next = favoriteIds.toMutableSet()
        if (!next.add(videoId)) {
            next.remove(videoId)
        }
        favoriteIds = next
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }

    fun createPlaylist(name: String, initialVideoId: Long? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val next = LinkedHashMap(playlists)
        val existing = next[trimmed].orEmpty()
        next[trimmed] = if (initialVideoId != null && !existing.contains(initialVideoId)) {
            existing + initialVideoId
        } else {
            existing
        }
        playlists = next
        persistPlaylists()
    }

    fun addVideoToPlaylist(name: String, videoId: Long) {
        val next = LinkedHashMap(playlists)
        val existing = next[name].orEmpty()
        if (!existing.contains(videoId)) {
            next[name] = existing + videoId
            playlists = next
            persistPlaylists()
        }
    }

    fun deletePlaylist(name: String) {
        val next = LinkedHashMap(playlists)
        next.remove(name)
        playlists = next
        persistPlaylists()
    }

    private fun persistPlaylists() {
        val obj = JSONObject()
        playlists.forEach { (name, ids) ->
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            obj.put(name, arr)
        }
        prefs.edit().putString("playlists", obj.toString()).apply()
    }

    fun clearWatchHistory() {
        watchHistoryIds = emptyList()
        resumePositions.clear()
        prefs.edit()
            .remove("watch_history")
            .remove("resume_positions")
            .apply()
    }
}

// ============================================================================
// DATA MODELS & MEDIASTORE SCANNER
// ============================================================================

@Immutable
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val bucketId: String,
    val folderName: String,
    val durationMs: Long,
    val durationFormatted: String,
    val sizeBytes: Long,
    val dateAddedSec: Long
)

@Immutable
data class VideoFolderItem(
    val bucketId: String,
    val title: String,
    val videoCount: Int,
    val coverVideoUri: Uri,
    val isCameraFolder: Boolean,
    val hasRecentVideos: Boolean
)

@Immutable
data class MediaLibrarySnapshot(
    val videos: List<VideoItem> = emptyList(),
    val folders: List<VideoFolderItem> = emptyList()
)

@Immutable
enum class BottomNavTab(val label: String, val headerTitle: String) {
    VIDEOS(label = "Videos", headerTitle = "Videos"),
    FOLDERS(label = "Folders", headerTitle = "Folders"),
    CLOUD(label = "Cloud", headerTitle = "Cloud"),
    MENU(label = "Menu", headerTitle = "Arc Player")
}

/**
 * The two lists the Cloud tab can be showing: the shelf itself, and the on-device playlists that used
 * to own the whole tab.
 *
 * A stack rather than a set of chips: the shelf is the tab, and a playlist screen is a place a tap in
 * it goes. Back returns from there.
 */
private enum class CloudScreen { SHELF, PLAYLISTS }

fun hasVideoReadPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val fullVideo = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        val partialVisual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        fullVideo || partialVisual
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun requiredVideoPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}

suspend fun scanDeviceVideos(context: Context): MediaLibrarySnapshot = withContext(Dispatchers.IO) {
    val videos = ArrayList<VideoItem>(256)
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.RELATIVE_PATH
    )

    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rawDisplayName = cursor.getString(displayNameCol)
                val rawTitle = cursor.getString(titleCol)
                val title = when {
                    !rawDisplayName.isNullOrBlank() -> rawDisplayName.substringBeforeLast('.')
                    !rawTitle.isNullOrBlank() -> rawTitle
                    else -> "Video_$id"
                }

                val relativePath = cursor.getString(relativePathCol) ?: ""
                val rawBucketId = cursor.getString(bucketIdCol)
                val rawBucketName = cursor.getString(bucketNameCol)

                val folderName = when {
                    !rawBucketName.isNullOrBlank() -> rawBucketName
                    relativePath.isNotBlank() -> relativePath.trimEnd('/', File.separatorChar)
                        .substringAfterLast('/')
                        .ifBlank { "Internal storage" }
                    else -> "Videos"
                }
                val bucketId = rawBucketId ?: folderName.lowercase(Locale.ROOT)
                val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                val dateAddedSec = cursor.getLong(dateAddedCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videos.add(
                    VideoItem(
                        id = id,
                        uri = contentUri,
                        title = title,
                        bucketId = bucketId,
                        folderName = folderName,
                        durationMs = durationMs,
                        durationFormatted = formatDuration(durationMs),
                        sizeBytes = sizeBytes,
                        dateAddedSec = dateAddedSec
                    )
                )
            }
        }
    }

    val nowSec = System.currentTimeMillis() / 1000L
    val recentThresholdSec = nowSec - 86_400L

    val folders = videos
        .groupBy { it.bucketId }
        .map { (bucketId, bucketVideos) ->
            val first = bucketVideos.first()
            val isCamera = first.folderName.equals("Camera", ignoreCase = true)
            val hasRecent = bucketVideos.any { it.dateAddedSec >= recentThresholdSec }
            VideoFolderItem(
                bucketId = bucketId,
                title = first.folderName,
                videoCount = bucketVideos.size,
                coverVideoUri = first.uri,
                isCameraFolder = isCamera,
                hasRecentVideos = hasRecent
            )
        }
        .sortedWith(
            compareByDescending<VideoFolderItem> { it.isCameraFolder }
                .thenByDescending { it.title.equals("Screenshots", ignoreCase = true) || it.title.contains("Screen", ignoreCase = true) }
                .thenByDescending { it.title.equals("Download", ignoreCase = true) || it.title.equals("Downloads", ignoreCase = true) }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )

    MediaLibrarySnapshot(
        videos = videos,
        folders = folders
    )
}

fun sortVideos(videos: List<VideoItem>, order: VideoSortOrder): List<VideoItem> {
    return when (order) {
        VideoSortOrder.DATE_DESC -> videos.sortedByDescending { it.dateAddedSec }
        VideoSortOrder.NAME_ASC -> videos.sortedBy { it.title.lowercase(Locale.ROOT) }
        VideoSortOrder.SIZE_DESC -> videos.sortedByDescending { it.sizeBytes }
        VideoSortOrder.DURATION_DESC -> videos.sortedByDescending { it.durationMs }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(sizeBytes: Long): String {
    val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

// ============================================================================
// ZERO-LAG PROGRESSIVE BLUR THUMBNAIL ENGINE (BAKED ONCE ON DISPATCHERS.IO)
// ============================================================================

object VideoThumbnailCache {
    private val maxKilobytes = (Runtime.getRuntime().maxMemory() / 1024L / 4L).toInt().coerceAtLeast(24 * 1024)
    val decodeSemaphore = Semaphore(permits = 4)

    private val lruCache = object : LruCache<String, ImageBitmap>(maxKilobytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return ((value.width * value.height * 4) / 1024).coerceAtLeast(1)
        }
    }

    fun get(uri: Uri): ImageBitmap? = lruCache.get(uri.toString())

    fun put(uri: Uri, bitmap: ImageBitmap) {
        lruCache.put(uri.toString(), bitmap)
    }
}

suspend fun loadVideoThumbnailCancellable(
    contentResolver: ContentResolver,
    uri: Uri,
    targetWidthPx: Int = 220,
    targetHeightPx: Int = 282
): ImageBitmap? {
    if (uri == Uri.EMPTY) return null
    VideoThumbnailCache.get(uri)?.let { return it }

    return withContext(Dispatchers.IO) {
        VideoThumbnailCache.get(uri)?.let { return@withContext it }

        VideoThumbnailCache.decodeSemaphore.withPermit {
            coroutineContext.ensureActive()
            VideoThumbnailCache.get(uri)?.let { return@withPermit it }

            val signal = CancellationSignal()
            val completionHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    signal.cancel()
                }
            }

            try {
                val rawBitmap = runCatching {
                    contentResolver.loadThumbnail(uri, Size(targetWidthPx, targetHeightPx), signal)
                }.getOrNull() ?: return@withPermit null

                coroutineContext.ensureActive()

                val cardBitmap = buildProgressiveBlurCardBitmap(
                    rawBitmap = rawBitmap,
                    targetW = targetWidthPx,
                    targetH = targetHeightPx
                )
                if (cardBitmap !== rawBitmap && !rawBitmap.isRecycled) {
                    rawBitmap.recycle()
                }

                cardBitmap.prepareToDraw()
                val imageBitmap = cardBitmap.asImageBitmap()
                VideoThumbnailCache.put(uri, imageBitmap)
                imageBitmap
            } finally {
                completionHandle.dispose()
            }
        }
    }
}

private fun buildProgressiveBlurCardBitmap(
    rawBitmap: Bitmap,
    targetW: Int,
    targetH: Int
): Bitmap {
    val src = if (rawBitmap.config == Bitmap.Config.HARDWARE) {
        rawBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return rawBitmap
    } else {
        rawBitmap
    }

    val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val bilinearPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val scale = max(
        targetW.toFloat() / src.width.coerceAtLeast(1),
        targetH.toFloat() / src.height.coerceAtLeast(1)
    )
    val dx = (targetW - src.width * scale) * 0.5f
    val dy = (targetH - src.height * scale) * 0.5f
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(dx, dy)
    }
    canvas.drawBitmap(src, matrix, bilinearPaint)
    if (src !== rawBitmap && !src.isRecycled) {
        src.recycle()
    }

    val blurTopY = (targetH * 0.55f).toInt()
    val blurStripH = (targetH - blurTopY).coerceAtLeast(1)

    val smallW = (targetW / 6).coerceAtLeast(8)
    val smallH = (blurStripH / 6).coerceAtLeast(6)

    val bottomSlice = Bitmap.createBitmap(out, 0, blurTopY, targetW, blurStripH)
    val smallBitmap = Bitmap.createScaledBitmap(bottomSlice, smallW, smallH, true)
    bottomSlice.recycle()

    blurPixelsInPlace(smallBitmap, radius = 2, passes = 3)

    val blurredStrip = Bitmap.createScaledBitmap(smallBitmap, targetW, blurStripH, true)
    smallBitmap.recycle()

    val maskCanvas = AndroidCanvas(blurredStrip)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(
            0f,
            0f,
            0f,
            blurStripH.toFloat(),
            intArrayOf(0x00000000, 0x99000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.36f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    maskCanvas.drawRect(0f, 0f, targetW.toFloat(), blurStripH.toFloat(), maskPaint)

    canvas.drawBitmap(blurredStrip, 0f, blurTopY.toFloat(), bilinearPaint)
    blurredStrip.recycle()

    val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            blurTopY.toFloat(),
            0f,
            targetH.toFloat(),
            intArrayOf(0x00000000, 0x36000000, 0x6E000000),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, blurTopY.toFloat(), targetW.toFloat(), targetH.toFloat(), scrimPaint)

    return out
}

private fun blurPixelsInPlace(bitmap: Bitmap, radius: Int, passes: Int) {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= radius * 2 || h <= radius * 2) return

    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val div = radius * 2 + 1

    repeat(passes) {
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (k in -radius..radius) {
                    val sampleX = (x + k).coerceIn(0, w - 1)
                    val c = pixels[rowOffset + sampleX]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                }
                temp[rowOffset + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (k in -radius..radius) {
                    val sampleY = (y + k).coerceIn(0, h - 1)
                    val c = temp[sampleY * w + x]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                }
                pixels[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
            }
        }
    }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
}

// ============================================================================
// LIBVLC PLAYER ENGINE (`ArcVlcPlayerEngine`)
// ============================================================================

@Immutable
data class VlcTrackOption(
    val id: Int,
    val name: String
)

@Stable
class ArcVlcPlayerEngine(
    context: Context,
    initialRate: Float = 1.0f
) : IVLCVout.OnNewVideoLayoutListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val libVlc: LibVLC = LibVLC(
        appContext,
        arrayListOf(
            "--avcodec-hw=any",
            "--android-display-chroma=RV32",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--freetype-rel-fontsize=16"
        )
    )

    val mediaPlayer: MediaPlayer = MediaPlayer(libVlc)

    private var activePfd: ParcelFileDescriptor? = null
    private var attachedTextureView: TextureView? = null
    private var pendingResumeSeekMs: Long = 0L
    private var lastStreamTickMs: Long = Long.MIN_VALUE

    /**
     * Told where the play head is, so a file that is still arriving can keep its download window
     * ahead of it. Null for everything on the device already.
     */
    var onStreamPosition: ((Long) -> Unit)? = null

    /**
     * Asked before a seek is handed to the player, for a file that is only partly on disk. Telegram
     * has to carry that byte range over first: the player reads a hole as the end of the media, and
     * a seek that lands in one does not come back. Asked again while it answers false, because the
     * wait is what keeps the fetch alive. Null for local files, which never wait.
     */
    var onCloudSeek: (suspend (Long) -> Boolean)? = null

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bufferedSeek: Job? = null

    /**
     * A cloud seek that is still waiting for its bytes, and the position it waited away from. Holds
     * the ticks the player keeps reporting off the scrubber while the fetch runs.
     */
    private var seekHold: SeekHold? = null

    /**
     * Whether a pending fetch is the reason the player is quiet, rather than the viewer. Only then
     * does the end of the wait press play again.
     */
    private var pausedForSeek = false

    /**
     * The furthest moment the player has actually shown, as opposed to one a fetch is still chasing.
     */
    private var lastPlayedPositionMs = 0L

    var isPlaying by mutableStateOf(false)
        private set

    var isFirstFrameRendered by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    /**
     * The position worth remembering for next time.
     *
     * During a cloud seek the play head is the target a fetch is chasing rather than a moment that
     * has been watched, and a seek that ran out of budget leaves it there on purpose. Saving that as
     * the resume point reopens the film somewhere its bytes have not arrived, which is how a resume
     * ends with dark video over audio from the start, so the furthest frame that genuinely played
     * stands in for it.
     */
    val resumablePositionMs: Long
        get() = lastPlayedPositionMs

    /**
     * A cloud seek that is waiting for bytes rather than a player that has gone quiet. The HUD reads
     * this to show what the download is doing while the scrubber is being held.
     */
    var isSeekWaiting by mutableStateOf(false)
        private set

    /**
     * When the player last reported where it was, or [BufferGuard.NEVER] before it has said anything.
     * A cloud stall is measured against this, and only a play head that moves can ask for the next
     * download window, so a gap here is both the symptom and the reason nobody notices.
     */
    var lastTickAtMs by mutableLongStateOf(BufferGuard.NEVER)
        private set

    /**
     * When a seek left the player owing a play head, or [BufferGuard.NEVER] when nothing is owed one.
     *
     * A seek only reaches the player once its bytes are on disk, so one that then reports nothing is
     * stuck on the position rather than on the link. The watchdog times that from here because its
     * usual anchor, the previous play-head report, is missing exactly when a resume seek is the first
     * thing the player was ever asked.
     */
    var playOwedSinceMs by mutableLongStateOf(BufferGuard.NEVER)
        private set

    /**
     * Whether the player is on its way to a position it was told to reach rather than one it watched.
     *
     * Both halves of this are the same lie about the play head: a re-open reports the moment it is
     * coming back from until the position it was asked for lands, and aiming the download window from
     * those reports cancels the fetch that position is waiting on.
     */
    val isCatchingUpToItsPosition: Boolean
        get() = playOwedSinceMs != BufferGuard.NEVER || pendingResumeSeekMs > 0L

    /**
     * Whether the player is quiet because somebody meant it to be. A viewer pause and a background
     * stop both land here; neither is a link problem and neither should raise a spinner.
     */
    var stoppedOnPurpose by mutableStateOf(false)
        private set

    /**
     * Quiet because the bytes are not there rather than because the viewer asked. The HUD shows the
     * same wait for this as it does for a seek, and the watchdog presses play again once they arrive.
     */
    var isBuffering by mutableStateOf(false)

    /**
     * The player's own claim that it ran out of file, which is not the same as the file having ended.
     *
     * A hole in a cloud file reads exactly the same way, so this is only a signal to go and look:
     * [resumablePositionMs] is what says whether the viewer genuinely got to the end. Anything that
     * moves the play head again clears it, which is why a rewind or a stall recovery needs no separate
     * bookkeeping to make a "next up" offer go away.
     */
    var isAtEnd by mutableStateOf(false)
        private set

    /**
     * Put the scrubber back on the frame that is actually on screen.
     *
     * A hole reads to the player as the end of the media and the end handler walks the bar to full
     * length, which on a film that is only waiting for bytes says it finished. The stall watchdog has
     * already ruled that out by the time it calls this.
     */
    fun holdAtLastFrame() {
        currentPositionMs = lastPlayedPositionMs
    }

    var durationMs by mutableLongStateOf(1L)
        private set

    var videoAspectRatio by mutableFloatStateOf(16f / 9f)
        private set

    var playbackRate by mutableFloatStateOf(initialRate)
        private set

    var softwareBoostPercent by mutableIntStateOf(100)
        private set

    var subtitleDelayMs by mutableLongStateOf(0L)
        private set

    var audioTracks by mutableStateOf<List<VlcTrackOption>>(emptyList())
        private set

    var selectedAudioTrackId by mutableIntStateOf(-1)
        private set

    var subtitleTracks by mutableStateOf<List<VlcTrackOption>>(emptyList())
        private set

    var selectedSubtitleTrackId by mutableIntStateOf(-1)
        private set

    /**
     * The track the viewer picked, held apart from what the player currently reports.
     *
     * A stall re-open builds a brand new Media, which tears the elementary streams down and lets VLC
     * run its own language default again — that is how an English track chosen by hand comes back
     * Hindi after a buffering restart. These carry the choice through it, and [refreshTracks] puts it
     * back on. Nullable rather than a -1 sentinel because -1 is a real subtitle choice: disable.
     */
    private var preferredAudioTrackId: Int? = null
    private var preferredSubtitleTrackId: Int? = null

    /**
     * Which file the [preferredAudioTrackId] above belongs to. [loadAndPlay] serves the first open and
     * the stall re-open alike, so this is the only thing that tells "same film, bytes came back" apart
     * from "next episode" — and the engine instance outlives both.
     */
    private var loadedVideoId: Long? = null

    /**
     * The caption forwarded into the channel for whatever is playing, once Telegram has delivered it.
     *
     * Held here rather than passed once at the screen, because a stall re-opens the media on the
     * engine's own initiative and a subtitle that survives up to the buffer hole and then disappears
     * is exactly the sort of thing a viewer would notice.
     */
    private var captionUri: Uri? = null

    /** Whether the caption has been put on the media now playing; a new media needs it again. */
    private var captionIsOn = false

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    if (pendingResumeSeekMs > 0L) {
                        val target = pendingResumeSeekMs
                        pendingResumeSeekMs = 0L
                        // Stamped here rather than only in applySeek, because the head this player is
                        // showing is the one it came back from, not the one it was told to reach. In
                        // the moment between this event and the seek a fast resume issues, the tick
                        // collector and the opening pump both read that stale head as the real one and
                        // yank the window behind it. Measured: `advance read=0 -> Fetch(offset=0)`
                        // lands in exactly this gap and cancels the fetch the resume is waiting on.
                        playOwedSinceMs = SystemClock.elapsedRealtime()
                        // Through seekTo, not straight into the player: for a cloud file the bytes
                        // at the resume point are usually the part that has not arrived yet.
                        seekTo(target)
                    }
                    // A slave rides on the media, so this is the first moment one can be added to the
                    // media this re-open made — and the refreshTracks below has to see it to list it.
                    if (!captionIsOn) captionUri?.let { attachCaption(it) }
                    refreshTracks()
                }

                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped -> {
                    isPlaying = false
                }

                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    currentPositionMs = durationMs
                }

                MediaPlayer.Event.Vout -> {
                    if (event.voutCount > 0) {
                        isFirstFrameRendered = true
                        refreshTracks()
                        mainHandler.post { applyContentFrameRate() }
                    }
                }

                MediaPlayer.Event.TimeChanged -> {
                    val time = event.timeChanged
                    if (time >= 0L) onTimeChanged(time)
                }

                MediaPlayer.Event.LengthChanged -> {
                    val len = event.lengthChanged
                    if (len > 0L) {
                        durationMs = len
                    }
                }
            }
        }
    }

    fun loadAndPlay(video: VideoItem, startPositionMs: Long = 0L) {
        isFirstFrameRendered = false
        bufferedSeek?.cancel()
        seekHold = null
        isSeekWaiting = false
        pausedForSeek = false
        stoppedOnPurpose = false
        isBuffering = false
        lastTickAtMs = BufferGuard.NEVER
        playOwedSinceMs = BufferGuard.NEVER
        currentPositionMs = startPositionMs.coerceAtLeast(0L)
        lastPlayedPositionMs = currentPositionMs
        durationMs = video.durationMs.coerceAtLeast(1L)
        pendingResumeSeekMs = startPositionMs.coerceAtLeast(0L)
        if (video.id != loadedVideoId) {
            loadedVideoId = video.id
            preferredAudioTrackId = null
            preferredSubtitleTrackId = null
            subtitleDelayMs = 0L
            captionUri = null
        }
        // Whatever caption was on the last media went away with it, and this one has not been given one.
        captionIsOn = false
        lastStreamTickMs = Long.MIN_VALUE

        runCatching {
            mediaPlayer.stop()
        }
        closeActivePfd()

        val media = runCatching {
            val pfd = appContext.contentResolver.openFileDescriptor(video.uri, "r")
            if (pfd != null) {
                activePfd = pfd
                Media(libVlc, pfd.fileDescriptor)
            } else {
                Media(libVlc, video.uri)
            }
        }.getOrElse {
            Media(libVlc, video.uri)
        }

        media.setHWDecoderEnabled(true, false)
        mediaPlayer.media = media
        media.release()

        mediaPlayer.rate = playbackRate
        mediaPlayer.volume = softwareBoostPercent
        // A new Media means a new input thread, and the player-level settings above do not follow it
        // across; the subtitle offset is one of them, so it is pushed back on with the rest.
        if (subtitleDelayMs != 0L) mediaPlayer.setSpuDelay(subtitleDelayMs * 1000L)
        mediaPlayer.play()
    }

    fun attachTextureView(textureView: TextureView) {
        if (attachedTextureView === textureView && mediaPlayer.vlcVout.areViewsAttached()) {
            return
        }
        detachTextureView()
        attachedTextureView = textureView
        val vout = mediaPlayer.vlcVout
        vout.setVideoView(textureView)
        if (textureView.width > 0 && textureView.height > 0) {
            vout.setWindowSize(textureView.width, textureView.height)
        }
        vout.attachViews(this)
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && mediaPlayer.vlcVout.areViewsAttached()) {
            mediaPlayer.vlcVout.setWindowSize(width, height)
        }
    }

    fun detachTextureView() {
        runCatching {
            if (mediaPlayer.vlcVout.areViewsAttached()) {
                mediaPlayer.vlcVout.detachViews()
            }
        }
        attachedTextureView = null
    }

    override fun onNewVideoLayout(
        vout: IVLCVout?,
        width: Int,
        height: Int,
        visibleWidth: Int,
        visibleHeight: Int,
        sarNum: Int,
        sarDen: Int
    ) {
        val w = if (visibleWidth > 0) visibleWidth else width
        val h = if (visibleHeight > 0) visibleHeight else height
        if (w > 0 && h > 0) {
            val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen.toFloat() else 1f
            val ratio = (w * sar) / h.toFloat()
            if (ratio in 0.25f..4.0f) {
                mainHandler.post {
                    videoAspectRatio = ratio
                }
            }
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            pausedForSeek = false
            stoppedOnPurpose = true
            mediaPlayer.pause()
        } else {
            if (currentPositionMs >= durationMs - 200L && durationMs > 0L) {
                mediaPlayer.setTime(0L, true)
                currentPositionMs = 0L
            }
            pausedForSeek = false
            stoppedOnPurpose = false
            mediaPlayer.play()
        }
    }

    fun seekTo(targetMs: Long) {
        // A file sent as a document has no length until the container opens, and clamping a resume
        // seek against the placeholder would drop it at the very start.
        val clamped = if (durationMs > 1L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        // Where the play head stood before this seek, kept so the hold can tell a stale tick from an
        // arrival. Read before the target is written, or it names the target itself.
        val fromMs = currentPositionMs
        currentPositionMs = clamped
        val gate = onCloudSeek
        if (gate == null) {
            seekHold = null
            isSeekWaiting = false
            applySeek(clamped)
            return
        }
        // The player keeps running while Telegram carries the bytes over, and every tick it reports
        // meanwhile belongs to the position the seek left. On the scrubber those ticks read as the
        // seek being ignored, so the hold drops them until one cannot be the old play head any more.
        seekHold = SeekHold(fromMs, clamped, SystemClock.elapsedRealtime())
        isSeekWaiting = true
        // Frozen for the length of the fetch, on the frame it was showing. Left running, the player
        // reads forward from the position the seek abandoned while the one download window is
        // pointed at the new one, and a hole in the file is what it reads. This is also the ANR:
        // a TextureView composites through the app's own surface, and a producer stalled on a
        // starvation queue eventually blocks the render thread with it.
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            pausedForSeek = true
        }
        // Only the newest gesture matters. A drag reports a seek per frame, and each one left
        // waiting for its own fetch would have to finish before the finger's last position is
        // reached.
        bufferedSeek?.cancel()
        lastStreamTickMs = Long.MIN_VALUE
        bufferedSeek = engineScope.launch {
            // A budget that runs out is not a refusal. The fetch is aimed and Telegram is still
            // filling it, and the wait is the only thing that keeps asking, so ending it here left
            // the player paused over a black frame with the hold dropped and no spinner: paused
            // means no ticks, and the ticks are what normally drag the window along. Ask again
            // until the bytes say they have arrived. Only a newer gesture or leaving the player
            // ends this wait now.
            while (true) {
                val arrived = gate(clamped)
                // TDLib's replies are not cancellable, so a superseded gesture can still finish its
                // wait. It must not then move the play head: the newer gesture owns it now.
                if (!isActive) return@launch
                if (arrived) break
            }
            isSeekWaiting = false
            // Handing the target to the player does not end the hold on its own: a fast seek lands on
            // the keyframe before it, and the ticks have to say where the player really is.
            applySeek(clamped)
        }
    }

    /**
     * One play-head report from the player. A held tick is the fetch still running away from where the
     * seek started; moving nothing for it is what keeps the scrubber on the target it was sent to.
     */
    private fun onTimeChanged(timeMs: Long) {
        seekHold?.let { hold ->
            if (hold.isStale(timeMs, SystemClock.elapsedRealtime())) return
            seekHold = null
        }
        // A play head that moves somewhere new is the answer the seek was owed, so the debt is paid
        // here and nowhere else. Left stamped, it would read as "still catching up" for the rest of
        // the film, and that keeps the download window parked wherever the resume point landed —
        // the player then runs out of file with a watchdog that refuses to move it.
        if (playOwedSinceMs != BufferGuard.NEVER && timeMs != currentPositionMs) {
            playOwedSinceMs = BufferGuard.NEVER
        }
        currentPositionMs = timeMs
        lastPlayedPositionMs = timeMs
        lastTickAtMs = SystemClock.elapsedRealtime()
        notifyStreamPosition(timeMs)
    }

    private fun applySeek(targetMs: Long) {
        if (mediaPlayer.isSeekable) {
            mediaPlayer.setTime(targetMs, true)
        }
        // Only a seek that resumes the film is owed a play head. One the viewer made while the player
        // was stopped is a frame they meant to sit on, and re-opening it would start it playing.
        val owed = pausedForSeek
        if (owed) {
            pausedForSeek = false
            mediaPlayer.play()
        }
        // Reported before the stamp below, so this is the head the window is aimed from while the seek
        // is being served rather than a stale one left behind. Either way the wait already pointed the
        // window here; this is what keeps it pointing forward once the seek is over.
        notifyStreamPosition(targetMs, force = true)
        if (owed) playOwedSinceMs = SystemClock.elapsedRealtime()
    }

    /**
     * A seek is reported straight away; ordinary playback only gets to ask once a second, because
     * every report costs a round trip to Telegram and the window has far more slack than that.
     */
    private fun notifyStreamPosition(positionMs: Long, force: Boolean = false) {
        val listener = onStreamPosition ?: return
        // While a seek is waiting for its bytes the window belongs to that seek. The play head is
        // still crawling through the old position meanwhile, and a tick from there would drag the
        // download back and the wait would never end.
        if (!force && bufferedSeek?.isActive == true) return
        if (!force && lastStreamTickMs != Long.MIN_VALUE && abs(positionMs - lastStreamTickMs) < STREAM_TICK_MS) {
            return
        }
        lastStreamTickMs = positionMs
        listener(positionMs)
    }

    fun seekByDelta(deltaMs: Long) {
        seekTo(currentPositionMs + deltaMs)
    }

    fun setSpeed(rate: Float) {
        val clamped = rate.coerceIn(0.25f, 3.0f)
        playbackRate = clamped
        mediaPlayer.rate = clamped
    }

    fun setSoftwareBoost(percent: Int) {
        val clamped = percent.coerceIn(100, 200)
        softwareBoostPercent = clamped
        mediaPlayer.volume = clamped
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer.audioTrack = trackId
        selectedAudioTrackId = mediaPlayer.audioTrack
        preferredAudioTrackId = selectedAudioTrackId
    }

    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer.spuTrack = trackId
        selectedSubtitleTrackId = mediaPlayer.spuTrack
        preferredSubtitleTrackId = selectedSubtitleTrackId
    }

    fun addExternalSubtitle(subtitleUri: Uri) {
        runCatching {
            mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, subtitleUri, true)
            mainHandler.postDelayed({ refreshTracks() }, 250L)
        }
    }

    /**
     * Offer the caption the channel holds for the film on screen.
     *
     * Through the same slave route the subtitle picker already uses rather than a `:sub-file` media
     * option: an option has to be set before the media starts, which would make every opening wait on
     * a Telegram round trip for the sake of the few films that have a caption waiting.
     */
    fun showCaption(subtitleUri: Uri) {
        captionUri = subtitleUri
        if (isPlaying && !captionIsOn) attachCaption(subtitleUri)
    }

    private fun attachCaption(subtitleUri: Uri) {
        runCatching { mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, subtitleUri, true) }
            .onSuccess { captionIsOn = true }
            .onFailure { android.util.Log.w("ArcPlayer", "caption $subtitleUri refused", it) }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        val nextMs = if (deltaMs == 0L) 0L else subtitleDelayMs + deltaMs
        subtitleDelayMs = nextMs
        mediaPlayer.setSpuDelay(nextMs * 1000L)
    }

    fun refreshTracks() {
        val rawAudio = mediaPlayer.audioTracks
        audioTracks = rawAudio?.map { VlcTrackOption(it.id, it.name ?: "Track ${it.id}") } ?: emptyList()
        // What the player reports is only the answer when nobody has asked a question: after a stall
        // re-open it reports the track VLC's own language default picked over the viewer's choice.
        TrackChoice.resolve(preferredAudioTrackId, mediaPlayer.audioTrack, audioTracks.map { it.id })
            ?.let { mediaPlayer.audioTrack = it }
        selectedAudioTrackId = mediaPlayer.audioTrack

        val rawSubs = mediaPlayer.spuTracks
        subtitleTracks = rawSubs?.map { VlcTrackOption(it.id, it.name ?: "Subtitle ${it.id}") } ?: emptyList()
        TrackChoice.resolve(preferredSubtitleTrackId, mediaPlayer.spuTrack, subtitleTracks.map { it.id })
            ?.let { mediaPlayer.spuTrack = it }
        selectedSubtitleTrackId = mediaPlayer.spuTrack

        val len = mediaPlayer.length
        if (len > 0L) {
            durationMs = len
        }
    }

    /**
     * Ask the panel for the clip's own refresh rate, so 24 fps content does not arrive as 2:3
     * pulldown judder on a 60 Hz one. Most clips decide to do nothing here: a panel with one mode
     * cannot answer, and a rate that is already a clean multiple needs no change.
     *
     * A TextureView can only carry the hint from API 35 up. Its Surface is private to the view and
     * a second consumer would take the buffer queue away from VLC, so on 31 to 34 the display
     * keeps its own rate.
     */
    private fun applyContentFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val view = attachedTextureView ?: return
        val track = runCatching { mediaPlayer.currentVideoTrack }.getOrNull()
        val fps = FrameRateMatch.contentFps(track?.frameRateNum ?: 0, track?.frameRateDen ?: 0)
        val modes = runCatching { view.display?.supportedRefreshRates?.toList() }.getOrNull().orEmpty()
        val current = runCatching { view.display?.mode?.refreshRate ?: 0f }.getOrNull() ?: 0f
        val target = FrameRateMatch.targetRefreshHz(fps, current, modes) ?: return
        android.util.Log.i("ArcFrameRate", "$fps fps content against $current Hz, modes $modes -> $target Hz")
        runCatching { view.setRequestedFrameRate(target) }
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            // A fetch landing after this must not press play again behind the viewer's back.
            pausedForSeek = false
            stoppedOnPurpose = true
            mediaPlayer.pause()
        }
    }

    private fun closeActivePfd() {
        runCatching {
            activePfd?.close()
        }
        activePfd = null
    }

    fun release() {
        bufferedSeek?.cancel()
        engineScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        detachTextureView()
        runCatching {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        closeActivePfd()
        runCatching {
            libVlc.release()
        }
    }

    private companion object {
        const val STREAM_TICK_MS = 1000L
    }
}

// ============================================================================
// UNIFIED STATIONARY 120HZ SCROLL & FLING ENGINE
// ============================================================================

@Stable
class OneUiHeaderState(
    val maxOffsetPx: Float,
    initialOffsetPx: Float
) {
    var offsetPx by mutableFloatStateOf(initialOffsetPx.coerceIn(0f, maxOffsetPx))

    val expansionFraction: Float
        get() = if (maxOffsetPx > 0f) (offsetPx / maxOffsetPx).coerceIn(0f, 1f) else 0f
}

@Composable
fun rememberOneUiHeaderState(
    expandedHeight: Dp = 250.dp,
    startInOneHandedMode: Boolean = false
): OneUiHeaderState {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { expandedHeight.toPx() }

    return rememberSaveable(
        maxOffsetPx,
        saver = Saver(
            save = { it.offsetPx },
            restore = { savedOffset ->
                OneUiHeaderState(
                    maxOffsetPx = maxOffsetPx,
                    initialOffsetPx = savedOffset
                )
            }
        )
    ) {
        OneUiHeaderState(
            maxOffsetPx = maxOffsetPx,
            initialOffsetPx = if (startInOneHandedMode) maxOffsetPx else 0f
        )
    }
}

@Stable
class OneUiUnifiedScrollController(
    val headerState: OneUiHeaderState,
    val gridState: LazyGridState,
    private val decaySpec: DecayAnimationSpec<Float>
) : ScrollableState, FlingBehavior {

    var isExecutingFling: Boolean = false
    var gestureStartedAtGridTop: Boolean = true

    fun onTouchDown() {
        isExecutingFling = false
        gestureStartedAtGridTop = !gridState.canScrollBackward
    }

    private fun stepUnified(
        gridScrollScope: ScrollScope,
        deltaY: Float,
        allowHeaderExpand: Boolean
    ): Float {
        if (abs(deltaY) < 0.0001f) return 0f

        if (deltaY < 0f) {
            val currentHeader = headerState.offsetPx
            val headerConsumed = if (currentHeader > 0f) {
                val nextHeader = (currentHeader + deltaY).coerceIn(0f, headerState.maxOffsetPx)
                val diff = nextHeader - currentHeader
                headerState.offsetPx = nextHeader
                diff
            } else {
                0f
            }

            val remainingUpward = deltaY - headerConsumed
            val gridConsumed = if (remainingUpward < -0.001f) {
                -gridScrollScope.scrollBy(-remainingUpward)
            } else {
                0f
            }
            return headerConsumed + gridConsumed
        } else {
            val gridConsumed = if (gridState.canScrollBackward) {
                -gridScrollScope.scrollBy(-deltaY)
            } else {
                0f
            }

            val remainingDownward = deltaY - gridConsumed
            val headerConsumed = if (remainingDownward > 0.001f && !gridState.canScrollBackward && allowHeaderExpand) {
                val currentHeader = headerState.offsetPx
                val nextHeader = (currentHeader + remainingDownward).coerceIn(0f, headerState.maxOffsetPx)
                val diff = nextHeader - currentHeader
                headerState.offsetPx = nextHeader
                diff
            } else {
                0f
            }
            return gridConsumed + headerConsumed
        }
    }

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) {
        gridState.scroll(scrollPriority) {
            val gridScrollScope = this
            val unifiedScope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    return stepUnified(
                        gridScrollScope = gridScrollScope,
                        deltaY = pixels,
                        allowHeaderExpand = !isExecutingFling || gestureStartedAtGridTop
                    )
                }
            }
            unifiedScope.block()
        }
    }

    override fun dispatchRawDelta(delta: Float): Float {
        if (delta == 0f) return 0f
        if (delta < 0f && headerState.offsetPx > 0f) {
            val current = headerState.offsetPx
            val next = (current + delta).coerceIn(0f, headerState.maxOffsetPx)
            headerState.offsetPx = next
            val headerConsumed = next - current
            val rem = delta - headerConsumed
            val gridConsumed = if (rem < 0f) -gridState.dispatchRawDelta(-rem) else 0f
            return headerConsumed + gridConsumed
        }
        return -gridState.dispatchRawDelta(-delta)
    }

    override val isScrollInProgress: Boolean
        get() = gridState.isScrollInProgress

    override val canScrollForward: Boolean
        get() = gridState.canScrollForward || headerState.offsetPx < headerState.maxOffsetPx

    override val canScrollBackward: Boolean
        get() = gridState.canScrollBackward || headerState.offsetPx > 0f

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        isExecutingFling = true
        try {
            val currentHeader = headerState.offsetPx
            val maxHeader = headerState.maxOffsetPx

            if (HeaderFling.isOpen(currentHeader)) {
                if (initialVelocity < -HeaderFling.MIN_FLICK_VELOCITY_PX) {
                    if (initialVelocity < -HeaderFling.LIST_FLING_VELOCITY_PX && gridState.canScrollForward) {
                        var lastVal = 0f
                        var vel = initialVelocity
                        AnimationState(
                            initialValue = 0f,
                            initialVelocity = initialVelocity
                        ).animateDecay(decaySpec) {
                            val delta = value - lastVal
                            lastVal = value
                            val consumed = scrollBy(delta)
                            vel = this.velocity
                            if (abs(delta - consumed) > 0.5f) {
                                cancelAnimation()
                            }
                        }
                        if (HeaderFling.isOpen(headerState.offsetPx)) {
                            snapHeaderMonotonic(targetPx = 0f, initialVelocityPx = vel)
                        }
                        return 0f
                    } else {
                        snapHeaderMonotonic(targetPx = 0f, initialVelocityPx = initialVelocity)
                        return 0f
                    }
                }

                if (initialVelocity > HeaderFling.MIN_FLICK_VELOCITY_PX) {
                    snapHeaderMonotonic(targetPx = maxHeader, initialVelocityPx = initialVelocity)
                    return 0f
                }

                val target = HeaderFling.snapTargetPx(currentHeader, maxHeader)
                snapHeaderMonotonic(targetPx = target, initialVelocityPx = initialVelocity)
                return 0f
            }

            if (!gridState.canScrollBackward && initialVelocity > HeaderFling.OPEN_FLICK_VELOCITY_PX) {
                snapHeaderMonotonic(targetPx = maxHeader, initialVelocityPx = initialVelocity)
                return 0f
            }

            if (abs(initialVelocity) > HeaderFling.MIN_DECAY_VELOCITY_PX) {
                var lastVal = 0f
                var remainingVy = initialVelocity
                AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity
                ).animateDecay(decaySpec) {
                    val delta = value - lastVal
                    lastVal = value
                    val consumed = scrollBy(delta)
                    remainingVy = this.velocity
                    if (abs(delta - consumed) > 0.5f) {
                        cancelAnimation()
                    }
                }

                if (!gridState.canScrollBackward && remainingVy > HeaderFling.TAIL_EXPAND_VELOCITY_PX && headerState.offsetPx < maxHeader) {
                    snapHeaderMonotonic(targetPx = maxHeader, initialVelocityPx = remainingVy)
                    return 0f
                }

                return remainingVy
            }

            return 0f
        } finally {
            isExecutingFling = false
        }
    }

    private suspend fun snapHeaderMonotonic(
        targetPx: Float,
        initialVelocityPx: Float
    ) {
        val startPx = headerState.offsetPx
        val distance = targetPx - startPx
        if (abs(distance) < 0.5f) {
            headerState.offsetPx = targetPx
            return
        }

        val stiffness = 550f
        val omega0 = sqrt(stiffness)
        val maxNoOvershootSpeed = omega0 * abs(distance)

        val clampedVelocity = if (distance > 0f) {
            initialVelocityPx.coerceIn(0f, maxNoOvershootSpeed)
        } else {
            initialVelocityPx.coerceIn(-maxNoOvershootSpeed, 0f)
        }

        var prev = startPx
        AnimationState(
            initialValue = startPx,
            initialVelocity = clampedVelocity
        ).animateTo(
            targetValue = targetPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = stiffness
            )
        ) {
            val monotonicValue = if (distance > 0f) {
                value.coerceIn(prev, targetPx)
            } else {
                value.coerceIn(targetPx, prev)
            }
            prev = monotonicValue
            headerState.offsetPx = monotonicValue
            if (abs(targetPx - monotonicValue) <= 0.5f) {
                headerState.offsetPx = targetPx
                cancelAnimation()
            }
        }
        headerState.offsetPx = targetPx
    }
}

private fun currentTopBarPillProgress(
    gridState: LazyGridState,
    headerState: OneUiHeaderState,
    transitionDistancePx: Float
): Float {
    if (headerState.offsetPx > 2f || transitionDistancePx <= 0f) return 0f
    val scrolledPx = if (gridState.firstVisibleItemIndex > 0) {
        transitionDistancePx
    } else {
        gridState.firstVisibleItemScrollOffset.toFloat()
    }
    return (scrolledPx / transitionDistancePx).coerceIn(0f, 1f)
}

// ============================================================================
// HOME SCREEN
// ============================================================================

@Immutable
private data class ActiveVideoPlayback(
    val video: VideoItem,
    val playlist: List<VideoItem>,
    val sharedKeyPrefix: String,
    /**
     * The Telegram files behind [playlist], empty for anything already on the device. The player
     * needs these rather than the grid rows because a cloud video has no path until it is asked for.
     */
    val cloudSources: List<CloudVideoItem> = emptyList(),
    /**
     * This title's files grouped into the parts they are copies of, in watch order, best copy first,
     * or empty when the queue was not opened from a shelf card. The player is otherwise blind to the
     * difference between a second episode and a second upload of the one it is holding.
     */
    val copySlots: List<List<Long>> = emptyList(),
    /**
     * What the catalogue says about the *work*, resolved once for the shelf card this was opened
     * from, or null when nothing opened it as a work.
     *
     * Only the representative file of a title gets a lookup, so a copy opened beside it has no card of
     * its own to find — which is why an episode of a sixty-file show could load with no wordmark while
     * the first one had it. The player asks this first and falls back to the per-file card for a film
     * reached outside a shelf, where there is no title to inherit from.
     */
    val titleCard: MediaCard? = null
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ArcPlayerHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val prefsStore = remember { ArcPreferencesStore(context) }

    var hasPermission by remember { mutableStateOf(hasVideoReadPermission(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var librarySnapshot by remember { mutableStateOf(MediaLibrarySnapshot()) }

    val jellyHazeState = remember { HazeState() }
    val jellyBackdrop = rememberLayerBackdrop()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(BottomNavTab.FOLDERS.ordinal) }
    val selectedTab = BottomNavTab.entries[selectedTabIndex]

    var openedFolderBucketId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedPlaylistName by rememberSaveable { mutableStateOf<String?>(null) }
    var morphingFolderBucketId by rememberSaveable { mutableStateOf<String?>(null) }
    var morphingVideoKey by rememberSaveable { mutableStateOf<String?>(null) }

    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var longPressedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var playlistToDelete by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var pendingPlaylistVideoId by remember { mutableStateOf<Long?>(null) }

    var cloudScreenName by rememberSaveable { mutableStateOf(CloudScreen.SHELF.name) }
    var cloudNotice by remember { mutableStateOf<String?>(null) }

    /**
     * The shelf card opened as a page.
     *
     * Kept off [rememberSaveable] on purpose: a page is opened by a tap and thrown away by a back
     * press, and its payload is a whole [TitleGroup], which has no sane way to survive a rotation.
     * [lastShelfTitle] exists only so the page still has something to draw while it fades out.
     */
    var shelfTitle by remember { mutableStateOf<ShelfTitle?>(null) }
    var lastShelfTitle by remember { mutableStateOf<ShelfTitle?>(null) }
    LaunchedEffect(shelfTitle) { shelfTitle?.let { lastShelfTitle = it } }
    val cloudRepository = remember(
        prefsStore.telegramApiId,
        prefsStore.telegramApiHash,
    ) {
        CloudSession.repositoryFor(
            context = context,
            apiId = prefsStore.effectiveTelegramApiId(),
            apiHash = prefsStore.effectiveTelegramApiHash(),
        )
    }

    // Brought up with the app rather than when the tab is opened: the session takes about a second to
    // restore, and asking for it at the tap is what put a "Signing in" card in front of a shelf that
    // was already on disk. `begin` is inert after its first pass, so a later credential change that
    // rebuilds the repository is the only thing that drives it again.
    LaunchedEffect(cloudRepository) {
        cloudRepository.begin()
    }

    // Same reasoning one layer down: the shelf written down by the last read is lifted off the disk
    // with the app, so the cards exist in memory before the tab is composed. Reading it inside the
    // tab's own effect meant the tap had to draw something first, and that something was
    // "Reading your shelf…" for a few frames. It is a no-op once a chat is held, and the number is
    // still 0 until preferences have been read, which keys this to run again when they land.
    LaunchedEffect(cloudRepository, prefsStore.movieFolderChatId) {
        cloudRepository.restoreManifest(prefsStore.movieFolderChatId)
    }

    var activePlayback by remember { mutableStateOf<ActiveVideoPlayback?>(null) }

    /** The last cloud film handed to the player, so putting it down is something we can notice. */
    var lastCloudPlayback by remember { mutableStateOf<CloudVideoItem?>(null) }

    var cloudResolving by remember { mutableStateOf(false) }

    val cloudStatus: String? = when {
        cloudResolving -> "Asking Telegram for this file…"
        cloudNotice != null -> cloudNotice
        else -> null
    }

    /** Releases waiting to hand a file's bytes back, keyed by TDLib file id. */
    val cloudReleases = remember { mutableMapOf<Int, Job>() }

    /**
     * Put a film's bytes up for grabs, a moment after the player stops reading them.
     *
     * The wait is the whole design: the player's view goes away on this frame while libVLC finishes with
     * the file on its own thread, and Telegram would delete bytes out from under it. A release that has
     * not landed yet is called back the moment that same film is in front of the viewer again — it was
     * never put down — while a release for a *different* film is left to run, which is what stepping
     * straight from one title to the next has to do.
     */
    fun forgetCloudCopySoon(watched: CloudVideoItem) {
        cloudReleases[watched.fileId]?.cancel()
        cloudReleases[watched.fileId] = coroutineScope.launch {
            delay(PLAYER_RELEASE_SETTLE_MS)
            cloudRepository.forgetCopy(watched)
            cloudReleases.remove(watched.fileId)
        }
    }

    /**
     * Say which film the player is reading now, and hand back the one it was reading before.
     *
     * Both ways onto the screen end here — a card tap, and the ◀▶ part skip inside the player, which
     * swaps the video without ever going through the null the close effect watches. Skipping from one
     * part to the next is exactly when the last film's bytes stop being read, so a release keyed only to
     * closing the player would leave a trail of episodes behind.
     */
    fun holdCloudCopy(incoming: CloudVideoItem) {
        val outgoing = lastCloudPlayback
        if (outgoing != null && outgoing.fileId != incoming.fileId) forgetCloudCopySoon(outgoing)
        cloudReleases.remove(incoming.fileId)?.cancel()
        lastCloudPlayback = incoming
    }

    /**
     * Hand a cloud file to the player.
     *
     * There is no content Uri to hand over, and working out which bytes Telegram has on disk is the
     * slow part — so it is not attempted here. The player is given an empty address, opens at once with
     * the film's wordmark over a held frame, and asks Telegram for the path itself. The ◀▶ part skip has
     * always launched this way; a Play button that resolved first only moved the same wait onto the
     * info page, where it looked like the app had frozen.
     */
    val launchCloudVideo: (CloudVideoItem, List<CloudVideoItem>, List<List<Long>>, MediaCard?) -> Unit =
        { item, list, slots, titleCard ->
            cloudNotice = null
            holdCloudCopy(item)
            val prefix = "cloud_${item.chatId}"
            morphingVideoKey = "${prefix}_${item.stableId}"
            prefsStore.rememberCloudWatched(item.stableId)
            activePlayback = ActiveVideoPlayback(
                video = item.toGridVideoItem(),
                playlist = list.map { it.toGridVideoItem() },
                sharedKeyPrefix = prefix,
                cloudSources = list,
                copySlots = slots,
                titleCard = titleCard
            )
        }

    /**
     * Hand a film's bytes back the moment it is put down.
     *
     * Nothing is kept for a second watch any more: Telegram holds every original, and a pile of films
     * nobody has asked for twice is the thing that made the app's storage grow, the number it had to
     * measure, and the cap it had to police. Letting one go the instant the viewer does costs a re-fetch
     * on the next watch and removes all three, so there is no budget to choose and nothing to survey.
     */
    LaunchedEffect(activePlayback) {
        val watched = lastCloudPlayback ?: return@LaunchedEffect
        if (activePlayback != null) return@LaunchedEffect
        lastCloudPlayback = null
        forgetCloudCopySoon(watched)
    }

    LaunchedEffect(prefsStore.autoPipEnabled) {
        (context.findActivity() as? MainActivity)?.autoPipEnabled = prefsStore.autoPipEnabled
    }

    /**
     * Take a whole work off the shelf.
     *
     * Every copy of it, not the one file whose card was held: the card is the film, and deleting half
     * of a group would leave the same poster on screen with a smaller pile behind it.
     */
    val removeFromShelf: (ShelfTitle) -> Unit = { opened ->
        shelfTitle = null
        coroutineScope.launch {
            cloudResolving = true
            val folder = prefsStore.movieFolderChatId
            val removed = runCatching {
                opened.parts.forEach { cloudRepository.removeFromFolder(it, folder) }
            }.isSuccess
            if (removed) runCatching { cloudRepository.loadVideos(folder) }
            cloudResolving = false
            cloudNotice = if (removed) "Taken off your shelf." else "Telegram would not delete that file."
        }
    }

    BackHandler(enabled = activePlayback != null) {
        android.util.Log.i("ArcPlayer", "player closed: back")
        activePlayback = null
    }

    BackHandler(enabled = activePlayback == null && isSearchExpanded) {
        isSearchExpanded = false
        searchQuery = ""
    }

    // The shelf is the Cloud tab's root: back from the playlists lands on the shelf, which is the only
    // screen with nothing behind it.
    val leaveDetailScreen: () -> Unit = {
        cloudNotice = null
        when {
            openedPlaylistName != null -> openedPlaylistName = null
            openedFolderBucketId != null -> openedFolderBucketId = null
            else -> cloudScreenName = CloudScreen.SHELF.name
        }
    }
    val cloudOnShelf = cloudScreenName == CloudScreen.SHELF.name && openedPlaylistName == null
    val somethingToLeave = openedFolderBucketId != null || openedPlaylistName != null || !cloudOnShelf
    BackHandler(enabled = activePlayback == null && !isSearchExpanded && somethingToLeave) {
        leaveDetailScreen()
    }

    // Registered last so it wins over the screens underneath it, and disabled while the player is up:
    // the page stays open behind the film, so backing out of playback lands on the page and not on the
    // grid it came from.
    BackHandler(enabled = activePlayback == null && shelfTitle != null) {
        shelfTitle = null
    }

    val foldersGridState = rememberLazyGridState()
    val allVideosGridState = rememberLazyGridState()
    val playlistsGridState = rememberLazyGridState()
    val menuGridState = rememberLazyGridState()
    val shelfGridState = key(prefsStore.movieFolderChatId) {
        rememberLazyGridState()
    }
    val openedFolderGridState = key(openedFolderBucketId, openedPlaylistName) {
        rememberLazyGridState()
    }

    // A restored screen name can be one this build no longer has, so an unknown value falls back to the
    // shelf rather than throwing during composition.
    val cloudScreen = CloudScreen.entries.firstOrNull { it.name == cloudScreenName } ?: CloudScreen.SHELF

    val activeGridState = when {
        selectedTab == BottomNavTab.FOLDERS && openedFolderBucketId != null -> openedFolderGridState
        selectedTab == BottomNavTab.CLOUD && openedPlaylistName != null -> openedFolderGridState
        selectedTab == BottomNavTab.CLOUD && cloudScreen == CloudScreen.PLAYLISTS -> playlistsGridState
        selectedTab == BottomNavTab.CLOUD -> shelfGridState
        selectedTab == BottomNavTab.VIDEOS -> allVideosGridState
        selectedTab == BottomNavTab.MENU -> menuGridState
        else -> foldersGridState
    }

    val headerState = rememberOneUiHeaderState(
        expandedHeight = 250.dp,
        startInOneHandedMode = false
    )

    // Keep header consistent if user switches to a tab that is already scrolled down
    LaunchedEffect(activeGridState) {
        if (activeGridState.canScrollBackward && headerState.offsetPx > 0f) {
            headerState.offsetPx = 0f
        }
    }

    val decaySpec = rememberSplineBasedDecay<Float>()
    val scrollController = remember(headerState, activeGridState, decaySpec) {
        OneUiUnifiedScrollController(
            headerState = headerState,
            gridState = activeGridState,
            decaySpec = decaySpec
        )
    }

    val showScrollToTopButton by remember(activeGridState, headerState) {
        derivedStateOf {
            headerState.offsetPx <= 2f &&
                (activeGridState.firstVisibleItemIndex > 0 || activeGridState.firstVisibleItemScrollOffset > 300)
        }
    }

    var scanJob by remember { mutableStateOf<Job?>(null) }

    val refreshMediaLibrary: (Boolean) -> Unit = remember(context, activeGridState) {
        { debounce ->
            if (hasVideoReadPermission(context)) {
                hasPermission = true
                scanJob?.cancel()
                scanJob = coroutineScope.launch {
                    if (debounce) {
                        delay(700L)
                        snapshotFlow { activeGridState.isScrollInProgress }.first { !it }
                    }
                    if (librarySnapshot.videos.isEmpty()) isScanning = true
                    val freshSnapshot = scanDeviceVideos(context)
                    if (freshSnapshot != librarySnapshot) {
                        librarySnapshot = freshSnapshot
                        launch(Dispatchers.IO) {
                            freshSnapshot.folders.forEach { folder ->
                                loadVideoThumbnailCancellable(
                                    contentResolver = context.contentResolver,
                                    uri = folder.coverVideoUri
                                )
                            }
                        }
                    }
                    isScanning = false
                }
            } else {
                hasPermission = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasVideoReadPermission(context)
        if (hasPermission) {
            refreshMediaLibrary(false)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(requiredVideoPermissions())
        } else {
            refreshMediaLibrary(false)
        }
    }

    DisposableEffect(lifecycleOwner, context, refreshMediaLibrary) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshMediaLibrary(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshMediaLibrary(true)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val actionBarHeight = 52.dp
    val pillTransitionDistancePx = with(LocalDensity.current) { 42.dp.toPx() }

    val currentHeaderTitle: () -> String = {
        when {
            selectedTab == BottomNavTab.FOLDERS && openedFolderBucketId != null ->
                librarySnapshot.folders.find { it.bucketId == openedFolderBucketId }?.title ?: selectedTab.headerTitle
            selectedTab == BottomNavTab.CLOUD && openedPlaylistName != null -> openedPlaylistName!!
            selectedTab == BottomNavTab.CLOUD && cloudScreenName == CloudScreen.PLAYLISTS.name -> "Playlists"
            selectedTab == BottomNavTab.CLOUD -> "Library"
            else -> selectedTab.headerTitle
        }
    }

    val filteredFolders = remember(librarySnapshot.folders, searchQuery) {
        if (searchQuery.isBlank()) {
            librarySnapshot.folders
        } else {
            librarySnapshot.folders.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val sortedFilteredAllVideos = remember(librarySnapshot.videos, searchQuery, prefsStore.sortOrder) {
        val base = if (searchQuery.isBlank()) {
            librarySnapshot.videos
        } else {
            librarySnapshot.videos.filter {
                it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.folderName.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
        sortVideos(base, prefsStore.sortOrder)
    }

    val lastResumableVideo = remember(librarySnapshot.videos, prefsStore.watchHistoryIds, prefsStore.resumePositions.size) {
        val videoById = librarySnapshot.videos.associateBy { it.id }
        prefsStore.watchHistoryIds.firstNotNullOfOrNull { id ->
            if (prefsStore.resumePositions.containsKey(id)) videoById[id] else null
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedTransitionScope = this

        AnimatedContent(
            targetState = activePlayback,
            transitionSpec = {
                fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(260, easing = FastOutSlowInEasing))
            },
            label = "HeroVideoPlayerTransition"
        ) { currentPlayback ->
            val playerAnimatedVisibilityScope = this

            if (currentPlayback == null) {
                // Plain wrapper so the floating overlays are siblings of the jellySource
                // node: a glass consumer inside the recorded subtree smears its own
                // content on API 34+ and cycles the render tree on API 31-33.
                Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .jellySource(hazeState = jellyHazeState, backdrop = jellyBackdrop)
                        .background(OneUiBackground)
                        .pointerInput(scrollController) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (change.changedToDown()) {
                                        scrollController.onTouchDown()
                                    }
                                }
                            }
                        }
                        .scrollable(
                            state = scrollController,
                            orientation = Orientation.Vertical,
                            flingBehavior = scrollController,
                            reverseDirection = false
                        )
                ) {
                    OneUiCollapsibleScaffold(
                        headerState = headerState,
                        statusBarTopPadding = statusBarTopPadding,
                        actionBarHeight = actionBarHeight,
                        expandedHeader = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(OneUiBackground)
                                    .graphicsLayer {
                                        val fraction = headerState.expansionFraction
                                        alpha = ((fraction - 0.22f) / 0.78f).coerceIn(0f, 1f)
                                        translationY = -(1f - fraction) * 28.dp.toPx()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = currentHeaderTitle(),
                                    transitionSpec = {
                                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)))
                                            .togetherWith(fadeOut(tween(150)))
                                    },
                                    label = "HeaderTitleTransition"
                                ) { title ->
                                    Text(
                                        text = title,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = OneUiTextPrimary,
                                        letterSpacing = (-0.3).sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        },
                        actionBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(actionBarHeight)
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSearchExpanded) {
                                    Surface(
                                        shape = TopActionPillShape,
                                        color = Color.Transparent,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .jellyGlass(
                                                hazeState = null, // pill lives inside the haze source; real blur would smear its own content
                                                backdrop = null,
                                                tintTransparency = 0.4f,
                                                noiseFactor = 0.06f,
                                                shape = TopActionPillShape
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 6.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    isSearchExpanded = false
                                                    searchQuery = ""
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Close search",
                                                    tint = OneUiTextPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = "Search folders & videos...",
                                                        fontSize = 14.sp,
                                                        color = OneUiTextSecondary
                                                    )
                                                }
                                                BasicTextField(
                                                    value = searchQuery,
                                                    onValueChange = { searchQuery = it },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        fontSize = 14.sp,
                                                        color = OneUiTextPrimary
                                                    ),
                                                    cursorBrush = SolidColor(OneUiTextPrimary),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchQuery = "" },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear search",
                                                        tint = OneUiTextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val showBackTitle = (selectedTab == BottomNavTab.FOLDERS && openedFolderBucketId != null) ||
                                        (selectedTab == BottomNavTab.CLOUD &&
                                            (openedPlaylistName != null ||
                                                cloudScreenName != CloudScreen.SHELF.name))

                                    Box(modifier = Modifier.weight(1f)) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showBackTitle,
                                            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
                                            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140))
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier.height(42.dp)
                                            ) {
                                                Spacer(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .graphicsLayer {
                                                            shadowElevation = currentTopBarPillProgress(
                                                                gridState = activeGridState,
                                                                headerState = headerState,
                                                                transitionDistancePx = pillTransitionDistancePx
                                                            ) * 8.dp.toPx()
                                                            shape = TopActionPillShape
                                                            clip = true
                                                        }
                                                        .jellyGlass(
                                                            hazeState = null, // pill lives inside the haze source; real blur would smear its own content
                                                            backdrop = null,
                                                            tintTransparency = 1f - 0.6f * currentTopBarPillProgress(
                                                                gridState = activeGridState,
                                                                headerState = headerState,
                                                                transitionDistancePx = pillTransitionDistancePx
                                                            ),
                                                            noiseFactor = 0.06f,
                                                            shape = TopActionPillShape
                                                        )
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 2.dp, end = 14.dp)
                                                ) {
                                                    val backSource = remember { MutableInteractionSource() }
                                                    IconButton(
                                                        interactionSource = backSource,
                                                        onClick = leaveDetailScreen,
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .jelly(backSource, scaleDownTo = 0.88f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                            contentDescription = "Back",
                                                            tint = OneUiTextPrimary,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = currentHeaderTitle(),
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = OneUiTextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.graphicsLayer {
                                                            alpha = (1f - headerState.expansionFraction * 1.5f).coerceIn(0f, 1f)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.height(42.dp)
                                    ) {
                                        Spacer(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .graphicsLayer {
                                                    val progress = currentTopBarPillProgress(
                                                        gridState = activeGridState,
                                                        headerState = headerState,
                                                        transitionDistancePx = pillTransitionDistancePx
                                                    )
                                                    alpha = progress
                                                    val scale = 0.92f + 0.08f * progress
                                                    scaleX = scale
                                                    scaleY = scale
                                                    shadowElevation = progress * 8.dp.toPx()
                                                    shape = TopActionPillShape
                                                    clip = true
                                                }
                                                .drawBehind {
                                                    drawRect(color = OneUiPillSurface)
                                                }
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            val searchSource = remember { MutableInteractionSource() }
                                            IconButton(
                                                interactionSource = searchSource,
                                                onClick = { isSearchExpanded = true },
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .jelly(searchSource, scaleDownTo = 0.88f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Search,
                                                    contentDescription = "Search videos",
                                                    tint = OneUiTextPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            MorphingMenu(
                                                items = buildList {
                                                    VideoSortOrder.entries.forEachIndexed { index, order ->
                                                        add(
                                                            MorphingMenuItem(
                                                                text = if (prefsStore.sortOrder == order) "✓ ${order.label}" else order.label,
                                                                dividerAfter = index == VideoSortOrder.entries.lastIndex,
                                                                onClick = { prefsStore.updateSortOrder(order) }
                                                            )
                                                        )
                                                    }
                                                    listOf(2, 3, 4).forEachIndexed { index, cols ->
                                                        add(
                                                            MorphingMenuItem(
                                                                text = if (prefsStore.gridColumns == cols) "✓ $cols Columns" else "$cols Columns",
                                                                dividerAfter = index == 2,
                                                                onClick = { prefsStore.updateGridColumns(cols) }
                                                            )
                                                        )
                                                    }
                                                    add(
                                                        MorphingMenuItem(
                                                            text = "Rescan storage",
                                                            onClick = { refreshMediaLibrary(false) }
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        content = {
                            when {
                                !hasPermission -> {
                                    PermissionRequestView(
                                        onGrantClick = {
                                            permissionLauncher.launch(requiredVideoPermissions())
                                        }
                                    )
                                }

                                isScanning && librarySnapshot.videos.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = OneUiTextPrimary,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                librarySnapshot.videos.isEmpty() -> {
                                    EmptyMediaStateView(onRescanClick = { refreshMediaLibrary(false) })
                                }

                                else -> {
                                    val topContentPad = statusBarTopPadding + actionBarHeight + 6.dp
                                    val bottomContentPad = 128.dp + navBarBottomPadding

                                    when (selectedTab) {
                                        BottomNavTab.FOLDERS -> {
                                            AnimatedContent(
                                                targetState = openedFolderBucketId,
                                                transitionSpec = {
                                                    (fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                                        scaleIn(initialScale = 0.96f, animationSpec = tween(240, easing = FastOutSlowInEasing)))
                                                        .togetherWith(
                                                            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                                                scaleOut(targetScale = 0.96f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                                                        )
                                                },
                                                label = "FolderSharedContainerMorph"
                                            ) { bucketId ->
                                                val folderVisibilityScope = this
                                                val targetFolder = bucketId?.let { id -> librarySnapshot.folders.find { it.bucketId == id } }
                                                if (targetFolder != null) {
                                                    val folderVideos = remember(
                                                        librarySnapshot.videos,
                                                        targetFolder.bucketId,
                                                        searchQuery,
                                                        prefsStore.sortOrder
                                                    ) {
                                                        val raw = librarySnapshot.videos.filter { it.bucketId == targetFolder.bucketId }
                                                        val searched = if (searchQuery.isBlank()) raw else raw.filter {
                                                            it.title.contains(searchQuery.trim(), ignoreCase = true)
                                                        }
                                                        sortVideos(searched, prefsStore.sortOrder)
                                                    }
                                                    with(sharedTransitionScope) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .sharedBounds(
                                                                    sharedContentState = rememberSharedContentState(
                                                                        key = "folder_container_${targetFolder.bucketId}"
                                                                    ),
                                                                    animatedVisibilityScope = folderVisibilityScope,
                                                                    boundsTransform = { _, _ ->
                                                                        spring(
                                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                                            stiffness = 380f
                                                                        )
                                                                    },
                                                                    clipInOverlayDuringTransition = OverlayClip(
                                                                        CardCornerShape
                                                                    )
                                                                )
                                                                .background(OneUiBackground)
                                                        ) {
                                                            VideoGridContent(
                                                                videos = folderVideos,
                                                                columns = prefsStore.gridColumns,
                                                                gridState = openedFolderGridState,
                                                                topPadding = topContentPad,
                                                                bottomPadding = bottomContentPad,
                                                                sharedKeyPrefix = "folder_${targetFolder.bucketId}",
                                                                morphingVideoKey = morphingVideoKey,
                                                                resumePositions = prefsStore.resumePositions,
                                                                favoriteIds = prefsStore.favoriteIds,
                                                                sharedTransitionScope = sharedTransitionScope,
                                                                playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
                                                                onVideoClick = { video ->
                                                                    val vKey = "folder_${targetFolder.bucketId}_${video.id}"
                                                                    morphingVideoKey = vKey
                                                                    activePlayback = ActiveVideoPlayback(
                                                                        video = video,
                                                                        playlist = folderVideos,
                                                                        sharedKeyPrefix = "folder_${targetFolder.bucketId}"
                                                                    )
                                                                },
                                                                onVideoLongClick = { video ->
                                                                    longPressedVideo = video
                                                                }
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    FolderGridContent(
                                                        folders = filteredFolders,
                                                        columns = prefsStore.gridColumns,
                                                        gridState = foldersGridState,
                                                        topPadding = topContentPad,
                                                        bottomPadding = bottomContentPad,
                                                        morphingFolderBucketId = morphingFolderBucketId,
                                                        sharedTransitionScope = sharedTransitionScope,
                                                        folderAnimatedVisibilityScope = folderVisibilityScope,
                                                        onFolderClick = { folder ->
                                                            morphingFolderBucketId = folder.bucketId
                                                            openedFolderBucketId = folder.bucketId
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        BottomNavTab.VIDEOS -> {
                                            VideoGridContent(
                                                videos = sortedFilteredAllVideos,
                                                columns = prefsStore.gridColumns,
                                                gridState = allVideosGridState,
                                                topPadding = topContentPad,
                                                bottomPadding = bottomContentPad,
                                                sharedKeyPrefix = "all_videos",
                                                morphingVideoKey = morphingVideoKey,
                                                resumePositions = prefsStore.resumePositions,
                                                favoriteIds = prefsStore.favoriteIds,
                                                sharedTransitionScope = sharedTransitionScope,
                                                playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
                                                onVideoClick = { video ->
                                                    morphingVideoKey = "all_videos_${video.id}"
                                                    activePlayback = ActiveVideoPlayback(
                                                        video = video,
                                                        playlist = sortedFilteredAllVideos,
                                                        sharedKeyPrefix = "all_videos"
                                                    )
                                                },
                                                onVideoLongClick = { video ->
                                                    longPressedVideo = video
                                                }
                                            )
                                        }

                                        BottomNavTab.CLOUD -> {
                                            CloudTabContent(
                                                repository = cloudRepository,
                                                prefsStore = prefsStore,
                                                allVideos = librarySnapshot.videos,
                                                columns = prefsStore.gridColumns,
                                                gridState = activeGridState,
                                                topPadding = topContentPad,
                                                bottomPadding = bottomContentPad,
                                                screen = cloudScreen,
                                                openedPlaylistName = openedPlaylistName,
                                                statusText = cloudStatus,
                                                morphingVideoKey = morphingVideoKey,
                                                sharedTransitionScope = sharedTransitionScope,
                                                playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
                                                onScreenChange = { next ->
                                                    cloudScreenName = next.name
                                                    openedPlaylistName = null
                                                    cloudNotice = null
                                                },
                                                onOpenShelfTitle = { opened -> shelfTitle = opened },
                                                onOpenPlaylist = { name -> openedPlaylistName = name },
                                                onLongPressCustomPlaylist = { name -> playlistToDelete = name },
                                                onCreatePlaylistClick = {
                                                    pendingPlaylistVideoId = null
                                                    newPlaylistNameInput = ""
                                                    showCreatePlaylistDialog = true
                                                },
                                                onVideoClick = { video, list, prefix ->
                                                    morphingVideoKey = "${prefix}_${video.id}"
                                                    activePlayback = ActiveVideoPlayback(
                                                        video = video,
                                                        playlist = list,
                                                        sharedKeyPrefix = prefix
                                                    )
                                                },
                                                onCloudVideoClick = { item, list ->
                                                    launchCloudVideo(item, list, emptyList(), null)
                                                },
                                                onShelfPlay = { item, list, slots, card ->
                                                    launchCloudVideo(item, list, slots, card)
                                                },
                                                onVideoLongClick = { video ->
                                                    longPressedVideo = video
                                                }
                                            )
                                        }

                                        BottomNavTab.MENU -> {
                                            MenuTabContent(
                                                snapshot = librarySnapshot,
                                                prefsStore = prefsStore,
                                                gridState = menuGridState,
                                                topPadding = topContentPad,
                                                bottomPadding = bottomContentPad
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                    AnimatedVisibility(
                        visible = lastResumableVideo != null && !showScrollToTopButton,
                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
                        exit = fadeOut(tween(140)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 86.dp + navBarBottomPadding)
                    ) {
                        lastResumableVideo?.let { resumeVideo ->
                            val posMs = prefsStore.resumePositions[resumeVideo.id] ?: 0L
                            Surface(
                                onClick = {
                                    morphingVideoKey = "all_videos_${resumeVideo.id}"
                                    activePlayback = ActiveVideoPlayback(
                                        video = resumeVideo,
                                        playlist = librarySnapshot.videos,
                                        sharedKeyPrefix = "all_videos"
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                color = OneUiPillSurface,
                                modifier = Modifier
                                    .height(38.dp)
                                    .shadow(8.dp, RoundedCornerShape(24.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = OneUiBadgeOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Resume ${resumeVideo.title} (${formatDuration(posMs)})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OneUiTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showScrollToTopButton,
                        enter = fadeIn(tween(180)) + scaleIn(
                            initialScale = 0.72f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        exit = fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.72f,
                            animationSpec = tween(150)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 86.dp + navBarBottomPadding)
                    ) {
                        OneUiScrollToTopButton(
                            onClick = {
                                coroutineScope.launch {
                                    activeGridState.animateScrollToItem(index = 0, scrollOffset = 0)
                                }
                            }
                        )
                    }

                    OneUiFloatingBottomBar(
                        selectedTab = selectedTab,
                        hazeState = jellyHazeState,
                        backdrop = jellyBackdrop,
                        onTabSelected = { tab ->
                            if (tab == BottomNavTab.FOLDERS && selectedTab == BottomNavTab.FOLDERS) {
                                openedFolderBucketId = null
                            }
                            if (tab == BottomNavTab.CLOUD && selectedTab == BottomNavTab.CLOUD) {
                                openedPlaylistName = null
                                cloudScreenName = CloudScreen.SHELF.name
                            }
                            // The page belongs to the Cloud tab, and it covers the whole screen, so
                            // carrying it onto another tab would hide the tab it landed on.
                            if (tab != BottomNavTab.CLOUD) shelfTitle = null
                            selectedTabIndex = tab.ordinal
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                bottom = 12.dp + navBarBottomPadding
                            )
                    )

                    // The page a shelf poster opens. It sits over everything, including the bar,
                    // because the reference page carries its own back arrow and owns the whole screen.
                    AnimatedVisibility(
                        visible = shelfTitle != null,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        (shelfTitle ?: lastShelfTitle)?.let { opened ->
                            CloudTitleInfoPage(
                                title = opened,
                                prefsStore = prefsStore,
                                statusText = cloudStatus,
                                isResolving = cloudResolving,
                                topInset = statusBarTopPadding,
                                bottomInset = navBarBottomPadding,
                                onBack = { shelfTitle = null },
                                onPlay = { item, list, slots -> launchCloudVideo(item, list, slots, opened.card) },
                                onRemove = removeFromShelf
                            )
                        }
                    }
                }
            } else {
                HeroVlcVideoPlayerScreen(
                    playback = currentPlayback,
                    prefsStore = prefsStore,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = playerAnimatedVisibilityScope,
                    cloudRepository = if (currentPlayback.cloudSources.isEmpty()) null else cloudRepository,
                    onSelectAdjacentVideo = { nextVideo ->
                        currentPlayback.cloudSources
                            .firstOrNull { it.stableId == nextVideo.id }
                            ?.let { holdCloudCopy(it) }
                        morphingVideoKey = "${currentPlayback.sharedKeyPrefix}_${nextVideo.id}"
                        activePlayback = currentPlayback.copy(video = nextVideo)
                    },
                    onDismiss = { activePlayback = null }
                )
            }
        }
    }

    longPressedVideo?.let { targetVideo ->
        val isFav = prefsStore.favoriteIds.contains(targetVideo.id)
        AlertDialog(
            onDismissRequest = { longPressedVideo = null },
            title = {
                Text(
                    text = targetVideo.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${targetVideo.folderName} • ${targetVideo.durationFormatted} • ${formatFileSize(targetVideo.sizeBytes)}",
                        fontSize = 12.5.sp,
                        color = OneUiTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        onClick = {
                            prefsStore.toggleFavorite(targetVideo.id)
                            longPressedVideo = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = OneUiPillSelected.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFav) OneUiBadgeOrange else OneUiTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isFav) "Remove from Favorites" else "Add to Favorites",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    prefsStore.playlists.keys.forEach { plName ->
                        Surface(
                            onClick = {
                                prefsStore.addVideoToPlaylist(plName, targetVideo.id)
                                longPressedVideo = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = OneUiPillSelected.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Add to \u201C$plName\u201D",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            pendingPlaylistVideoId = targetVideo.id
                            newPlaylistNameInput = ""
                            longPressedVideo = null
                            showCreatePlaylistDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = OneUiPillSelected.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "New Playlist...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { longPressedVideo = null }) {
                    Text("Close", color = OneUiTextPrimary)
                }
            }
        )
    }

    playlistToDelete?.let { targetPlaylist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("Delete \u201C$targetPlaylist\u201D? Your video files on device will not be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefsStore.deletePlaylist(targetPlaylist)
                        playlistToDelete = null
                    }
                ) {
                    Text("Delete", color = OneUiBadgeOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = OneUiTextSecondary)
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistNameInput.isNotBlank()) {
                            prefsStore.createPlaylist(newPlaylistNameInput, pendingPlaylistVideoId)
                        }
                        showCreatePlaylistDialog = false
                    }
                ) {
                    Text("Create", fontWeight = FontWeight.SemiBold, color = OneUiTextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = OneUiTextSecondary)
                }
            }
        )
    }
}

// ============================================================================
// PLAYLISTS TAB & MENU TAB SCREENS (UNIFIED WITH ONE UI SCROLL CONTROLLER)
// ============================================================================

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlaylistsTabContent(
    allVideos: List<VideoItem>,
    prefsStore: ArcPreferencesStore,
    columns: Int,
    gridState: LazyGridState,
    topPadding: Dp,
    bottomPadding: Dp,
    openedPlaylistName: String?,
    morphingVideoKey: String?,
    sharedTransitionScope: SharedTransitionScope,
    playerAnimatedVisibilityScope: AnimatedVisibilityScope,
    onOpenPlaylist: (String) -> Unit,
    onLongPressCustomPlaylist: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>, String) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit
) {
    val videoById = remember(allVideos) { allVideos.associateBy { it.id } }

    if (openedPlaylistName != null) {
        val playlistVideos = remember(openedPlaylistName, allVideos, prefsStore.favoriteIds, prefsStore.watchHistoryIds, prefsStore.playlists) {
            when (openedPlaylistName) {
                "Favorites" -> allVideos.filter { prefsStore.favoriteIds.contains(it.id) }
                "Watch History" -> prefsStore.watchHistoryIds.mapNotNull { videoById[it] }
                else -> prefsStore.playlists[openedPlaylistName].orEmpty().mapNotNull { videoById[it] }
            }
        }
        VideoGridContent(
            videos = playlistVideos,
            columns = columns,
            gridState = gridState,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            sharedKeyPrefix = "playlist_$openedPlaylistName",
            morphingVideoKey = morphingVideoKey,
            resumePositions = prefsStore.resumePositions,
            favoriteIds = prefsStore.favoriteIds,
            sharedTransitionScope = sharedTransitionScope,
            playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
            onVideoClick = { video ->
                onVideoClick(video, playlistVideos, "playlist_$openedPlaylistName")
            },
            onVideoLongClick = onVideoLongClick
        )
        return
    }

    val historyVideos = remember(allVideos, prefsStore.watchHistoryIds) {
        prefsStore.watchHistoryIds.mapNotNull { videoById[it] }
    }
    val favoriteVideos = remember(allVideos, prefsStore.favoriteIds) {
        allVideos.filter { prefsStore.favoriteIds.contains(it.id) }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "builtin_history") {
            VideoFolderCard(
                folder = VideoFolderItem(
                    bucketId = "builtin_history",
                    title = "Watch History",
                    videoCount = historyVideos.size,
                    coverVideoUri = historyVideos.firstOrNull()?.uri ?: Uri.EMPTY,
                    isCameraFolder = false,
                    hasRecentVideos = historyVideos.isNotEmpty()
                ),
                isMorphTarget = false,
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
                onClick = { onOpenPlaylist("Watch History") }
            )
        }

        item(key = "builtin_favorites") {
            VideoFolderCard(
                folder = VideoFolderItem(
                    bucketId = "builtin_favorites",
                    title = "Favorites",
                    videoCount = favoriteVideos.size,
                    coverVideoUri = favoriteVideos.firstOrNull()?.uri ?: Uri.EMPTY,
                    isCameraFolder = false,
                    hasRecentVideos = false
                ),
                isMorphTarget = false,
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
                onClick = { onOpenPlaylist("Favorites") }
            )
        }

        items(
            items = prefsStore.playlists.entries.toList(),
            key = { "pl_${it.key}" }
        ) { (plName, ids) ->
            val resolved = ids.mapNotNull { videoById[it] }
            val coverUri = resolved.firstOrNull()?.uri ?: Uri.EMPTY
            VideoFolderCard(
                folder = VideoFolderItem(
                    bucketId = "pl_$plName",
                    title = plName,
                    videoCount = resolved.size,
                    coverVideoUri = coverUri,
                    isCameraFolder = false,
                    hasRecentVideos = false
                ),
                isMorphTarget = false,
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
                onClick = { onOpenPlaylist(plName) },
                onLongClick = { onLongPressCustomPlaylist(plName) }
            )
        }

        item(key = "create_new_playlist_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .clip(CardCornerShape)
                    .background(OneUiPillSelected)
                    .clickable(onClick = onCreatePlaylistClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = OneUiTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "New Playlist",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary
                    )
                }
            }
        }
    }
}

// ============================================================================
// CLOUD TAB: TELEGRAM SIGN-IN, CHAT LIST AND CLOUD VIDEO GRID
// ============================================================================

/**
 * Present a Telegram video as if it were a local one so the existing card, hero transition and
 * player can all be reused unchanged.
 *
 * The id is the negative key from [CloudIds], which is what lets resume positions and the player's
 * own bookkeeping share the same maps as the local library. [streamUri] is only filled in at tap
 * time, once TDLib has actually handed over a readable file.
 */
private fun CloudVideoItem.toGridVideoItem(streamUri: Uri? = null, card: MediaCard? = null): VideoItem = VideoItem(
    id = stableId,
    uri = streamUri ?: Uri.EMPTY,
    // The uploader's name is only a question. Until the catalogue answers, the card shows it as is;
    // a refused lookup arrives as a card holding the cleaned-up name, so this never parses during scroll.
    title = card?.title ?: title,
    bucketId = "cloud_$chatId",
    folderName = card?.subtitle ?: chatTitle,
    durationMs = durationMs,
    // A film sent as a file has no duration in Telegram's metadata, so the badge carries its size
    // instead of the meaningless 00:00.
    durationFormatted = if (durationMs > 0L) formatDuration(durationMs) else sizeFormatted,
    sizeBytes = sizeBytes,
    dateAddedSec = 0L
)

/**
 * The Cloud tab: the shelf, and the on-device playlists a pill on it opens.
 *
 * Dispatch only: each screen owns its own grid, because a shelf of cards grouped out of file names has
 * nothing in common with a list of playlist rows.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CloudTabContent(
    repository: CloudRepository,
    prefsStore: ArcPreferencesStore,
    allVideos: List<VideoItem>,
    columns: Int,
    gridState: LazyGridState,
    topPadding: Dp,
    bottomPadding: Dp,
    screen: CloudScreen,
    openedPlaylistName: String?,
    statusText: String?,
    morphingVideoKey: String?,
    sharedTransitionScope: SharedTransitionScope,
    playerAnimatedVisibilityScope: AnimatedVisibilityScope,
    onScreenChange: (CloudScreen) -> Unit,
    onOpenShelfTitle: (ShelfTitle) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onLongPressCustomPlaylist: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>, String) -> Unit,
    onCloudVideoClick: (CloudVideoItem, List<CloudVideoItem>) -> Unit,
    onShelfPlay: (CloudVideoItem, List<CloudVideoItem>, List<List<Long>>, MediaCard?) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit
) {
    val authState by repository.authState.collectAsState()
    if (authState.stage != CloudAuthStage.AUTHORIZED) {
        CloudAuthGrid(
            repository = repository,
            prefsStore = prefsStore,
            state = authState,
            gridState = gridState,
            topPadding = topPadding,
            bottomPadding = bottomPadding
        )
        return
    }

    val grid = CloudGridArgs(
        prefsStore = prefsStore,
        columns = columns,
        gridState = gridState,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        statusText = statusText,
        morphingVideoKey = morphingVideoKey,
        sharedTransitionScope = sharedTransitionScope,
        playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
        onShelfTitleOpen = onOpenShelfTitle,
        onCloudVideoClick = onCloudVideoClick,
        onShelfPlay = onShelfPlay
    )

    when {
        screen == CloudScreen.PLAYLISTS -> PlaylistsTabContent(
            allVideos = allVideos,
            prefsStore = prefsStore,
            columns = columns,
            gridState = gridState,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            openedPlaylistName = openedPlaylistName,
            morphingVideoKey = morphingVideoKey,
            sharedTransitionScope = sharedTransitionScope,
            playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
            onOpenPlaylist = onOpenPlaylist,
            onLongPressCustomPlaylist = onLongPressCustomPlaylist,
            onCreatePlaylistClick = onCreatePlaylistClick,
            onVideoClick = onVideoClick,
            onVideoLongClick = onVideoLongClick
        )
        else -> CloudShelfGrid(
            repository = repository,
            grid = grid,
            onOpenPlaylists = { onScreenChange(CloudScreen.PLAYLISTS) }
        )
    }
}

/** The grid arguments the shelf and its section emitters all carry, so a card does not need eleven parameters. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class CloudGridArgs(
    val prefsStore: ArcPreferencesStore,
    val columns: Int,
    val gridState: LazyGridState,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val statusText: String?,
    val morphingVideoKey: String?,
    val sharedTransitionScope: SharedTransitionScope,
    val playerAnimatedVisibilityScope: AnimatedVisibilityScope,
    val onShelfTitleOpen: (ShelfTitle) -> Unit,
    val onCloudVideoClick: (CloudVideoItem, List<CloudVideoItem>) -> Unit,
    /**
     * Open a whole work straight into the player, which is what the Continue Watching row does: the
     * info page is the only other thing that plays this way, and it reaches the same callback.
     *
     * [onCloudVideoClick] cannot stand in for it, because a bare file gives the player no copies to
     * pick from and no parts to step through.
     */
    val onShelfPlay: (CloudVideoItem, List<CloudVideoItem>, List<List<Long>>, MediaCard?) -> Unit
)

@Composable
private fun CloudAuthGrid(
    repository: CloudRepository,
    prefsStore: ArcPreferencesStore,
    state: CloudAuthState,
    gridState: LazyGridState,
    topPadding: Dp,
    bottomPadding: Dp
) {
    val busy by repository.busy.collectAsState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(1),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topPadding, bottom = bottomPadding)
    ) {
        item(key = "cloud_auth_panel") {
            CloudAuthPanel(
                state = state,
                busy = busy,
                repository = repository,
                prefsStore = prefsStore
            )
        }
    }
}

/**
 * The shelf: one private channel, its files gathered into one card per work.
 *
 * Nothing here is a list of Telegram chats. A card is a film or a show, and the files behind it are the
 * copies and episodes that make it up, which is the only reason [TitleGroups] exists.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CloudShelfGrid(
    repository: CloudRepository,
    grid: CloudGridArgs,
    onOpenPlaylists: () -> Unit
) {
    val videosByChat by repository.videos.collectAsState()
    val thumbnails by repository.thumbnails.collectAsState()
    val progressByFile by repository.progress.collectAsState()

    val folderChatId = grid.prefsStore.movieFolderChatId
    var shelfGone by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(folderChatId != 0L) }
    var creating by remember { mutableStateOf(false) }
    var createFailed by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The shelf is filled from Telegram, not from here, so coming back to the app is the moment it has
    // to be asked again — the tab does not leave composition on its own.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reloadToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openTelegram: () -> Unit = {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("tg://")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess || runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        Toast.makeText(
            context,
            if (opened) {
                "Forward any movie or episode into your $SHELF_CHANNEL_TITLE channel — " +
                    "it lands here once Telegram delivers it."
            } else {
                "Telegram is not installed on this device."
            },
            Toast.LENGTH_LONG
        ).show()
    }

    // Filing happens in Telegram itself: this app cannot lift a file out of someone else's chat without
    // reading that chat, so the button hands the viewer over and says what to do once there — having
    // first made sure a channel is waiting to be handed over to.
    val handOffToTelegram: () -> Unit = {
        createFailed = false
        creating = true
        scope.launch {
            try {
                val shelf = repository.ensureShelf(folderChatId)
                if (shelf != folderChatId) grid.prefsStore.rememberMovieFolder(shelf)
                openTelegram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (refused: Exception) {
                android.util.Log.w("ArcCloud", "shelf make failed", refused)
                createFailed = true
            } finally {
                creating = false
            }
        }
    }

    LaunchedEffect(folderChatId, reloadToken) {
        // A revisit asks again in the background rather than spinning over cards that are already on
        // screen; a first look at a shelf has nothing to hold up yet.
        loading = videosByChat[folderChatId] == null
        shelfGone = false
        when (val shelf = repository.readShelf(folderChatId)) {
            is CloudRepository.ShelfRead.Ready -> {
                // A different channel means the account's shelf was made elsewhere: the id is written
                // and this effect re-runs on the channel that has the files.
                if (shelf.chatId != folderChatId) grid.prefsStore.rememberMovieFolder(shelf.chatId)
                else loading = false
            }
            CloudRepository.ShelfRead.Gone -> {
                shelfGone = true
                loading = false
            }
        }
    }

    val shelfVideos = if (shelfGone || folderChatId == 0L) emptyList() else videosByChat[folderChatId].orEmpty()
    val videoById = remember(shelfVideos) { shelfVideos.associateBy { it.stableId } }
    val groups = remember(shelfVideos) {
        TitleGroups.group(shelfVideos.map { TitleCandidate(it.stableId, it.title, it.sizeBytes) })
    }

    // Only the file a card hangs on is asked about. The copies underneath it are searched when someone
    // opens them, which stops a shelf of duplicates spending the catalogue's quota on artwork nobody
    // is looking at.
    TitleSearchPass(groups.mapNotNull { videoById[it.representative.id] }, grid.prefsStore)

    /**
     * Each card cut down to what the Continue Watching row has to ask of it: which files are copies of
     * one part, and how long each of them runs.
     *
     * Held apart from the positions themselves because those change when a film is put down, while the
     * shelf's shape only changes when Telegram hands back a different list.
     */
    val playables = remember(shelfVideos) {
        groups.mapNotNull { group ->
            val representative = videoById[group.representative.id] ?: return@mapNotNull null
            ShelfPlayable(
                group = group,
                parts = group.parts.mapNotNull { videoById[it.id] },
                representative = representative,
                slots = group.parts
                    .groupBy { (it.season ?: 0) to (it.episode ?: 0) }
                    .values
                    .map { slot -> slot.map { it.id } },
                lengthsMs = group.parts.mapNotNull { part ->
                    videoById[part.id]?.let { part.id to it.durationMs }
                }.toMap()
            )
        }
    }

    // Read in this composable rather than inside the grid's own scope. A lazy list's content lambda is
    // not a recomposition scope that state changes reach, so a row built in there would be frozen at
    // whatever the shelf looked like the last time the list was rebuilt: still showing a film watched to
    // its end, and never moving the one just put down to the front.
    //
    // The positions themselves change when a film is put down, while the shelf's shape only changes when
    // Telegram hands back a different list, so only this half re-runs.
    val resumeMs = grid.prefsStore.resumePositions
    val lastPlayedMs = grid.prefsStore.cloudWatchedAt
    val unfinished = playables.mapNotNull { playable ->
        val progress = PlayOrder.progress(
            playable.slots,
            resumeMs,
            playable.lengthsWithWatched(grid.prefsStore.knownLengths),
            lastPlayedMs
        ) ?: return@mapNotNull null
        playable to progress
    }.sortedByDescending { (_, progress) -> lastPlayedMs[progress.partId] ?: 0L }
        .take(CONTINUE_RAIL_LIMIT)


    LazyVerticalGrid(
        state = grid.gridState,
        columns = GridCells.Fixed(grid.columns),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = grid.topPadding,
            bottom = grid.bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "shelf_actions", span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudPill(text = "Add from Telegram", onClick = handOffToTelegram)
                CloudPill(text = "On-device playlists", onClick = onOpenPlaylists)
            }
        }

        grid.statusText?.let { message ->
            item(key = "cloud_status", span = { GridItemSpan(maxLineSpan) }) {
                CloudEmptyRow(text = message, actionLabel = null, onAction = {})
            }
        }

        if (folderChatId == 0L || shelfGone) {
            item(key = "shelf_missing", span = { GridItemSpan(maxLineSpan) }) {
                CloudEmptyRow(
                    text = when {
                        creating -> "Making your shelf in Telegram…"
                        createFailed -> "Telegram would not make the channel. Try again in a moment."
                        folderChatId != 0L ->
                            "Telegram still knows the number of the channel this shelf was built in, but " +
                                "will not give its files back — the channel has been deleted, or this " +
                                "sign-in can no longer read it, and no other channel named " +
                                "$SHELF_CHANNEL_TITLE has anything in it. Add from Telegram makes one this " +
                                "app can read and opens Telegram with the name to forward into."
                        else ->
                            "Your shelf is a private Telegram channel called $SHELF_CHANNEL_TITLE that only " +
                                "this app writes to. A file you add is forwarded there, so the film itself " +
                                "never uploads twice."
                    },
                    actionLabel = if (creating) null else "Add from Telegram",
                    onAction = handOffToTelegram
                )
            }
            return@LazyVerticalGrid
        }

        // Cards from the manifest are on screen while the shelf walk runs, so the wait is only ever
        // said out loud when there is nothing to say instead.
        if (loading && shelfVideos.isEmpty()) {
            item(key = "shelf_loading", span = { GridItemSpan(maxLineSpan) }) {
                CloudEmptyRow(text = "Reading your shelf…", actionLabel = null, onAction = {})
            }
            return@LazyVerticalGrid
        }

        if (groups.isEmpty()) {
            item(key = "shelf_empty", span = { GridItemSpan(maxLineSpan) }) {
                CloudEmptyRow(
                    text = "Nothing on the shelf yet. Forward a movie or an episode into your " +
                        "$SHELF_CHANNEL_TITLE channel in Telegram and it appears here.",
                    actionLabel = "Add from Telegram",
                    onAction = handOffToTelegram
                )
            }
            return@LazyVerticalGrid
        }

        // Inside the page rather than in the bar above it: the bar's search belongs to the videos on
        // this phone, and a shelf is a different pile of files under different names.
        item(key = "shelf_search", span = { GridItemSpan(maxLineSpan) }) {
            ShelfSearchField(query, onQueryChange = { query = it })
        }

        val searching = query.isNotBlank()
        val matches = if (searching) {
            playables.filter { playable ->
                ShelfSearch.matches(
                    query,
                    playable.searchText + ' ' +
                        (grid.prefsStore.mediaCards[playable.representative.stableId]?.title ?: "")
                )
            }
        } else {
            emptyList()
        }

        if (searching) {
            if (matches.isEmpty()) {
                item(key = "shelf_search_none", span = { GridItemSpan(maxLineSpan) }) {
                    CloudEmptyRow(
                        text = "Nothing on the shelf answers \"$query\". This reads the names your files " +
                            "carry and the titles the catalogue gave them — it does not go looking for " +
                            "anything you have not added.",
                        actionLabel = null,
                        onAction = {}
                    )
                }
            } else {
                shelfSection(
                    matches.map { it.group },
                    "Results",
                    videoById,
                    grid,
                    thumbnails,
                    progressByFile
                )
            }
            return@LazyVerticalGrid
        }

        if (unfinished.isNotEmpty()) {
            item(key = "shelf_continue", span = { GridItemSpan(maxLineSpan) }) {
                ShelfRail(unfinished, grid, thumbnails, progressByFile)
            }
        }

        val movies = groups.filter { it.kind == TitleKind.MOVIE }
        val series = groups.filter { it.kind == TitleKind.SERIES }
        shelfSection(movies, "Movies", videoById, grid, thumbnails, progressByFile)
        shelfSection(series, "Series", videoById, grid, thumbnails, progressByFile)
    }
}

/**
 * One shelf section: a header and its cards, emitted into the grid's own scope.
 *
 * Two sections rather than two rails, because a shelf of everything you own should scroll as one list.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private fun LazyGridScope.shelfSection(
    groups: List<TitleGroup>,
    header: String,
    videoById: Map<Long, CloudVideoItem>,
    grid: CloudGridArgs,
    thumbnails: Map<Long, ByteArray>,
    progressByFile: Map<Int, CloudProgress>
) {
    if (groups.isEmpty()) return
    item(key = "shelf_section_$header", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = header,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OneUiTextPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
    }
    items(
        items = groups,
        key = { "shelf_${it.key}" },
        contentType = { "shelf_card" }
    ) { group ->
        val representative = videoById[group.representative.id] ?: return@items
        val card = grid.prefsStore.mediaCards[representative.stableId]
        val bytes = thumbnails[representative.stableId]
        val inlineThumb = remember(bytes) {
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        val savedPos = grid.prefsStore.resumePositions[representative.stableId] ?: 0L
        val watchFraction = if (savedPos > 0L && representative.durationMs > 0L) {
            (savedPos.toFloat() / representative.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val prefix = "cloud_${representative.chatId}"
        VideoFileCard(
            video = group.toShelfCard(representative, card),
            sharedKeyPrefix = prefix,
            isMorphTarget = "${prefix}_${representative.stableId}" == grid.morphingVideoKey,
            watchProgressFraction = watchFraction,
            isFavorite = grid.prefsStore.favoriteIds.contains(representative.stableId),
            sharedTransitionScope = grid.sharedTransitionScope,
            animatedVisibilityScope = grid.playerAnimatedVisibilityScope,
            inlineThumbnail = inlineThumb,
            posterUrl = card?.posterUrl,
            downloadFraction = progressByFile[representative.fileId]?.fraction,
            onClick = {
                grid.onShelfTitleOpen(
                    ShelfTitle(
                        group = group,
                        parts = group.parts.mapNotNull { videoById[it.id] },
                        representative = representative,
                        card = card
                    )
                )
            },
            onLongClick = {}
        )
    }
}

/**
 * One shelf card cut down to the two questions the Continue Watching row asks of it: which files are
 * copies of one part, and how long each of them runs.
 *
 * [ShelfTitle] holds the same shape but is built when a card is tapped, for one work, and carries the
 * catalogue and episode tables a page needs. A row drawn over the whole shelf needs neither, and the
 * shelf's shape only changes when Telegram hands back a different list — so this is remembered once
 * while the positions it is read against change every time a film is put down.
 */
private class ShelfPlayable(
    val group: TitleGroup,
    val parts: List<CloudVideoItem>,
    val representative: CloudVideoItem,
    val slots: List<List<Long>>,
    val lengthsMs: Map<Long, Long>
) {

    /**
     * Everything the shelf already knows about this work, in one line: the name read out of the file
     * names, the year, and every part's number and title.
     *
     * The catalogue's own title is not folded in here — it lands a moment later and is appended where
     * this is read — because this half is settled the moment the list is.
     */
    val searchText: String = buildString {
        append(group.displayTitle).append(' ')
        group.year?.let { append(it).append(' ') }
        group.parts.forEach { part ->
            part.episodeTitle?.let { append(it).append(' ') }
            val episode = part.episode
            if (episode != null) {
                val season = part.season
                if (season != null) append("S").append(season)
                append("E").append(episode).append(' ')
            }
        }
    }

    /**
     * The shelf's own lengths, with any the player has since read off a file folded in.
     *
     * Telegram reports no duration for a file sent as a document, so [lengthsMs] is empty for most of a
     * real shelf, and a row that cannot say how long a film is cannot say how much of it is left.
     */
    fun lengthsWithWatched(stored: Map<Long, Long>): Map<Long, Long> =
        slots.flatMap { it }.associateWith { id ->
            maxOf(lengthsMs[id] ?: 0L, stored[id] ?: 0L)
        }
}

/** Where the row stops being a reminder and starts being a second shelf. */
private const val CONTINUE_RAIL_LIMIT = 12

/** Wider than a grid card, because a rail has no column count to answer to. */
private const val CONTINUE_RAIL_CARD_WIDTH_DP = 150

/**
 * Minutes, rounded up.
 *
 * Forty seconds of a film left is still a film left, and a line that reads "0m left" says the opposite.
 */
private fun minutesCeil(durationMs: Long): Int = ((durationMs + 59_999L) / 60_000L).toInt()

/**
 * The shelf's own search box.
 *
 * It filters what is already on screen rather than asking anything, so the answer arrives as fast as a
 * thumb moves — which is the only reason a shelf of a thousand files is usable at all.
 */
@Composable
private fun ShelfSearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = OneUiPillSurface,
        border = BorderStroke(1.dp, OneUiTextSecondary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = OneUiTextSecondary,
                modifier = Modifier
                    .padding(start = 6.dp, end = 2.dp)
                    .size(19.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search your shelf",
                        fontSize = 14.sp,
                        color = OneUiTextSecondary
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = OneUiTextPrimary),
                    cursorBrush = SolidColor(OneUiTextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = OneUiTextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * The works with minutes left in them, most recently put down first.
 *
 * A rail rather than more grid cards because the shelf below is ordered by what a work is and this is
 * ordered by when it was last watched; the two only agree by accident.
 */
@Composable
private fun ShelfRail(
    entries: List<Pair<ShelfPlayable, PlayOrder.Progress>>,
    grid: CloudGridArgs,
    thumbnails: Map<Long, ByteArray>,
    progressByFile: Map<Int, CloudProgress>
) {
    Column {
        Text(
            text = "Continue watching",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OneUiTextPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
        // A scrolling Row, not a LazyRow: the shelf is already the only vertical list on the page and
        // a rail of at most a dozen posters is nothing to defer.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            entries.forEach { (playable, progress) ->
                key(playable.group.key) {
                    Box(modifier = Modifier.width(CONTINUE_RAIL_CARD_WIDTH_DP.dp)) {
                        ShelfRailCard(playable, progress, grid, thumbnails, progressByFile)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ShelfRailCard(
    playable: ShelfPlayable,
    progress: PlayOrder.Progress,
    grid: CloudGridArgs,
    thumbnails: Map<Long, ByteArray>,
    progressByFile: Map<Int, CloudProgress>
) {
    val representative = playable.representative
    val card = grid.prefsStore.mediaCards[representative.stableId]
    val bytes = thumbnails[representative.stableId]
    val inlineThumb = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val lengthMs = progress.lengthMs
    val watchFraction = if (lengthMs > 0L) {
        (progress.resumeMs / lengthMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    // A poster that says "2016" under a half-watched film is answering a question the row has already
    // answered by existing. Where the part is known by number, the line names it: a show left off at
    // its fourth episode is not resumed by its first.
    val part = playable.group.parts.firstOrNull { it.id == progress.partId }
    val where = when {
        part?.episode == null -> ""
        part.season != null -> "S${part.season}E${part.episode} · "
        else -> "E${part.episode} · "
    }
    val shown = playable.group.toShelfCard(representative, card).let {
        val left = progress.remainingMs > 0L
        val minutes = minutesCeil(if (left) progress.remainingMs else progress.resumeMs)
        it.copy(folderName = "$where${formatRuntime(minutes)}${if (left) " left" else " in"}")
    }
    VideoFileCard(
        video = shown,
        // The same work is on screen twice, and a shared-element key may only be held by one of them,
        // so the rail never morphs: the grid card underneath keeps that.
        sharedKeyPrefix = "continue_${representative.chatId}",
        isMorphTarget = false,
        watchProgressFraction = watchFraction,
        isFavorite = grid.prefsStore.favoriteIds.contains(representative.stableId),
        sharedTransitionScope = grid.sharedTransitionScope,
        animatedVisibilityScope = grid.playerAnimatedVisibilityScope,
        inlineThumbnail = inlineThumb,
        posterUrl = card?.posterUrl,
        downloadFraction = progressByFile[representative.fileId]?.fraction,
        onClick = {
            val file = playable.parts.firstOrNull { it.stableId == progress.partId }
                ?: return@VideoFileCard
            // The row names the copy that was watched, but every copy of that part is stamped with the
            // same minute, so whichever of them the play button then picks still opens where it left off.
            val deepest = PlayOrder.positionByCopy(playable.slots, grid.prefsStore.resumePositions)[file.stableId]
                ?: 0L
            grid.prefsStore.rememberResumePosition(file.stableId, deepest)
            grid.onShelfPlay(file, playable.parts, playable.slots, card)
        },
        onLongClick = {}
    )
}

/**
 * A card for one work, in the shape the grid already draws.
 *
 * The line under a film is the year the catalogue gave it. Under a show it is how many episodes the
 * shelf holds, because a series card that says "2019" promises a film, and the archive of 900 files
 * behind it is not that.
 */
private fun TitleGroup.toShelfCard(
    representative: CloudVideoItem,
    card: MediaCard?
): VideoItem = VideoItem(
    id = representative.stableId,
    uri = Uri.EMPTY,
    title = card?.title ?: displayTitle,
    bucketId = "cloud_shelf",
    folderName = if (kind == TitleKind.SERIES) {
        if (parts.size == 1) "1 episode" else "${parts.size} episodes"
    } else {
        card?.subtitle ?: year?.toString() ?: representative.sizeFormatted
    },
    durationMs = representative.durationMs,
    durationFormatted = if (kind == TitleKind.SERIES) "${parts.size} eps" else representative.sizeFormatted,
    sizeBytes = sizeBytes,
    dateAddedSec = 0L
)

/** Ask the catalogues what these files are, once each, while the screen holding them is on screen. */
@Composable
private fun TitleSearchPass(videos: List<CloudVideoItem>, prefsStore: ArcPreferencesStore) {
    LaunchedEffect(videos) {
        if (videos.isEmpty()) return@LaunchedEffect
        MediaLibrary.search(
            requests = videos
                // A card from before wordmarks were fetched still needs the second half of the lookup,
                // and only that half: the name it carries is settled.
                .filter {
                    val card = prefsStore.mediaCards[it.stableId]
                    card == null || (card.source == "tmdb" && !card.logoKnown)
                }
                .map { LookupRequest(it.stableId, it.title) },
            apiKey = BuildConfig.TMDB_API_KEY,
            onFound = { id, card -> prefsStore.rememberMediaCard(id, card) }
        )
    }
}

/**
 * One shelf card opened as a page: the work, every file of it the shelf holds, and what the catalogue
 * said about it.
 *
 * The card carries its files along because the page has to answer two unrelated questions at once —
 * "what is this" from the catalogue, and "what can actually be played" from Telegram — and the second
 * answer is never as wide as the first. A show whose eight episodes are all on the shelf has one
 * playable file per catalogue row; a film uploaded by three groups has three rows and one page.
 */
private class ShelfTitle(
    val group: TitleGroup,
    val parts: List<CloudVideoItem>,
    val representative: CloudVideoItem,
    val card: MediaCard?
) {
    val name: String = card?.title?.nullIfBlank() ?: group.displayTitle
    val isSeries: Boolean = card?.isSeries ?: (group.kind == TitleKind.SERIES)
    val providerId: Int? = card?.providerId
    val posterUrl: String? = card?.posterUrl
    val wordmarkUrl: String? = card?.logoUrl

    /** Watch order, as the grouping left it. */
    val partIds: List<Long> = group.parts.map { it.id }

    /**
     * The files of one part, gathered into the slot they are copies of, in watch order, best copy first.
     *
     * A slot is one episode of one season, or a whole film. The page needs the grouping rather than a
     * flat list because "play the best one" means the best copy *of the part about to be played*, and
     * that question cannot be answered without looking at every copy's position at once.
     */
    val copySlots: List<List<Long>> = group.parts
        .groupBy { (it.season ?: 0) to (it.episode ?: 0) }
        .values
        .map { slot -> slot.map { it.id } }

    /** How long each file runs, so an episode watched through is passed over rather than offered again. */
    val lengths: Map<Long, Long> = parts.associate { it.stableId to it.durationMs }

    /**
     * Season, then episode number, then every file the shelf holds for it, best copy first.
     *
     * The whole pile is kept rather than the top of it because a second copy is not a second episode:
     * a show carried by two groups arrives with two files per number, and the row that hides one of them
     * leaves the other to be read as an episode of its own.
     */
    val filesByEpisode: Map<Int, Map<Int, List<CloudVideoItem>>> = run {
        val byId = parts.associateBy { it.stableId }
        group.parts.mapNotNull { part ->
            val season = part.season ?: return@mapNotNull null
            val number = part.episode ?: return@mapNotNull null
            val file = byId[part.id] ?: return@mapNotNull null
            Triple(season, number, file)
        }.groupBy { it.first }.mapValues { row ->
            // Parts arrive biggest first, so each number's first file is the best copy of it.
            row.value.groupBy { it.second }.mapValues { copy -> copy.value.map { it.third } }
        }
    }

    /**
     * The files named by their number in the whole run rather than by a season of it, which is how a
     * long show reaches a shelf. Which season one of these belongs to is not in the file name, so it
     * is answered from the catalogue's season lengths rather than guessed at here.
     */
    val filesByAbsolute: Map<Int, List<CloudVideoItem>> = run {
        val byId = parts.associateBy { it.stableId }
        group.parts.mapNotNull { part ->
            if (part.season != null) return@mapNotNull null
            val number = part.episode ?: return@mapNotNull null
            val file = byId[part.id] ?: return@mapNotNull null
            number to file
        }.groupBy { it.first }.mapValues { row -> row.value.map { it.second } }
    }
}

/** How long a closed player is given to let go of its file before the bytes are handed back. */
private const val PLAYER_RELEASE_SETTLE_MS = 1_500L

private const val INFO_HERO_HEIGHT_DP = 300
private const val INFO_HERO_COLLAPSE_DP = 210

/**
 * The page a shelf poster opens.
 *
 * It scrolls as one column with the artwork drifting behind it at 0.6x, because the reference's hero
 * is not a header that disappears — the picture slides away and the film's own lettering goes with it,
 * leaving the plain title in the bar. Nothing here is a second copy of the grid's card: the page is
 * about the work, and the grid is about finding it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CloudTitleInfoPage(
    title: ShelfTitle,
    prefsStore: ArcPreferencesStore,
    statusText: String?,
    isResolving: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
    onPlay: (CloudVideoItem, List<CloudVideoItem>, List<List<Long>>) -> Unit,
    onRemove: (ShelfTitle) -> Unit
) {
    val detail = TitleDetailFetch(title.providerId, title.isSeries, prefsStore)
    val scrollState = rememberScrollState()
    val collapsePx = with(LocalDensity.current) { INFO_HERO_COLLAPSE_DP.dp.toPx() }
    val lifted = (scrollState.value / collapsePx).coerceIn(0f, 1f)
    val barOpaque = lifted > 0.55f

    val backdrop = rememberCachedArt(detail?.backdropUrl ?: title.posterUrl, 1_100)
    val wordmark = rememberCachedArt(title.wordmarkUrl, PosterCache.WORDMARK_WIDTH_PX)

    val resumeByPart = title.partIds.associateWith { prefsStore.resumePositions[it] ?: 0L }
    // Twenty minutes spent on one copy are twenty minutes into the part, so every copy of it shows and
    // opens at the same place. The player only ever reads the file it was handed.
    val resumeByCopy = PlayOrder.positionByCopy(title.copySlots, resumeByPart)
    val playChoice = PlayOrder.nextCopy(
        slots = title.copySlots,
        resumeMs = resumeByCopy,
        lengthsMs = title.lengths,
        lastPlayedMs = prefsStore.cloudWatchedAt
    )
    val playFile = title.parts.firstOrNull { it.stableId == playChoice?.partId } ?: title.representative
    val playLabel = if ((playChoice?.resumeMs ?: 0L) > 0L) "Resume" else "Play"

    val playCopy: (CloudVideoItem) -> Unit = { file ->
        prefsStore.rememberResumePosition(file.stableId, resumeByCopy[file.stableId] ?: 0L)
        onPlay(file, title.parts, title.copySlots)
    }

    // Every file the shelf holds, filed under the season and number it covers. The run-numbered files
    // can only be placed once the catalogue's season lengths are known, so this waits on `detail` and
    // simply leaves them out until it arrives.
    val filesBySeason: Map<Int, Map<Int, List<CloudVideoItem>>> = remember(title, detail) {
        val filed = title.filesByEpisode.toMutableMap()
        detail?.seasons?.let { seasons ->
            title.filesByAbsolute.forEach { (number, files) ->
                val placed = AbsoluteSeasons.seasonFor(number, seasons) ?: return@forEach
                val merged = (filed[placed]?.get(number).orEmpty() + files).distinctBy { it.stableId }
                filed[placed] = (filed[placed] ?: emptyMap()) + (number to merged)
            }
        }
        filed
    }

    // The season a show's page opens on. It has to be a season the shelf really holds a file for:
    // asking the grouping alone can name a season whose episodes never joined to a file, which leaves
    // the page opening on a run of "Not on your shelf" rows while the one watchable episode sits
    // somewhere else. So the target season only wins when it is one of the mapped seasons.
    val shelfSeasons = filesBySeason.keys
    val playPart = title.group.parts.firstOrNull { it.id == playFile.stableId }
    val playSeason = playPart?.season
        ?: playPart?.episode?.let { AbsoluteSeasons.seasonFor(it, detail?.seasons.orEmpty()) }
    var season by remember(title) {
        mutableStateOf(
            playSeason?.takeIf { it in shelfSeasons }
                ?: shelfSeasons.minOrNull()
                ?: detail?.seasons?.firstOrNull { it.number > 0 }?.number
                ?: 1
        )
    }
    // A part numbered by its place in the whole run only lands on a season once those lengths are
    // known, and they arrive a moment after this page's first frame. So it is corrected when they
    // show up, and given up the moment the viewer picks a season themselves.
    var seasonChosen by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title, detail, seasonChosen) {
        val seasons = detail?.seasons ?: return@LaunchedEffect
        if (seasonChosen || playSeason == null || playSeason == season) return@LaunchedEffect
        if (playSeason in shelfSeasons) season = playSeason
    }
    val cataloguedEpisodes = SeasonEpisodeFetch(title.providerId, season, prefsStore)

    // The episode a copies sheet was opened for. It holds the row's own files rather than a number to
    // look up, because switching season behind the sheet would change what that number means.
    var copiesSheet by remember(title) { mutableStateOf<EpisodeCopiesSheet?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(OneUiBackground)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(INFO_HERO_HEIGHT_DP.dp)
                    .graphicsLayer { clip = true }
            ) {
                val art = backdrop
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Oversized and pulled up so the drift never uncovers an edge: the picture
                        // travels at 0.6x the column, which is what keeps the hero from reading dead.
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((INFO_HERO_HEIGHT_DP + 180).dp)
                            .offset(y = (-90).dp)
                            .graphicsLayer { translationY = scrollState.value * 0.4f }
                    )
                } else {
                    PlaceholderThumbnailBox(modifier = Modifier.fillMaxSize())
                }

                Spacer(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent)
                            )
                        )
                )
                // Wordmarks are white artwork, so they need a dark band to sit on: without it a
                // light backdrop or the page colour behind it swallows the logo completely.
                Spacer(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Black.copy(alpha = 0.42f),
                                1f to Color.Black.copy(alpha = 0.6f)
                            )
                        )
                )
                Spacer(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to OneUiBackground
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, end = 18.dp, bottom = 96.dp)
                ) {
                    val logo = wordmark
                    if (logo != null) {
                        Image(
                            bitmap = logo.asImageBitmap(),
                            contentDescription = title.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .heightIn(max = 82.dp)
                        )
                    } else {
                        Text(
                            text = title.name,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                val genreLine = detail?.genres?.joinToString("   ·   ")
                if (!genreLine.isNullOrBlank()) {
                    Text(
                        text = genreLine,
                        fontSize = 13.sp,
                        color = OneUiTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    val playSource = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .background(OneUiPillSurface, TopActionPillShape)
                            .jellyClick(
                                interactionSource = playSource,
                                indication = null,
                                scaleDownTo = 0.96f,
                                onClick = { playCopy(playFile) }
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = OneUiTextPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = OneUiTextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = playLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTextPrimary
                            )
                        }
                    }
                    MorphingMenu(
                        items = listOf(
                            MorphingMenuItem(
                                text = "Remove from shelf",
                                icon = Icons.Default.Delete,
                                isDestructive = true,
                                onClick = { onRemove(title) }
                            )
                        ),
                        buttonSize = 46.dp,
                        menuWidth = 210.dp
                    )
                }

                statusText?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = OneUiTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                InfoMetaRow(title, detail)

                if (title.providerId == null) {
                    Text(
                        text = "No catalogue entry — the name on this card came from the file itself.",
                        fontSize = 12.sp,
                        color = OneUiTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                detail?.let { info ->
                    info.directors.joinToString(", ").takeIf { it.isNotBlank() }?.let { names ->
                        InfoCreditLine(if (info.isSeries) "Creator" else "Director", names)
                    }
                    info.writers.joinToString(", ").takeIf { it.isNotBlank() }?.let { names ->
                        InfoCreditLine("Writer", names)
                    }

                    if (info.overview.isNotBlank()) {
                        InfoSynopsis(info.overview)
                    }

                    if (info.cast.isNotEmpty()) {
                        InfoSectionHeading("Cast")
                        InfoCastRail(info)
                    }

                    if (info.isSeries) {
                        if (info.seasons.isNotEmpty()) {
                            InfoSectionHeading("Seasons")
                            InfoSeasonRail(info, season) {
                                seasonChosen = true
                                season = it
                            }
                        }
                        InfoSectionHeading("Season $season")
                        InfoEpisodeList(
                            season = season,
                            episodes = cataloguedEpisodes,
                            title = title,
                            filesBySeason = filesBySeason,
                            onPlay = { file -> playCopy(file) },
                            onShowCopies = { episode -> copiesSheet = EpisodeCopiesSheet(season, episode) }
                        )
                    }
                }

                // Several copies of one film are one card, and the pile behind that card is the only place
                // where the difference between them shows. Telegram hands over an exact size and, for a video
                // sent as a video, an exact frame size; everything else a viewer chooses with — codec, where
                // it was ripped from, Dolby, burn-in subtitles — was typed into the file name.
                if (!title.isSeries && title.parts.size > 1) {
                    InfoSectionHeading("Copies")
                    InfoCopyList(title.parts, playFile.stableId, resumeByCopy) { file -> playCopy(file) }
                }
            }

            Spacer(modifier = Modifier.height(36.dp + bottomInset))
        }

        // The bar the hero becomes: a plain title fading up over the picture as it leaves.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(OneUiBackground.copy(alpha = lifted))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp + topInset)
                    .padding(top = topInset, start = 10.dp, end = 16.dp)
            ) {
                val backSource = remember { MutableInteractionSource() }
                IconButton(
                    interactionSource = backSource,
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .jelly(backSource, scaleDownTo = 0.88f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (barOpaque) OneUiTextPrimary else Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUiTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = ((lifted - 0.5f) / 0.5f).coerceIn(0f, 1f) }
                )
            }
        }

        // Every copy of one episode. An episode row can show only one size, so the pile behind it needs
        // its own surface — the same rows a film gets under "Copies", scoped to this episode's files.
        copiesSheet?.let { sheet ->
            ModalBottomSheet(
                onDismissRequest = { copiesSheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = OneUiBackground,
                contentColor = OneUiTextPrimary,
                scrimColor = Color.Black.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 26.dp)
                ) {
                    Text(
                        text = "Season ${sheet.season} · Episode ${sheet.episode.number}",
                        fontSize = 11.sp,
                        color = OneUiTextSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = sheet.episode.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCopyList(
                        files = sheet.episode.copies,
                        autoPlayId = sheet.episode.file?.stableId,
                        resumeByCopy = resumeByCopy
                    ) { file ->
                        copiesSheet = null
                        playCopy(file)
                    }
                }
            }
        }
    }
}

/** Year, certificate, length and score — the line under the buttons, in the order the reference gives it. */
@Composable
private fun InfoMetaRow(title: ShelfTitle, detail: TitleDetail?) {
    val year = detail?.startYear ?: title.group.year
    val yearLine = when {
        year == null -> ""
        detail?.endYear != null && detail.endYear != year -> "$year–${detail.endYear}"
        else -> year.toString()
    }
    val runtime = when {
        detail != null && detail.runtimeMinutes > 0 ->
            formatRuntime(detail.runtimeMinutes) + if (title.isSeries) " per episode" else ""
        title.representative.durationMs > 0L -> formatDuration(title.representative.durationMs)
        else -> ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        if (yearLine.isNotBlank()) {
            Text(text = yearLine, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OneUiTextPrimary)
        }
        detail?.maturity?.takeIf { it.isNotBlank() }?.let { rating ->
            Box(
                modifier = Modifier
                    .border(1.dp, OneUiTextSecondary.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(text = rating, fontSize = 11.sp, color = OneUiTextSecondary)
            }
        }
        if (runtime.isNotBlank()) {
            Text(text = runtime, fontSize = 13.sp, color = OneUiTextPrimary)
        }
        detail?.voteScore?.let { score ->
            Text(
                text = "★ " + String.format(Locale.US, "%.1f", score),
                fontSize = 13.sp,
                color = OneUiTextPrimary
            )
        }
    }

    Text(
        text = if (title.parts.size == 1) {
            "1 file on your shelf · ${formatFileSize(title.group.sizeBytes)}"
        } else {
            "${title.parts.size} files on your shelf · ${formatFileSize(title.group.sizeBytes)}"
        },
        fontSize = 12.sp,
        color = OneUiTextSecondary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun InfoCreditLine(label: String, names: String) {
    Row(modifier = Modifier.padding(top = 10.dp)) {
        Text(text = "$label  ", fontSize = 13.sp, color = OneUiTextSecondary)
        Text(text = names, fontSize = 13.sp, color = OneUiTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Three lines until asked for more, because a synopsis nobody can finish is worse than a short one. */
@Composable
private fun InfoSynopsis(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val canToggle = text.length > 210
    val source = remember { MutableInteractionSource() }
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = OneUiTextPrimary,
        maxLines = if (expanded || !canToggle) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = if (canToggle) {
            Modifier
                .padding(top = 12.dp)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    onClickLabel = if (expanded) "Less" else "More",
                    onClick = { expanded = !expanded }
                )
        } else {
            Modifier.padding(top = 12.dp)
        }
    )
}

@Composable
private fun InfoSectionHeading(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = OneUiTextPrimary,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun InfoCastRail(detail: TitleDetail) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        detail.cast.forEach { member ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(78.dp)
            ) {
                ArtSlot(
                    art = rememberCachedArt(member.photoUrl, 170),
                    contentDescription = member.name,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(OneUiTextSecondary.copy(alpha = 0.14f))
                )
                Text(
                    text = member.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = OneUiTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (member.role.isNotBlank()) {
                    Text(
                        text = member.role,
                        fontSize = 10.sp,
                        color = OneUiTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSeasonRail(detail: TitleDetail, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        detail.seasons.forEach { seasonCard ->
            val isSelected = seasonCard.number == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArtSlot(
                    art = rememberCachedArt(seasonCard.posterUrl, 240),
                    contentDescription = seasonCard.title,
                    modifier = Modifier
                        .width(84.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = OneUiTextPrimary,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .jellyClick(interactionSource = null, indication = null) { onSelect(seasonCard.number) }
                )
                Text(
                    text = "Season ${seasonCard.number}",
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) OneUiTextPrimary else OneUiTextSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** One catalogue episode paired with every shelf file that can play it, best copy first. */
private class ShelfEpisode(
    val number: Int,
    val name: String,
    val overview: String,
    val stillUrl: String?,
    val runtimeMinutes: Int,
    val copies: List<CloudVideoItem>
) {
    /** What a tap on the row plays. */
    val file: CloudVideoItem? = copies.firstOrNull()
}

/** Which episode's copies a sheet was opened for, so the sheet can say so in its own heading. */
private class EpisodeCopiesSheet(
    val season: Int,
    val episode: ShelfEpisode
)

@Composable
private fun InfoEpisodeList(
    season: Int,
    episodes: List<EpisodeDetail>?,
    title: ShelfTitle,
    filesBySeason: Map<Int, Map<Int, List<CloudVideoItem>>>,
    onPlay: (CloudVideoItem) -> Unit,
    onShowCopies: (ShelfEpisode) -> Unit
) {
    if (episodes == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OneUiTextSecondary)
            Text(text = "Reading season $season…", fontSize = 12.sp, color = OneUiTextSecondary)
        }
        return
    }

    val byNumber = filesBySeason[season].orEmpty()
    val catalogued = episodes.map { episode ->
        ShelfEpisode(
            number = episode.number,
            name = episode.title.ifBlank { "Episode ${episode.number}" },
            overview = episode.overview,
            stillUrl = episode.stillUrl,
            runtimeMinutes = episode.runtimeMinutes,
            copies = byNumber[episode.number].orEmpty()
        )
    }
    // A file belongs to whichever season the catalogue placed it in, not to every season being looked
    // at, and a second copy of a placed episode is placed too — which is why this reads every copy
    // rather than only the one each row plays. Anything left over is the shelf's own episode row.
    val placed = filesBySeason.values.flatMap { it.values }.flatten().map { it.stableId }.toSet()
    val unlisted = title.group.parts.mapNotNull { part ->
        // A file the catalogue has no row for is still a file the shelf can play.
        val number = part.episode ?: return@mapNotNull null
        if (part.id in placed) return@mapNotNull null
        if (part.season != null && part.season != season) return@mapNotNull null
        val file = title.parts.firstOrNull { it.stableId == part.id } ?: return@mapNotNull null
        ShelfEpisode(number, file.title, "", null, 0, listOf(file))
    }
    val rows = (catalogued + unlisted).sortedBy { it.number }

    if (rows.isEmpty()) {
        Text(
            text = "The catalogue has no episodes listed for season $season.",
            fontSize = 12.sp,
            color = OneUiTextSecondary
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            InfoEpisodeRow(row, onPlay, onShowCopies)
        }
    }
}

@Composable
private fun InfoEpisodeRow(
    row: ShelfEpisode,
    onPlay: (CloudVideoItem) -> Unit,
    onShowCopies: (ShelfEpisode) -> Unit
) {
    val file = row.file
    val hasCopies = row.copies.size > 1
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = if (file == null) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .jellyCombinedClickable(
                    interactionSource = null,
                    indication = null,
                    onLongClick = if (hasCopies) {
                        { onShowCopies(row) }
                    } else {
                        null
                    },
                    onLongClickLabel = if (hasCopies) "Show copies" else null,
                    onClick = { onPlay(file) }
                )
        }
    ) {
        ArtSlot(
            art = rememberCachedArt(row.stillUrl, 340),
            contentDescription = row.name,
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(OneUiTextSecondary.copy(alpha = 0.14f))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (file == null) OneUiTextSecondary else OneUiTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (row.overview.isNotBlank()) {
                Text(
                    text = row.overview,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = OneUiTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = when {
                        file != null && row.runtimeMinutes > 0 -> "${row.runtimeMinutes}m · ${file.sizeFormatted}"
                        file != null -> file.sizeFormatted
                        else -> "Not on your shelf"
                    },
                    fontSize = 11.sp,
                    color = OneUiTextSecondary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // A long press does the same, but nothing on a touch screen says so — and the only other
                // clue that a second copy exists is a size line that quietly names the biggest one.
                if (hasCopies) {
                    Box(
                        modifier = Modifier
                            .jellyClick(interactionSource = null, indication = null) { onShowCopies(row) }
                            .border(1.dp, OneUiTextSecondary.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${row.copies.size} copies",
                            fontSize = 10.sp,
                            color = OneUiTextSecondary
                        )
                    }
                }
            }
        }
        if (file != null) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = OneUiTextPrimary,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .size(20.dp)
            )
        }
    }
}

/**
 * Every copy of one thing, best first — the order the grouping leaves them in, which is also the order
 * the play button works through, so the row a viewer chooses and the row the button chooses are the same
 * row unless the viewer chooses a different one.
 */
@Composable
private fun InfoCopyList(
    files: List<CloudVideoItem>,
    autoPlayId: Long?,
    resumeByCopy: Map<Long, Long>,
    onPlay: (CloudVideoItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        files.forEach { file ->
            InfoCopyRow(
                file = file,
                isAutoPlay = file.stableId == autoPlayId,
                resumeMs = resumeByCopy[file.stableId] ?: 0L,
                onPlay = onPlay
            )
        }
    }
}

/**
 * One copy, described by whatever its own name states.
 *
 * The box on the left carries the resolution rather than artwork because three copies of one film share
 * one poster and one backdrop, and the thing that differs between them is the picture. Where no name
 * states one, the exact size stands in — the only quality figure Telegram cannot get wrong.
 */
@Composable
private fun InfoCopyRow(
    file: CloudVideoItem,
    isAutoPlay: Boolean,
    resumeMs: Long,
    onPlay: (CloudVideoItem) -> Unit
) {
    val read = remember(file.title, file.height) { ReleaseInfo.parse(file.title, file.height) }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .jellyClick(interactionSource = null, indication = null) { onPlay(file) }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(OneUiTextSecondary.copy(alpha = 0.14f))
        ) {
            Text(
                text = read.quality ?: file.sizeFormatted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OneUiTextPrimary,
                maxLines = 1
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = read.tags.joinToString("   ·   ").ifBlank { copyLabel(file.title) },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = OneUiTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val footer = listOfNotNull(
                file.sizeFormatted.takeIf { read.quality != null },
                formatDuration(file.durationMs).takeIf { file.durationMs > 0L },
                read.group?.let { "@$it" },
                if (resumeMs > 0L) "Resume ${formatDuration(resumeMs)}" else null
            ).joinToString("   ·   ")
            if (footer.isNotBlank() || isAutoPlay) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(top = 5.dp)
                ) {
                    Text(
                        text = footer,
                        fontSize = 11.sp,
                        color = OneUiTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Beside the arrow rather than in front of the line, so every row's size starts at the
                    // same edge and the words sit next to the thing they explain.
                    if (isAutoPlay) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, OneUiTextSecondary.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(text = "Play opens this", fontSize = 10.sp, color = OneUiTextSecondary)
                        }
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = OneUiTextPrimary,
            modifier = Modifier
                .padding(top = 20.dp)
                .size(20.dp)
        )
    }
}

/** The name as typed, minus its container suffix, for a copy whose tail said nothing worth a badge. */
private fun copyLabel(fileName: String): String {
    val bare = fileName.substringAfterLast('\\').substringAfterLast('/')
    val dot = bare.lastIndexOf('.')
    if (dot <= 0) return bare
    val suffix = bare.substring(dot + 1)
    return if (suffix.length in 2..4 && suffix.all { it.isLetterOrDigit() }) bare.substring(0, dot) else bare
}

/** Artwork from the catalogue, drawn into whatever box the caller gives it. */
@Composable
private fun ArtSlot(art: Bitmap?, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = art
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Spacer(modifier = modifier)
    }
}

/** One catalogue image, decoded down before it reaches the screen. Null until it lands, or if it never does. */
@Composable
private fun rememberCachedArt(url: String?, targetWidthPx: Int): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = url?.let {
            runCatching { PosterCache.bitmap(context, it, targetWidthPx) }.getOrNull()
        }
    }
    return bitmap
}

/**
 * The page's own half of the catalogue: cast, seasons and synopsis, asked once per title.
 *
 * Not queued through [MediaLibrary] the way a grid's artwork is, because exactly one page is ever
 * open and it is open because somebody asked for it.
 */
@Composable
private fun TitleDetailFetch(providerId: Int?, isSeries: Boolean, prefsStore: ArcPreferencesStore): TitleDetail? {
    LaunchedEffect(providerId, isSeries) {
        val id = providerId ?: return@LaunchedEffect
        if (prefsStore.titleDetails.containsKey(titleDetailKey(id, isSeries))) return@LaunchedEffect
        val fetched = withContext(Dispatchers.IO) {
            runCatching { TmdbDetails.title(BuildConfig.TMDB_API_KEY, id, isSeries) }.getOrNull()
        }
        if (fetched != null) prefsStore.rememberTitleDetail(id, isSeries, fetched)
    }
    return prefsStore.titleDetail(providerId, isSeries)
}

/** One season's episodes, asked when the page shows that season and kept only for the session. */
@Composable
private fun SeasonEpisodeFetch(showId: Int?, season: Int, prefsStore: ArcPreferencesStore): List<EpisodeDetail>? {
    val id = showId ?: return emptyList()
    LaunchedEffect(id, season) {
        if (prefsStore.episodesOf(id, season) != null) return@LaunchedEffect
        val fetched = withContext(Dispatchers.IO) {
            runCatching { TmdbDetails.episodes(BuildConfig.TMDB_API_KEY, id, season) }.getOrNull()
        }
        if (fetched != null) prefsStore.rememberEpisodesOf(id, season, fetched)
    }
    return prefsStore.episodesOf(id, season)
}

/** The one action a Cloud screen offers outside its own list: go and get more files. */
@Composable
private fun CloudPill(text: String, onClick: () -> Unit) {
    val source = remember(text) { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(OneUiPillSurface)
            .border(
                width = 1.dp,
                color = OneUiTextSecondary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .jelly(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OneUiTextPrimary)
    }
}

@Composable
private fun CloudEmptyRow(
    text: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardCornerShape)
            .background(OneUiPillSurface)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = OneUiTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun CloudSubmitButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onClick, enabled = enabled) {
            Text(label)
        }
        if (busy) {
            Spacer(modifier = Modifier.width(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * The whole sign-in flow, driven only by [CloudAuthState.stage].
 *
 * Nothing here decides what comes next - the reducer in [CloudAuthMachine] does, from TDLib's own
 * authorization updates. This composable only renders whichever prompt the stage names, so a state
 * Telegram has not reached can never be offered to the user.
 */
@Composable
private fun CloudAuthPanel(
    state: CloudAuthState,
    busy: Boolean,
    repository: CloudRepository,
    prefsStore: ArcPreferencesStore
) {
    var firstField by remember(state.stage) { mutableStateOf("") }
    var secondField by remember(state.stage) { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardCornerShape,
        color = OneUiPillSurface
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp)) {
            Text(
                text = cloudAuthHeading(state.stage),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = OneUiTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = cloudAuthBody(state.stage, state),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = OneUiTextSecondary
            )

            state.error?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (state.stage) {
                CloudAuthStage.MISSING_CREDENTIALS -> {
                    OutlinedTextField(
                        value = firstField,
                        onValueChange = { firstField = it },
                        label = { Text("api_id") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secondField,
                        onValueChange = { secondField = it },
                        label = { Text("api_hash") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CloudSubmitButton(
                        label = "Save and connect",
                        enabled = firstField.toIntOrNull() != null && secondField.isNotBlank(),
                        busy = false,
                        onClick = {
                            prefsStore.updateTelegramCredentials(
                                firstField.trim().toIntOrNull() ?: 0,
                                secondField
                            )
                        }
                    )
                }

                CloudAuthStage.WAITING_FOR_PHONE_NUMBER -> {
                    OutlinedTextField(
                        value = firstField,
                        onValueChange = { firstField = it },
                        label = { Text("Phone number with country code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CloudSubmitButton(
                        label = "Send code",
                        enabled = firstField.isNotBlank() && !busy,
                        busy = busy,
                        onClick = { repository.submitPhoneNumber(firstField) }
                    )
                }

                CloudAuthStage.WAITING_FOR_CODE -> {
                    OutlinedTextField(
                        value = firstField,
                        onValueChange = { firstField = it },
                        label = { Text(cloudCodeLabel(state.codeHint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (state.codeHint?.digitsOnly == true) {
                                KeyboardType.NumberPassword
                            } else {
                                KeyboardType.Ascii
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CloudSubmitButton(
                        label = "Sign in",
                        enabled = firstField.length >= 5 && !busy,
                        busy = busy,
                        onClick = { repository.submitCode(firstField.trim()) }
                    )
                }

                CloudAuthStage.WAITING_FOR_PASSWORD -> {
                    OutlinedTextField(
                        value = firstField,
                        onValueChange = { firstField = it },
                        label = { Text("Two-step verification password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CloudSubmitButton(
                        label = "Unlock",
                        enabled = firstField.isNotBlank() && !busy,
                        busy = busy,
                        onClick = { repository.submitPassword(firstField) }
                    )
                }

                CloudAuthStage.WAITING_FOR_REGISTRATION -> {
                    OutlinedTextField(
                        value = firstField,
                        onValueChange = { firstField = it },
                        label = { Text("First name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secondField,
                        onValueChange = { secondField = it },
                        label = { Text("Last name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CloudSubmitButton(
                        label = "Create account",
                        enabled = firstField.isNotBlank() && !busy,
                        busy = busy,
                        onClick = { repository.submitRegistration(firstField, secondField) }
                    )
                }

                CloudAuthStage.FAILED, CloudAuthStage.CLOSED -> CloudSubmitButton(
                    label = "Enter a number",
                    enabled = !busy,
                    busy = busy,
                    onClick = { repository.resetToPhoneNumber() }
                )

                CloudAuthStage.READY_TO_START -> CloudSubmitButton(
                    label = "Connect to Telegram",
                    enabled = !busy,
                    busy = busy,
                    onClick = { repository.begin() }
                )

                else -> CloudSubmitButton(
                    label = "Working…",
                    enabled = false,
                    busy = true,
                    onClick = {}
                )
            }
        }
    }
}

private fun cloudAuthHeading(stage: CloudAuthStage): String = when (stage) {
    CloudAuthStage.MISSING_CREDENTIALS -> "Telegram keys"
    CloudAuthStage.READY_TO_START,
    CloudAuthStage.WAITING_FOR_PARAMETERS,
    CloudAuthStage.REQUESTING_CODE,
    CloudAuthStage.CHECKING_CODE -> "Signing in"
    CloudAuthStage.WAITING_FOR_PHONE_NUMBER -> "Your number"
    CloudAuthStage.WAITING_FOR_CODE -> "Confirmation code"
    CloudAuthStage.WAITING_FOR_PASSWORD -> "Password required"
    CloudAuthStage.WAITING_FOR_REGISTRATION -> "New account"
    CloudAuthStage.AUTHORIZED -> "Signed in"
    CloudAuthStage.CLOSED -> "Session closed"
    CloudAuthStage.FAILED -> "Sign-in failed"
}

private fun cloudAuthBody(stage: CloudAuthStage, state: CloudAuthState): String = when (stage) {
    CloudAuthStage.MISSING_CREDENTIALS ->
        "Arc Player needs an api_id and api_hash from my.telegram.org before it can talk to " +
            "Telegram. They are stored on this device only."
    CloudAuthStage.READY_TO_START ->
        "Nothing has been sent yet. Connecting starts a Telegram session for this device."
    CloudAuthStage.WAITING_FOR_PARAMETERS ->
        "Starting TDLib and registering this app with Telegram…"
    CloudAuthStage.WAITING_FOR_PHONE_NUMBER ->
        "Enter the number that belongs to your Telegram account, including the country code."
    CloudAuthStage.REQUESTING_CODE ->
        "Asking Telegram for a code for ${state.lastPhoneNumber}…"
    CloudAuthStage.WAITING_FOR_CODE ->
        "Telegram sent a code to ${state.lastPhoneNumber}. ${state.codeHint?.codeLength
            ?.takeIf { it > 0 }
            ?.let { "It is $it characters long." } ?: ""}"
    CloudAuthStage.CHECKING_CODE ->
        "Waiting for Telegram to confirm…"
    CloudAuthStage.WAITING_FOR_PASSWORD ->
        "This account has two-step verification turned on."
    CloudAuthStage.WAITING_FOR_REGISTRATION ->
        "No Telegram account uses this number yet, so it will be registered as a new one."
    CloudAuthStage.AUTHORIZED -> ""
    CloudAuthStage.CLOSED ->
        "Telegram closed this session."
    CloudAuthStage.FAILED ->
        "Telegram refused the request. If this keeps happening, restart Arc Player - TDLib clients " +
            "cannot be rebuilt inside a running process."
}

private fun cloudCodeLabel(hint: CodeHint?): String = when {
    hint == null -> "Code"
    hint.codeLength > 0 -> "${hint.codeLength}-digit code"
    hint.digitsOnly -> "Code"
    else -> "Code words"
}

@Composable
private fun MenuTabContent(
    snapshot: MediaLibrarySnapshot,
    prefsStore: ArcPreferencesStore,
    gridState: LazyGridState,
    topPadding: Dp,
    bottomPadding: Dp
) {
    val totalBytes = remember(snapshot.videos) { snapshot.videos.sumOf { it.sizeBytes } }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(1),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 6.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "menu_library_overview") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Library Overview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${snapshot.videos.size} Videos • ${snapshot.folders.size} Folders • ${formatFileSize(totalBytes)}",
                        fontSize = 13.5.sp,
                        color = OneUiTextSecondary
                    )
                }
            }
        }

        item(key = "menu_playback_settings") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Playback & One UI Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Audio Playback", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Keep audio playing when switching apps or locking screen", fontSize = 12.sp, color = OneUiTextSecondary)
                        }
                        Switch(
                            checked = prefsStore.backgroundAudioEnabled,
                            onCheckedChange = { prefsStore.updateBackgroundAudio(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Picture-in-Picture (PiP)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Enter floating window when swiping home while playing", fontSize = 12.sp, color = OneUiTextSecondary)
                        }
                        Switch(
                            checked = prefsStore.autoPipEnabled,
                            onCheckedChange = { prefsStore.updateAutoPip(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grid Columns", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 4).forEach { cols ->
                                val selected = prefsStore.gridColumns == cols
                                Surface(
                                    onClick = { prefsStore.updateGridColumns(cols) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) OneUiTextPrimary else OneUiPillSelected
                                ) {
                                    Text(
                                        text = "$cols",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else OneUiTextPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watch History (${prefsStore.watchHistoryIds.size} items)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(onClick = { prefsStore.clearWatchHistory() }) {
                            Text("Clear History", color = OneUiBadgeOrange)
                        }
                    }
                }
            }
        }

        item(key = "menu_data_sources") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Data Sources",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary
                    )
                    Text(
                        text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        fontSize = 12.sp,
                        color = OneUiTextSecondary
                    )
                    Text(
                        text = "Series artwork comes from AniList, and Wikipedia fills in titles the others miss.",
                        fontSize = 12.sp,
                        color = OneUiTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun OneUiCollapsibleScaffold(
    headerState: OneUiHeaderState,
    statusBarTopPadding: Dp,
    actionBarHeight: Dp,
    expandedHeader: @Composable () -> Unit,
    actionBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Layout(
        contents = listOf(content, expandedHeader, actionBar)
    ) { (contentMeasurables, headerMeasurables, actionBarMeasurables), constraints ->
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val statusBarPx = statusBarTopPadding.roundToPx()
        val actionBarPx = actionBarHeight.roundToPx()
        val expandedHeaderPx = headerState.maxOffsetPx.roundToInt()

        val contentPlaceable = contentMeasurables.first().measure(
            Constraints.fixed(maxWidth, maxHeight)
        )
        val headerPlaceable = headerMeasurables.first().measure(
            Constraints.fixed(maxWidth, expandedHeaderPx)
        )
        val actionBarPlaceable = actionBarMeasurables.first().measure(
            Constraints.fixed(maxWidth, actionBarPx)
        )

        layout(maxWidth, maxHeight) {
            val currentOffsetPx = headerState.offsetPx.roundToInt()
            contentPlaceable.placeRelative(
                x = 0,
                y = currentOffsetPx
            )
            headerPlaceable.placeRelative(
                x = 0,
                y = statusBarPx + currentOffsetPx - expandedHeaderPx
            )
            actionBarPlaceable.placeRelative(
                x = 0,
                y = statusBarPx + currentOffsetPx
            )
        }
    }
}

@Composable
private fun OneUiScrollToTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = OneUiPillSurface,
        modifier = modifier
            .size(40.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.16f)
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val w = size.width
                val h = size.height
                val strokeWidth = w * 0.115f
                drawLine(
                    color = OneUiTextPrimary,
                    start = Offset(w * 0.50f, h * 0.82f),
                    end = Offset(w * 0.50f, h * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = OneUiTextPrimary,
                    start = Offset(w * 0.22f, h * 0.46f),
                    end = Offset(w * 0.50f, h * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = OneUiTextPrimary,
                    start = Offset(w * 0.78f, h * 0.46f),
                    end = Offset(w * 0.50f, h * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FolderGridContent(
    folders: List<VideoFolderItem>,
    columns: Int,
    gridState: LazyGridState,
    topPadding: Dp,
    bottomPadding: Dp,
    morphingFolderBucketId: String?,
    sharedTransitionScope: SharedTransitionScope?,
    folderAnimatedVisibilityScope: AnimatedVisibilityScope?,
    onFolderClick: (VideoFolderItem) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (folders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching folders",
                        fontSize = 15.sp,
                        color = OneUiTextSecondary
                    )
                }
            }
        } else {
            items(
                items = folders,
                key = { it.bucketId },
                contentType = { "video_folder_item" }
            ) { folder ->
                VideoFolderCard(
                    folder = folder,
                    isMorphTarget = folder.bucketId == morphingFolderBucketId,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = folderAnimatedVisibilityScope,
                    onClick = { onFolderClick(folder) }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun VideoGridContent(
    videos: List<VideoItem>,
    columns: Int,
    gridState: LazyGridState,
    topPadding: Dp,
    bottomPadding: Dp,
    sharedKeyPrefix: String,
    morphingVideoKey: String?,
    resumePositions: Map<Long, Long>,
    favoriteIds: Set<Long>,
    sharedTransitionScope: SharedTransitionScope,
    playerAnimatedVisibilityScope: AnimatedVisibilityScope,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (videos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No videos in this view",
                        fontSize = 15.sp,
                        color = OneUiTextSecondary
                    )
                }
            }
        } else {
            items(
                items = videos,
                key = { it.id },
                contentType = { "video_file_item" }
            ) { video ->
                val itemKey = "${sharedKeyPrefix}_${video.id}"
                val savedPos = resumePositions[video.id] ?: 0L
                val progressFraction = if (savedPos > 0L && video.durationMs > 0L) {
                    (savedPos.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                VideoFileCard(
                    video = video,
                    sharedKeyPrefix = sharedKeyPrefix,
                    isMorphTarget = itemKey == morphingVideoKey,
                    watchProgressFraction = progressFraction,
                    isFavorite = favoriteIds.contains(video.id),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = playerAnimatedVisibilityScope,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    }
}

// ============================================================================
// FULL-BLEED PROGRESSIVE BLUR FOLDER & VIDEO CARDS (ONE UI 7 STYLE)
// ============================================================================

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun VideoFolderCard(
    folder: VideoFolderItem,
    isMorphTarget: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FolderCardPressScale"
    )

    val sharedModifier = if (isMorphTarget && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "folder_container_${folder.bucketId}"
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 380f
                    )
                },
                clipInOverlayDuringTransition = OverlayClip(CardCornerShape)
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(sharedModifier)
            .graphicsLayer {
                clip = true
                shape = CardCornerShape
            }
            .background(OneUiPlaceholderGray)
            .border(
                width = 0.5.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = CardCornerShape
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        MediaThumbnailImage(
            uri = folder.coverVideoUri,
            contentDescription = folder.title,
            modifier = Modifier.fillMaxSize()
        )

        if (folder.isCameraFolder) {
            CameraBadgeOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = folder.title,
                    style = CardTitleTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (folder.hasRecentVideos) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(OneUiBadgeOrange)
                    )
                }
            }

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = folder.videoCount.toString(),
                style = CardCountTextStyle,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun VideoFileCard(
    video: VideoItem,
    sharedKeyPrefix: String,
    isMorphTarget: Boolean,
    watchProgressFraction: Float,
    isFavorite: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    inlineThumbnail: Bitmap? = null,
    posterUrl: String? = null,
    downloadFraction: Float? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "VideoCardPressScale"
    )

    val sharedHeroModifier = if (isMorphTarget) {
        val cornerRadius by animatedVisibilityScope.transition.animateDp(
            label = "HeroThumbnailCornerRadius"
        ) { state ->
            when (state) {
                androidx.compose.animation.EnterExitState.Visible -> 22.dp
                else -> 0.dp
            }
        }
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                state = rememberSharedContentState(
                    key = "video_hero_${sharedKeyPrefix}_${video.id}"
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 360f
                    )
                },
                clipInOverlayDuringTransition = OverlayClip(
                    RoundedCornerShape(cornerRadius)
                )
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(sharedHeroModifier)
            .graphicsLayer {
                clip = true
                shape = CardCornerShape
            }
            .background(OneUiPlaceholderGray)
            .border(
                width = 0.5.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = CardCornerShape
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        MediaThumbnailImage(
            uri = video.uri,
            contentDescription = video.title,
            inlineBitmap = inlineThumbnail,
            posterUrl = posterUrl,
            modifier = Modifier.fillMaxSize()
        )

        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = OneUiBadgeOrange,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 8.dp)
                    .size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .background(Color.Black.copy(alpha = 0.55f), DurationPillShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = video.durationFormatted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
        ) {
            Text(
                text = video.title,
                style = CardTitleTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = video.folderName,
                style = CardCountTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        if (watchProgressFraction > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(watchProgressFraction)
                        .background(OneUiBadgeOrange)
                )
            }
        }

        // Cloud cards put the transfer bar on the top edge so it never competes with the watch bar.
        if (downloadFraction != null && downloadFraction < 0.995f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.32f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(downloadFraction.coerceAtLeast(0.02f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// ============================================================================
// HERO LIBVLC VIDEO PLAYER SCREEN WITH FULL GESTURES, PIP, LOCK & SUBTITLES
// ============================================================================

@Immutable
private enum class VideoScaleMode(val label: String) {
    FIT("Fit"),
    CROP("Crop"),
    STRETCH("16:9")
}

private enum class ActiveGestureMode {
    NONE,
    BRIGHTNESS,
    VOLUME,
    HORIZONTAL_SEEK,
    VERTICAL_DISMISS,
    PINCH_ZOOM
}

private val SpeedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * The film's own lettering, held over the picture while the stream catches up.
 *
 * A title card rather than a second spinner: the first thing a viewer waiting on Telegram wants is
 * proof the right film is coming, and a franchise's own wordmark answers that at a glance where a
 * name in our font needs a read. It breathes rather than sits still because a still image over a
 * still frame is indistinguishable from a crash, and the fetch itself is already reported in bytes
 * under it — so this is the only thing allowed to move.
 */
@Composable
private fun TitleWordmark(wordmark: Bitmap, modifier: Modifier = Modifier) {
    // 1.44 s for a full cycle, measured off the reference clip's own luminance trace.
    val breath = rememberInfiniteTransition(label = "wordmark")
    val lit by breath.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wordmarkBreath"
    )
    Image(
        bitmap = wordmark.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // Wordmarks run from almost square to fifteen times as wide as they are tall, so the box is
        // allowed to be wide and short and Fit picks whichever edge binds for this particular film.
        // The height cap is what keeps a stacked logo off the download readout underneath it.
        modifier = modifier
            .fillMaxWidth(0.44f)
            .heightIn(max = 88.dp)
            .graphicsLayer { this.alpha = lit }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroVlcVideoPlayerScreen(
    playback: ActiveVideoPlayback,
    prefsStore: ArcPreferencesStore,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    cloudRepository: CloudRepository?,
    onSelectAdjacentVideo: (VideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val mainActivity = activity as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val video = playback.video

    // Cloud ids are always negative and local MediaStore ids always positive, so this lookup
    // cannot mistake one for the other.
    val shelfSource = remember(video.id, playback.cloudSources) {
        playback.cloudSources.firstOrNull { it.stableId == video.id }
    }
    // A card opened off the manifest carries the file number Telegram's *last* session handed out. The
    // open below asks for this message again and publishes the answer here, so the window pump, the
    // seek and the progress readouts all aim at the number this client is listening for.
    var cloudSource by remember(video.id) { mutableStateOf(shelfSource) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxSystemVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    val vlcEngine = remember {
        ArcVlcPlayerEngine(context, initialRate = prefsStore.defaultPlaybackSpeed)
    }

    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showTracksSheet by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudSticky by remember { mutableStateOf(false) }
    var gestureGaugeFraction by remember { mutableStateOf<Float?>(null) }
    var isBoostGauge by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    /** Seconds left on the next-up card. Zero means nothing is being offered. */
    var nextUpSeconds by remember { mutableStateOf(0) }

    /** The part whose offer the viewer turned down; asking again for the same one would be nagging. */
    var declinedPartId by remember { mutableStateOf<Long?>(null) }

    /**
     * The line under the spinner while a cloud seek waits for its bytes. Kept apart from the gesture
     * pill: this one is read for the whole length of a wait, not for the moment a gesture ends.
     */
    var seekStageText by remember { mutableStateOf("") }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var currentBrightness by remember {
        val winAttr = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (winAttr in 0.01f..1.0f) winAttr else 0.55f)
    }
    var currentVolumePercent by remember {
        val sysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(((sysVol.toFloat() / maxSystemVolume) * 100f).coerceIn(0f, 100f))
    }

    val dragOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val dragProgress = (abs(dragOffsetY.value) / (dismissThresholdPx * 2.5f)).coerceIn(0f, 1f)

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            vlcEngine.addExternalSubtitle(uri)
        }
    }

    /**
     * The item the player is reading right now, address included. A stall is recovered by re-opening
     * the same file, so the watchdog needs what the load below worked out without paying for another
     * round trip to Telegram to find it again.
     */
    var openedMedia by remember(video.id) { mutableStateOf<VideoItem?>(null) }

    /**
     * True while the first fetch for a cloud file is still in flight.
     *
     * The loading overlay needs a bounded start and end for the opening wait: `isFirstFrameRendered`
     * alone stays false forever when Telegram never delivers, which would leave a spinner turning
     * over a film that is not coming.
     */
    var isAskingTelegram by remember(video.id) { mutableStateOf(false) }

    // Tap to first picture: fetching the range, then letting the player open what landed.
    val isWaitingForFirstFrame = isAskingTelegram ||
        (cloudSource != null && openedMedia != null && !vlcEngine.isFirstFrameRendered)

    // Every state in which the picture is held and nothing is being played. The transport row stands
    // down for all of them: there is no position to move while the player has no media, and the row
    // sits exactly where the loading overlay puts the film's title.
    val isWaitingForPicture = isWaitingForFirstFrame || vlcEngine.isSeekWaiting || vlcEngine.isBuffering

    // Read straight from the observed map, so a lookup that lands while the film is already loading
    // puts its wordmark on screen without anything needing to ask again. Hidden when the controls are
    // locked, which is the viewer asking for nothing on top of the picture.
    //
    // The shelf's card travels with the film, so a copy or an episode that never got its own lookup
    // still shows the wordmark resolved for the work. Only a film opened outside a shelf — the local
    // library, a search result — has no title to inherit and falls back to its file's card.
    val titleCard = playback.titleCard ?: prefsStore.mediaCards[video.id]
    val wordmarkUrl = titleCard?.logoUrl?.takeIf { !isControlsLocked }

    // Decoded here rather than inside the wordmark so the overlay knows whether the art has landed
    // yet, and can keep the spinner under it until it does instead of leaving a hole.
    var wordmarkBitmap by remember(wordmarkUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wordmarkUrl) {
        wordmarkBitmap = wordmarkUrl?.let {
            runCatching { PosterCache.bitmap(context, it, PosterCache.WORDMARK_WIDTH_PX) }.getOrNull()
        }
    }

    LaunchedEffect(video.uri) {
        zoomScale = 1f
        panOffset = Offset.Zero
        gestureHudSticky = false
        // The watched minute belongs to the part rather than to the file that earned it, so a copy the
        // viewer has never opened here picks up where one of its siblings stopped.
        val sharedPosition =
            PlayOrder.positionByCopy(playback.copySlots, prefsStore.resumePositions)[video.id]
        val savedResumePos = sharedPosition ?: prefsStore.resumePositions[video.id] ?: 0L
        val streamUri = when {
            video.uri != Uri.EMPTY -> video.uri
            cloudRepository == null || cloudSource == null -> Uri.EMPTY
            else -> {
                isAskingTelegram = true
                val asked = cloudSource
                val live = asked?.let {
                    runCatching { cloudRepository.resolveLive(it) }.getOrNull() ?: it
                }
                isAskingTelegram = false
                if (live != null && live != asked) cloudSource = live
                val path = live?.let { runCatching { cloudRepository.localPathFor(it) }.getOrNull() }
                path?.let { Uri.fromFile(File(it)) } ?: Uri.EMPTY
            }
        }
        if (streamUri == Uri.EMPTY) {
            // The player opened on this film's promise and has nothing to show, so the reason stays on
            // screen rather than flicking for 800 ms over a black frame.
            gestureHudSticky = true
            gestureHudText = "Telegram did not deliver this file."
            return@LaunchedEffect
        }
        val media = video.copy(uri = streamUri)
        openedMedia = media
        vlcEngine.loadAndPlay(media, startPositionMs = savedResumePos)
        if (savedResumePos > ResumeRules.MIN_RESUME_POSITION_MS) {
            gestureHudText = "Resumed from ${formatDuration(savedResumePos)}"
        }
    }

    /**
     * The caption the channel holds for this film, fetched while the picture is already moving.
     *
     * Deliberately beside the open rather than inside it: finding one costs a Telegram round trip even
     * when the answer is that there isn't one, and every film would pay that before its first frame so
     * that the few with a forwarded subtitle could have it a moment after they start.
     */
    LaunchedEffect(video.id) {
        val source = cloudSource ?: return@LaunchedEffect
        val repository = cloudRepository ?: return@LaunchedEffect
        val caption = runCatching { repository.captionFileFor(source) }.getOrNull() ?: return@LaunchedEffect
        vlcEngine.showCaption(Uri.fromFile(caption))
        gestureHudText = "Subtitle from your shelf"
    }

    /**
     * A cloud file is only ever partly on disk, so the player reports where it is reading and the
     * download window is dragged along behind that. Local files never install this.
     */
    DisposableEffect(vlcEngine, cloudSource, cloudRepository) {
        val source = cloudSource
        val repository = cloudRepository
        if (source == null || repository == null) {
            vlcEngine.onStreamPosition = null
            vlcEngine.onCloudSeek = null
            onDispose {
                vlcEngine.onStreamPosition = null
                vlcEngine.onCloudSeek = null
            }
        } else {
            // One collector that always picks up the newest position. A drag reports a tick per frame,
            // and launching a request for each had the download window thrown between the middle of
            // the gesture and wherever the finger happened to be a moment ago.
            val readBytes = MutableStateFlow(-1L)
            val watcher = coroutineScope.launch {
                readBytes.collect { bytes ->
                    if (bytes < 0L) return@collect
                    // While the player is catching up to a position it was sent to, its own reports are
                    // about where it came from, and aiming the window from them cancels the fetch that
                    // position is waiting on.
                    if (vlcEngine.isCatchingUpToItsPosition) return@collect
                    runCatching { repository.advanceWindow(source, bytes) }
                        .onFailure { android.util.Log.w("ArcCloud", "window advance failed", it) }
                }
            }
            // A file sent as a document carries no duration in Telegram's metadata, so the play
            // head can only be turned into a byte offset once the player has opened the container.
            val playLength: () -> Long = {
                source.durationMs.takeIf { it > 0L } ?: vlcEngine.durationMs.takeIf { it > 1_000L } ?: 0L
            }
            vlcEngine.onStreamPosition = listener@{ positionMs ->
                val durationMs = playLength()
                if (durationMs == 0L) return@listener
                readBytes.value = repository.cachedByteOffset(source, positionMs, durationMs)
            }
            // A seek cannot wait for the next tick the way playback can: the play head lands in a
            // hole, and the player reads a hole as the end of the media with nothing left to recover
            // it. So the bytes go first and the seek follows them.
            vlcEngine.onCloudSeek = gate@{ positionMs ->
                val durationMs = playLength()
                if (durationMs == 0L) return@gate true
                val bytes = repository.awaitByteOffsetForSeek(source, positionMs, durationMs)
                val arrived = repository.awaitBytesForSeek(source, bytes)
                // A wait shows itself as the dimmed frame and the spinner over the video. Only a
                // fetch that stayed undelivered across every budget says so out loud.
                if (!arrived) gestureHudText = "Telegram is slow to deliver that part."
                arrived
            }
            onDispose {
                vlcEngine.onStreamPosition = null
                vlcEngine.onCloudSeek = null
                watcher.cancel()
            }
        }
    }

    /**
     * Keeps a cloud film's download window moving after the play head has stopped, and says so on
     * screen.
     *
     * The window is normally dragged along by position ticks, which makes a stall self-sustaining:
     * the picture stops, the ticks stop, and nothing is left asking Telegram for the bytes that would
     * restart it. That is why a film that ran out of link used to sit frozen forever rather than
     * buffering. This loop is the way out, and it runs whether or not the player is reporting.
     */
    LaunchedEffect(cloudSource, cloudRepository, vlcEngine) {
        val source = cloudSource ?: return@LaunchedEffect
        val repository = cloudRepository ?: return@LaunchedEffect
        var stalledSinceMs = BufferGuard.NEVER
        var restarts = 0
        var lastRestartMs = BufferGuard.NEVER
        var restartAimedAtMs = -1L
        var admittedSlowly = false
        var lastHeadMs = -1L
        var headMovedAtMs = BufferGuard.NEVER
        while (true) {
            val durationMs = vlcEngine.durationMs
            val headMs = vlcEngine.resumablePositionMs
            val now = SystemClock.elapsedRealtime()
            // A new moment on the scrubber, not a report arriving, is what says the picture is alive. A
            // decoder working slower than real time still moves its head every second, and reading that
            // as a stall spends the re-opens throwing the film back to the keyframe before its own play
            // head — which is what a resume that never started looked like.
            if (headMs != lastHeadMs) {
                lastHeadMs = headMs
                headMovedAtMs = now
            }
            // A re-open that then gets somewhere is the only thing that forgives the attempt count.
            // Ticking for a moment after a restart proves nothing on its own.
            if (restartAimedAtMs >= 0L && headMs >= restartAimedAtMs + BufferGuard.PROGRESS_AFTER_RESTART_MS) {
                restartAimedAtMs = -1L
                restarts = 0
            }
            val guard = BufferGuard.Snapshot(
                nowMs = now,
                lastTickMs = vlcEngine.lastTickAtMs,
                headMovedAtMs = headMovedAtMs,
                playing = vlcEngine.isPlaying,
                stoppedOnPurpose = vlcEngine.stoppedOnPurpose,
                seeking = vlcEngine.isSeekWaiting,
                watchedMs = headMs,
                durationMs = durationMs,
                fileComplete = repository.progress.value[source.fileId]?.isComplete == true,
                playOwedSinceMs = vlcEngine.playOwedSinceMs
            )
            val stalled = durationMs > 1L && BufferGuard.isStalled(guard)
            if (!stalled) {
                stalledSinceMs = BufferGuard.NEVER
                vlcEngine.isBuffering = false
            } else {
                if (stalledSinceMs == BufferGuard.NEVER) stalledSinceMs = now
                vlcEngine.isBuffering = true
                // Only the fake end is ours to undo. A scrubber the viewer moved during the wait is a
                // position they chose, and dragging it back every second would be the app arguing.
                if (vlcEngine.currentPositionMs == vlcEngine.durationMs) vlcEngine.holdAtLastFrame()
                val headBytes = repository.cachedByteOffset(source, headMs, durationMs)
                runCatching { repository.advanceWindow(source, headBytes) }
                    .onFailure { android.util.Log.w("ArcCloud", "stall pump failed", it) }
                val ahead = runCatching { repository.readableAhead(source, headBytes) }.getOrNull() ?: 0L
                if (BufferGuard.shouldRestart(
                        nowMs = now,
                        stalledSinceMs = stalledSinceMs,
                        bytesAhead = ahead,
                        restarts = restarts,
                        lastRestartMs = lastRestartMs,
                        graceMs = BufferGuard.graceMs(guard)
                    )
                ) {
                    val media = openedMedia
                    if (media != null) {
                        restarts++
                        lastRestartMs = now
                        restartAimedAtMs = headMs
                        android.util.Log.i(
                            "ArcCloud",
                            "stall restart #$restarts at ${headMs}ms with $ahead bytes ahead"
                        )
                        vlcEngine.loadAndPlay(media, startPositionMs = headMs)
                    }
                }
                if (restarts >= BufferGuard.MAX_RESTARTS && !admittedSlowly) {
                    admittedSlowly = true
                    gestureHudSticky = true
                    gestureHudText = "Telegram is slow to deliver that part."
                }
            }
            delay(1_000L)
        }
    }

    /**
     * What the download is doing while a seek waits for it. A static "fetching" line reads as a hang
     * after ten seconds of a link that is moving, and lies after forty on one that is not, so the wait
     * reports instead the two facts a viewer can judge: how much of the film is on the device, and how
     * fast that number is climbing.
     */
    LaunchedEffect(vlcEngine.isSeekWaiting, vlcEngine.isBuffering, isWaitingForFirstFrame, cloudSource?.fileId, cloudRepository) {
        val source = cloudSource
        val repository = cloudRepository
        val waiting = vlcEngine.isSeekWaiting || vlcEngine.isBuffering || isWaitingForFirstFrame
        if (!waiting || source == null || repository == null) {
            return@LaunchedEffect
        }
        val meter = ByteRate()
        while (vlcEngine.isSeekWaiting || vlcEngine.isBuffering || isWaitingForFirstFrame) {
            // Ask, rather than only report. Once the film has a duration the play head's ticks drag
            // the window along and the stall watchdog covers a stopped head, but neither of them runs
            // while the player is still trying to open: the ticks need a duration to turn a moment
            // into a byte, and the watchdog needs one before it believes anything is stuck. Measured
            // on a cold 3.6 GB film: one aim at the head, then five minutes of nobody asking and a
            // frozen "1% cached · 0 KB/s" on screen. So while there is no duration, this loop is the
            // only thing that can keep the file coming.
            //
            // It stands down the moment the player owes a play head, because then it is aiming at the
            // wrong place and doing damage: a re-open resets the length to the placeholder, so this pump
            // believes the film is at its start and asks for byte 0, which cancels the window the resume
            // seek is waiting on and starts the file arriving from the beginning all over again.
            // Measured on a cold resume: three re-opens, each one following its own `seek served from
            // disk` with `advance read=0 … -> Fetch(offset=0)`, and the picture never came up.
            if (vlcEngine.durationMs <= 1L && !vlcEngine.isCatchingUpToItsPosition) {
                runCatching { repository.advanceWindow(source, 0L) }
                    .onFailure { android.util.Log.w("ArcCloud", "opening pump failed", it) }
            }
            // The wait is already asking Telegram for these numbers every poll and publishing what it
            // hears, so the loading line must not spend a round trip of its own.
            val progress = repository.progress.value[source.fileId]
            val speed = ByteRate.speedLabel(
                meter.sample(SystemClock.elapsedRealtime(), progress?.bytesOnDisk ?: 0L)
            )
            val cachedPercent = ((progress?.fraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
            seekStageText = if (speed == null) {
                "$cachedPercent% cached · asking Telegram…"
            } else {
                "$cachedPercent% cached · $speed"
            }
            delay(1_000L)
        }
    }

    LaunchedEffect(vlcEngine.isPlaying, video.id) {
        mainActivity?.isVideoActivelyPlaying = vlcEngine.isPlaying
        while (vlcEngine.isPlaying) {
            prefsStore.recordPlaybackProgress(
                videoId = video.id,
                positionMs = vlcEngine.resumablePositionMs,
                durationMs = vlcEngine.durationMs
            )
            delay(1500L)
        }
    }

    DisposableEffect(hostView, vlcEngine.isPlaying) {
        hostView.keepScreenOn = vlcEngine.isPlaying
        onDispose {
            hostView.keepScreenOn = false
            mainActivity?.isVideoActivelyPlaying = false
        }
    }

    DisposableEffect(lifecycleOwner, vlcEngine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val inPip = activity?.isInPictureInPictureMode == true
                if (!inPip && !prefsStore.backgroundAudioEnabled) {
                    vlcEngine.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            prefsStore.recordPlaybackProgress(
                videoId = video.id,
                positionMs = vlcEngine.resumablePositionMs,
                durationMs = vlcEngine.durationMs
            )
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            vlcEngine.release()
        }
    }

    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null && gestureGaugeFraction == null && !gestureHudSticky) {
            delay(800L)
            gestureHudText = null
        }
    }

    val cornerRadius by animatedVisibilityScope.transition.animateDp(
        label = "PlayerHeroCornerRadius"
    ) { state ->
        when (state) {
            androidx.compose.animation.EnterExitState.Visible -> (dragProgress * 22f).dp
            else -> 22.dp
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // The skip buttons walk the parts rather than the files: another upload of the film already
    // playing is not what a viewer means by "next", and the copy worth landing on is the best one
    // of the part on either side.
    val stepping = remember(playback.copySlots, playback.playlist, video.id) {
        PlayOrder.stepping(playback.copySlots, playback.playlist.map { it.id }, video.id)
    }
    val filesById = remember(playback.playlist) { playback.playlist.associateBy { it.id } }
    val previousVideo = stepping.order.getOrNull(stepping.index - 1)?.let { filesById[it] }
    val nextVideo = stepping.order.getOrNull(stepping.index + 1)?.let { filesById[it] }
    val hasPrevious = stepping.index > 0 && previousVideo != null
    val hasNext = stepping.index >= 0 && nextVideo != null
    val isInPipMode = mainActivity?.isInPipState == true

    /**
     * Offers the next episode when this one is watched out, and hands it over unless the viewer says
     * otherwise.
     *
     * The offer waits on the numbers rather than on the player's end report, because the two are not
     * the same fact: a cloud file with a hole in it ends exactly the way a finished episode does, and
     * acting on the report alone would skip the rest of an episode every time Telegram is slow near a
     * boundary. [NextUp] holds that rule and the ones beside it, including the one that keeps a folder
     * queue — whose "next" is simply whatever else was uploaded — out of this entirely.
     */
    LaunchedEffect(vlcEngine.isAtEnd, video.id, declinedPartId) {
        nextUpSeconds = 0
        val upcoming = nextVideo
        if (upcoming == null || declinedPartId == video.id) return@LaunchedEffect
        val isOffered = NextUp.shouldOffer(
            NextUp.Snapshot(
                lastShownMs = vlcEngine.resumablePositionMs,
                durationMs = vlcEngine.durationMs,
                waitingForBytes = vlcEngine.isBuffering || vlcEngine.isSeekWaiting,
                knowsItsParts = playback.copySlots.isNotEmpty(),
                hasNextPart = hasNext
            )
        )
        if (!vlcEngine.isAtEnd || !isOffered) return@LaunchedEffect
        for (second in NextUp.COUNTDOWN_SECONDS downTo 1) {
            nextUpSeconds = second
            delay(1_000L)
            // Rewound, paused, or carried on by the stall watchdog: the ending was not the ending after
            // all, and the card has to come off rather than jump the film sideways.
            if (!vlcEngine.isAtEnd || vlcEngine.stoppedOnPurpose) {
                nextUpSeconds = 0
                return@LaunchedEffect
            }
        }
        nextUpSeconds = 0
        declinedPartId = null
        onSelectAdjacentVideo(upcoming)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - dragProgress * 0.75f).coerceIn(0f, 1f)))
            .pointerInput(isControlsLocked, zoomScale) {
                detectTapGestures(
                    onTap = {
                        if (showTracksSheet) {
                            showTracksSheet = false
                        } else {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (isControlsLocked) return@detectTapGestures
                        val width = size.width.toFloat()
                        when {
                            zoomScale > 1.05f -> {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                gestureHudText = "Zoom 1.0x"
                            }
                            offset.x < width * 0.35f -> {
                                vlcEngine.seekByDelta(-10_000L)
                                gestureHudText = "-10s"
                            }
                            offset.x > width * 0.65f -> {
                                vlcEngine.seekByDelta(10_000L)
                                gestureHudText = "+10s"
                            }
                            else -> {
                                vlcEngine.togglePlayPause()
                            }
                        }
                    }
                )
            }
            .pointerInput(isControlsLocked, showTracksSheet) {
                if (isControlsLocked || showTracksSheet) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val screenW = size.width.toFloat().coerceAtLeast(1f)
                    val screenH = size.height.toFloat().coerceAtLeast(1f)

                    var mode = ActiveGestureMode.NONE
                    var totalDx = 0f
                    var totalDy = 0f
                    var seekStartMs = vlcEngine.currentPositionMs
                    var seekPreviewMs = seekStartMs

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount >= 2) {
                            mode = ActiveGestureMode.PINCH_ZOOM
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                zoomScale = (zoomScale * zoomChange).coerceIn(0.5f, 4.0f)
                                panOffset = if (zoomScale > 1f) panOffset + panChange else Offset.Zero
                                gestureHudText = String.format(Locale.US, "Zoom %.1fx", zoomScale)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (pressedCount == 1 && mode != ActiveGestureMode.PINCH_ZOOM) {
                            val change = event.changes.first()
                            // The scrubber and the buttons are children of this surface and consume
                            // their own drags. Without this, a scrub on the bar also moves the play
                            // head by however far the finger travelled sideways.
                            if (change.isConsumed) continue
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalDx += dx
                            totalDy += dy

                            if (mode == ActiveGestureMode.NONE) {
                                if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                    mode = if (abs(totalDx) > abs(totalDy) * 1.25f) {
                                        seekStartMs = vlcEngine.currentPositionMs
                                        ActiveGestureMode.HORIZONTAL_SEEK
                                    } else {
                                        when {
                                            startX < screenW * 0.34f -> ActiveGestureMode.BRIGHTNESS
                                            startX > screenW * 0.66f -> ActiveGestureMode.VOLUME
                                            else -> ActiveGestureMode.VERTICAL_DISMISS
                                        }
                                    }
                                }
                            }

                            when (mode) {
                                ActiveGestureMode.BRIGHTNESS -> {
                                    change.consume()
                                    val deltaBright = -dy / (screenH * 0.65f)
                                    currentBrightness = (currentBrightness + deltaBright).coerceIn(0.02f, 1.0f)
                                    activity?.window?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = currentBrightness
                                        win.attributes = lp
                                    }
                                    val pct = (currentBrightness * 100f).roundToInt()
                                    isBoostGauge = false
                                    gestureGaugeFraction = currentBrightness
                                    gestureHudText = "Brightness $pct%"
                                }

                                ActiveGestureMode.VOLUME -> {
                                    change.consume()
                                    val deltaVolPct = (-dy / (screenH * 0.65f)) * 150f
                                    currentVolumePercent = (currentVolumePercent + deltaVolPct).coerceIn(0f, 200f)
                                    val pctInt = currentVolumePercent.roundToInt()

                                    if (pctInt <= 100) {
                                        val targetStreamVol = ((pctInt / 100f) * maxSystemVolume).roundToInt().coerceIn(0, maxSystemVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
                                        vlcEngine.setSoftwareBoost(100)
                                        isBoostGauge = false
                                        gestureGaugeFraction = pctInt / 100f
                                        gestureHudText = "Volume $pctInt%"
                                    } else {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVolume, 0)
                                        vlcEngine.setSoftwareBoost(pctInt)
                                        isBoostGauge = true
                                        gestureGaugeFraction = (pctInt - 100) / 100f
                                        gestureHudText = "Boost $pctInt%"
                                    }
                                }

                                ActiveGestureMode.HORIZONTAL_SEEK -> {
                                    change.consume()
                                    val deltaSeekMs = ((totalDx / screenW) * 90_000f).toLong()
                                    seekPreviewMs = (seekStartMs + deltaSeekMs).coerceIn(0L, vlcEngine.durationMs)
                                    val sign = if (deltaSeekMs >= 0L) "+" else "-"
                                    gestureHudText = "$sign${formatDuration(abs(deltaSeekMs))} (${formatDuration(seekPreviewMs)})"
                                }

                                ActiveGestureMode.VERTICAL_DISMISS -> {
                                    change.consume()
                                    coroutineScope.launch {
                                        dragOffsetY.snapTo(dragOffsetY.value + dy)
                                    }
                                }

                                else -> Unit
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // The seek lands when the finger lifts, the same as the scrubber. Seeking per
                    // frame would mean one download window per frame on a cloud file.
                    if (mode == ActiveGestureMode.HORIZONTAL_SEEK) {
                        vlcEngine.seekTo(seekPreviewMs)
                    }

                    gestureGaugeFraction = null
                    isBoostGauge = false
                    if (mode == ActiveGestureMode.BRIGHTNESS || mode == ActiveGestureMode.VOLUME || mode == ActiveGestureMode.HORIZONTAL_SEEK) {
                        gestureHudText = null
                    }
                    if (mode == ActiveGestureMode.VERTICAL_DISMISS) {
                        if (dragOffsetY.value > dismissThresholdPx) {
                            android.util.Log.i("ArcPlayer", "player closed: dragged ${dragOffsetY.value}px")
                            if (!prefsStore.backgroundAudioEnabled) {
                                vlcEngine.pause()
                            }
                            onDismiss()
                        } else {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                }
            }
    ) {
        with(sharedTransitionScope) {
            val isTransitionRunning = animatedVisibilityScope.transition.isRunning
            val effectiveAspectRatio = when (scaleMode) {
                VideoScaleMode.FIT -> vlcEngine.videoAspectRatio
                VideoScaleMode.CROP -> vlcEngine.videoAspectRatio
                VideoScaleMode.STRETCH -> 16f / 9f
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(
                        if (scaleMode == VideoScaleMode.CROP) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(effectiveAspectRatio)
                        }
                    )
                    .graphicsLayer {
                        val dismissScale = 1f - (dragProgress * 0.16f)
                        scaleX = dismissScale * zoomScale
                        scaleY = dismissScale * zoomScale
                        translationX = panOffset.x
                        translationY = dragOffsetY.value + panOffset.y
                    }
                    .sharedElement(
                        state = rememberSharedContentState(
                            key = "video_hero_${playback.sharedKeyPrefix}_${video.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 360f
                            )
                        },
                        clipInOverlayDuringTransition = OverlayClip(
                            RoundedCornerShape(cornerRadius)
                        )
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.Black)
                    .onSizeChanged { intSize ->
                        vlcEngine.updateSurfaceSize(intSize.width, intSize.height)
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            vlcEngine.attachTextureView(this)
                        }
                    },
                    update = { textureView ->
                        vlcEngine.attachTextureView(textureView)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (!vlcEngine.isFirstFrameRendered || isTransitionRunning) {
                    MediaThumbnailImage(
                        uri = video.uri,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AnimatedVisibility(
                    visible = isWaitingForPicture && !isInPipMode,
                    enter = fadeIn(tween(140)),
                    exit = fadeOut(tween(220)),
                    modifier = Modifier.matchParentSize()
                ) {
                    // The frame the player froze on, seen through a dim rather than hidden behind a
                    // black panel: the difference between a film that is held and one that is gone is
                    // whether the last picture is still there.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // One stack, centred: the band this sits in is the letterboxed film, which on
                        // a phone is barely a fifth of the screen, and anything anchored to its bottom
                        // lands on top of a title that is anchored to its middle.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val wordmark = wordmarkBitmap
                            if (wordmark != null) {
                                TitleWordmark(wordmark)
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            }
                            if (seekStageText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = seekStageText,
                                    fontSize = 12.5.sp,
                                    color = Color.White.copy(alpha = 0.86f)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = gestureHudText != null && !isInPipMode,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.88f, animationSpec = tween(100)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 72.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.76f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = gestureHudText.orEmpty(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBoostGauge) OneUiBadgeOrange else Color.White
                    )
                    gestureGaugeFraction?.let { frac ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                                    .background(if (isBoostGauge) OneUiBadgeOrange else Color.White)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isControlsLocked && showControls && !isInPipMode,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarBottom + 32.dp)
        ) {
            Surface(
                onClick = { isControlsLocked = false },
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock controls",
                        tint = OneUiBadgeOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to Unlock",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isControlsLocked && !isInPipMode && showControls && dragProgress < 0.05f && !animatedVisibilityScope.transition.isRunning,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                        .padding(
                            top = statusBarTop + 6.dp,
                            bottom = 16.dp,
                            start = 8.dp,
                            end = 12.dp
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                android.util.Log.i("ArcPlayer", "player closed: back arrow")
                                if (!prefsStore.backgroundAudioEnabled) {
                                    vlcEngine.pause()
                                }
                                onDismiss()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close player",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${video.folderName} • LibVLC",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1
                            )
                        }

                        val isFav = prefsStore.favoriteIds.contains(video.id)
                        IconButton(onClick = { prefsStore.toggleFavorite(video.id) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) OneUiBadgeOrange else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        PlayerChipButton(
                            text = String.format(Locale.US, "%.2gx", vlcEngine.playbackRate),
                            onClick = {
                                val nextIdx = (SpeedPresets.indexOf(vlcEngine.playbackRate) + 1) % SpeedPresets.size
                                val nextRate = SpeedPresets[nextIdx]
                                vlcEngine.setSpeed(nextRate)
                                prefsStore.updateDefaultSpeed(nextRate)
                            }
                        )

                        PlayerChipButton(
                            text = "Tracks & Subs",
                            onClick = { showTracksSheet = !showTracksSheet }
                        )

                        PlayerChipButton(
                            text = scaleMode.label,
                            onClick = {
                                scaleMode = VideoScaleMode.entries[(scaleMode.ordinal + 1) % VideoScaleMode.entries.size]
                            }
                        )

                        PlayerChipButton(
                            text = "Rotate",
                            onClick = {
                                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        )

                        PlayerChipButton(
                            text = "PiP",
                            onClick = {
                                runCatching {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                            }
                        )

                        PlayerChipButton(
                            text = "Lock",
                            onClick = { isControlsLocked = true }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isWaitingForPicture,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerCircleIconButton(
                            size = 46.dp,
                            enabled = hasPrevious,
                            onClick = { previousVideo?.let(onSelectAdjacentVideo) }
                        ) {
                            SkipIconCanvas(isNext = false, enabled = hasPrevious)
                        }

                        PlayerCircleIconButton(
                            size = 50.dp,
                            onClick = {
                                vlcEngine.seekByDelta(-10_000L)
                                gestureHudText = "-10s"
                            }
                        ) {
                            Text(
                                text = "-10",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        PlayerCircleIconButton(
                            size = 68.dp,
                            onClick = { vlcEngine.togglePlayPause() }
                        ) {
                            if (vlcEngine.isPlaying) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val barW = size.width * 0.24f
                                    val barH = size.height * 0.78f
                                    val top = (size.height - barH) / 2f
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(size.width * 0.16f, top),
                                        size = ComposeSize(barW, barH),
                                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                    )
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(size.width * 0.60f, top),
                                        size = ComposeSize(barW, barH),
                                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        PlayerCircleIconButton(
                            size = 50.dp,
                            onClick = {
                                vlcEngine.seekByDelta(10_000L)
                                gestureHudText = "+10s"
                            }
                        ) {
                            Text(
                                text = "+10",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        PlayerCircleIconButton(
                            size = 46.dp,
                            enabled = hasNext,
                            onClick = { nextVideo?.let(onSelectAdjacentVideo) }
                        ) {
                            SkipIconCanvas(isNext = true, enabled = hasNext)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp,
                            bottom = navBarBottom + 20.dp
                        )
                ) {
                    Slider(
                        // The drag owns the thumb until the finger lifts. Seeking per frame would
                        // fight the play head, which keeps snapping the value back, and would leave
                        // a half dozen download windows pointing at every part of the film the
                        // gesture swept over.
                        value = scrubFraction
                            ?: (vlcEngine.currentPositionMs.toFloat() / vlcEngine.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction -> scrubFraction = fraction },
                        onValueChangeFinished = {
                            val fraction = scrubFraction
                            scrubFraction = null
                            if (fraction != null) {
                                vlcEngine.seekTo((fraction * vlcEngine.durationMs).toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(
                                scrubFraction?.let { (it * vlcEngine.durationMs).toLong() }
                                    ?: vlcEngine.currentPositionMs
                            ),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Text(
                            text = formatDuration(vlcEngine.durationMs),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }

        // Rests above the scrubber rather than beside it: the transport row is the widest thing the
        // player draws at the bottom, and on a phone held sideways the two would overlap if this came
        // in on the same band.
        AnimatedVisibility(
            visible = nextUpSeconds > 0 && !isInPipMode && !isControlsLocked,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = navBarBottom + 112.dp)
        ) {
            nextVideo?.let { upcoming ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    // The two actions set the floor here, so the caption is what has to give: without a
                    // cap the row pushed the second button off a portrait phone's edge.
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Next up in ${nextUpSeconds}s",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.66f)
                            )
                            Text(
                                text = NextUp.labelFor(upcoming.title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        PlayerChipButton(
                            text = "Play now",
                            onClick = {
                                nextUpSeconds = 0
                                declinedPartId = null
                                onSelectAdjacentVideo(upcoming)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PlayerChipButton(
                            text = "Cancel",
                            onClick = {
                                declinedPartId = video.id
                                nextUpSeconds = 0
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showTracksSheet && !isInPipMode,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = navBarBottom + 96.dp
                )
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF21C1C20),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Audio Tracks",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (vlcEngine.audioTracks.isEmpty()) {
                        Text(
                            text = "Default Audio",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    } else {
                        vlcEngine.audioTracks.forEach { track ->
                            val isSelected = track.id == vlcEngine.selectedAudioTrackId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable { vlcEngine.selectAudioTrack(track.id) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subtitles",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        PlayerChipButton(
                            text = "+ Load .SRT / .ASS",
                            onClick = {
                                subtitlePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (vlcEngine.subtitleTracks.isEmpty()) {
                        Text(
                            text = "No embedded subtitles",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    } else {
                        val isDisabledSelected = vlcEngine.selectedSubtitleTrackId == -1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDisabledSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { vlcEngine.selectSubtitleTrack(-1) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Disable Subtitles",
                                fontSize = 13.sp,
                                fontWeight = if (isDisabledSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isDisabledSelected) Color.White else Color.White.copy(alpha = 0.78f)
                            )
                        }

                        vlcEngine.subtitleTracks.filter { it.id != -1 }.forEach { sub ->
                            val isSelected = sub.id == vlcEngine.selectedSubtitleTrackId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable { vlcEngine.selectSubtitleTrack(sub.id) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subtitle Sync: ${vlcEngine.subtitleDelayMs} ms",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlayerChipButton(
                                text = "-100ms",
                                onClick = { vlcEngine.adjustSubtitleDelay(-100L) }
                            )
                            PlayerChipButton(
                                text = "Reset",
                                onClick = { vlcEngine.adjustSubtitleDelay(0L) }
                            )
                            PlayerChipButton(
                                text = "+100ms",
                                onClick = { vlcEngine.adjustSubtitleDelay(100L) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChipButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun PlayerCircleIconButton(
    size: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.55f else 0.25f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun SkipIconCanvas(
    isNext: Boolean,
    enabled: Boolean
) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            if (isNext) {
                moveTo(w * 0.18f, h * 0.18f)
                lineTo(w * 0.68f, h * 0.50f)
                lineTo(w * 0.18f, h * 0.82f)
                close()
            } else {
                moveTo(w * 0.82f, h * 0.18f)
                lineTo(w * 0.32f, h * 0.50f)
                lineTo(w * 0.82f, h * 0.82f)
                close()
            }
        }
        drawPath(path = path, color = tint)
        val barX = if (isNext) w * 0.78f else w * 0.22f
        drawLine(
            color = tint,
            start = Offset(barX, h * 0.20f),
            end = Offset(barX, h * 0.80f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round
        )
    }
}

// ============================================================================
// THUMBNAIL RENDERING & PLACEHOLDERS
// ============================================================================

@Composable
private fun MediaThumbnailImage(
    uri: Uri,
    contentDescription: String,
    modifier: Modifier = Modifier,
    inlineBitmap: Bitmap? = null,
    posterUrl: String? = null
) {
    val posterContext = LocalContext.current
    var poster by remember(posterUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(posterUrl) {
        poster = PosterCache.bitmap(posterContext, posterUrl)
    }

    // Real artwork wins as soon as it lands; until then the card keeps the frame Telegram sent.
    val preferred = poster?.asImageBitmap() ?: inlineBitmap?.asImageBitmap()
    if (preferred != null) {
        Image(
            bitmap = preferred,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    if (uri == Uri.EMPTY) {
        PlaceholderThumbnailBox(modifier = modifier)
        return
    }

    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf(VideoThumbnailCache.get(uri)) }

    if (bitmap == null) {
        LaunchedEffect(uri) {
            bitmap = loadVideoThumbnailCancellable(
                contentResolver = context.contentResolver,
                uri = uri,
                targetWidthPx = 220,
                targetHeightPx = 282
            )
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        PlaceholderThumbnailBox(modifier = modifier)
    }
}

@Composable
private fun PlaceholderThumbnailBox(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val bottomGradient = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                startY = h * 0.55f,
                endY = h
            )
            onDrawBehind {
                drawRect(color = OneUiPlaceholderGray)
                val boxSize = w * 0.22f
                val left = (w - boxSize) / 2f
                val top = (h - boxSize) * 0.40f
                drawRoundRect(
                    color = Color(0xFF9E9EA3),
                    topLeft = Offset(left, top),
                    size = ComposeSize(boxSize, boxSize),
                    cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
                    style = Stroke(width = w * 0.022f)
                )
                drawRect(brush = bottomGradient)
            }
        }
    )
}

@Composable
private fun CameraBadgeOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(0f, h * 0.14f),
            size = ComposeSize(w, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.10f, h * 0.24f),
            size = ComposeSize(w * 0.80f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
        )
        drawCircle(
            color = Color(0xFF1A1A1A),
            radius = w * 0.16f,
            center = Offset(w * 0.50f, h * 0.53f)
        )
    }
}

// ============================================================================
// PERMISSION & EMPTY STATE VIEWS
// ============================================================================

@Composable
private fun PermissionRequestView(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Allow access to your videos",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = OneUiTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Arc Player scans your device folders to display your local videos and albums.",
            fontSize = 14.sp,
            color = OneUiTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onGrantClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OneUiTextPrimary,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Allow video access",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyMediaStateView(onRescanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No videos found",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = OneUiTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Videos recorded with your camera or downloaded to your device will appear here automatically.",
            fontSize = 13.5.sp,
            color = OneUiTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRescanClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OneUiPillSelected,
                contentColor = OneUiTextPrimary
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Rescan storage",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================================
// FLOATING ONE UI PILL BOTTOM NAVIGATION BAR
// ============================================================================

@Composable
private fun OneUiFloatingBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier
) {
    // Mirrors Arc Music's BottomNavigation structure exactly: clip -> glass -> padding.
    // A trailing Modifier.shadow() or an inner Surface on the glass node makes Haze
    // blur the bar's own icons instead of just the backdrop.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .jellyGlass(
                hazeState = hazeState,
                backdrop = backdrop,
                tintTransparency = 0.4f,
                noiseFactor = 0.06f,
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
            val tabCount = BottomNavTab.entries.size
            val selectedIndex = BottomNavTab.entries.indexOf(selectedTab).coerceIn(0, tabCount - 1)
            val cellWidth = maxWidth / tabCount
            val pillWidth = 64.dp
            val targetOffset = cellWidth * (selectedIndex + 0.5f) - pillWidth / 2
            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "navbar_pill_offset"
            )

            // Sliding selection capsule (One UI style)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = animatedOffset)
                    .size(width = pillWidth, height = 44.dp)
                    .background(OneUiTextPrimary.copy(alpha = 0.09f), CircleShape)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .jellyClick(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                scaleDownTo = 0.92f,
                                onClick = { onTabSelected(tab) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BottomBarTabIcon(
                                tab = tab,
                                selected = isSelected,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = OneUiTextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
}

@Composable
private fun BottomBarTabIcon(
    tab: BottomNavTab,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        val color = OneUiTextPrimary

        when (tab) {
            BottomNavTab.VIDEOS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.16f),
                    size = ComposeSize(w * 0.76f, h * 0.68f),
                    cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                    style = if (selected) Stroke(width = w * 0.11f) else stroke
                )
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.42f, h * 0.35f)
                    lineTo(w * 0.64f, h * 0.50f)
                    lineTo(w * 0.42f, h * 0.65f)
                    close()
                }
                drawPath(path = path, color = color)
            }

            BottomNavTab.FOLDERS -> {
                if (selected) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.14f, h * 0.14f),
                        size = ComposeSize(w * 0.12f, h * 0.72f),
                        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.32f, h * 0.14f),
                        size = ComposeSize(w * 0.54f, h * 0.72f),
                        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
                    )
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.16f, h * 0.16f),
                        size = ComposeSize(w * 0.68f, h * 0.68f),
                        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                        style = stroke
                    )
                    drawLine(
                        color = color,
                        start = Offset(w * 0.34f, h * 0.16f),
                        end = Offset(w * 0.34f, h * 0.84f),
                        strokeWidth = stroke.width
                    )
                }
            }

            BottomNavTab.CLOUD -> {
                val cloud = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.30f, h * 0.68f)
                    lineTo(w * 0.70f, h * 0.68f)
                    cubicTo(
                        w * 0.87f, h * 0.68f,
                        w * 0.86f, h * 0.45f,
                        w * 0.71f, h * 0.45f
                    )
                    cubicTo(
                        w * 0.67f, h * 0.28f,
                        w * 0.43f, h * 0.27f,
                        w * 0.37f, h * 0.45f
                    )
                    cubicTo(
                        w * 0.17f, h * 0.47f,
                        w * 0.15f, h * 0.68f,
                        w * 0.30f, h * 0.68f
                    )
                    close()
                }
                if (selected) {
                    drawPath(path = cloud, color = color)
                } else {
                    drawPath(path = cloud, color = color, style = stroke)
                }
            }

            BottomNavTab.MENU -> {
                val yPositions = listOf(0.26f, 0.50f, 0.74f)
                val lineStroke = if (selected) w * 0.11f else w * 0.085f
                yPositions.forEach { yFactor ->
                    drawLine(
                        color = color,
                        start = Offset(w * 0.18f, h * yFactor),
                        end = Offset(w * 0.82f, h * yFactor),
                        strokeWidth = lineStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
