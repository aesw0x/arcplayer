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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aeswox.arcplayer.ui.animations.StaggeredListItem
import com.aeswox.arcplayer.ui.animations.jelly
import com.aeswox.arcplayer.ui.animations.jellyClick
import com.aeswox.arcplayer.ui.animations.rememberHeaderActionsVisible
import com.aeswox.arcplayer.ui.animations.jellyCombinedClickable
import com.aeswox.arcplayer.ui.animations.jellyTabCrossSlide
import com.aeswox.arcplayer.ui.animations.jellySkeletonSweep
import com.aeswox.arcplayer.ui.animations.physicsBounceOverscroll
import com.aeswox.arcplayer.ui.components.AppIntro
import com.aeswox.arcplayer.update.Release
import com.aeswox.arcplayer.update.UpdateCard
import com.aeswox.arcplayer.update.UpdateCheck
import com.aeswox.arcplayer.update.UpdateDownload
import com.aeswox.arcplayer.update.UpdateManager
import com.aeswox.arcplayer.update.UpdateProgress
import com.aeswox.arcplayer.ui.components.ClapperIntroGate
import com.aeswox.arcplayer.ui.components.FrostedDialog
import com.aeswox.arcplayer.ui.components.FrostedModalPanel
import com.aeswox.arcplayer.ui.components.HugeIcons
import com.aeswox.arcplayer.ui.components.MorphingMenu
import com.aeswox.arcplayer.ui.components.MorphingMenuItem
import com.aeswox.arcplayer.ui.haptics.AppHaptics
import com.aeswox.arcplayer.ui.haptics.HapticIntensity
import com.aeswox.arcplayer.ui.theme.Background as JellyBackground
import com.aeswox.arcplayer.ui.theme.Accent as JellyAccent
import com.aeswox.arcplayer.ui.theme.Error as JellyError
import com.aeswox.arcplayer.ui.theme.OnBackground as JellyOnBackground
import com.aeswox.arcplayer.ui.theme.OnPrimary as JellyOnPrimary
import com.aeswox.arcplayer.ui.theme.OnSurface as JellyOnSurface
import com.aeswox.arcplayer.ui.theme.OnSurfaceVariant as JellyOnSurfaceVariant
import com.aeswox.arcplayer.ui.theme.Primary as JellyPrimary
import com.aeswox.arcplayer.ui.theme.Surface as JellySurface
import com.aeswox.arcplayer.ui.theme.SurfaceContainer as JellySurfaceContainer
import com.aeswox.arcplayer.ui.theme.SurfaceContainerHigh as JellySurfaceContainerHigh
import com.aeswox.arcplayer.ui.theme.ArcPlayerJellyTheme
import com.aeswox.arcplayer.ui.jelly.jellyGlass
import com.aeswox.arcplayer.ui.jelly.jellySource
import com.aeswox.arcplayer.backdrop.backdrops.LayerBackdrop
import com.aeswox.arcplayer.backdrop.backdrops.rememberLayerBackdrop
import com.aeswox.arcplayer.cloud.CloudAuthStage
import com.aeswox.arcplayer.cloud.CloudRepository
import com.aeswox.arcplayer.cloud.CloudSession
import com.aeswox.arcplayer.cloud.CloudVideoItem
import com.aeswox.arcplayer.cloud.CloudWindowPlanner
import com.aeswox.arcplayer.cloud.DownloadEntry
import com.aeswox.arcplayer.cloud.DownloadLedger
import com.aeswox.arcplayer.cloud.DownloadNotifier
import com.aeswox.arcplayer.cloud.DownloadNotices
import com.aeswox.arcplayer.cloud.KeptMedia
import com.aeswox.arcplayer.cloud.CloudProgress
import com.aeswox.arcplayer.cloud.SHELF_CHANNEL_TITLE
import com.aeswox.arcplayer.cloud.formatCloudBytes
import com.aeswox.arcplayer.metadata.AbsoluteSeasons
import com.aeswox.arcplayer.metadata.EpisodeDetail
import com.aeswox.arcplayer.metadata.LookupRequest
import com.aeswox.arcplayer.metadata.MediaCard
import com.aeswox.arcplayer.metadata.MediaGuess
import com.aeswox.arcplayer.metadata.MediaLibrary
import com.aeswox.arcplayer.metadata.MediaNameParser
import com.aeswox.arcplayer.metadata.PosterCache
import com.aeswox.arcplayer.metadata.PlayOrder
import com.aeswox.arcplayer.metadata.ReleaseInfo
import com.aeswox.arcplayer.metadata.ShelfSearch
import com.aeswox.arcplayer.metadata.TmdbDetails
import com.aeswox.arcplayer.metadata.TmdbSearch
import com.aeswox.arcplayer.metadata.TmdbSearchResult
import com.aeswox.arcplayer.metadata.episodeLabel
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
import com.aeswox.arcplayer.stream.ByteRate
import com.aeswox.arcplayer.stream.BufferGuard
import dev.chrisbanes.haze.HazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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

    private var introNotDrawnYet = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemPanel = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Android's own splash panel would show the launcher icon and then crossfade into the app — a
        // second, frozen clapperboard stacked on top of the one the intro draws. It is not removed, only
        // kept up until the app has drawn its first frame: the handover is black onto black, and the
        // only mark ever on screen is the animated one.
        systemPanel.setKeepOnScreenCondition { introNotDrawnYet }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            ArcPlayerTheme {
                ClapperIntroGate(
                    onFirstFrame = { introNotDrawnYet = false }
                ) {
                    ArcPlayerHomeScreen()
                }
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
// THEME & SHARED CONSTANTS (Frosted Jelly dark foundation)
// ============================================================================

private val OneUiBackground = JellyBackground
private val OneUiTextPrimary = JellyOnSurface
private val OneUiTextSecondary = JellyOnSurfaceVariant
private val OneUiCardSurface = JellySurface
private val OneUiPillSurface = JellySurfaceContainer
private val OneUiPillSelected = JellySurfaceContainerHigh
private val OneUiSelectedFill = JellyPrimary
private val OneUiOnSelectedFill = JellyOnPrimary
private val OneUiAccent = JellyAccent
private val OneUiDestructive = JellyError

/** A flat grey block reads as a finished, empty cell; this is the surface the skeleton sweep crosses. */
private val SlotWaitingTint = Color.White.copy(alpha = 0.06f)

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

/** Subtitle lift is asked for as a share of the picture's height, and 40 % is already most of a line. */
private const val SUBTITLE_LIFT_MAX_PERCENT = 40

/**
 * Safe baseline margin (~6% of picture height) so subtitles never clip against the bottom
 * of the phone in Cinema fill (2.35:1) mode or collide with the system gesture bar in Fit mode.
 */
private const val SUBTITLE_BASE_MARGIN_PERCENT = 6
private const val SUBTITLE_LIFT_MIN_PERCENT = -SUBTITLE_BASE_MARGIN_PERCENT

/** The band the size control is allowed to move inside, measured against the size the player uses today. */
private const val SUBTITLE_SIZE_MIN_PERCENT = 60
private const val SUBTITLE_SIZE_MAX_PERCENT = 200

/** One tap on either control. Small enough that a viewer can stop on the look they came for. */
private const val SUBTITLE_LIFT_STEP_PERCENT = 4
private const val SUBTITLE_SIZE_STEP_PERCENT = 20

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
    var doubleTapSeekSeconds by mutableIntStateOf(10)
        private set

    /**
     * How the viewer wants their subtitles: a lift off the bottom of the picture as a share of its
     * height, and a font size as a share of the one the player draws by default.
     *
     * Kept here rather than in the engine because the engine is thrown away every time the player
     * closes, and because libVLC reads both numbers only as a media opens — so the next film has to be
     * able to pick the answer up without anything else having to remember to hand it over.
     */
    var subtitleLiftPercent by mutableIntStateOf(0)
        private set
    var subtitleSizePercent by mutableIntStateOf(100)
        private set

    /**
     * Tactile feedback, mirrored straight into [AppHaptics] on every change.
     *
     * The engine is a process-wide object holding a vibrator handle, and a control halfway down a
     * list has no business asking the preferences file what the viewer chose — so the answer lives in
     * both places: here as observable state for the Settings row, and there for everything that knocks.
     */
    var hapticsEnabled by mutableStateOf(true)
        private set
    var hapticIntensity by mutableStateOf(HapticIntensity.MEDIUM)
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
     * Whether the first-launch tour of the Telegram setup has already been put in front of the viewer.
     *
     * Stamped when the page is shown, not when it is dismissed: the promise made to the viewer is that
     * this happens once, and an install killed halfway across the page has still had its one showing.
     */
    var telegramWelcomeSeen by mutableStateOf(false)
        private set

    /**
     * The build the viewer last turned down.
     *
     * Held as a version rather than as a date or a switch, because the promise a dismissal makes is about
     * that one release: the next is a different thing to consider, and an app that nagged about it would
     * be an app switched off at the first opportunity.
     */
    var updateDismissedVersion by mutableStateOf("")
        private set

    /**
     * When each file was last played, keyed by its stable id — from the channel or off this phone.
     *
     * The one figure that lets a shelf card say "Resume" instead of "Play" when the copy the viewer last
     * reached for is not the one its name ranks best, and the reason the Continue Watching rail can order
     * itself by what was watched rather than by what was uploaded. Keyed by the id the file is played
     * under — MediaStore's own for a device file, the derived one for a Telegram copy — so both sorts land
     * in the same map and a locally watched film moves the rail exactly as a channel one does.
     */
    val watchedAt = mutableStateMapOf<Long, Long>()

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
            val loadedSeekSec = prefs.getInt("double_tap_seek_seconds", 10)
            doubleTapSeekSeconds = if (loadedSeekSec in listOf(5, 10, 15, 20)) loadedSeekSec else 10
            subtitleLiftPercent = prefs.getInt("subtitle_lift_pct", 0).coerceIn(SUBTITLE_LIFT_MIN_PERCENT, SUBTITLE_LIFT_MAX_PERCENT)
            subtitleSizePercent = prefs.getInt("subtitle_size_pct", 100).coerceIn(SUBTITLE_SIZE_MIN_PERCENT, SUBTITLE_SIZE_MAX_PERCENT)

            hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
            hapticIntensity = HapticIntensity.fromName(prefs.getString("haptic_intensity", null))
            AppHaptics.isEnabled = hapticsEnabled
            AppHaptics.intensity = hapticIntensity

            // Credentials typed into the Cloud tab win over whatever was compiled in.
            telegramApiId = prefs.getInt("tg_api_id", 0)
            telegramApiHash = prefs.getString("tg_api_hash", "").orEmpty()

            movieFolderChatId = prefs.getLong("movie_folder_chat_id", 0L)
            telegramWelcomeSeen = prefs.getBoolean("telegram_welcome_seen", false)
            updateDismissedVersion = prefs.getString("update_dismissed_version", "").orEmpty()

            val watchedJson = JSONObject(prefs.getString("cloud_watched_at", "{}") ?: "{}")
            watchedJson.keys().forEach { key ->
                val id = key.toLongOrNull()
                val at = watchedJson.optLong(key, 0L)
                if (id != null && at > 0L) watchedAt[id] = at
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
                        isSeries = card.optBoolean("isSeries"),
                        // Absent on every card written before the stamp existed, which reads as 0 and
                        // so counts as an answer owed a second look.
                        rules = card.optInt("rules")
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
                    .put("rules", card.rules)
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
     * A shelf warming pass writes every title it holds, so the wide artwork is known before a card is
     * tapped; `persist` is false there because thirty separate writes of the whole blob is thirty times
     * the disk work one batched write costs.
     */
    fun rememberTitleDetail(providerId: Int, isSeries: Boolean, detail: TitleDetail, persist: Boolean = true) {
        titleDetails[titleDetailKey(providerId, isSeries)] = detail
        if (persist) persistTitleDetails()
    }

    /** The whole detail store as one blob, so a warm pass writes it once rather than thirty times. */
    fun persistTitleDetails() {
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

    /** Stamp that the first-launch tour has been shown, so it never shows a second time. */
    fun rememberTelegramWelcome() {
        telegramWelcomeSeen = true
        prefs.edit().putBoolean("telegram_welcome_seen", true).apply()
    }

    /** Turn one release down. The next one is still welcome to ask. */
    fun dismissUpdate(version: String) {
        updateDismissedVersion = version
        prefs.edit().putString("update_dismissed_version", version).apply()
    }

    /**
     * Mark a file as the most recently watched thing on the device.
     *
     * Written when playback opens rather than when it closes: a film abandoned two minutes in is still
     * the film the viewer last reached for, and it is the one most likely to be reached for again
     * tonight.
     *
     * The stored key still says "cloud" from when only a Telegram copy could be watched. Renaming it
     * would cost every install its place in the rail.
     */
    fun rememberWatchedAt(videoId: Long) {
        watchedAt[videoId] = System.currentTimeMillis()
        val obj = JSONObject()
        watchedAt.forEach { (id, at) -> obj.put(id.toString(), at) }
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

    fun updateDoubleTapSeekSeconds(seconds: Int) {
        val clamped = when {
            seconds <= 7 -> 5
            seconds <= 12 -> 10
            seconds <= 17 -> 15
            else -> 20
        }
        doubleTapSeekSeconds = clamped
        prefs.edit().putInt("double_tap_seek_seconds", clamped).apply()
    }

    fun updateSubtitleStyle(liftPercent: Int, sizePercent: Int) {
        subtitleLiftPercent = liftPercent.coerceIn(SUBTITLE_LIFT_MIN_PERCENT, SUBTITLE_LIFT_MAX_PERCENT)
        subtitleSizePercent = sizePercent.coerceIn(SUBTITLE_SIZE_MIN_PERCENT, SUBTITLE_SIZE_MAX_PERCENT)
        prefs.edit()
            .putInt("subtitle_lift_pct", subtitleLiftPercent)
            .putInt("subtitle_size_pct", subtitleSizePercent)
            .apply()
    }

    fun updateHaptics(enabled: Boolean) {
        hapticsEnabled = enabled
        AppHaptics.isEnabled = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun updateHapticIntensity(level: HapticIntensity) {
        hapticIntensity = level
        AppHaptics.intensity = level
        prefs.edit().putString("haptic_intensity", level.name).apply()
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
    override val title: String,
    val bucketId: String,
    val folderName: String,
    override val durationMs: Long,
    val durationFormatted: String,
    override val sizeBytes: Long,
    val dateAddedSec: Long,
    /**
     * MediaStore's own path, like `Movies/` or `DCIM/Camera/`.
     *
     * Kept because the leaf name alone cannot tell a film from a messenger's saved clip: one lives a
     * whole floor down, in `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/`.
     */
    val relativePath: String = "",
    override val frameHeight: Int = 0,
) : Playable {
    override val stableId: Long get() = id
    override val arrivedAtSec: Long get() = dateAddedSec
    override val sizeFormatted: String get() = formatCloudBytes(sizeBytes)

    /**
     * A grid row is not proof of where the file lives, only of the key it is filed under: a Telegram
     * video is presented as one of these, and [CloudIds] gives it a negative key for exactly this
     * reason. So the sign answers the question even when the row came off the channel.
     */
    override val isOnPhone: Boolean get() = id >= 0L

    override fun withLength(learnedMs: Long): VideoItem =
        copy(durationMs = maxOf(durationMs, learnedMs))
}

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
enum class BottomNavTab(val label: String) {
    HOME("Home"), SEARCH("Search"), LIBRARY("Library"), SETTINGS("Settings")
}

private enum class LibraryPage(val headerTitle: String) {
    VIDEOS("All videos"), FOLDERS("On Device"), CLOUD("Collection"), MENU("Settings"),
    DOWNLOADS("Downloads")
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
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.MediaColumns.HEIGHT
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
            // Height tells the shelf whether a file whose name says nothing is a 1080p rip or a 480p one,
            // which is the same evidence Telegram's own frame size gives for a channel file. It is asked
            // leniently because it is a bonus rather than a requirement: some scanners publish video rows
            // with no dimensions at all, and a missing column is worth less than the whole library.
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

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
                        dateAddedSec = dateAddedSec,
                        relativePath = relativePath,
                        frameHeight = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    )
                )
            }
        }
    }

    MediaLibrarySnapshot(
        videos = videos,
        folders = foldersOf(videos)
    )
}

/**
 * The folders one pile of files fills, in the order the browser shows them.
 *
 * Rebuilt from a pile rather than filtered from the folders of a bigger one, because a folder's count,
 * cover and "recent" tick all belong to the files actually standing in it. Narrow the library by
 * dropping half the rows and a card still saying "12 videos" over a grid of three is a card that lies.
 */
private fun foldersOf(videos: List<VideoItem>): List<VideoFolderItem> {
    val recentThresholdSec = System.currentTimeMillis() / 1000L - 86_400L
    return videos
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
}

/**
 * This library narrowed to the files that belong to a film or an episode.
 *
 * The phone's video table is the camera roll, the screen recordings and last Tuesday's clip together
 * with the movies, and the viewer has asked to be shown the movies. Which rows those are is not decided
 * twice: [kept] is the shelf's own answer — the one pass that reads a file name against a catalogue —
 * so this is a filter over the ids it recognised, with the folders rebuilt from what survived.
 */
internal fun MediaLibrarySnapshot.filmsOnly(kept: Set<Long>): MediaLibrarySnapshot {
    val films = videos.filter { it.id in kept }
    return if (films.size == videos.size) this else copy(videos = films, folders = foldersOf(films))
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

object SeekPreviewCache {
    private val maxKilobytes = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(16 * 1024)

    private val lruCache = object : LruCache<String, Bitmap>(maxKilobytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(uri: Uri, bucketMs: Long): Bitmap? = lruCache.get("${uri}_$bucketMs")

    fun put(uri: Uri, bucketMs: Long, bitmap: Bitmap) {
        lruCache.put("${uri}_$bucketMs", bitmap)
    }

    fun clear() {
        lruCache.evictAll()
    }
}

class SeekPreviewSession(
    private val appContext: Context,
    val uri: Uri
) {
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var retriever: MediaMetadataRetriever? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var isInitialized = false
    private var isFailed = false

    private fun openDataSource(ret: MediaMetadataRetriever): ParcelFileDescriptor? {
        if (uri.scheme == ContentResolver.SCHEME_FILE || uri.scheme == null) {
            val path = uri.path
            if (!path.isNullOrBlank() && File(path).exists()) {
                ret.setDataSource(path)
                return null
            }
        }

        // 1. ContentResolver openFileDescriptor (best for content URIs, MediaStore, SAF, Downloads)
        val pfd = runCatching {
            appContext.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull()

        if (pfd != null) {
            try {
                ret.setDataSource(pfd.fileDescriptor)
                return pfd
            } catch (t: Throwable) {
                runCatching { pfd.close() }
            }
        }

        // 2. Query MediaStore DATA column if available
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val dataPath = runCatching {
                val proj = arrayOf(MediaStore.Video.Media.DATA)
                appContext.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
                }
            }.getOrNull()

            if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                try {
                    ret.setDataSource(dataPath)
                    return null
                } catch (_: Throwable) {}
            }
        }

        // 3. Fallback: Context + Uri
        ret.setDataSource(appContext, uri)
        return null
    }

    fun warmUp(initialPositionMs: Long = 0L) {
        sessionScope.launch {
            val bucketMs = ((initialPositionMs / 2500L) * 2500L).coerceAtLeast(0L)
            getFrame(bucketMs)
        }
    }

    fun prefetchNeighbors(centerBucketMs: Long, durationMs: Long) {
        sessionScope.launch {
            val offsets = longArrayOf(2500L, -2500L, 5000L, -5000L)
            for (offset in offsets) {
                if (!isActive) break
                val targetMs = centerBucketMs + offset
                if (targetMs in 0L..durationMs) {
                    if (SeekPreviewCache.get(uri, targetMs) == null) {
                        getFrame(targetMs)
                    }
                }
            }
        }
    }

    suspend fun getFrame(
        bucketMs: Long,
        targetWidthPx: Int = 220,
        targetHeightPx: Int = 124
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cached = SeekPreviewCache.get(uri, bucketMs)
        if (cached != null) return@withContext cached

        mutex.withLock {
            val doubleCheck = SeekPreviewCache.get(uri, bucketMs)
            if (doubleCheck != null) return@withLock doubleCheck
            if (isFailed) return@withLock null

            if (!isInitialized) {
                try {
                    val ret = MediaMetadataRetriever()
                    activePfd = openDataSource(ret)
                    retriever = ret
                    isInitialized = true
                } catch (t: Throwable) {
                    isFailed = true
                    closeInternal()
                    return@withLock null
                }
            }

            val ret = retriever ?: return@withLock null
            val timeUs = (bucketMs * 1000L).coerceAtLeast(0L)

            var bmp: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                bmp = runCatching {
                    ret.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidthPx,
                        targetHeightPx
                    )
                }.getOrNull()
            }
            if (bmp == null) {
                bmp = runCatching {
                    ret.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull()
            }
            if (bmp == null) {
                bmp = runCatching {
                    ret.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                }.getOrNull()
            }

            if (bmp != null) {
                if (bmp.width > targetWidthPx * 1.4f || bmp.height > targetHeightPx * 1.4f) {
                    val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                    val dstW = targetWidthPx
                    val dstH = (targetWidthPx / aspect).toInt().coerceAtLeast(1)
                    val scaled = runCatching {
                        Bitmap.createScaledBitmap(bmp, dstW, dstH, true)
                    }.getOrNull()
                    if (scaled != null && scaled !== bmp) {
                        bmp.recycle()
                        bmp = scaled
                    }
                }
                SeekPreviewCache.put(uri, bucketMs, bmp)
            }
            bmp
        }
    }

    private fun closeInternal() {
        runCatching { retriever?.close() }
        runCatching { activePfd?.close() }
        retriever = null
        activePfd = null
    }

    fun close() {
        sessionScope.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                closeInternal()
            }
        }
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
    initialSubtitleLiftPercent: Int = 0,
    initialSubtitleSizePercent: Int = 100
) : IVLCVout.OnNewVideoLayoutListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * The viewer's subtitle placement: a lift off the bottom of the picture and a size against
     * VLC's own.
     *
     * Declared above [libVlc] because its numbers go into the arguments that instance is built with,
     * and a property read before its own initialiser runs reads zero rather than what the viewer asked.
     */
    var subtitleLiftPercent by mutableIntStateOf(
        initialSubtitleLiftPercent.coerceIn(SUBTITLE_LIFT_MIN_PERCENT, SUBTITLE_LIFT_MAX_PERCENT)
    )
        private set
    var subtitleSizePercent by mutableIntStateOf(
        initialSubtitleSizePercent.coerceIn(SUBTITLE_SIZE_MIN_PERCENT, SUBTITLE_SIZE_MAX_PERCENT)
    )
        private set

    /** Asked to remember the two numbers above, so the next film — and the next launch — starts with them. */
    var onSubtitleStyleChange: ((liftPercent: Int, sizePercent: Int) -> Unit)? = null

    /** Height of the surface the picture is drawn into, which is what a lift in percent is measured against. */
    private var subtitleSurfaceHeightPx = 0

    /**
     * The picture height a subtitle lift is measured against.
     *
     * VLC counts the margin in pixels from the bottom of what it draws into, so the surface itself is
     * the honest answer. Before the first layout there is none, and the shorter edge of the screen is
     * used — the same number a full-screen landscape player hands back a moment later.
     */
    private fun subtitleWindowHeightPx(): Int =
        if (subtitleSurfaceHeightPx > 0) subtitleSurfaceHeightPx
        else minOf(
            appContext.resources.displayMetrics.widthPixels,
            appContext.resources.displayMetrics.heightPixels
        )

    /**
     * Every argument this libVLC instance is built with, the two subtitle numbers among them.
     *
     * They can only be said here. VLC reads a caption's margin and text scale when it makes the
     * subpicture thread that belongs to an instance, and never asks the media about them again — a
     * `:sub-margin` or `:sub-text-scale` handed to a [Media] is dropped on the floor, which is how
     * both first looked wired up and did nothing. So a style change builds a new instance rather than
     * a new media, which is what [restartVlcInstance] is for.
     */
    private fun vlcInstanceArgs(): ArrayList<String> {
        val args = arrayListOf(
            "--avcodec-hw=any",
            "--android-display-chroma=RV32",
            "--no-drop-late-frames",
            "--no-skip-frames"
        )
        val effectiveLiftPercent = (SUBTITLE_BASE_MARGIN_PERCENT + subtitleLiftPercent).coerceIn(0, SUBTITLE_LIFT_MAX_PERCENT)
        if (effectiveLiftPercent > 0) {
            args += "--sub-margin=${subtitleWindowHeightPx() * effectiveLiftPercent / 100}"
        }
        if (subtitleSizePercent != 100) {
            args += "--sub-text-scale=$subtitleSizePercent"
        }
        return args
    }

    private var libVlc: LibVLC = LibVLC(appContext, vlcInstanceArgs())

    var mediaPlayer: MediaPlayer = MediaPlayer(libVlc)
        private set

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

    /**
     * Whether the input itself refused the file being opened, rather than merely going quiet on it.
     *
     * Set only for a file that has never shown a picture, which is what separates the two: a copy of a
     * film that has already painted and then hits an error is a download that fell over, and the
     * watchdog's job is to re-open the same bytes. A copy that never painted at all is a file this
     * player cannot read, and the shelf usually holds another one of the same part.
     */
    var openRefused by mutableStateOf(false)
        private set

    /** Whether the file currently loaded has painted once, kept across a stall re-open of the same file. */
    private var hasPaintedForLoadedVideo = false

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

    /**
     * Whether [videoAspectRatio] is the film's own or the placeholder the layout is drawn against
     * before libVLC has reported a video track.
     *
     * The placeholder is 16:9, so anything that decides orientation from the number alone would send a
     * vertical clip to landscape and back again a few hundred milliseconds later.
     */
    var videoLayoutKnown by mutableStateOf(false)
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
     * The item [loadAndPlay] was last handed, uri and all.
     *
     * Held because a subtitle style change has to reopen the same file to take effect, and by then the
     * screen is showing the film, not deciding which copy of it to open.
     */
    private var currentVideo: VideoItem? = null

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
        bindPlayerEvents()
    }

    /**
     * Hand the player now owned by this engine its events.
     *
     * Kept out of the constructor because [restartVlcInstance] builds a new player, and a new player
     * tells this engine nothing until it is told to listen again.
     */
    private fun bindPlayerEvents() {
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
                    isAtEnd = true
                }

                MediaPlayer.Event.Vout -> {
                    if (event.voutCount > 0) {
                        isFirstFrameRendered = true
                        hasPaintedForLoadedVideo = true
                        refreshTracks()
                        publishVideoLayout()
                        mainHandler.post { applyContentFrameRate() }
                    }
                }

                // The one signal that separates a file this player cannot read from a download that has
                // gone quiet: both leave the player equally still, and only the first is worth answering
                // with a different copy of the same film.
                MediaPlayer.Event.EncounteredError -> {
                    if (!hasPaintedForLoadedVideo) openRefused = true
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
        currentVideo = video
        isFirstFrameRendered = false
        bufferedSeek?.cancel()
        seekHold = null
        isSeekWaiting = false
        pausedForSeek = false
        stoppedOnPurpose = false
        isBuffering = false
        videoLayoutKnown = false
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
            // A different file is a different chance to paint, so the refusal recorded against the last
            // one — and the fact that this one has not been seen yet — start from nothing.
            hasPaintedForLoadedVideo = false
            openRefused = false
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
            subtitleSurfaceHeightPx = textureView.height
        }
        vout.attachViews(this)
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && mediaPlayer.vlcVout.areViewsAttached()) {
            mediaPlayer.vlcVout.setWindowSize(width, height)
            subtitleSurfaceHeightPx = height
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
                    videoLayoutKnown = true
                }
            }
        }
    }

    /**
     * The film's own shape, read off the track the decoder is actually showing.
     *
     * [onNewVideoLayout] is the documented source for this and it never answers on a real phone:
     * measured on a Note10+, every call it made carried six zeros, so the layout kept drawing against
     * the 16:9 placeholder and nothing downstream ever learned the film was widescreen. The selected
     * video track holds the same numbers — the coded size, plus the sample aspect ratio that says how
     * wide each of those pixels is — and the player is already asked for it once the picture exists,
     * which is why this runs from the same event rather than on a poll of its own.
     */
    private fun publishVideoLayout() {
        val track = runCatching { mediaPlayer.currentVideoTrack }.getOrNull() ?: return
        val width = track.width
        val height = track.height
        if (width <= 0 || height <= 0) return
        val sar = if (track.sarNum > 0 && track.sarDen > 0) {
            track.sarNum.toFloat() / track.sarDen.toFloat()
        } else {
            1f
        }
        val ratio = (width * sar) / height.toFloat()
        if (ratio in 0.25f..4.0f) {
            videoAspectRatio = ratio
            videoLayoutKnown = true
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
                // Starting over is the one backward jump that is not a stale report, and a hold still
                // armed from the seek to the end would drop every tick the replay produces.
                seekHold = null
            }
            pausedForSeek = false
            stoppedOnPurpose = false
            mediaPlayer.play()
        }
    }

    fun seekTo(targetMs: Long) {
        // A play head sent somewhere new is no longer at the end, and saying so here is what lets a
        // rewind during the next-up countdown take the card back off.
        isAtEnd = false
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
            val now = SystemClock.elapsedRealtime()
            // The tick that lands a seek does not retire the hold: libVLC still has reports from the
            // position the seek left, and the first of them to arrive after the landing is what pulls
            // the scrubber back and turns the one download window around.
            if (hold.isExpired(now)) seekHold = null
            else if (hold.isStale(timeMs, now)) return
        }
        // A play head that moves somewhere new is the answer the seek was owed, so the debt is paid
        // here and nowhere else. Left stamped, it would read as "still catching up" for the rest of
        // the film, and that keeps the download window parked wherever the resume point landed —
        // the player then runs out of file with a watchdog that refuses to move it.
        if (playOwedSinceMs != BufferGuard.NEVER && timeMs != currentPositionMs) {
            playOwedSinceMs = BufferGuard.NEVER
        }
        // The end is only a fact until the play head moves again: a rewind, a replay from the start,
        // or a stall recovery that finds bytes after a hole all report ticks well short of the
        // length, and each one is the ending not having been an ending.
        if (isAtEnd && timeMs < durationMs - NextUp.HONEST_END_TOLERANCE_MS) isAtEnd = false
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

    /** Push the subtitles further up from the bottom of the picture. */
    fun adjustSubtitleLift(deltaPercent: Int) {
        setSubtitleStyle(subtitleLiftPercent + deltaPercent, subtitleSizePercent)
    }

    /** Draw the subtitles bigger or smaller. */
    fun adjustSubtitleSize(deltaPercent: Int) {
        setSubtitleStyle(subtitleLiftPercent, subtitleSizePercent + deltaPercent)
    }

    fun resetSubtitleLift() {
        setSubtitleStyle(0, subtitleSizePercent)
    }

    fun resetSubtitleSize() {
        setSubtitleStyle(subtitleLiftPercent, 100)
    }

    private fun setSubtitleStyle(liftPercent: Int, sizePercent: Int) {
        val nextLift = liftPercent.coerceIn(SUBTITLE_LIFT_MIN_PERCENT, SUBTITLE_LIFT_MAX_PERCENT)
        val nextSize = sizePercent.coerceIn(SUBTITLE_SIZE_MIN_PERCENT, SUBTITLE_SIZE_MAX_PERCENT)
        if (nextLift == subtitleLiftPercent && nextSize == subtitleSizePercent) return
        subtitleLiftPercent = nextLift
        subtitleSizePercent = nextSize
        onSubtitleStyleChange?.invoke(nextLift, nextSize)
        restartVlcInstance()
    }

    /**
     * Build a new libVLC around the same film, at the same second, so the two numbers above reach it.
     *
     * VLC speaks them once, when an instance makes the subpicture thread that draws captions, so a
     * change needs a new instance — a new [Media] is not enough. Everything a re-open already carries
     * survives: the resume point, the chosen tracks, the subtitle sync — which is what makes this a
     * blink rather than a rewind. And like every other open it ends playing, so a film stopped on a
     * frame starts moving again when its subtitles are moved.
     *
     * Nothing may outlive the instance it was made from, so the player is released before it, the
     * surface goes with the player and comes back attached to its replacement.
     */
    private fun restartVlcInstance() {
        val video = currentVideo ?: return
        val positionMs = currentPositionMs
        val textureView = attachedTextureView

        detachTextureView()
        runCatching { mediaPlayer.stop() }
        mediaPlayer.release()
        libVlc.release()

        libVlc = LibVLC(appContext, vlcInstanceArgs())
        mediaPlayer = MediaPlayer(libVlc)
        bindPlayerEvents()
        textureView?.let { attachTextureView(it) }

        loadAndPlay(video, startPositionMs = positionMs)
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
    val titleCard: MediaCard? = null,
    /**
     * Why the file now playing was handed over by a sibling that refused, and which files of this
     * title have already been refused.
     *
     * Both ride this object rather than the player's own state because the switch remounts the player:
     * its content is keyed on the playback it was opened with, so a new [video] is a new composition
     * and everything it remembered is gone — including the count that keeps two broken copies from
     * trading places forever, and the sentence that tells the viewer their tap did not go for nothing.
     */
    val switchReason: String? = null,
    val refusedCopies: Set<Long> = emptySet()
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
    val destinationState = rememberSaveableStateHolder()

    var mainTabName by rememberSaveable { mutableStateOf(BottomNavTab.HOME.name) }
    val mainTab = BottomNavTab.valueOf(mainTabName)
    var libraryPageName by rememberSaveable { mutableStateOf<String?>(null) }
    val livePage = if (mainTab == BottomNavTab.SETTINGS) LibraryPage.MENU
        else libraryPageName?.let(LibraryPage::valueOf) ?: LibraryPage.CLOUD
    val showingBrowser = mainTab == BottomNavTab.SETTINGS ||
        (mainTab == BottomNavTab.LIBRARY && libraryPageName != null)

    // The browser's page, held even while the browser is gone. AnimatedContent keeps the leaving page
    // composed and re-runs its lambda against whatever the enclosing scope now says, so tapping Home from
    // the Settings menu used to rebuild the slide-away copy as the shelf: a different grid state, a
    // different header title, and the whole browser pass over again between the tap and the first frame.
    // A plain array, not a state holder — this scope writes it and reads it back in the same pass.
    val lastBrowserPage = remember { arrayOf(LibraryPage.MENU) }
    if (showingBrowser) lastBrowserPage[0] = livePage
    val selectedTab = if (showingBrowser) livePage else lastBrowserPage[0]

    // Settings is not a page of the tab stack — it is the browser wearing a tab, and CollectionScreens
    // draws nothing for it. The stack still has to hold up the page you came from while the browser
    // slides over it, so the last real tab is kept beside mainTab rather than read back out of it.
    // Written here and read only deeper down, so the write cannot re-trigger this scope.
    val stackTabName = remember { mutableStateOf(BottomNavTab.HOME.name) }
    if (mainTab != BottomNavTab.SETTINGS) stackTabName.value = mainTab.name

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
    var telegramOnboardingStarted by rememberSaveable { mutableStateOf(false) }
    var cloudNotice by remember { mutableStateOf<String?>(null) }
    val openLibraryBrowser: (LibraryPage, CloudScreen) -> Unit = { page, screen ->
        telegramOnboardingStarted = false
        mainTabName = BottomNavTab.LIBRARY.name
        libraryPageName = page.name
        cloudScreenName = screen.name
        openedFolderBucketId = null
        openedPlaylistName = null
        isSearchExpanded = false
        searchQuery = ""
    }

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
    // The shelf's own recede while a title page slides in over it. Frosted Jelly runs a detail push as
    // two independent destination transitions rather than one linked number: the screen leaving dims on
    // the shorter fade and shrinks on the longer slide, which is what gives the incoming page depth.
    // Going back is not the same move played backwards — the reference lets the home screen spring the
    // last stretch into place, so the shelf overshoots its full size a hair and settles.
    val detailOpen = shelfTitle != null
    val shelfRecede by animateFloatAsState(
        targetValue = if (detailOpen) 1f else 0f,
        animationSpec = if (detailOpen) tween(durationMillis = 600, easing = DetailPushEase)
        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ShelfRecedeScale"
    )
    val shelfDim by animateFloatAsState(
        targetValue = if (detailOpen) 1f else 0f,
        animationSpec = if (detailOpen) tween(durationMillis = 400)
        else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ShelfRecedeFade"
    )
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
    val telegramAuth by cloudRepository.authState.collectAsState()
    val showingTelegramSetup = showingBrowser && livePage == LibraryPage.CLOUD &&
        cloudScreenName == CloudScreen.SHELF.name &&
        (telegramAuth.stage != CloudAuthStage.AUTHORIZED || telegramOnboardingStarted)

    // The tour is the front door, so on a first install it opens by itself. Held until the clapperboard
    // has lifted: navigating underneath the overlay left the browser mid-slide when the mark went away.
    // A chat id on disk is the only thing that vetoes it — it means this install already made a
    // collection, whatever the session happens to report tonight — and skipping it leaves the
    // "Connect Telegram" pill on Home exactly where it always was.
    LaunchedEffect(Unit) {
        AppIntro.awaitHomeRelease()
        if (!prefsStore.telegramWelcomeSeen && prefsStore.movieFolderChatId == 0L) {
            prefsStore.rememberTelegramWelcome()
            openLibraryBrowser(LibraryPage.CLOUD, CloudScreen.SHELF)
        }
    }

    // The updater asks once per launch, and only after the heavy work has landed: `awaitHomeRelease` is
    // the same hand the clapperboard, the staggered entrances and the stranded-download sweep wait on, and
    // the extra beat keeps the answer from arriving while the shelf is still assembling itself. Settings
    // asks the same question on tap, which is why the answers are kept apart below rather than collapsed
    // into a null — a card nobody asked for says nothing when GitHub cannot be reached, but a button
    // someone pressed has to say which of the two happened.
    val updateManager = remember { UpdateManager(context) }
    var updateRelease by remember { mutableStateOf<Release?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateUnreachable by remember { mutableStateOf(false) }
    var updateDismissed by remember { mutableStateOf(false) }
    var updateDownload by remember { mutableStateOf<UpdateDownload>(UpdateDownload.Idle) }
    val checkForUpdates: () -> Unit = {
        if (!updateChecking) {
            updateChecking = true
            updateUnreachable = false
            coroutineScope.launch {
                when (val answer = updateManager.check()) {
                    is UpdateCheck.Available -> updateRelease = answer.release
                    // A clean "nothing newer" also retires an offer from a check that guessed wrong.
                    UpdateCheck.Current -> updateRelease = null
                    // A release found by an earlier answer survives a flaky network.
                    UpdateCheck.Unreachable -> updateUnreachable = true
                }
                updateChecking = false
            }
        }
    }
    LaunchedEffect(Unit) {
        AppIntro.awaitHomeRelease()
        delay(1_200)
        checkForUpdates()
    }
    // "Later" is a decision about the card, not about the release — Settings still offers it.
    val updateOffer = updateRelease?.takeUnless { it.version == prefsStore.updateDismissedVersion }
    val offerUpdate: (Release) -> Unit = { release ->
        updateManager.download(coroutineScope, release) { updateDownload = it }
    }
    val installUpdate: () -> Unit = {
        // Bytes first, permission second: one tap always does something, and asking for a setting before
        // there is a file to install only spends a trip to the system screens.
        if (updateManager.install()) updateDismissed = true
        else runCatching { context.startActivity(updateManager.installPermissionRequest()) }
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

    // A page pushed over the search field does not take the field's focus away — it keeps sitting there
    // behind the slide, so the keyboard it asked for stays standing over the page the tap just opened.
    // The cover puts the keyboard down itself. Both halves, because dropping focus does not reliably
    // recall an IME that another window is already holding.
    val screenFocus = LocalFocusManager.current
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(shelfTitle, activePlayback) {
        if (shelfTitle != null || activePlayback != null) {
            screenFocus.clearFocus(true)
            softwareKeyboard?.hide()
        }
    }

    /**
     * Which copies this device is holding, read once for the whole screen.
     *
     * Collected here rather than inside the grid or a title page because both of those decide *which file
     * to open*, and a shelf rebuilt around a stale answer would play the streamed copy of a film the
     * viewer just saved. The ledger arrives from disk on its own guard, so this starts empty and fills in
     * rather than blocking a frame to be certain.
     */
    val cloudDownloads by cloudRepository.downloads.collectAsState()

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
     *
     * Only a channel copy is held. A file that was already on the phone has nothing to fetch and nothing
     * to hand back, so moving onto one puts down whatever Telegram copy the player was reading — which is
     * the point of the whole map, and the reason it goes empty rather than staying on the last download.
     */
    fun holdShelfCopy(incoming: Playable) {
        val outgoing = lastCloudPlayback
        val incomingCopy = incoming as? CloudVideoItem
        if (outgoing != null && outgoing.fileId != incomingCopy?.fileId) forgetCloudCopySoon(outgoing)
        if (incomingCopy == null) {
            lastCloudPlayback = null
            return
        }
        cloudReleases.remove(incomingCopy.fileId)?.cancel()
        lastCloudPlayback = incomingCopy
    }

    /**
     * Hand a file to the player, from either place its bytes live.
     *
     * A Telegram copy has no content Uri to hand over, and working out which bytes the channel has on
     * disk is the slow part — so it is not attempted here. The player is given an empty address, opens at
     * once with the film's wordmark over a held frame, and asks Telegram for the path itself. A copy
     * already on the phone comes with its own address and never enters that path.
     *
     * The ◀▶ part skip has always launched this way; a Play button that resolved first only moved the
     * same wait onto the info page, where it looked like the app had frozen.
     */
    val launchShelfVideo: (Playable, List<Playable>, List<List<Long>>, MediaCard?) -> Unit =
        { item, list, slots, titleCard ->
            cloudNotice = null
            holdShelfCopy(item)
            val prefix = item.morphPrefix()
            morphingVideoKey = "${prefix}_${item.stableId}"
            prefsStore.rememberWatchedAt(item.stableId)
            // The queue is the whole work, and the player only ever sees rows of one shape: it opens a
            // Uri when there is one, and asks the channel for the file behind a negative id when there
            // is not. Which of those a given row needs is a question the row answers by itself.
            val rows = list.map { it.toPlayerRow() }
            activePlayback = ActiveVideoPlayback(
                video = rows.firstOrNull { it.id == item.stableId } ?: item.toPlayerRow(),
                playlist = rows,
                sharedKeyPrefix = prefix,
                cloudSources = list.filterIsInstance<CloudVideoItem>(),
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
     * of a group would leave the same poster on screen with a smaller pile behind it. Only the copies
     * that came off the channel are in that group, though. A file the viewer put on this phone is not
     * this app's to delete, and saying "taken off your shelf" while it sits on in `Movies/` would be a
     * report about a shelf that still holds the thing.
     */
    val removeFromShelf: (ShelfTitle) -> Unit = { opened ->
        shelfTitle = null
        coroutineScope.launch {
            cloudResolving = true
            val folder = prefsStore.movieFolderChatId
            val onChannel = opened.parts.filterIsInstance<CloudVideoItem>()
            val removed = onChannel.isNotEmpty() && runCatching {
                onChannel.forEach { cloudRepository.removeFromFolder(it, folder) }
            }.isSuccess
            if (removed) runCatching { cloudRepository.loadVideos(folder) }
            cloudResolving = false
            cloudNotice = when {
                !removed -> "Telegram would not delete that file."
                opened.parts.size > onChannel.size ->
                    "Taken off your shelf. The copy on this phone stays where you put it."
                else -> "Taken off your shelf."
            }
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
        telegramOnboardingStarted = false
        when {
            openedPlaylistName != null -> openedPlaylistName = null
            openedFolderBucketId != null -> openedFolderBucketId = null
            else -> {
                libraryPageName = null
                cloudScreenName = CloudScreen.SHELF.name
            }
        }
    }
    BackHandler(enabled = activePlayback == null && mainTab != BottomNavTab.HOME && !showingBrowser) {
        mainTabName = BottomNavTab.HOME.name
    }
    // The heading's circle is the same exit as the system back, so both answer to the same rule rather
    // than to two lists that drift apart when a page is added.
    val leaveBrowser: () -> Unit = {
        if (mainTab == BottomNavTab.SETTINGS) mainTabName = BottomNavTab.HOME.name
        else leaveDetailScreen()
    }
    BackHandler(enabled = activePlayback == null && showingBrowser && !isSearchExpanded) {
        leaveBrowser()
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
    val downloadsGridState = rememberLazyGridState()
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
        selectedTab == LibraryPage.FOLDERS && openedFolderBucketId != null -> openedFolderGridState
        selectedTab == LibraryPage.CLOUD && openedPlaylistName != null -> openedFolderGridState
        selectedTab == LibraryPage.CLOUD && cloudScreen == CloudScreen.PLAYLISTS -> playlistsGridState
        selectedTab == LibraryPage.CLOUD -> shelfGridState
        selectedTab == LibraryPage.VIDEOS -> allVideosGridState
        selectedTab == LibraryPage.MENU -> menuGridState
        selectedTab == LibraryPage.DOWNLOADS -> downloadsGridState
        else -> foldersGridState
    }

    val showScrollToTopButton by remember(activeGridState) {
        derivedStateOf {
            activeGridState.firstVisibleItemIndex > 0 || activeGridState.firstVisibleItemScrollOffset > 300
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

    // The download row is what keeps a fetch alive once the screen is gone, and on Android 13 the viewer
    // has to agree to see it. Asked when a download is actually started rather than on launch, so the
    // question has a reason attached to it; the download runs either way, and the settings page says what
    // refusing costs.
    val notificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (hasPermission) refreshMediaLibrary(false)
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
    // Nothing is pinned above these grids any more, so the page starts at the status bar and the heading
    // is the first thing in the scroll.
    val browserTopPadding = statusBarTopPadding + 10.dp

    val videoMenuItems = buildList {
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
        add(MorphingMenuItem(text = "Rescan storage", onClick = { refreshMediaLibrary(false) }))
    }
    val deviceSearch = BrowserSearch(
        expanded = isSearchExpanded,
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        onExpand = { isSearchExpanded = true },
        onClose = { isSearchExpanded = false; searchQuery = "" },
        menuItems = videoMenuItems
    )

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedTransitionScope = this

        AnimatedContent(
            targetState = activePlayback != null,
            transitionSpec = {
                fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(260, easing = FastOutSlowInEasing))
            },
            label = "HeroVideoPlayerTransition"
        ) { isPlayerOpen ->
            val playerAnimatedVisibilityScope = this

            if (!isPlayerOpen) {
                // Plain wrapper so the floating overlays are siblings of the jellySource
                // node: a glass consumer inside the recorded subtree smears its own
                // content on API 34+ and cycles the render tree on API 31-33.
                Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        // Recording stops while the info page is up, because the page records itself for
                        // its own back circle. Two live full-screen blur targets is the one thing this
                        // device's scroll budget cannot pay for, and the only consumer of this source —
                        // the floating bar — is under the page anyway.
                        .jellySource(
                            hazeState = if (shelfTitle == null) jellyHazeState else null,
                            backdrop = if (shelfTitle == null) jellyBackdrop else null
                        )
                        .background(OneUiBackground)
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f - 0.08f * shelfRecede
                            scaleY = 1f - 0.08f * shelfRecede
                            alpha = 1f - shelfDim
                        }
                ) {
                // Built above both branches, not inside one. Leaving for the browser used to throw the
                // shelf away with the page that held it, and rebuilding it re-ran the catalogue passes,
                // which write back into the same prefs a rebuild reads from: measured on the Note as
                // three full library parses of ~260 ms each between the tap and the first moved pixel.
                val shelfState = rememberCollectionShelf(cloudRepository, prefsStore, librarySnapshot)
                // The library every screen below reads. The raw scan is the camera roll as much as it is
                // the collection, and the shelf is the one pass that can tell a film from a clip, so the
                // device screens, the playlists and the Settings counts all work off this narrowed copy
                // rather than each deciding for itself what counts as a movie.
                val library = remember(librarySnapshot, shelfState.value.deviceIds) {
                    librarySnapshot.filmsOnly(shelfState.value.deviceIds)
                }
                val filteredFolders = remember(library.folders, searchQuery) {
                    if (searchQuery.isBlank()) {
                        library.folders
                    } else {
                        library.folders.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
                    }
                }
                val sortedFilteredAllVideos = remember(library.videos, searchQuery, prefsStore.sortOrder) {
                    val base = if (searchQuery.isBlank()) {
                        library.videos
                    } else {
                        library.videos.filter {
                            it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                                it.folderName.contains(searchQuery.trim(), ignoreCase = true)
                        }
                    }
                    sortVideos(base, prefsStore.sortOrder)
                }
                AnimatedContent(
                    targetState = showingBrowser,
                    transitionSpec = {
                        val (enter, exit) = jellyTabCrossSlide(targetState)
                        enter togetherWith exit
                    },
                    label = "browser_switch"
                ) { browser ->
                if (!browser) {
                    // Built above the transition, not inside it: the entering page is composed in the same
                    // frame the slide is supposed to start in, so anything the page worked out for itself
                    // was worked out between the lifted finger and the first frame.
                    AnimatedContent(
                        targetState = BottomNavTab.valueOf(stackTabName.value),
                        transitionSpec = {
                            val (enter, exit) = jellyTabCrossSlide(targetState.ordinal > initialState.ordinal)
                            enter togetherWith exit
                        },
                        label = "tab_switch"
                    ) { tab ->
                    destinationState.SaveableStateProvider("collection:${tab.name}") {
                    CollectionScreens(
                        tab = tab,
                        shelfState = shelfState,
                        prefsStore = prefsStore,
                        snapshot = library,
                        topInset = statusBarTopPadding,
                        bottomInset = navBarBottomPadding,
                        onOpenTitle = { shelfTitle = it },
                        onShelfPlay = launchShelfVideo,
                        onLocalPlay = { video, videos ->
                            prefsStore.rememberWatchedAt(video.id)
                            activePlayback = ActiveVideoPlayback(video, videos, "home_local")
                        },
                        onOpenDevice = { openLibraryBrowser(LibraryPage.FOLDERS, CloudScreen.SHELF) },
                        onOpenAllVideos = { openLibraryBrowser(LibraryPage.VIDEOS, CloudScreen.SHELF) },
                        onOpenPlaylists = { openLibraryBrowser(LibraryPage.CLOUD, CloudScreen.PLAYLISTS) },
                        onManageCollection = { openLibraryBrowser(LibraryPage.CLOUD, CloudScreen.SHELF) },
                        // A count rather than the progress map: the byte ticks land many times a second,
                        // and a Library tab that recomposes with them is a Library tab that drops frames
                        // while a film is arriving.
                        activeDownloads = cloudDownloads.values.count { !it.isComplete },
                        onOpenDownloads = { openLibraryBrowser(LibraryPage.DOWNLOADS, CloudScreen.SHELF) }
                    )
                    }
                    }
                } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    val browsingDevice = selectedTab == LibraryPage.FOLDERS || selectedTab == LibraryPage.VIDEOS
                    when {
                        browsingDevice && !hasPermission -> {
                            PermissionRequestView(
                                onGrantClick = {
                                    permissionLauncher.launch(requiredVideoPermissions())
                                }
                            )
                        }

                        browsingDevice && library.videos.isEmpty() && (isScanning || shelfState.value.loading) -> {
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

                        browsingDevice && library.videos.isEmpty() -> {
                            EmptyMediaStateView(onRescanClick = { refreshMediaLibrary(false) })
                        }

                        else -> {
                            // Bar footprint is 12.dp bottom inset + its 56.dp height, so this
                            // keeps the same 46.dp of air above the glass after the labels left.
                            val bottomContentPad = 114.dp + navBarBottomPadding

                            when (selectedTab) {
                                LibraryPage.FOLDERS -> {
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
                                        val targetFolder = bucketId?.let { id -> library.folders.find { it.bucketId == id } }
                                        if (targetFolder != null) {
                                            val folderVideos = remember(
                                                library.videos,
                                                targetFolder.bucketId,
                                                searchQuery,
                                                prefsStore.sortOrder
                                            ) {
                                                val raw = library.videos.filter { it.bucketId == targetFolder.bucketId }
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
                                                        header = {
                                                            BrowserHeading(
                                                                title = targetFolder.title,
                                                                onBack = leaveBrowser,
                                                                search = deviceSearch
                                                            )
                                                        },
                                                        topPadding = browserTopPadding,
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
                                                            prefsStore.rememberWatchedAt(video.id)
                                                            activePlayback = ActiveVideoPlayback(
                                                                video = video,
                                                                playlist = folderVideos,
                                                                sharedKeyPrefix = "folder_${targetFolder.bucketId}"
                                                            )
                                                        },
                                                        onVideoLongClick = { video ->
                                                            AppHaptics.grab()
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
                                                header = {
                                                    BrowserHeading(
                                                        title = LibraryPage.FOLDERS.headerTitle,
                                                        onBack = leaveBrowser,
                                                        search = deviceSearch
                                                    )
                                                },
                                                topPadding = browserTopPadding,
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

                                LibraryPage.VIDEOS -> {
                                    VideoGridContent(
                                        videos = sortedFilteredAllVideos,
                                        columns = prefsStore.gridColumns,
                                        gridState = allVideosGridState,
                                        header = {
                                            BrowserHeading(
                                                title = LibraryPage.VIDEOS.headerTitle,
                                                onBack = leaveBrowser,
                                                search = deviceSearch
                                            )
                                        },
                                        topPadding = browserTopPadding,
                                        bottomPadding = bottomContentPad,
                                        sharedKeyPrefix = "all_videos",
                                        morphingVideoKey = morphingVideoKey,
                                        resumePositions = prefsStore.resumePositions,
                                        favoriteIds = prefsStore.favoriteIds,
                                        sharedTransitionScope = sharedTransitionScope,
                                        playerAnimatedVisibilityScope = playerAnimatedVisibilityScope,
                                        onVideoClick = { video ->
                                            morphingVideoKey = "all_videos_${video.id}"
                                            prefsStore.rememberWatchedAt(video.id)
                                            activePlayback = ActiveVideoPlayback(
                                                video = video,
                                                playlist = sortedFilteredAllVideos,
                                                sharedKeyPrefix = "all_videos"
                                            )
                                        },
                                        onVideoLongClick = { video ->
                                            AppHaptics.grab()
                                            longPressedVideo = video
                                        }
                                    )
                                }

                                LibraryPage.CLOUD -> {
                                    CloudTabContent(
                                        repository = cloudRepository,
                                        prefsStore = prefsStore,
                                        allVideos = library.videos,
                                        columns = prefsStore.gridColumns,
                                        gridState = activeGridState,
                                        topPadding = browserTopPadding,
                                        bottomPadding = bottomContentPad,
                                        screen = cloudScreen,
                                        onboardingStarted = telegramOnboardingStarted,
                                        onOnboardingStartedChange = { telegramOnboardingStarted = it },
                                        onboardingBottomPadding = navBarBottomPadding,
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
                                        onBack = leaveBrowser,
                                        onOpenShelfTitle = { shelfTitle = it },
                                        onOpenPlaylist = { name -> openedPlaylistName = name },
                                        onLongPressCustomPlaylist = { name -> playlistToDelete = name },
                                        onCreatePlaylistClick = {
                                            pendingPlaylistVideoId = null
                                            newPlaylistNameInput = ""
                                            showCreatePlaylistDialog = true
                                        },
                                        onVideoClick = { video, list, prefix ->
                                            morphingVideoKey = "${prefix}_${video.id}"
                                            prefsStore.rememberWatchedAt(video.id)
                                            activePlayback = ActiveVideoPlayback(
                                                video = video,
                                                playlist = list,
                                                sharedKeyPrefix = prefix
                                            )
                                        },
                                        onCloudVideoClick = { item, list ->
                                            launchShelfVideo(item, list, emptyList(), null)
                                        },
                                        onShelfPlay = { item, list, slots, card ->
                                            launchShelfVideo(item, list, slots, card)
                                        },
                                        onVideoLongClick = { video ->
                                            AppHaptics.grab()
                                            longPressedVideo = video
                                        }
                                    )
                                }

                                LibraryPage.MENU -> {
                                    SettingsContent(
                                        snapshot = library,
                                        prefsStore = prefsStore,
                                        repository = cloudRepository,
                                        onManageCollection = { openLibraryBrowser(LibraryPage.CLOUD, CloudScreen.SHELF) },
                                        onAskNotifications = {
                                            notificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        },
                                        updateChecking = updateChecking,
                                        updateUnreachable = updateUnreachable,
                                        updateRelease = updateRelease,
                                        updateDownload = updateDownload,
                                        onUpdateAction = {
                                            // The same three verbs the card offers, in the order the page's
                                            // own label promises: a finished file installs, a found release
                                            // downloads, and anything else asks GitHub again.
                                            val release = updateRelease
                                            when {
                                                updateDownload is UpdateDownload.Ready -> installUpdate()
                                                release != null -> offerUpdate(release)
                                                else -> checkForUpdates()
                                            }
                                        },
                                        gridState = menuGridState,
                                        header = {
                                            BrowserHeading(
                                                title = LibraryPage.MENU.headerTitle,
                                                onBack = leaveBrowser,
                                                search = null
                                            )
                                        },
                                        topPadding = browserTopPadding,
                                        bottomPadding = bottomContentPad
                                    )
                                }

                                LibraryPage.DOWNLOADS -> {
                                    DownloadsContent(
                                        shelf = shelfState.value,
                                        downloads = cloudDownloads,
                                        progress = cloudRepository.progress,
                                        prefsStore = prefsStore,
                                        gridState = downloadsGridState,
                                        header = {
                                            BrowserHeading(
                                                title = LibraryPage.DOWNLOADS.headerTitle,
                                                onBack = leaveBrowser,
                                                search = null
                                            )
                                        },
                                        topPadding = browserTopPadding,
                                        bottomPadding = bottomContentPad,
                                        onOpenTitle = { shelfTitle = it },
                                        onCancelDownload = { file ->
                                            coroutineScope.launch { cloudRepository.cancelDownload(file) }
                                        },
                                        onRemoveDownload = { file ->
                                            coroutineScope.launch { cloudRepository.removeDownload(file) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                }
                }
                }
                }

                    AnimatedVisibility(
                        visible = showingBrowser && !showingTelegramSetup && showScrollToTopButton,
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
                            .padding(bottom = 72.dp + navBarBottomPadding)
                    ) {
                        ScrollToTopCircle(
                            onClick = {
                                coroutineScope.launch {
                                    activeGridState.animateScrollToItem(index = 0, scrollOffset = 0)
                                }
                            }
                        )
                    }

                    if (!showingTelegramSetup) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .height(92.dp + navBarBottomPadding)
                                .background(Brush.verticalGradient(
                                    0f to OneUiBackground.copy(alpha = 0f),
                                    0.25f to OneUiBackground.copy(alpha = 0.28f),
                                    0.62f to OneUiBackground.copy(alpha = 0.76f),
                                    1f to OneUiBackground
                                ))
                        )
                        OneUiFloatingBottomBar(
                            selectedTab = mainTab,
                            hazeState = jellyHazeState,
                            backdrop = jellyBackdrop,
                            onTabSelected = { tab ->
                                mainTabName = tab.name
                                libraryPageName = null
                                shelfTitle = null
                                openedFolderBucketId = null
                                openedPlaylistName = null
                                cloudScreenName = CloudScreen.SHELF.name
                                isSearchExpanded = false
                                searchQuery = ""
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp + navBarBottomPadding)
                        )
                    }

                    // The offer floats above the capsule rather than joining the scroll: glass is only
                    // affordable to something that is not inside what it blurs, and a card that pushed the
                    // hero down two seconds after the app opened would read as the collection changing
                    // shape. It steps aside for anything that owns the screen.
                    updateOffer?.let { offered ->
                        UpdateCard(
                            visible = !updateDismissed && !showingBrowser && !showingTelegramSetup &&
                                shelfTitle == null,
                            release = offered,
                            download = updateDownload,
                            hazeState = jellyHazeState,
                            backdrop = jellyBackdrop,
                            onUpdate = {
                                AppHaptics.click()
                                offerUpdate(offered)
                            },
                            onInstall = {
                                AppHaptics.click()
                                installUpdate()
                            },
                            onDismiss = {
                                AppHaptics.click()
                                prefsStore.dismissUpdate(offered.version)
                                updateDismissed = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 116.dp + navBarBottomPadding)
                        )
                    }

                    // The page a shelf poster opens. It sits over everything, including the bar,
                    // because the reference page carries its own back arrow and owns the whole screen.
                    // It arrives the way a detail screen arrives in the reference app: pushed in from the
                    // right on the smooth-deceleration curve while the shelf behind it dims and recedes,
                    // and it leaves back the way it came.
                    AnimatedVisibility(
                        visible = shelfTitle != null,
                        enter = fadeIn(tween(400)) +
                            slideInHorizontally(tween(600, easing = DetailPushEase)) { it },
                        exit = fadeOut(tween(400)) +
                            slideOutHorizontally(tween(600, easing = DetailPushEase)) { it },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        (shelfTitle ?: lastShelfTitle)?.let { opened ->
                            CloudTitleInfoPage(
                                title = opened,
                                prefsStore = prefsStore,
                                statusText = cloudStatus,
                                isResolving = cloudResolving,
                                downloads = cloudDownloads,
                                downloadProgress = cloudRepository.progress,
                                topInset = statusBarTopPadding,
                                bottomInset = navBarBottomPadding,
                                onBack = { shelfTitle = null },
                                onPlay = { item, list, slots -> launchShelfVideo(item, list, slots, opened.card) },
                                onRemove = removeFromShelf,
                                onDownload = { file ->
                                    if (DownloadNotifier.needsPermissionAsk(context)) {
                                        notificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    coroutineScope.launch { cloudRepository.download(file) }
                                },
                                onCancelDownload = { file -> coroutineScope.launch { cloudRepository.cancelDownload(file) } },
                                onRemoveDownload = { file -> coroutineScope.launch { cloudRepository.removeDownload(file) } }
                            )
                        }
                    }
                }
            } else {
                activePlayback?.let { currentPlayback ->
                    HeroVlcVideoPlayerScreen(
                        playback = currentPlayback,
                        prefsStore = prefsStore,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = playerAnimatedVisibilityScope,
                        cloudRepository = if (currentPlayback.cloudSources.isEmpty()) null else cloudRepository,
                        onSelectAdjacentVideo = { nextVideo ->
                            // A step onto a file that was already here puts down the download the player was
                            // reading; a step to another channel copy releases the one it is leaving.
                            holdShelfCopy(
                                currentPlayback.cloudSources.firstOrNull { it.stableId == nextVideo.id }
                                    ?: nextVideo
                            )
                            morphingVideoKey = "${currentPlayback.sharedKeyPrefix}_${nextVideo.id}"
                            // A step is a fresh question, so the refusals that answered the last one are
                            // dropped with it: the next part gets its own try at its own copies.
                            activePlayback = currentPlayback.copy(
                                video = nextVideo,
                                switchReason = null,
                                refusedCopies = emptySet()
                            )
                        },
                        onFallToAnotherCopy = { nextVideo, reason ->
                            holdShelfCopy(
                                currentPlayback.cloudSources.firstOrNull { it.stableId == nextVideo.id }
                                    ?: nextVideo
                            )
                            // The copy that refused is dropped from the shelf's memory of what was watched
                            // last, because leaving its stamp the newest one would send Resume and the
                            // Continue Watching row straight back to the file that just failed.
                            prefsStore.rememberWatchedAt(nextVideo.id)
                            morphingVideoKey = "${currentPlayback.sharedKeyPrefix}_${nextVideo.id}"
                            activePlayback = currentPlayback.copy(
                                video = nextVideo,
                                switchReason = reason,
                                refusedCopies = currentPlayback.refusedCopies + currentPlayback.video.id
                            )
                        },
                        onDismiss = { activePlayback = null }
                    )
                }
            }
        }
    }

    longPressedVideo?.let { targetVideo ->
        val isFav = prefsStore.favoriteIds.contains(targetVideo.id)
        FrostedDialog(
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
                            AppHaptics.favorite(!isFav)
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
                                tint = if (isFav) OneUiAccent else OneUiTextPrimary,
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
        FrostedDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("Delete \u201C$targetPlaylist\u201D? Your video files on device will not be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppHaptics.destructive()
                        prefsStore.deletePlaylist(targetPlaylist)
                        playlistToDelete = null
                    }
                ) {
                    Text("Delete", color = OneUiDestructive, fontWeight = FontWeight.SemiBold)
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
        FrostedDialog(
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
    heading: String,
    onBack: () -> Unit,
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
    val header: @Composable () -> Unit = { BrowserHeading(heading, onBack, null) }
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
            header = header,
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
        modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "playlists_heading", span = { GridItemSpan(maxLineSpan) }) {
            header()
        }

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
 * The row the player builds its labels from, for a file of either origin.
 *
 * A Telegram copy is presented as if it were a local one so the card, the hero transition and the player
 * all work unchanged; a device file already is that row. Reaching the file itself is the case
 * [Playable] was documented to leave for a cast, and it is the only thing here that cares which of the
 * two a given copy turned out to be.
 */
private fun Playable.toPlayerRow(): VideoItem =
    if (this is CloudVideoItem) toGridVideoItem() else this as VideoItem

/**
 * The shelf whose card this file morphs from.
 *
 * Only the Telegram shelf keys its shared elements by chat, and only that shelf morphs into the player at
 * all, so a device file gets a prefix of its own: the ◀▶ skip has to be able to name the key it started
 * from, and nothing will ever register it.
 */
private fun Playable.morphPrefix(): String =
    if (this is CloudVideoItem) "cloud_$chatId" else "phone_$stableId"

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
    onboardingStarted: Boolean,
    onOnboardingStartedChange: (Boolean) -> Unit,
    onboardingBottomPadding: Dp,
    openedPlaylistName: String?,
    statusText: String?,
    morphingVideoKey: String?,
    sharedTransitionScope: SharedTransitionScope,
    playerAnimatedVisibilityScope: AnimatedVisibilityScope,
    onScreenChange: (CloudScreen) -> Unit,
    onBack: () -> Unit,
    onOpenShelfTitle: (ShelfTitle) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onLongPressCustomPlaylist: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>, String) -> Unit,
    onCloudVideoClick: (CloudVideoItem, List<CloudVideoItem>) -> Unit,
    onShelfPlay: (CloudVideoItem, List<CloudVideoItem>, List<List<Long>>, MediaCard?) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit
) {
    val heading = when {
        openedPlaylistName != null -> openedPlaylistName!!
        screen == CloudScreen.PLAYLISTS -> "Playlists"
        else -> "Library"
    }
    val authState by repository.authState.collectAsState()
    if (screen == CloudScreen.SHELF && (authState.stage != CloudAuthStage.AUTHORIZED || onboardingStarted)) {
        val busy by repository.busy.collectAsState()
        val scope = rememberCoroutineScope()
        var settingUp by remember { mutableStateOf(false) }
        var setupError by remember { mutableStateOf<String?>(null) }
        TelegramOnboarding(
            state = authState,
            busy = busy,
            started = onboardingStarted,
            hasCollection = prefsStore.movieFolderChatId != 0L,
            setupBusy = settingUp,
            setupError = setupError,
            topPadding = topPadding,
            bottomPadding = onboardingBottomPadding,
            onStart = {
                onOnboardingStartedChange(true)
                if (authState.stage == CloudAuthStage.FAILED ||
                    (authState.stage == CloudAuthStage.READY_TO_START && authState.error != null)) {
                    repository.retryAuthorization()
                } else repository.begin()
            },
            onWelcome = { onOnboardingStartedChange(false) },
            onExit = onBack,
            onPhone = repository::submitPhoneNumber,
            onCode = repository::submitCode,
            onPassword = repository::submitPassword,
            onRegistration = repository::submitRegistration,
            onKeys = prefsStore::updateTelegramCredentials,
            onChangeNumber = repository::resetToPhoneNumber,
            onSetup = {
                if (!settingUp) {
                    if (prefsStore.movieFolderChatId != 0L) onOnboardingStartedChange(false)
                    else {
                        settingUp = true
                        setupError = null
                        scope.launch {
                            try {
                                val shelf = repository.ensureShelf(prefsStore.movieFolderChatId)
                                prefsStore.rememberMovieFolder(shelf)
                                onOnboardingStartedChange(false)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                setupError = "Your collection couldn't be set up. Check your connection and try again."
                            } finally {
                                settingUp = false
                            }
                        }
                    }
                }
            }
        )
        return
    }

    val grid = CloudGridArgs(
        prefsStore = prefsStore,
        columns = columns,
        gridState = gridState,
        header = { BrowserHeading(heading, onBack, null) },
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
            heading = heading,
            onBack = onBack,
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
    val header: @Composable () -> Unit,
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
    val downloads by repository.downloads.collectAsState()

    val folderChatId = grid.prefsStore.movieFolderChatId
    var shelfGone by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
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
    //
    // This screen is the channel alone, so its cards are keyed the same way the merged shelf keys them —
    // which is the same id, because a channel-only group's [ShelfTitle.cardKey] is one of its own files.
    val storedCards = grid.prefsStore.mediaCards.toMap()
    val shelfTitles = remember(shelfVideos, groups, storedCards) { shelfCards(shelfVideos, groups, storedCards) }
    TitleSearchPass(shelfTitles, grid.prefsStore)

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
    val lastPlayedMs = grid.prefsStore.watchedAt
    val savedIds = DownloadLedger.completeIds(downloads)
    val unfinished = playables.mapNotNull { playable ->
        val progress = PlayOrder.progress(
            playable.slots,
            resumeMs,
            playable.lengthsWithWatched(grid.prefsStore.knownLengths),
            lastPlayedMs,
            savedIds
        ) ?: return@mapNotNull null
        playable to progress
    }.sortedByDescending { (_, progress) -> lastPlayedMs[progress.partId] ?: 0L }
        .take(CONTINUE_RAIL_LIMIT)


    LazyVerticalGrid(
        state = grid.gridState,
        columns = GridCells.Fixed(grid.columns),
        modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = grid.topPadding,
            bottom = grid.bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "shelf_heading", span = { GridItemSpan(maxLineSpan) }) {
            grid.header()
        }

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
            modifier = Modifier.physicsBounceOverscroll(isHorizontal = true)
                .horizontalScroll(rememberScrollState())
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
    representative: Playable,
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

/** Ask the catalogues what these works are, once each, while the screen holding them is on screen. */
@Composable
internal fun TitleSearchPass(titles: List<ShelfTitle>, prefsStore: ArcPreferencesStore) {
    LaunchedEffect(titles) {
        if (titles.isEmpty()) return@LaunchedEffect
        MediaLibrary.search(
            requests = titles
                // A card from before wordmarks were fetched still needs the second half of the lookup,
                // and only that half: the name it carries is settled.
                .filter {
                    val card = prefsStore.mediaCards[it.cardKey]
                    // Older rules get one more ask, but only for the files left with nothing to look
                    // at. A card that already carries a poster is worth more than the chance the
                    // scoring changed since, and re-opening those would spend a request per film to
                    // possibly replace a good answer with a worse one. A name rescued from Wikipedia
                    // counts as nothing to look at: it is the one source whose answer has no artwork,
                    // so the card reads as a grey box until a rule change lets the chain try the
                    // catalogues that do have the poster.
                    card == null ||
                        (card.posterUrl.isNullOrBlank() && card.rules < MediaCard.RULES) ||
                        (card.source == "tmdb" && !card.logoKnown)
                }
                // Asked under the work's key and with the name of the file that key belongs to, so a
                // film the phone also holds still spends one request rather than one per copy.
                .map { LookupRequest(it.cardKey, it.asker.title) },
            apiKey = BuildConfig.TMDB_API_KEY,
            onFound = { id, card -> prefsStore.rememberMediaCard(id, card) }
        )
    }
}

/**
 * One shelf card opened as a page: the work, every file of it the library holds, and what the catalogue
 * said about it.
 *
 * The card carries its files along because the page has to answer two unrelated questions at once —
 * "what is this" from the catalogue, and "what can actually be played" from the phone and from
 * Telegram — and the second answer is never as wide as the first. A show whose eight episodes are all
 * on the shelf has one playable file per catalogue row; a film uploaded by three groups has three rows
 * and one page.
 *
 * [parts] are files from either place, and the page is allowed to know only that: what a copy is called,
 * how big and how long it is, and whether it is already here. Which of them gets deleted off a shelf, or
 * fetched through a chat, is decided at the one place that casts back to the concrete type.
 */
internal class ShelfTitle(
    val group: TitleGroup,
    val parts: List<Playable>,
    val representative: Playable,
    val card: MediaCard?
) {
    val name: String = card?.title?.nullIfBlank() ?: group.displayTitle
    val isSeries: Boolean = card?.isSeries ?: (group.kind == TitleKind.SERIES)
    val providerId: Int? = card?.providerId
    val posterUrl: String? = card?.posterUrl
    val wordmarkUrl: String? = card?.logoUrl

    /**
     * The id this work's catalogue answer is filed under.
     *
     * Not simply the representative's, because the best copy of a film the viewer also owns is now the
     * one on the phone — and an answer that was looked up for a channel file would be orphaned the
     * moment that file stopped heading its own card. So the card stays with a copy that came off
     * Telegram wherever one exists, which keeps every answer this install already paid for, and only a
     * work nothing but the phone holds has to be asked about from scratch.
     */
    val cardKey: Long = parts.firstOrNull { !it.isOnPhone }?.stableId ?: representative.stableId

    /** The file whose name the catalogues are asked with, which is the one [cardKey] answers for. */
    val asker: Playable = parts.firstOrNull { it.stableId == cardKey } ?: representative

    /** Whether this work exists on the channel at all, which is what decides if it can be taken off a shelf. */
    val heldOnChannel: Boolean = parts.any { !it.isOnPhone }

    /** The same title wearing a newer catalogue answer, which is the only thing that differs. */
    fun withCard(fresh: MediaCard?): ShelfTitle = ShelfTitle(group, parts, representative, fresh)

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
    val filesByEpisode: Map<Int, Map<Int, List<Playable>>> = run {
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
    val filesByAbsolute: Map<Int, List<Playable>> = run {
        val byId = parts.associateBy { it.stableId }
        group.parts.mapNotNull { part ->
            if (part.season != null) return@mapNotNull null
            val number = part.episode ?: return@mapNotNull null
            val file = byId[part.id] ?: return@mapNotNull null
            number to file
        }.groupBy { it.first }.mapValues { row -> row.value.map { it.second } }
    }
}

/**
 * The cards one pile of files makes, each wearing the catalogue answer its key was filed under.
 *
 * Both shelves build through here: the collection screens hand over the channel and the phone together,
 * the manage-collection screen hands over the channel alone. One place to cut a group into a card is what
 * keeps a work's key the same number on both screens, which is the only reason an answer paid for in one
 * tab is still there when the other opens.
 */
internal fun shelfCards(
    files: List<Playable>,
    groups: List<TitleGroup>,
    cards: Map<Long, MediaCard>
): List<ShelfTitle> {
    val byId = files.associateBy { it.stableId }
    return groups.mapNotNull { group ->
        val parts = group.parts.mapNotNull { byId[it.id] }
        if (parts.isEmpty()) return@mapNotNull null
        val representative = byId[group.representative.id] ?: parts.first()
        // Built once with no card, so the work can report its own key before the answer is read with it.
        val bare = ShelfTitle(group, parts, representative, null)
        bare.withCard(cards[bare.cardKey])
    }
}

/** How long a closed player is given to let go of its file before the bytes are handed back. */
private const val PLAYER_RELEASE_SETTLE_MS = 1_500L

/** Frosted Jelly's smooth-deceleration curve: the one every detail push in the design system lands on. */
private val DetailPushEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

// The picture hands the screen to the page over this much of its own height, and the fade finishes
// exactly where the shared scrim turns to page colour, so the handover never shows a seam.
private const val INFO_HERO_COLLAPSE_FRACTION = 0.84f

/** Frosted Artwork Parallax Fade: the picture recedes at six tenths of the column's own speed. */
private const val INFO_ARTWORK_PARALLAX = 0.6f

/** How much page colour feathers in over the picture's bottom edge before it turns solid. */
private const val INFO_HERO_FEATHER_DP = 64

/**
 * The circle that keeps a film on this phone.
 *
 * Three readings of one glass disc, so the row never shifts under a finger: an arrow to start, the same
 * arrow ringed while the bytes come, a tick once they have landed. Pressing the ring takes the promise
 * back — a viewer watching a bar grow has already said what they want, so a second press means "stop"
 * rather than "ask me again" — while the tick opens a menu, because wiping a four gigabyte film off a
 * device is not a thing a mis-tap should be allowed to do.
 *
 * The progress is a flow rather than a map handed down by the page, and it is collected here: the pump
 * publishes several times a second while anything is arriving, and a read above the page would
 * recompose the hero, the copies list and the metadata with every one of those.
 */
@Composable
private fun DownloadCircle(
    file: CloudVideoItem,
    entry: DownloadEntry?,
    progress: StateFlow<Map<Int, CloudProgress>>,
    onDownload: (CloudVideoItem) -> Unit,
    onCancel: (CloudVideoItem) -> Unit,
    onRemove: (CloudVideoItem) -> Unit
) {
    val saved = entry?.isComplete == true
    val arriving = entry != null && !saved
    DownloadCircleTarget(
        size = 56.dp,
        saved = saved,
        arriving = arriving,
        // The id the fetch was started under, which is the one the pump publishes against. The shelf's own
        // id is the fallback: an entry and its file are the same copy, and a re-listed document can name
        // the pair differently than the row that asked for it.
        progressKey = entry?.fileId ?: file.fileId,
        progress = progress,
        onClick = { if (arriving) onCancel(file) else onDownload(file) },
        onRemove = { onRemove(file) }
    )
}

/**
 * The disc itself, at whatever size the row carrying it can hold.
 *
 * The size is a parameter rather than a second control because the same promise is drawn in more than one
 * place — the info page's action row at 56 dp and a Downloads row at 38 — and a film's download state that
 * looks different depending on where you happened to look it up reads as three different states.
 */
@Composable
private fun DownloadCircleTarget(
    size: Dp,
    saved: Boolean,
    arriving: Boolean,
    progressKey: Int,
    progress: StateFlow<Map<Int, CloudProgress>>,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val published by progress.collectAsState()
    val fraction = published[progressKey]?.fraction ?: 0f
    val stroke = if (size >= 44.dp) 3.dp else 2.dp
    // Half the disc, floored where a smaller glyph would be a smudge rather than a download arrow.
    val glyph = (size * 0.4f).coerceAtLeast(12.dp)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OneUiPillSurface),
        contentAlignment = Alignment.Center
    ) {
        if (saved) {
            MorphingMenu(
                items = listOf(
                    MorphingMenuItem(
                        text = "Remove download",
                        icon = Icons.Default.Delete,
                        isDestructive = true,
                        onClick = {
                            AppHaptics.destructive()
                            onRemove()
                        }
                    )
                ),
                buttonSize = size,
                contentDescription = "Available offline",
                icon = Icons.Default.Check,
                tint = OneUiTextPrimary
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .then(
                        if (arriving) {
                            Modifier.drawBehind {
                                val ring = stroke.toPx()
                                val radius = this.size.minDimension / 2f - ring
                                val ringSize = ComposeSize(radius * 2f, radius * 2f)
                                val ringTopLeft = Offset(
                                    (this.size.width - ringSize.width) / 2f,
                                    (this.size.height - ringSize.height) / 2f
                                )
                                // The track is drawn whole under the part that has landed, so a fetch that
                                // has not reported a single byte yet still reads as working.
                                drawArc(
                                    color = OneUiTextPrimary.copy(alpha = 0.16f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = ringTopLeft,
                                    size = ringSize,
                                    style = Stroke(width = ring)
                                )
                                if (fraction > 0f) {
                                    drawArc(
                                        color = OneUiTextPrimary,
                                        startAngle = -90f,
                                        sweepAngle = 360f * fraction,
                                        useCenter = false,
                                        topLeft = ringTopLeft,
                                        size = ringSize,
                                        style = Stroke(width = ring, cap = StrokeCap.Round)
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .jellyClick(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = if (arriving) "Cancel download" else null
                    ) {
                        AppHaptics.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HugeIcons.Download,
                    contentDescription = if (arriving) "Downloading, tap to cancel" else "Keep offline",
                    tint = OneUiTextPrimary,
                    // The glyph the disc's other two readings get: the tick this becomes and the overflow
                    // circle beside it. A download that lands would otherwise be seen to shrink, and a
                    // promise about the row holding still has to cover what is in it.
                    modifier = Modifier.size(glyph)
                )
            }
        }
    }
}

/**
 * The page a shelf poster opens.
 *
 * Its hero is the collection home's hero with one title instead of a carousel: the same height rule,
 * the same scrim, and the same stack of wordmark, one metadata line and a single pill standing on the
 * picture. The page scrolls as one column with the artwork drifting behind it at 0.6x, because the
 * reference's hero is not a header that disappears — the picture slides away and the film's own
 * lettering goes with it, leaving no bar behind: just a short scrim under the status bar and the back
 * arrow in its own floating glass. The picture is the card's own: the shelf
 * and this page wear the same shot, so the push carries no change of artwork across it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CloudTitleInfoPage(
    title: ShelfTitle,
    prefsStore: ArcPreferencesStore,
    statusText: String?,
    isResolving: Boolean,
    downloads: Map<Long, DownloadEntry>,
    downloadProgress: StateFlow<Map<Int, CloudProgress>>,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
    onPlay: (Playable, List<Playable>, List<List<Long>>) -> Unit,
    onRemove: (ShelfTitle) -> Unit,
    onDownload: (CloudVideoItem) -> Unit,
    onCancelDownload: (CloudVideoItem) -> Unit,
    onRemoveDownload: (CloudVideoItem) -> Unit
) {
    // The card arrives with the tap, so it is a snapshot; the store is the live one, and this page
    // starts a lookup of its own. Everything the page says about the work is read through [shown],
    // while the keys below stay on [title] so a landing answer neither replays the entrance nor
    // throws away the season the viewer picked.
    val storedCard = prefsStore.mediaCards[title.cardKey]
    val shown = remember(title, storedCard) { title.withCard(storedCard ?: title.card) }

    // A file the catalogues drew a blank on is otherwise refused for good, since the pass only re-asks
    // a refusal when the matching rules change. Opening its page is the moment the viewer says they
    // care about this title, so it asks once more — and only while there is no artwork to show.
    LaunchedEffect(title.cardKey) {
        val card = prefsStore.mediaCards[title.cardKey]
        if (card == null || card.posterUrl.isNullOrBlank()) {
            MediaLibrary.askNow(
                videoId = title.cardKey,
                fileName = title.asker.title,
                apiKey = BuildConfig.TMDB_API_KEY,
                onFound = { id, found -> prefsStore.rememberMediaCard(id, found) }
            )
        }
    }

    val detail = TitleDetailFetch(shown.providerId, shown.isSeries, prefsStore)
    val scrollState = rememberScrollState()
    val heroHeight = rememberCollectionHeroHeight()
    val collapsePx = with(LocalDensity.current) { (heroHeight * INFO_HERO_COLLAPSE_FRACTION).toPx() }
    // The top scrim rides the artwork out. It lights in proportion to how far the picture has fallen
    // and only reaches full strength at `collapsePx`, the same distance over which the picture's own
    // alpha goes to zero — so the shade is complete exactly when there is no artwork left to shade
    // over, and holds there for the rest of the page.
    val topScrim = (scrollState.value / collapsePx).coerceIn(0f, 1f)

    // The one picture this title wears everywhere, including on the card this page was opened from.
    val artUrl = rememberShelfArtUrl(shown, prefsStore)
    val art = rememberCachedArt(artUrl, PosterCache.HERO_WIDTH_PX)
    val wordmark = rememberCachedArt(shown.wordmarkUrl, PosterCache.WORDMARK_WIDTH_PX)
    // The page arrives as a whole screen, so its own lettering and controls come in a beat behind it
    // rather than all at once on the first frame.
    val contentReveal = remember(title) { Animatable(0f) }
    LaunchedEffect(title) {
        if (contentReveal.value < 1f) {
            contentReveal.animateTo(1f, tween(durationMillis = 220, delayMillis = 160, easing = FastOutSlowInEasing))
        }
    }
    val revealRisePx = with(LocalDensity.current) { 14.dp.toPx() }

    val resumeByPart = title.partIds.associateWith { prefsStore.resumePositions[it] ?: 0L }
    // Twenty minutes spent on one copy are twenty minutes into the part, so every copy of it shows and
    // opens at the same place. The player only ever reads the file it was handed.
    val resumeByCopy = PlayOrder.positionByCopy(title.copySlots, resumeByPart)
    // The copies this device has been made to hold on to, byte for byte. One read of the ledger for the
    // whole page: it decides which copy Play opens, which rows say "Offline", and which circle wears a
    // tick instead of an arrow.
    val savedIds = DownloadLedger.completeIds(downloads)
    val playChoice = PlayOrder.nextCopy(
        slots = title.copySlots,
        resumeMs = resumeByCopy,
        lengthsMs = title.lengths,
        lastPlayedMs = prefsStore.watchedAt,
        savedIds = savedIds
    )
    val playFile = title.parts.firstOrNull { it.stableId == playChoice?.partId } ?: title.representative
    val playLabel = if ((playChoice?.resumeMs ?: 0L) > 0L) "Resume" else "Play"

    // The circle keeps exactly the copy Play opens — the best upload when nothing has been watched, the
    // one last watched when it has — so the two buttons can never disagree about which file this title is.
    // A device file answers null: it is already offline, and a button promising offline viewing of a film
    // that never left the phone is a second word for nothing. That is also why this gate is not the shelf
    // removal one — a purely local card has no shelf to remove, but it has no fetch to make either.
    val downloadTarget = playFile as? CloudVideoItem
    // The circle belongs to whichever copy of this title is live: the one Play opens, unless one of its
    // siblings is being fetched right now. A ring that disappeared because the viewer pressed Play on a
    // different copy of the same film would leave that fetch running with nothing on screen saying so,
    // and a fetch nobody can see is a fetch nobody can stop.
    val downloadFile = (title.parts.firstOrNull { downloads[it.stableId]?.isComplete == false } as? CloudVideoItem)
        ?: downloadTarget

    // One line under the title, in the home hero's order: when, what, how long, how it landed. Built
    // here rather than below the picture because it belongs to the artwork, not to the facts column.
    val heroMeta = listOfNotNull(
        infoYearLine(detail, title.group.year).takeIf { it.isNotEmpty() },
        if (shown.isSeries) "Series" else "Movie",
        infoRuntimeLine(detail, shown).takeIf { it.isNotEmpty() },
        detail?.maturity?.takeIf { it.isNotBlank() },
        detail?.voteScore?.takeIf { it > 0.0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }
    ).joinToString(" · ")

    val playCopy: (Playable) -> Unit = { file ->
        prefsStore.rememberResumePosition(file.stableId, resumeByCopy[file.stableId] ?: 0L)
        onPlay(file, title.parts, title.copySlots)
    }

    // Every file the shelf holds, filed under the season and number it covers. The run-numbered files
    // can only be placed once the catalogue's season lengths are known, so this waits on `detail` and
    // simply leaves them out until it arrives.
    val filesBySeason: Map<Int, Map<Int, List<Playable>>> = remember(title, detail) {
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
    var season by remember(title, shown.providerId) {
        mutableIntStateOf(
            playSeason?.takeIf { it in shelfSeasons }
                ?: shelfSeasons.minOrNull()
                ?: detail?.seasons?.firstOrNull { it.number > 0 }?.number
                ?: 1
        )
    }
    // A part numbered by its place in the whole run only lands on a season once those lengths are
    // known, and they arrive a moment after this page's first frame. So it is corrected when they
    // show up, and given up the moment the viewer picks a season themselves.
    var seasonChosen by remember(title, shown.providerId) { mutableStateOf(false) }
    LaunchedEffect(title, shown.providerId, detail, seasonChosen) {
        val seasons = detail?.seasons ?: return@LaunchedEffect
        if (seasonChosen) return@LaunchedEffect
        val target = playSeason?.takeIf { it in shelfSeasons } ?: shelfSeasons.minOrNull()
        if (target != null && target != season) {
            season = target
        }
    }
    // Only a show has seasons to ask for. Handed a film's id, the season endpoint answers 404, and the
    // answer would be thrown away anyway — the episode list below renders for a series alone.
    val cataloguedEpisodes = SeasonEpisodeFetch(shown.providerId.takeIf { shown.isSeries }, season, prefsStore)

    // The episode a copies sheet was opened for. It holds the row's own files rather than a number to
    // look up, because switching season behind the sheet would change what that number means.
    var copiesSheet by remember(title) { mutableStateOf<EpisodeCopiesSheet?>(null) }
    var showFixMatchSheet by remember(title) { mutableStateOf(false) }

    val heroPx = with(LocalDensity.current) { heroHeight.toPx() }
    val featherPx = with(LocalDensity.current) { INFO_HERO_FEATHER_DP.dp.toPx() }
    val seamPx = with(LocalDensity.current) { 3.dp.toPx() }
    val heroPull = remember { Animatable(0f) }

    // The page's own frosted source. The back circle used to sample the shelf's, which meant it frosted
    // the dimmed grid *behind the whole page* — a static blur that never moved no matter what scrolled
    // past it — instead of this page's own artwork.
    val pageHaze = remember { HazeState() }
    val pageBackdrop = rememberLayerBackdrop()

    // A glass consumer cannot sit inside the node it samples: it would draw its own content back into
    // itself, and on API 31-33 it cycles the render tree. So the picture, the cover sheet and the
    // scrolling column are recorded here, and the chrome below is their sibling — the same arrangement
    // the floating bar has with the shelf.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .jellySource(hazeState = pageHaze, backdrop = pageBackdrop)
                .background(OneUiBackground)
        ) {
            // The picture lives outside the scrolling column, exactly as it does on the collection home.
            // The bounce overscroll translates the whole column — wordmark, metadata and pill ride it
            // down — so a picture inside that column is dragged with them and opens bare page colour above
            // the top edge mid-stretch. This layer follows only the real scroll, so the picture holds the
            // top of the screen while the content gives way.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        clip = true
                        val travel = scrollState.value
                        translationY = -travel * INFO_ARTWORK_PARALLAX
                        alpha = (1f - travel / collapsePx).coerceIn(0f, 1f)
                    }
            ) {
                // The home hero's framing, to the pixel: same frame, same 1.18 overzoom. The picture and its
                // frame translate together now that the layer is pinned, so the old oversized-and-lifted box
                // bought no headroom at all — it only asked Crop to fit a taller destination, which printed
                // this same film noticeably tighter than the page above it.
                val picture = art
                if (picture != null) {
                    Image(
                        bitmap = picture.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.18f; scaleY = 1.18f }
                    )
                } else {
                    PlaceholderThumbnailBox(modifier = Modifier.fillMaxSize())
                }
                // The scrim rides the picture, not the column, so the shade a white wordmark sits on stays
                // where the artwork is rather than sliding off it.
                Box(modifier = Modifier.fillMaxSize().background(HeroScrim))
            }

            // The home hero's cover sheet, same recipe: page colour feathered in over the last 64 dp and
            // then solid, riding the hero's edge at 1:1 with the column plus the stretch. It is what closes
            // the picture under the page as the list comes up, and it is why the pinned layer's own clip
            // edge never prints a bright line of raw artwork across the seam.
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = heroPx - scrollState.value + heroPull.value.coerceAtMost(0f) - seamPx - featherPx
                    }
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(INFO_HERO_FEATHER_DP.dp).background(HeroFeather))
                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(OneUiBackground))
            }

            Column(
                modifier = Modifier
                    .physicsBounceOverscroll(displacement = heroPull)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // The hero's slot in the column carries only what the stretch is meant to move. It comes in
                // a beat after the picture, so the hero reads as a still first and then starts talking.
                Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp)
                            .graphicsLayer {
                                alpha = contentReveal.value
                                translationY = (1f - contentReveal.value) * revealRisePx
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val logo = wordmark
                        if (logo != null) {
                            Image(
                                bitmap = logo.asImageBitmap(),
                                contentDescription = shown.name,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .heightIn(max = 76.dp)
                            )
                        } else {
                            Text(
                                text = shown.name,
                                color = Color.White,
                                fontSize = 30.sp,
                                lineHeight = 36.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = heroMeta,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CollectionPill(
                                modifier = Modifier.width(158.dp).height(56.dp),
                                onClick = {
                                    AppHaptics.click()
                                    playCopy(playFile)
                                }
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
                            downloadFile?.let { file ->
                                DownloadCircle(
                                    file = file,
                                    entry = downloads[file.stableId],
                                    progress = downloadProgress,
                                    onDownload = onDownload,
                                    onCancel = onCancelDownload,
                                    onRemove = onRemoveDownload
                                )
                            }
                            // Options for this title: rematch / fix metadata, or remove from shelf.
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(OneUiPillSurface)
                            ) {
                                MorphingMenu(
                                    items = listOfNotNull(
                                        MorphingMenuItem(
                                            text = "Fix match",
                                            icon = Icons.Outlined.Search,
                                            onClick = { showFixMatchSheet = true }
                                        ),
                                        if (title.heldOnChannel) {
                                            MorphingMenuItem(
                                                text = "Remove from shelf",
                                                icon = Icons.Default.Delete,
                                                isDestructive = true,
                                                onClick = {
                                                    // No confirmation follows this, so the thud is the only warning
                                                    // that the whole work is leaving the shelf rather than one file.
                                                    AppHaptics.destructive()
                                                    onRemove(title)
                                                }
                                            )
                                        } else null
                                    ),
                                    buttonSize = 56.dp,
                                    tint = OneUiTextPrimary
                                )
                            }
                        }
                    }
                }

                // Home assembles its cards in order; this page was lifting its entire body in one beat,
                // which reads as a wall appearing rather than a page being read. Each section now takes the
                // same slot Home takes, on a key that belongs to this title alone, so a title opened a
                // second time opens already filled.
                val arrivalKey = "info:${title.group.key}"
                Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                    val genreLine = detail?.genres?.joinToString("   ·   ")
                    if (!genreLine.isNullOrBlank()) {
                        StaggeredListItem(0, "$arrivalKey:genres") {
                            Text(
                                text = genreLine,
                                fontSize = 13.sp,
                                color = OneUiTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 24.dp)
                            )
                        }
                    }

                    statusText?.let { message ->
                        StaggeredListItem(1, "$arrivalKey:status") {
                            Text(
                                text = message,
                                fontSize = 12.sp,
                                color = OneUiTextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    StaggeredListItem(2, "$arrivalKey:shelf") { InfoShelfLine(title) }

                    if (shown.providerId == null) {
                        StaggeredListItem(3, "$arrivalKey:nocat") {
                            // Two different reasons for an empty picture, which the box itself cannot tell
                            // apart. A card carrying a source is a catalogue that knows the film and has no
                            // poster for it — Wikipedia leaves the fair-use ones out of its API by design —
                            // while a card with no source is the file name standing in for an answer nobody
                            // gave. Saying the wrong one sends the viewer looking for a naming bug.
                            val namedByACatalogue = shown.card?.source?.let { it != MediaCard.NONE } == true
                            Text(
                                text = if (namedByACatalogue) {
                                    "The catalogues know this title and have no artwork for it."
                                } else {
                                    "No catalogue entry — the name on this card came from the file itself."
                                },
                                fontSize = 12.sp,
                                color = OneUiTextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    detail?.let { info ->
                        StaggeredListItem(4, "$arrivalKey:credits") {
                            Column {
                                info.directors.joinToString(", ").takeIf { it.isNotBlank() }?.let { names ->
                                    InfoCreditLine(if (info.isSeries) "Creator" else "Director", names)
                                }
                                info.writers.joinToString(", ").takeIf { it.isNotBlank() }?.let { names ->
                                    InfoCreditLine("Writer", names)
                                }
                            }
                        }

                        if (info.overview.isNotBlank()) {
                            StaggeredListItem(5, "$arrivalKey:synopsis") { InfoSynopsis(info.overview) }
                        }

                        if (info.cast.isNotEmpty()) {
                            StaggeredListItem(6, "$arrivalKey:cast") {
                                Column {
                                    InfoSectionHeading("Cast")
                                    InfoCastRail(info)
                                }
                            }
                        }

                        if (info.isSeries) {
                            if (info.seasons.isNotEmpty()) {
                                StaggeredListItem(7, "$arrivalKey:seasons") {
                                    Column {
                                        InfoSectionHeading("Seasons")
                                        InfoSeasonRail(info, season) {
                                            seasonChosen = true
                                            season = it
                                        }
                                    }
                                }
                            }
                            StaggeredListItem(8, "$arrivalKey:episodes") {
                                Column {
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
                        }
                    }

                    // Several copies of one film are one card, and the pile behind that card is the only place
                    // where the difference between them shows. Telegram hands over an exact size and, for a video
                    // sent as a video, an exact frame size; everything else a viewer chooses with — codec, where
                    // it was ripped from, Dolby, burn-in subtitles — was typed into the file name.
                    if (!shown.isSeries && title.parts.size > 1) {
                        StaggeredListItem(9, "$arrivalKey:copies") {
                            Column {
                                InfoSectionHeading("Copies")
                                InfoCopyList(title.parts, playFile.stableId, resumeByCopy, savedIds) { file -> playCopy(file) }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp + bottomInset))
            }
        }

        // What the picture leaves behind as it goes is not a bar. A full-width slab of page colour
        // reads as a header this page never asked for, and its bottom edge prints a line straight
        // across the content. This is a SHORT scrim: a plateau across the status bar and an 18.dp
        // dissolve, nothing more. It exists for the clock and the icons, and a band that reaches
        // past them starts eating the artwork.
        //
        // Two things it has to get right. It starts at y=0, not at the status bar's lower edge — the
        // icons are white over whatever scrolls past, so the shade belongs behind them. And it is lit
        // by the artwork's own exit, so it never outshines the picture it is standing under.
        val scrimBandHeight = topInset + 18.dp
        val statusBarFraction = (topInset / scrimBandHeight).coerceIn(0.05f, 0.85f)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(scrimBandHeight)
                .background(Brush.verticalGradient(
                    0f to OneUiBackground.copy(alpha = 0.88f * topScrim),
                    statusBarFraction to OneUiBackground.copy(alpha = 0.74f * topScrim),
                    1f to OneUiBackground.copy(alpha = 0f)
                ))
        )

        // The back arrow stands in its own frosted circle — the nav bar's glass and the same outline
        // border, so it reads as one object resting on the page rather than a control fitted into a
        // header. Nothing else lives up here: the hero's own lettering is the title.
        //
        // It steps aside on the Immersive Header Action Visibility rule: fade, shrink to 0.7 and slide
        // half its own width off the leading edge, all three on the same medium-bouncy spring, and the
        // same way back. Direction decides when it goes, never position, so a fling coasting past a
        // point cannot bring it back on its own.
        val actionsStanding = rememberHeaderActionsVisible(scrollState)
        AnimatedVisibility(
            visible = actionsStanding,
            enter = fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) +
                slideInHorizontally(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    initialOffsetX = { -it / 2 }
                ) +
                scaleIn(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    initialScale = 0.7f
                ),
            exit = fadeOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) +
                slideOutHorizontally(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    targetOffsetX = { -it / 2 }
                ) +
                scaleOut(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    targetScale = 0.7f
                ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = topInset + 10.dp)
        ) {
            val backSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .jelly(backSource, scaleDownTo = 0.88f)
                    .clip(CircleShape)
                    .jellyGlass(hazeState = pageHaze, backdrop = pageBackdrop, shape = CircleShape)
                    // Without an edge the circle disappears into the black page, exactly like the bar did.
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(
                        interactionSource = backSource,
                        indication = null,
                        onClick = {
                            AppHaptics.click()
                            onBack()
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUiTextPrimary,
                    modifier = Modifier.size(22.dp)
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
                FrostedModalPanel {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .physicsBounceOverscroll()
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
                            resumeByCopy = resumeByCopy,
                            savedIds = savedIds
                        ) { file ->
                            copiesSheet = null
                            playCopy(file)
                        }
                    }
                }
            }
        }

        if (showFixMatchSheet) {
            val coroutineScope = rememberCoroutineScope()
            FixMatchSheet(
                title = title,
                shown = shown,
                apiKey = BuildConfig.TMDB_API_KEY,
                onDismiss = { showFixMatchSheet = false },
                onApplyMatch = { match ->
                    val apiKey = BuildConfig.TMDB_API_KEY
                    val newCard = MediaCard(
                        title = match.title,
                        subtitle = if (match.isSeries) null else match.year?.toString(),
                        posterUrl = match.posterUrl,
                        source = "tmdb",
                        logoUrl = null,
                        logoKnown = false,
                        providerId = match.id,
                        isSeries = match.isSeries
                    )
                    title.parts.forEach { part ->
                        val sub = if (newCard.isSeries) {
                            val guess = MediaNameParser.parse(part.title)
                            episodeLabel(guess)
                        } else newCard.subtitle
                        prefsStore.rememberMediaCard(part.stableId, newCard.copy(subtitle = sub ?: newCard.subtitle))
                    }
                    prefsStore.rememberMediaCard(title.cardKey, newCard)
                    if (title.representative.stableId != title.cardKey) {
                        prefsStore.rememberMediaCard(title.representative.stableId, newCard)
                    }

                    coroutineScope.launch {
                        val detail = withContext(Dispatchers.IO) {
                            runCatching { TmdbDetails.title(apiKey, match.id, match.isSeries) }.getOrNull()
                        }
                        if (detail != null) {
                            prefsStore.rememberTitleDetail(match.id, match.isSeries, detail)
                        }
                        val logoUrl = withContext(Dispatchers.IO) {
                            runCatching { TmdbDetails.logo(apiKey, match.id, match.isSeries) }.getOrNull()
                        }
                        val withLogo = newCard.copy(logoUrl = logoUrl, logoKnown = true)
                        prefsStore.rememberMediaCard(title.cardKey, withLogo)
                        title.parts.forEach { part ->
                            val existing = prefsStore.mediaCards[part.stableId]
                            if (existing != null) {
                                prefsStore.rememberMediaCard(part.stableId, existing.copy(logoUrl = logoUrl, logoKnown = true))
                            }
                        }

                        if (match.isSeries && detail != null) {
                            val targetSeason = playSeason?.takeIf { it in filesBySeason.keys }
                                ?: filesBySeason.keys.minOrNull()
                                ?: detail.seasons.firstOrNull { it.number > 0 }?.number
                                ?: 1
                            val eps = withContext(Dispatchers.IO) {
                                runCatching { TmdbDetails.episodes(apiKey, match.id, targetSeason) }.getOrNull()
                            }
                            if (eps != null) {
                                prefsStore.rememberEpisodesOf(match.id, targetSeason, eps)
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixMatchSheet(
    title: ShelfTitle,
    shown: ShelfTitle,
    apiKey: String,
    onDismiss: () -> Unit,
    onApplyMatch: (TmdbSearchResult) -> Unit
) {
    var query by rememberSaveable { mutableStateOf(shown.name) }
    var results by remember { mutableStateOf<List<TmdbSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(query, apiKey) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || apiKey.isBlank()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        val fetched = withContext(Dispatchers.IO) {
            runCatching { TmdbSearch.search(apiKey, trimmed) }.getOrDefault(emptyList())
        }
        results = fetched.distinctBy { "${it.isSeries}:${it.id}" }
        isSearching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OneUiBackground,
        contentColor = OneUiTextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 580.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp)
        ) {
            Text(
                text = "Fix Match",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = OneUiTextPrimary
            )
            Text(
                text = "Search TMDB or auto-detect with updated anime rules",
                fontSize = 12.sp,
                color = OneUiTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Auto-detect button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .clickable(enabled = !isApplying) {
                        isApplying = true
                        coroutineScope.launch {
                            val detectedCard = withContext(Dispatchers.IO) {
                                runCatching { MediaLibrary.cardFor(title.asker.title, apiKey) }.getOrNull()
                            }
                            if (detectedCard?.providerId != null) {
                                onApplyMatch(
                                    TmdbSearchResult(
                                        id = detectedCard.providerId,
                                        title = detectedCard.title,
                                        year = detectedCard.subtitle?.toIntOrNull(),
                                        isSeries = detectedCard.isSeries,
                                        posterUrl = detectedCard.posterUrl,
                                        overview = null,
                                        isAnimation = true
                                    )
                                )
                                onDismiss()
                            } else {
                                isApplying = false
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-detect with updated rules",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary
                    )
                    Text(
                        text = "Re-analyzes file name with anime & year detection",
                        fontSize = 11.sp,
                        color = OneUiTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search title...", color = OneUiTextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = OneUiTextSecondary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = OneUiTextSecondary,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { query = "" }
                                .padding(8.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = OneUiTextPrimary,
                    unfocusedTextColor = OneUiTextPrimary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isApplying) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Applying match...",
                            fontSize = 13.sp,
                            color = OneUiTextSecondary
                        )
                    }
                }
            } else if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (results.isEmpty() && query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (apiKey.isBlank()) "TMDB API key not configured" else "No matching titles found",
                        fontSize = 13.sp,
                        color = OneUiTextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(results, key = { "${it.isSeries}:${it.id}" }) { item ->
                        val isCurrentMatch = item.id == shown.providerId
                        FixMatchResultRow(
                            item = item,
                            isCurrentMatch = isCurrentMatch,
                            onClick = {
                                isApplying = true
                                onApplyMatch(item)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixMatchResultRow(
    item: TmdbSearchResult,
    isCurrentMatch: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .jelly(interaction, scaleDownTo = 0.98f)
            .background(OneUiCardSurface)
            .border(
                width = if (isCurrentMatch) 1.5.dp else 1.dp,
                color = if (isCurrentMatch) MaterialTheme.colorScheme.primary else OneUiTextSecondary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val art = rememberCachedArt(item.posterUrl, 160)
        ArtSlot(
            art = art,
            contentDescription = item.title,
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OneUiTextSecondary.copy(alpha = 0.14f))
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUiTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCurrentMatch) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Current",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            val meta = listOfNotNull(
                if (item.isAnimation) "Anime" else if (item.isSeries) "Series" else "Movie",
                item.year?.toString()
            ).joinToString(" · ")
            Text(
                text = meta,
                fontSize = 12.sp,
                color = if (item.isAnimation) MaterialTheme.colorScheme.primary else OneUiTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            item.overview?.let { overview ->
                Text(
                    text = overview,
                    fontSize = 11.sp,
                    color = OneUiTextSecondary.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** The years a title goes by, folded into a range once the catalogue knows where it ended. */
private fun infoYearLine(detail: TitleDetail?, fileYear: Int?): String {
    val year = detail?.startYear ?: fileYear ?: return ""
    return if (detail?.endYear != null && detail.endYear != year) "$year–${detail.endYear}" else year.toString()
}

/** What the catalogue calls a running time, and what the file itself says when the catalogue is silent. */
private fun infoRuntimeLine(detail: TitleDetail?, title: ShelfTitle): String = when {
    detail != null && detail.runtimeMinutes > 0 ->
        formatRuntime(detail.runtimeMinutes) + if (title.isSeries) " per episode" else ""
    title.representative.durationMs > 0L -> formatDuration(title.representative.durationMs)
    else -> ""
}

/** What your own shelf holds of this title — the one fact the artwork can't carry. */
@Composable
private fun InfoShelfLine(title: ShelfTitle) {
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
            .physicsBounceOverscroll(isHorizontal = true)
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
            .physicsBounceOverscroll(isHorizontal = true)
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
                        .jellyClick(interactionSource = null, indication = null) {
                            AppHaptics.tick()
                            onSelect(seasonCard.number)
                        }
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
    val copies: List<Playable>
) {
    /** What a tap on the row plays. */
    val file: Playable? = copies.firstOrNull()
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
    filesBySeason: Map<Int, Map<Int, List<Playable>>>,
    onPlay: (Playable) -> Unit,
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
    onPlay: (Playable) -> Unit,
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
                            .jellyClick(interactionSource = null, indication = null) {
                                AppHaptics.click()
                                onShowCopies(row)
                            }
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
    files: List<Playable>,
    autoPlayId: Long?,
    resumeByCopy: Map<Long, Long>,
    savedIds: Set<Long>,
    onPlay: (Playable) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        files.forEach { file ->
            InfoCopyRow(
                file = file,
                isAutoPlay = file.stableId == autoPlayId,
                resumeMs = resumeByCopy[file.stableId] ?: 0L,
                isOffline = file.stableId in savedIds,
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
 * states one, the exact size stands in — the only quality figure a file cannot get wrong.
 *
 * The line underneath says where the copy lives, because that is the one difference between two rows
 * that otherwise claim the same picture: this one opens now, and that one has to be fetched first.
 */
@Composable
private fun InfoCopyRow(
    file: Playable,
    isAutoPlay: Boolean,
    resumeMs: Long,
    isOffline: Boolean,
    onPlay: (Playable) -> Unit
) {
    val read = remember(file.title, file.frameHeight) { ReleaseInfo.parse(file.title, file.frameHeight) }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .jellyClick(interactionSource = null, indication = null) {
                AppHaptics.click()
                onPlay(file)
            }
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
                // "Offline" is said only by the ledger, never by Telegram's record of the file: a channel
                // copy can report bytes on disk that the launch sweep has already handed back, and a row
                // that promised a flight-proof film on the strength of that record would be lying.
                when {
                    file.isOnPhone -> "On this phone"
                    isOffline -> "Offline"
                    else -> "On Telegram"
                },
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

/**
 * One catalogue image, decoded down before it reaches the screen. Null until it lands, or if it never does.
 *
 * Seeded from the memory cache rather than from null, so an image already decoded for another card
 * appears on this one in the same frame instead of flashing an empty slot for a beat first.
 */
@Composable
internal fun rememberCachedArt(url: String?, targetWidthPx: Int): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(url, targetWidthPx) { mutableStateOf(PosterCache.cached(url, targetWidthPx)) }
    LaunchedEffect(url, targetWidthPx) {
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
internal fun TitleDetailFetch(providerId: Int?, isSeries: Boolean, prefsStore: ArcPreferencesStore): TitleDetail? {
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

/**
 * The one picture a shelf title wears everywhere: the catalogue's wide shot once the shelf knows it,
 * the poster until then.
 *
 * The card, the rails and the info page's hero all ask this. They used to ask different things — a card
 * drew the portrait poster while its page drew a wide shot from a different photograph entirely — so
 * opening a title read as a change of subject rather than as the same film seen closer.
 */
internal fun shelfArtUrl(title: ShelfTitle, detail: TitleDetail?): String? =
    detail?.backdropUrl ?: title.posterUrl

/** The same, read through the store so a card redraws the moment its detail lands. */
@Composable
internal fun rememberShelfArtUrl(title: ShelfTitle, prefsStore: ArcPreferencesStore): String? =
    shelfArtUrl(title, prefsStore.titleDetail(title.providerId, title.isSeries))

/**
 * Asks the catalogue about every title on the shelf, once, so each card can wear the shot its page will.
 *
 * The detail request is the one that carries the wide backdrop, and until now it only ran when a page was
 * opened. Serial rather than concurrent: the shelf holds dozens of titles, the answers persist for the
 * install, and a burst of thirty calls is how a free key gets rate limited.
 */
@Composable
internal fun ShelfArtworkWarmPass(titles: List<ShelfTitle>, prefsStore: ArcPreferencesStore) {
    val wanted = remember(titles) {
        titles.mapNotNull { title -> title.providerId?.let { it to title.isSeries } }.distinct()
    }
    LaunchedEffect(wanted, prefsStore) {
        val missing = wanted.filter { (id, series) -> prefsStore.titleDetail(id, series) == null }
        if (missing.isEmpty()) return@LaunchedEffect
        missing.forEach { (id, series) ->
            val fetched = withContext(Dispatchers.IO) {
                runCatching { TmdbDetails.title(BuildConfig.TMDB_API_KEY, id, series) }.getOrNull()
            }
            if (fetched != null) prefsStore.rememberTitleDetail(id, series, fetched, persist = false)
        }
        prefsStore.persistTitleDetails()
    }
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

/**
 * Whether the system has promised not to put this install to sleep.
 *
 * A question the app asks the device rather than remembers answering: the viewer can revoke it from the
 * battery screen at any time, and a settings page that said "off" while the system said "on" would be
 * worse than no page at all.
 */
private fun isExemptFromSleep(context: Context): Boolean {
    val power = ContextCompat.getSystemService(context, PowerManager::class.java) ?: return false
    return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
}

/**
 * Ask the system to stop putting this install to sleep.
 *
 * Two ways in, because the door is not in the same place on every build: the direct request is answered
 * by the system with a dialog naming this app, and on installs that refuse it the list of optimised apps
 * is the same switch with a person in front of it. The `package:` data is what makes the first dialog
 * about Arc Player rather than about the battery screen in general.
 */
private fun askToSkipSleep(context: Context) {
    val asked = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess || runCatching {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!asked) {
        Toast.makeText(context, "This device has no battery optimisation settings.", Toast.LENGTH_LONG).show()
    }
}

/**
 * The app's own page inside system settings, which is where Samsung hides its second sleeper.
 *
 * One UI puts an app to sleep on its own schedule as well as through the optimisation asked about above,
 * and no public intent lands on that screen. The nearest honest route is this app's page, one entry short
 * of Battery, and the row says which switch to look for rather than pretending a button reaches it.
 */
private fun openAppSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) {
        Toast.makeText(context, "This device has no app settings to open.", Toast.LENGTH_LONG).show()
    }
}

/**
 * One line of the ledger with the shelf next to it: what this install is keeping, what the viewer calls
 * it, and how much of it has landed.
 *
 * A promise records a file — where it lives and how long it was told to be — and nothing about what it is
 * called, so every row on the Downloads page is a join with the shelf. The join can miss: a post can fall
 * out of the shelf walk between the tap and the next launch, and a promise nobody can name is still a
 * promise holding bytes. It gets a row rather than vanishing quietly off a page whose whole job is to say
 * what is kept.
 */
private class KeptFilm(
    val entry: DownloadEntry,
    val title: ShelfTitle?,
    val name: String,
    /** Bytes on the device, against the length the shelf promised — the same pair the shade draws. */
    val landed: Long,
    val fraction: Float,
) {
    val arriving: Boolean get() = !entry.isComplete
    val percent: Int get() = (fraction * 100f).roundToInt()

    /**
     * The copy a cancel or a removal is aimed at.
     *
     * The shelf's own whenever it still carries the file. Otherwise built out of the promise, because both
     * actions need only the chat, the message and TDLib's number for the file — which is exactly what the
     * ledger writes down — and a row nobody can act on is a film the viewer is keeping with no way to stop
     * keeping it.
     */
    val target: CloudVideoItem
        get() = (title?.parts?.firstOrNull { it.stableId == entry.stableId } as? CloudVideoItem)
            ?: CloudVideoItem(
                stableId = entry.stableId,
                chatId = entry.chatId,
                chatTitle = SHELF_CHANNEL_TITLE,
                messageId = entry.messageId,
                fileId = entry.fileId,
                title = name,
                sizeBytes = entry.sizeBytes,
                durationMs = 0L,
                width = 0,
                height = 0,
                mimeType = "video/*",
                supportsStreaming = false,
            )
}

/** The page's rows: every promise, joined to the shelf, in the order the promises were made. */
private fun keptFilms(
    shelf: CollectionShelf,
    entries: Map<Long, DownloadEntry>,
    progress: Map<Int, CloudProgress>,
): List<KeptFilm> {
    val byCopy = HashMap<Long, ShelfTitle>()
    for (title in shelf.resolved) {
        for (part in title.parts) byCopy[part.stableId] = title
    }
    return entries.values.map { entry ->
        val title = byCopy[entry.stableId]
        val fileTitle = title?.parts?.firstOrNull { it.stableId == entry.stableId }?.title
        val landed = DownloadNotices.landedBytes(entry.sizeBytes, progress[entry.fileId])
        KeptFilm(
            entry = entry,
            title = title,
            name = title?.name?.takeIf { it.isNotBlank() }
                ?: fileTitle?.takeIf { it.isNotBlank() }
                ?: "A kept film the shelf no longer names",
            // A film the ledger calls whole is whole; its row does not need Telegram to still be
            // reporting bytes for it, and after a restart nothing is.
            landed = if (entry.isComplete) entry.sizeBytes else landed,
            fraction = DownloadNotices.progressOf(entry.sizeBytes, landed),
        )
    }
}

/**
 * The Downloads page: every film this install is keeping, with the fetches still moving at the top.
 *
 * Built as a page of the browser rather than as another hand-rolled overlay, which is what gives it the
 * collapsing header, the glass back pill and the slide the rest of the app uses between screens without
 * this file writing a single line of transition code.
 */
@Composable
private fun DownloadsContent(
    shelf: CollectionShelf,
    downloads: Map<Long, DownloadEntry>,
    progress: StateFlow<Map<Int, CloudProgress>>,
    prefsStore: ArcPreferencesStore,
    gridState: LazyGridState,
    header: @Composable () -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenTitle: (ShelfTitle) -> Unit,
    onCancelDownload: (CloudVideoItem) -> Unit,
    onRemoveDownload: (CloudVideoItem) -> Unit
) {
    val published by progress.collectAsState()
    val films = remember(shelf, downloads, published) { keptFilms(shelf, downloads, published) }
    val arriving = films.filter { it.arriving }
    val kept = films.filterNot { it.arriving }
    val keptBytes = kept.sumOf { it.entry.sizeBytes }
    val folder = KeptMedia.FOLDER.trimEnd('/')

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(1),
        modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "downloads_heading") {
            header()
        }
        item(key = "downloads_summary") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        arriving.isNotEmpty() && kept.isNotEmpty() ->
                            "${arriving.size} ${if (arriving.size == 1) "film" else "films"} arriving · ${kept.size} kept"
                        arriving.isNotEmpty() ->
                            "Keeping ${arriving.size} ${if (arriving.size == 1) "film" else "films"}"
                        kept.isNotEmpty() ->
                            "${kept.size} ${if (kept.size == 1) "film" else "films"} kept on this device"
                        else -> "Nothing is kept on this device"
                    },
                    fontSize = 15.sp,
                    color = OneUiTextSecondary
                )
                Text(
                    text = if (kept.isNotEmpty()) {
                        "${formatFileSize(keptBytes)} in $folder, playable without a connection"
                    } else {
                        "Kept films land in $folder, where the Gallery can see them too"
                    },
                    fontSize = 13.sp,
                    color = OneUiTextSecondary
                )
            }
        }

        if (arriving.isNotEmpty()) {
            item(key = "downloads_section_arriving") { DownloadsSectionLabel("Arriving") }
            itemsIndexed(arriving, key = { _, film -> "download_${film.entry.stableId}" }) { index, film ->
                StaggeredListItem(index, "download:${film.entry.stableId}") {
                    DownloadFilmRow(film, prefsStore, progress, onOpenTitle, onCancelDownload, onRemoveDownload)
                }
            }
        }

        if (kept.isNotEmpty()) {
            item(key = "downloads_section_kept") { DownloadsSectionLabel("On this device") }
            itemsIndexed(kept, key = { _, film -> "download_${film.entry.stableId}" }) { index, film ->
                StaggeredListItem(index, "download:${film.entry.stableId}") {
                    DownloadFilmRow(film, prefsStore, progress, onOpenTitle, onCancelDownload, onRemoveDownload)
                }
            }
        }

        if (films.isEmpty()) {
            item(key = "downloads_empty") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = OneUiCardSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No downloads yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OneUiTextPrimary
                        )
                        Text(
                            text = "Press the download circle on a film's page and this install keeps a " +
                                "copy of it — the same copy the player opens when there is no signal.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = OneUiTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = OneUiTextSecondary
    )
}

/**
 * The bar for one film: three dp of track with the landed part laid over it.
 *
 * Animated because TDLib publishes a tick per packet group, which on a good link is a step every half
 * second rather than a slide, and a gauge that jumps reads as a broken one.
 */
@Composable
private fun DownloadBar(fraction: Float) {
    val shown by animateFloatAsState(fraction, label = "DownloadBarFraction")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(OneUiTextSecondary.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(shown.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(OneUiAccent)
        )
    }
}

/**
 * One film the viewer is keeping.
 *
 * The row wears the same poster the shelf card wears, because a download is the same film seen from the
 * other end. The action on the right is the info page's own disc at the row's height, so a film arriving
 * here shows the ringed arrow with the same fraction it shows on its page, and a kept one the same tick
 * with the same overflow behind it.
 */
@Composable
private fun DownloadFilmRow(
    film: KeptFilm,
    prefsStore: ArcPreferencesStore,
    progress: StateFlow<Map<Int, CloudProgress>>,
    onOpenTitle: (ShelfTitle) -> Unit,
    onCancelDownload: (CloudVideoItem) -> Unit,
    onRemoveDownload: (CloudVideoItem) -> Unit
) {
    val title = film.title
    val artUrl = if (title != null) rememberShelfArtUrl(title, prefsStore) else null
    val entry = film.entry
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = OneUiCardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (title != null) {
                    Modifier.jellyClick(role = Role.Button, onClickLabel = "Open ${title.name}") {
                        onOpenTitle(title)
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ArtSlot(
                art = rememberCachedArt(artUrl, 240),
                contentDescription = film.name,
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlotWaitingTint)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = film.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = OneUiTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    !film.arriving -> Text(
                        text = "${formatFileSize(entry.sizeBytes)} · available offline",
                        fontSize = 12.sp,
                        color = OneUiTextSecondary
                    )
                    entry.sizeBytes > 0L -> {
                        Text(
                            text = "${film.percent}% · ${formatFileSize(film.landed)} of " +
                                formatFileSize(entry.sizeBytes),
                            fontSize = 12.sp,
                            color = OneUiTextSecondary
                        )
                        DownloadBar(film.fraction)
                    }
                    // Said rather than drawn: TDLib answers the length of a document only once it has the
                    // record, and a bar would be a promise about a number nobody has given.
                    else -> Text(
                        text = "Waiting for Telegram to say how long it is",
                        fontSize = 12.sp,
                        color = OneUiTextSecondary
                    )
                }
            }
            // Every row here is a promise the ledger holds, so the disc is never in its "ask me" reading:
            // a ring being pressed means stop, and a tick being pressed means the menu that holds Remove.
            DownloadCircleTarget(
                size = 38.dp,
                saved = !film.arriving,
                arriving = film.arriving,
                progressKey = entry.fileId,
                progress = progress,
                onClick = { onCancelDownload(film.target) },
                onRemove = { onRemoveDownload(film.target) }
            )
        }
    }
}

@Composable
private fun SettingsContent(
    snapshot: MediaLibrarySnapshot,
    prefsStore: ArcPreferencesStore,
    repository: CloudRepository,
    onManageCollection: () -> Unit,
    onAskNotifications: () -> Unit,
    updateChecking: Boolean,
    updateUnreachable: Boolean,
    updateRelease: Release?,
    updateDownload: UpdateDownload,
    onUpdateAction: () -> Unit,
    gridState: LazyGridState,
    header: @Composable () -> Unit,
    topPadding: Dp,
    bottomPadding: Dp
) {
    val totalBytes = remember(snapshot.videos) { snapshot.videos.sumOf { it.sizeBytes } }
    val authState by repository.authState.collectAsState()
    val connected = authState.stage == CloudAuthStage.AUTHORIZED
    val context = LocalContext.current
    // Both are the device's answers, not this app's, so both are re-read every time the page is looked at
    // again — which is also the moment the viewer has most likely just come back from changing one.
    var exemptFromSleep by remember { mutableStateOf(isExemptFromSleep(context)) }
    var notificationsAllowed by remember { mutableStateOf(DownloadNotifier.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exemptFromSleep = isExemptFromSleep(context)
        notificationsAllowed = DownloadNotifier.canNotify(context)
    }

    // One line carries both the answer and the doing, in that order of priority: a transfer that has
    // started is a louder fact than the button that started it, and the reason the home card stays
    // silent about a failed check does not apply here — this question was asked out loud, so an
    // unanswered one has to be admitted.
    val updateCaption = when {
        updateDownload is UpdateDownload.Running ->
            "Downloading Arc Player ${updateRelease?.version.orEmpty()}… ${(updateDownload.fraction * 100).toInt()}%"
        updateDownload is UpdateDownload.Ready -> "It is on this phone, waiting to be installed."
        updateDownload is UpdateDownload.Failed -> "The download stopped. It can be tried again."
        updateChecking -> "Asking GitHub…"
        updateRelease != null -> "Arc Player ${updateRelease.version} is out."
        updateUnreachable -> "Couldn't reach GitHub."
        else -> "This is the newest release."
    }
    val updateActionLabel = when {
        updateDownload is UpdateDownload.Ready -> "Install"
        updateDownload is UpdateDownload.Failed -> "Retry"
        updateChecking -> "Checking"
        updateRelease != null -> "Update"
        else -> "Check"
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(1),
        modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "settings_heading") {
            header()
        }
        item(key = "settings_telegram") {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = OneUiCardSurface,
                modifier = Modifier.fillMaxWidth().jellyClick(onClick = onManageCollection)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Telegram collection", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (connected) "Connected · Manage your collection" else "Connect your account",
                        fontSize = 14.sp, color = OneUiTextSecondary)
                }
            }
        }
        item(key = "menu_library_overview") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = OneUiCardSurface,
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

        item(key = "menu_downloads") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = OneUiCardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Downloads",
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
                            Text("Download notifications", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "How far a film has come, and a word when it is ready to watch offline",
                                fontSize = 12.sp,
                                color = OneUiTextSecondary
                            )
                        }
                        if (notificationsAllowed) {
                            Text("On", fontSize = 13.sp, color = OneUiTextSecondary)
                        } else {
                            TextButton(onClick = onAskNotifications) {
                                Text("Allow", color = OneUiAccent)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery optimization", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "While it is on, the phone is free to freeze a download the moment the " +
                                    "screen goes off. Off means a long film can land from a pocket.",
                                fontSize = 12.sp,
                                color = OneUiTextSecondary
                            )
                        }
                        TextButton(onClick = { askToSkipSleep(context) }, enabled = !exemptFromSleep) {
                            Text(if (exemptFromSleep) "Off" else "Turn off", color = OneUiAccent)
                        }
                    }

                    // Said out loud because it is the second sleeper and the one that bites on this brand:
                    // the exemption above is the system's, and this is the manufacturer's on top of it.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Put never to sleep", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "On Samsung: Battery → Background usage limits → put in Unmonitored apps. " +
                                    "The system cannot press this one.",
                                fontSize = 12.sp,
                                color = OneUiTextSecondary
                            )
                        }
                        TextButton(onClick = { openAppSettings(context) }) {
                            Text("Open", color = OneUiAccent)
                        }
                    }
                }
            }
        }

        item(key = "menu_playback_settings") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = OneUiCardSurface,
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

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Double-Tap Seek Interval", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Skip step for double and multi-tap seek", fontSize = 12.sp, color = OneUiTextSecondary)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = OneUiPillSelected
                            ) {
                                Text(
                                    text = "${prefsStore.doubleTapSeekSeconds}s",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiSelectedFill,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Slider(
                            value = prefsStore.doubleTapSeekSeconds.toFloat(),
                            onValueChange = { newVal ->
                                val snapped = (kotlin.math.round(newVal / 5f) * 5f).toInt().coerceIn(5, 20)
                                if (snapped != prefsStore.doubleTapSeekSeconds) {
                                    prefsStore.updateDoubleTapSeekSeconds(snapped)
                                    AppHaptics.tick()
                                }
                            },
                            valueRange = 5f..20f,
                            steps = 2,
                            colors = SliderDefaults.colors(
                                thumbColor = OneUiSelectedFill,
                                activeTrackColor = OneUiSelectedFill,
                                inactiveTrackColor = OneUiPillSelected
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(5, 10, 15, 20).forEach { s ->
                                Text(
                                    text = "${s}s",
                                    fontSize = 11.sp,
                                    color = if (prefsStore.doubleTapSeekSeconds == s) OneUiSelectedFill else OneUiTextSecondary,
                                    fontWeight = if (prefsStore.doubleTapSeekSeconds == s) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clickable {
                                        if (prefsStore.doubleTapSeekSeconds != s) {
                                            prefsStore.updateDoubleTapSeekSeconds(s)
                                            AppHaptics.tick()
                                        }
                                    }
                                )
                            }
                        }
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
                                    color = if (selected) OneUiSelectedFill else OneUiPillSelected
                                ) {
                                    Text(
                                        text = "$cols",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) OneUiOnSelectedFill else OneUiTextPrimary,
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
                            Text("Clear History", color = OneUiDestructive)
                        }
                    }
                }
            }
        }

        item(key = "menu_touch_feedback") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = OneUiCardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Touch Feedback",
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
                            Text("Haptic Feedback", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("A knock when a control is pressed, a heavier one when something is deleted",
                                fontSize = 12.sp, color = OneUiTextSecondary)
                        }
                        Switch(
                            checked = prefsStore.hapticsEnabled,
                            onCheckedChange = {
                                prefsStore.updateHaptics(it)
                                AppHaptics.toggle(it)
                            }
                        )
                    }

                    AnimatedVisibility(visible = prefsStore.hapticsEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Intensity", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HapticIntensity.entries.forEach { level ->
                                    val selected = prefsStore.hapticIntensity == level
                                    Surface(
                                        // Choosing a strength you cannot feel is a guess, so picking one plays it.
                                        onClick = {
                                            prefsStore.updateHapticIntensity(level)
                                            AppHaptics.preview(level)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) OneUiSelectedFill else OneUiPillSelected
                                    ) {
                                        Text(
                                            text = level.label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) OneUiOnSelectedFill else OneUiTextPrimary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "menu_updates") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = OneUiCardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Updates",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Version", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(BuildConfig.VERSION_NAME, fontSize = 13.sp, color = OneUiTextSecondary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check for updates", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(updateCaption, fontSize = 12.sp, color = OneUiTextSecondary)
                        }
                        if (updateDownload !is UpdateDownload.Running) {
                            TextButton(onClick = onUpdateAction, enabled = !updateChecking) {
                                Text(updateActionLabel, color = OneUiAccent)
                            }
                        }
                    }

                    if (updateDownload is UpdateDownload.Running) {
                        UpdateProgress(updateDownload.fraction)
                    }
                }
            }
        }

        item(key = "menu_data_sources") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = OneUiCardSurface,
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

/**
 * A browser page's heading, drawn as the grid's first row so it travels with the cards under it.
 *
 * This is what replaced the pinned action bar and its pull-down one-handed header: the collection home
 * has always put its 32 sp word inside the scroll, and these pages now do the same, with the page's own
 * controls standing beside it as circles rather than hanging in a bar that never moves.
 */
@Composable
private fun BrowserHeading(
    title: String,
    onBack: () -> Unit,
    search: BrowserSearch?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (search != null && search.expanded) {
            SearchPillRow(search)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeadingCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                CollectionHeading(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                )
                if (search != null) {
                    Spacer(Modifier.width(8.dp))
                    HeadingCircle(Icons.Outlined.Search, "Search videos", search.onExpand)
                    Spacer(Modifier.width(6.dp))
                    MorphingMenu(
                        items = search.menuItems,
                        modifier = Modifier.clip(CircleShape).background(OneUiPillSurface),
                        buttonSize = 44.dp
                    )
                }
            }
        }
    }
}

/** A page's way into its own titles, kept to the page because the shelf answers from a different catalogue. */
private class BrowserSearch(
    val expanded: Boolean,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onExpand: () -> Unit,
    val onClose: () -> Unit,
    val menuItems: List<MorphingMenuItem>
)

@Composable
private fun SearchPillRow(search: BrowserSearch) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TopActionPillShape)
            .background(OneUiPillSurface)
            .padding(start = 2.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeadingCircle(Icons.AutoMirrored.Filled.ArrowBack, "Close search", search.onClose)
        Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (search.query.isEmpty()) {
                Text(
                    text = "Search folders & videos...",
                    fontSize = 15.sp,
                    color = OneUiTextSecondary
                )
            }
            BasicTextField(
                value = search.query,
                onValueChange = search.onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = OneUiTextPrimary),
                cursorBrush = SolidColor(OneUiTextPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (search.query.isNotEmpty()) {
            HeadingCircle(Icons.Default.Close, "Clear search") { search.onQueryChange("") }
        }
    }
}

@Composable
private fun HeadingCircle(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(OneUiPillSurface)
            .jellyClick(role = Role.Button, onClickLabel = label) {
                AppHaptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = OneUiTextPrimary, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ScrollToTopCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = OneUiPillSurface,
        modifier = modifier
            .size(44.dp)
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
    header: @Composable () -> Unit,
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
        modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "folder_heading", span = { GridItemSpan(maxLineSpan) }) {
            header()
        }
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
    header: @Composable () -> Unit,
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
        modifier = Modifier.physicsBounceOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = topPadding,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "video_heading", span = { GridItemSpan(maxLineSpan) }) {
            header()
        }
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
            .background(SlotWaitingTint)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.08f),
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
                            .background(OneUiAccent)
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
            .background(SlotWaitingTint)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.08f),
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
                tint = OneUiAccent,
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
                        .background(OneUiAccent)
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
    FIT("Fit to screen"),
    STRETCH("Stretch"),
    CROP("Crop to fit")
}

private enum class ActiveGestureMode {
    NONE,
    BRIGHTNESS,
    VOLUME,
    HORIZONTAL_SEEK,
    VERTICAL_DISMISS,
    PINCH_ZOOM,

    /** A vertical drag that began on the chrome: deliberately answers to nothing. */
    CHROME
}

private enum class FastSeekSide {
    NONE,
    LEFT,
    RIGHT
}

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

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HeroVlcVideoPlayerScreen(
    playback: ActiveVideoPlayback,
    prefsStore: ArcPreferencesStore,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    cloudRepository: CloudRepository?,
    onSelectAdjacentVideo: (VideoItem) -> Unit,
    onFallToAnotherCopy: (VideoItem, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val mainActivity = activity as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current

    // A playing film owns the whole glass. While this screen is up the status and navigation bars
    // are gone, and an edge swipe only borrows them back for a moment — so the first swipe can
    // neither drop the notification shade on the picture nor throw the viewer home mid-scene.
    DisposableEffect(activity, hostView) {
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, hostView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
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
        ArcVlcPlayerEngine(
            context,
            initialSubtitleLiftPercent = prefsStore.subtitleLiftPercent,
            initialSubtitleSizePercent = prefsStore.subtitleSizePercent
        )
    }

    // The two subtitle numbers belong to the viewer, not to this engine, which is built again the next
    // time a film opens.
    DisposableEffect(vlcEngine, prefsStore) {
        vlcEngine.onSubtitleStyleChange = { liftPercent, sizePercent ->
            prefsStore.updateSubtitleStyle(liftPercent, sizePercent)
        }
        onDispose { vlcEngine.onSubtitleStyleChange = null }
    }

    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var trackSheet by remember { mutableStateOf(TrackSheet.NONE) }
    var expandedPlayerTools by remember { mutableStateOf(false) }

    /** Bumped by every touch inside the control layer, which restarts the hide countdown. */
    var controlsTouchStamp by remember { mutableIntStateOf(0) }

    /** True while a finger is held down in the control layer, so the countdown waits for the lift. */
    var isTouchingControls by remember { mutableStateOf(false) }

    // The chrome borrows the screen rather than owning it: three seconds after it comes up, or after
    // the last touch inside it, the frame goes back to the film. A finger resting on the scrubber is
    // still using it, so the countdown waits for the lift rather than pulling the bar out from under
    // the drag, and an open tracks sheet is an answer the viewer is still reading.
    LaunchedEffect(showControls, isControlsLocked, trackSheet, controlsTouchStamp, isTouchingControls) {
        if (!showControls || isControlsLocked || trackSheet != TrackSheet.NONE || isTouchingControls) return@LaunchedEffect
        delay(3_000L)
        showControls = false
    }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var sizeToastLabel by remember { mutableStateOf("") }
    var sizeToastVisible by remember { mutableStateOf(false) }
    var sizeToastStamp by remember { mutableIntStateOf(0) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var gestureHudSticky by remember { mutableStateOf(false) }
    var gestureGaugeFraction by remember { mutableStateOf<Float?>(null) }
    var isBoostGauge by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var multiTapSide by remember { mutableStateOf(FastSeekSide.NONE) }
    var multiTapSeconds by remember { mutableIntStateOf(0) }
    var multiTapResetJob by remember { mutableStateOf<Job?>(null) }
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    /** Where the last scrub tick was felt, as a fraction of the run. */
    var lastScrubTick by remember { mutableFloatStateOf(0f) }

    /** Seconds left on the next-up card. Zero means nothing is being offered. */
    var nextUpSeconds by remember(video.id) { mutableIntStateOf(0) }

    /** The next-up card up before the end, with no countdown: offered, not imminent. */
    var nextUpEarly by remember(video.id) { mutableStateOf(false) }

    /** The part whose offer the viewer turned down; asking again for the same one would be nagging. */
    var declinedPartId by remember(video.id) { mutableStateOf<Long?>(null) }

    /**
     * The line under the spinner while a cloud seek waits for its bytes. Kept apart from the gesture
     * pill: this one is read for the whole length of a wait, not for the moment a gesture ends.
     */
    var seekStageText by remember(video.id) { mutableStateOf("") }

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

    /** The open gave up — no path, no file. Its explanation is a pill, not a spinner that never stops. */
    var openGaveUp by remember(video.id) { mutableStateOf(false) }

    /**
     * Which copies of this part the player has already been refused by, and why the file now opening
     * was asked for — both carried on the playback, because this composable is rebuilt by the switch.
     */
    var reasonAlreadySaid by remember(video.id) { mutableStateOf<String?>(null) }

    /**
     * Put down the copy that would not open and hand the player another one of the same part.
     *
     * Two ways a file fails here, and neither is the viewer's doing: Telegram will not deliver the bytes,
     * and libVLC reads the container and refuses it. A shelf that holds the film twice has a second try
     * to offer, and the one it offers is the best of what is left — which after a refusal of the picked
     * copy is usually the file already on this phone.
     */
    fun tryAnotherCopy(reason: String): Boolean {
        val next = PlayOrder.otherCopyOfPart(
            slots = playback.copySlots,
            presentIds = playback.playlist.map { it.id },
            failedId = video.id,
            alreadyTried = playback.refusedCopies
        )?.let { id -> playback.playlist.firstOrNull { it.id == id } } ?: return false
        onFallToAnotherCopy(next, reason)
        return true
    }

    // Tap to first picture: fetching the range, then letting the player open what landed.
    //
    // A cloud film is waiting from the moment the player mounts, not from the moment it has something
    // loaded: resolving the path is a Telegram round trip of its own (measured at 2.6 s on a warm file),
    // and gating on `openedMedia` left that stretch with no overlay at all — the transport row up over an
    // empty band, which is what the first seconds of a film looked like.
    val isWaitingForFirstFrame = !openGaveUp && (isAskingTelegram ||
        ((cloudSource != null || openedMedia != null) && !vlcEngine.isFirstFrameRendered))

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
    val parsedTitle = remember(video.title) { MediaNameParser.parse(video.title) }
    val playerTitle = titleCard?.title ?: when (val guess = parsedTitle) {
        is MediaGuess.Movie -> guess.title
        is MediaGuess.Episode -> guess.series
        is MediaGuess.Unnamed -> guess.fallback
    }
    val playerSubtitle = when (val guess = parsedTitle) {
        is MediaGuess.Episode -> NextUp.labelFor(video.title)
        is MediaGuess.Movie -> titleCard?.subtitle ?: guess.year?.toString()
        is MediaGuess.Unnamed -> titleCard?.subtitle
    }

    // Decoded here rather than inside the wordmark so the overlay knows whether the art has landed
    // yet, and can keep the spinner under it until it does instead of leaving a hole.
    var wordmarkBitmap by remember(wordmarkUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wordmarkUrl) {
        wordmarkBitmap = wordmarkUrl?.let {
            runCatching { PosterCache.bitmap(context, it, PosterCache.WORDMARK_WIDTH_PX) }.getOrNull()
        }
    }

    /**
     * The film's own wide shot — the same picture the info page wears — asked for at the hero's width
     * so it is the bitmap already in the cache rather than a second decode of the same file.
     *
     * Null for anything the shelf never resolved: a local import, or a title whose catalogue row has no
     * backdrop. Those keep the file's own extracted frame.
     */
    val loadingArtUrl = prefsStore
        .titleDetail(titleCard?.providerId, titleCard?.isSeries == true)?.backdropUrl
        ?: titleCard?.posterUrl
    val loadingArt = rememberCachedArt(loadingArtUrl, PosterCache.HERO_WIDTH_PX)

    LaunchedEffect(video.id, video.uri) {
        zoomScale = 1f
        panOffset = Offset.Zero
        gestureHudSticky = false
        // The watched minute belongs to the part rather than to the file that earned it, so a copy the
        // viewer has never opened here picks up where one of its siblings stopped.
        val sharedPosition =
            PlayOrder.positionByCopy(playback.copySlots, prefsStore.resumePositions)[video.id]
        val savedResumePos = sharedPosition ?: prefsStore.resumePositions[video.id] ?: 0L
        // A film the viewer kept is read off the disk it was saved on, before a single question is asked
        // of Telegram: the point of keeping it is that it opens on a plane, and the resolve below spends a
        // round trip to name a file this phone already has the address of. The ledger is awaited first
        // because it is read at launch and this open can beat it — and on a plane, the fall-through has no
        // answer at all. A copy still arriving is deliberately not taken through here: half a file read
        // straight off the disk has no way to ask for the rest, which is the whole job of the window.
        val keptHandle = cloudSource?.let { source ->
            cloudRepository?.let { repository ->
                repository.restoreLedger()
                repository.downloadedHandleFor(source)
            }
        }
        if (keptHandle != null) {
            // Letting go of the source is how the window's three customers stand down at once: the tick
            // that drags the fetch behind the play head, the loop that keeps it moving through a stall,
            // and the seek that waits on bytes. With the film already here there is nothing to aim, and
            // aiming would spend the link re-streaming what the viewer already paid for.
            cloudSource = null
        }
        val streamUri = when {
            video.uri != Uri.EMPTY -> video.uri
            keptHandle != null -> Uri.parse(keptHandle)
            cloudRepository == null || cloudSource == null -> {
                // Three ways out of here land on a black player with no log line at all, and a phone
                // that says "it opened and played nothing" cannot be argued with. Which of them fired
                // is the whole diagnosis, so it gets written down — with the launch route, because
                // `cloudSources` is carried by the screen that opened the film, and an empty list says
                // a grid handed over a cloud row as though it were one of its own local videos.
                android.util.Log.i(
                    "ArcPlayer",
                    "resolve refused: repo=${cloudRepository != null} source=${cloudSource?.fileId} " +
                        "parts=${playback.cloudSources.size} from=${playback.sharedKeyPrefix} video=${video.id}"
                )
                Uri.EMPTY
            }
            else -> {
                isAskingTelegram = true
                val asked = cloudSource
                val live = asked?.let {
                    runCatching { cloudRepository.resolveLive(it) }
                        .onFailure { android.util.Log.w("ArcPlayer", "resolveLive threw", it) }
                        .getOrNull() ?: it
                }
                isAskingTelegram = false
                if (live != null && live != asked) cloudSource = live
                val path = live?.let {
                    runCatching { cloudRepository.localPathFor(it) }
                        .onFailure { android.util.Log.w("ArcPlayer", "localPathFor threw", it) }
                        .getOrNull()
                }
                android.util.Log.i("ArcPlayer", "resolve file=${asked?.fileId} path=${path ?: "none"}")
                path?.let { Uri.fromFile(File(it)) } ?: Uri.EMPTY
            }
        }
        if (streamUri == Uri.EMPTY) {
            // The shelf holds this part twice more often than it used to, so a copy Telegram will not
            // hand back is asked of the other files before it is said out loud.
            if (tryAnotherCopy("That copy could not be fetched. Playing another.")) return@LaunchedEffect
            // The player opened on this film's promise and has nothing to show, so the reason stays on
            // screen rather than flicking for 800 ms over a black frame.
            openGaveUp = true
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
        // Said once per file: a re-open of the same copy — a stall that aims the window again — must
        // not repeat a sentence the viewer has already read.
        playback.switchReason?.let { reason ->
            if (reason != reasonAlreadySaid) {
                reasonAlreadySaid = reason
                gestureHudText = reason
            }
        }
    }

    // libVLC took this file in hand and refused it, which is a different thing from going quiet: a
    // truncated upload and a codec this build cannot open both land here, and both are answered by the
    // other copy of the part rather than by a re-open of bytes that will never play.
    LaunchedEffect(vlcEngine.openRefused, video.id) {
        if (!vlcEngine.openRefused) return@LaunchedEffect
        if (!tryAnotherCopy("That file would not open here. Playing another copy.")) {
            openGaveUp = true
            gestureHudSticky = true
            gestureHudText = "This file would not open on this phone."
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
        // [BufferGuard.NEVER] is Long.MIN_VALUE, so subtracting it wraps past zero and prints an age
        // that reads as "it just happened" — which is how a black player's own stall line argued that
        // the player had reported a position a moment ago.
        fun ago(nowMs: Long, stamp: Long): String =
            if (stamp == BufferGuard.NEVER) "never" else (nowMs - stamp).toString()
        var stalledSinceMs = BufferGuard.NEVER
        var restarts = 0
        var lastRestartMs = BufferGuard.NEVER
        var lastAimMs = -1L
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
            // The only thing that forgives the attempt count is the film getting clear of the spot it
            // keeps dying in. Two seconds used to do it, and that is what made a stall loop: re-opening at
            // the failing byte plays a few seconds before the same byte fails again, so every restart
            // earned its reset and the three attempts never ran out.
            if (lastAimMs >= 0L && headMs > lastAimMs + BufferGuard.FAULT_CLEARED_MS) {
                lastAimMs = -1L
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
                playOwedSinceMs = vlcEngine.playOwedSinceMs,
                firstFrameRendered = vlcEngine.isFirstFrameRendered
            )
            val stalled = durationMs > 1L && BufferGuard.isStalled(guard)
            if (!stalled) {
                stalledSinceMs = BufferGuard.NEVER
                vlcEngine.isBuffering = false
                // The line this loop put up describes a wait, so it goes when the wait does. Left up,
                // it outlives the stall and sits over a film that is playing, with nothing on screen
                // able to clear it short of opening another one.
                if (admittedSlowly) {
                    admittedSlowly = false
                    gestureHudSticky = false
                    gestureHudText = null
                }
            } else {
                vlcEngine.isBuffering = true
                // Only the fake end is ours to undo. A scrubber the viewer moved during the wait is a
                // position they chose, and dragging it back every second would be the app arguing.
                if (vlcEngine.currentPositionMs == vlcEngine.durationMs) vlcEngine.holdAtLastFrame()
                val headBytes = repository.cachedByteOffset(source, headMs, durationMs)
                runCatching { repository.advanceWindow(source, headBytes) }
                    .onFailure { android.util.Log.w("ArcCloud", "stall pump failed", it) }
                val settled = runCatching { repository.settledAhead(source, headBytes) }.getOrNull() ?: 0L
                if (stalledSinceMs == BufferGuard.NEVER) {
                    stalledSinceMs = now
                    // Thrown once, at the moment the picture goes quiet, because the restart that may
                    // follow is a guess and this is the evidence for it. Every one of these numbers is
                    // the app's own belief about the film or the player, so a frozen picture that never
                    // gets its restart can be read apart from here: a stall with `settled=0` is a link
                    // question, one with megabytes settled and `playing=false` is a player question.
                    android.util.Log.i(
                        "ArcCloud",
                        "stall seen at ${headMs}ms headBytes=$headBytes settled=$settled " +
                            "playing=${vlcEngine.isPlaying} firstFrame=${vlcEngine.isFirstFrameRendered} " +
                            "seekWaiting=${vlcEngine.isSeekWaiting} duration=${durationMs} " +
                            "headMovedAgo=${ago(now, headMovedAtMs)} tickedAgo=${ago(now, vlcEngine.lastTickAtMs)} " +
                            "owedSince=${vlcEngine.playOwedSinceMs} aim=$lastAimMs"
                    )
                }
                if (BufferGuard.shouldRestart(
                        nowMs = now,
                        stalledSinceMs = stalledSinceMs,
                        bytesAhead = settled,
                        restarts = restarts,
                        lastRestartMs = lastRestartMs,
                        graceMs = BufferGuard.graceMs(guard),
                        bytesToEof = (source.sizeBytes - headBytes).coerceAtLeast(0L)
                    )
                ) {
                    val media = openedMedia
                    if (media != null) {
                        val targetMs = BufferGuard.restartTargetMs(headMs, lastAimMs, durationMs)
                        restarts++
                        lastRestartMs = now
                        lastAimMs = targetMs
                        android.util.Log.i(
                            "ArcCloud",
                            "stall restart #$restarts at ${targetMs}ms (head was ${headMs}ms) " +
                                "with $settled settled bytes ahead"
                        )
                        vlcEngine.loadAndPlay(media, startPositionMs = targetMs)
                    }
                }
                if ((restarts >= BufferGuard.MAX_RESTARTS ||
                        BufferGuard.isWaitingOnTheLink(now, stalledSinceMs, settled)) && !admittedSlowly
                ) {
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
            // Standing down before the player has media is not a gap: until then the resolve path owns
            // the window and is aiming at the file's tail for its cue table. A byte-0 aim from here in
            // that stretch is a second hand on one window, which is how the tail fetch gets starved.
            if (openedMedia != null && vlcEngine.durationMs <= 1L && !vlcEngine.isCatchingUpToItsPosition) {
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

    /**
     * Default the player screen to landscape orientation.
     *
     * The player defaults to [ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE] immediately upon opening,
     * allowing sensor flips between standard and reverse landscape. When the player exits, [onDispose]
     * restores [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT] so the rest of the app remains locked in portrait.
     * The Rotate tool in player controls acts as a manual toggle to portrait if desired.
     */
    LaunchedEffect(video.id) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.let { win ->
            val controller = WindowInsetsControllerCompat(win, hostView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(video.id) {
        onDispose {
            prefsStore.recordPlaybackProgress(
                videoId = video.id,
                positionMs = vlcEngine.resumablePositionMs,
                durationMs = vlcEngine.durationMs
            )
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
            // Back to the lock the rest of the app lives in, not to the viewer's own switch. FULL_USER
            // follows auto-rotate, so on a phone with that turned on the home was measured lying
            // landscape after a film that had turned itself sideways — and there is no landscape home
            // to lie in. PORTRAIT is what the manifest launches into, so this is the same opinion the
            // app had before the player ever spoke up.
            pendingSingleTapJob?.cancel()
            multiTapResetJob?.cancel()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            vlcEngine.release()
        }
    }

    LaunchedEffect(gestureHudText) {
        val line = gestureHudText
        if (line != null && gestureGaugeFraction == null && !gestureHudSticky) {
            // A gauge reading is a glance; a sentence explaining a copy switch nobody asked for has to
            // be readable while the eye is still looking for the picture. Held by the line rather than
            // by a flag the caller sets, because this effect is re-entered on a switch and a second
            // writer of the same sentence would reset the timer.
            delay(if (line.length > 24) 2_600L else 800L)
            gestureHudText = null
        }
    }

    // Measured off the reference clip: the size pill fades in over 0.45 s and out over 0.37 s,
    // with roughly 0.6 s of full alpha between. The clear fires at the end of that hold; the two
    // fades belong to the AnimatedVisibility below.
    LaunchedEffect(sizeToastStamp) {
        if (sizeToastStamp == 0) return@LaunchedEffect
        delay(1050L)
        sizeToastVisible = false
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
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controlsMargin = if (isLandscape) (configuration.screenWidthDp.dp * 0.085f).coerceAtMost(80.dp) else 24.dp
    val layoutDirection = LocalLayoutDirection.current
    val controlsLeft = maxOf(controlsMargin, with(density) { WindowInsets.safeDrawing.getLeft(this, layoutDirection).toDp() })
    val controlsRight = maxOf(controlsMargin, with(density) { WindowInsets.safeDrawing.getRight(this, layoutDirection).toDp() })
    val compactToolbar = configuration.screenWidthDp.dp - controlsLeft - controlsRight < 440.dp
    var bottomControlsHeight by remember { mutableIntStateOf(0) }
    var topControlsHeight by remember { mutableIntStateOf(0) }
    var nextUpCardHeight by remember { mutableIntStateOf(0) }
    val nextUpBottom = if (isLandscape) navBarBottom + (if (compactToolbar) 112.dp else 92.dp)
        else with(density) { bottomControlsHeight.toDp() } + 12.dp
    val centerControlsOffset = if (isLandscape) minOf(
        0.dp,
        configuration.screenHeightDp.dp / 2 - with(density) { bottomControlsHeight.toDp() } - 12.dp,
        if (nextUpEarly || nextUpSeconds > 0) {
            configuration.screenHeightDp.dp / 2 - nextUpBottom - with(density) { nextUpCardHeight.toDp() } - 48.dp
        } else 0.dp
    ) else 0.dp

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

    /**
     * Puts that same skip in reach before the end instead of only at it.
     *
     * The last seconds of a cloud part are the ones that can die on the viewer: a tail the container
     * will not parse reads exactly like a tail that never arrived, and neither the pump nor a re-open
     * can tell those apart. Measured on the phone as a play head that stops at 23:38 of 23:48 with
     * every remaining byte already on disk and three re-opens that produced no ticks at all. Waiting
     * for an end report from a part like that means waiting forever, so from [NextUp.EARLY_OFFER_MS]
     * out the card is simply up and the viewer decides.
     *
     * No countdown here. This is an offer, not a handover — the handover stays with the effect above,
     * which only runs once the player has honestly reported the end.
     */
    LaunchedEffect(video.id, declinedPartId, nextVideo?.id) {
        nextUpEarly = false
        if (nextVideo == null || declinedPartId == video.id) return@LaunchedEffect
        while (true) {
            nextUpEarly = !vlcEngine.isAtEnd && nextUpSeconds == 0 && NextUp.withinEarlyWindow(
                NextUp.Snapshot(
                    lastShownMs = vlcEngine.resumablePositionMs,
                    durationMs = vlcEngine.durationMs,
                    // Deliberately not [VlcEngine.isBuffering]: the freeze this window exists for is
                    // reported as buffering, and that is precisely when the way out has to stay up.
                    waitingForBytes = vlcEngine.isSeekWaiting,
                    knowsItsParts = playback.copySlots.isNotEmpty(),
                    hasNextPart = hasNext
                )
            )
            delay(1_000L)
        }
    }

    val videoRatio = vlcEngine.videoAspectRatio
    var containerPx by remember { mutableStateOf(IntSize.Zero) }
    // The rect libVLC is told to draw: the largest box of the film's own shape that fits
    // the container. The modes never renegotiate that with the decoder — they only
    // transform the layer showing it, which is what lets a mode change land as a single
    // frame cut the way the reference does, instead of a resize the eye can follow.
    val containerW = containerPx.width
    val containerH = containerPx.height
    val fitW = if (containerW > 0 && containerH > 0) {
        min(containerW, (containerH * videoRatio).toInt()).coerceAtLeast(1)
    } else {
        0
    }
    val fitH = if (fitW > 0) (fitW / videoRatio).toInt().coerceAtLeast(1) else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - dragProgress * 0.75f).coerceIn(0f, 1f)))
            .pointerInput(isControlsLocked, trackSheet) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTime = SystemClock.uptimeMillis()
                    val downPos = down.position
                    var moved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) {
                            moved = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Finger lifted cleanly
                            break
                        }
                        if ((change.position - downPos).getDistance() > touchSlop) {
                            moved = true
                            break
                        }
                    }

                    if (!moved && (SystemClock.uptimeMillis() - downTime) < 500L) {
                        val now = SystemClock.uptimeMillis()
                        val screenWidth = size.width.toFloat()
                        val isLeft = downPos.x < screenWidth * 0.35f
                        val isRight = downPos.x > screenWidth * 0.65f

                        val stepSec = prefsStore.doubleTapSeekSeconds
                        val stepMs = stepSec * 1000L

                        if (!isControlsLocked) {
                            // 1. Chained multi-tap on the same side (triple tap, 4th tap, etc.)
                            if (multiTapSide == FastSeekSide.LEFT && isLeft) {
                                multiTapResetJob?.cancel()
                                pendingSingleTapJob?.cancel()
                                multiTapSeconds += stepSec
                                AppHaptics.tick()
                                vlcEngine.seekByDelta(-stepMs)
                                gestureHudText = "-${multiTapSeconds}s"
                                multiTapResetJob = coroutineScope.launch {
                                    delay(650L)
                                    multiTapSide = FastSeekSide.NONE
                                    multiTapSeconds = 0
                                }
                                lastTapTimestamp = now
                                lastTapPosition = downPos
                                return@awaitEachGesture
                            }

                            if (multiTapSide == FastSeekSide.RIGHT && isRight) {
                                multiTapResetJob?.cancel()
                                pendingSingleTapJob?.cancel()
                                multiTapSeconds += stepSec
                                AppHaptics.tick()
                                vlcEngine.seekByDelta(stepMs)
                                gestureHudText = "+${multiTapSeconds}s"
                                multiTapResetJob = coroutineScope.launch {
                                    delay(650L)
                                    multiTapSide = FastSeekSide.NONE
                                    multiTapSeconds = 0
                                }
                                lastTapTimestamp = now
                                lastTapPosition = downPos
                                return@awaitEachGesture
                            }
                        }

                        // If user tapped a different area during an active multi-tap session, reset it
                        if (multiTapSide != FastSeekSide.NONE) {
                            multiTapResetJob?.cancel()
                            multiTapSide = FastSeekSide.NONE
                            multiTapSeconds = 0
                        }

                        // 2. Check if this is a double-tap
                        val timeSinceLast = now - lastTapTimestamp
                        val isSameArea = when {
                            isLeft && lastTapPosition.x < screenWidth * 0.35f -> true
                            isRight && lastTapPosition.x > screenWidth * 0.65f -> true
                            !isLeft && !isRight && lastTapPosition.x in (screenWidth * 0.35f)..(screenWidth * 0.65f) &&
                                (downPos - lastTapPosition).getDistance() < 120.dp.toPx() -> true
                            else -> false
                        }
                        val isDoubleTap = timeSinceLast < 320L && isSameArea

                        if (isDoubleTap) {
                            pendingSingleTapJob?.cancel()
                            lastTapTimestamp = 0L

                            if (!isControlsLocked) {
                                when {
                                    isLeft -> {
                                        multiTapSide = FastSeekSide.LEFT
                                        multiTapSeconds = stepSec
                                        AppHaptics.tick()
                                        vlcEngine.seekByDelta(-stepMs)
                                        gestureHudText = "-${stepSec}s"
                                        multiTapResetJob = coroutineScope.launch {
                                            delay(650L)
                                            multiTapSide = FastSeekSide.NONE
                                            multiTapSeconds = 0
                                        }
                                    }
                                    isRight -> {
                                        multiTapSide = FastSeekSide.RIGHT
                                        multiTapSeconds = stepSec
                                        AppHaptics.tick()
                                        vlcEngine.seekByDelta(stepMs)
                                        gestureHudText = "+${stepSec}s"
                                        multiTapResetJob = coroutineScope.launch {
                                            delay(650L)
                                            multiTapSide = FastSeekSide.NONE
                                            multiTapSeconds = 0
                                        }
                                    }
                                    zoomScale > 1.05f -> {
                                        coroutineScope.launch {
                                            AppHaptics.click()
                                            gestureHudText = "Original"
                                            panOffset = Offset.Zero
                                            Animatable(zoomScale).animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) {
                                                zoomScale = value
                                            }
                                        }
                                    }
                                    else -> {
                                        AppHaptics.click()
                                        vlcEngine.togglePlayPause()
                                    }
                                }
                            }
                        } else {
                            // First tap!
                            lastTapTimestamp = now
                            lastTapPosition = downPos
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = coroutineScope.launch {
                                delay(280L)
                                if (trackSheet != TrackSheet.NONE) {
                                    trackSheet = TrackSheet.NONE
                                } else {
                                    showControls = !showControls
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(isControlsLocked, trackSheet) {
                if (isControlsLocked || trackSheet != TrackSheet.NONE) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
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
                                val newZoom = (zoomScale * zoomChange).coerceIn(0.7f, 4.0f)
                                zoomScale = newZoom
                                panOffset = if (newZoom > 1f) panOffset + panChange else Offset.Zero
                                gestureHudText = String.format(Locale.US, "Zoom %.2fx", newZoom)
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
                                    } else if (showControls &&
                                        (startY < topControlsHeight || startY > screenH - bottomControlsHeight)
                                    ) {
                                        // The two scrim bands. The top one is where the status bar is pulled
                                        // down from, and answering it with brightness — or with a dismissal
                                        // from the middle — stole that gesture every time the chrome happened
                                        // to be up. The bottom band is the scrubber's neighbourhood.
                                        ActiveGestureMode.CHROME
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
                    if (mode == ActiveGestureMode.PINCH_ZOOM) {
                        val currentZoom = zoomScale
                        val fillScale = if (fitW > 0 && fitH > 0) {
                            max(containerW / fitW.toFloat(), containerH / fitH.toFloat())
                        } else 1f

                        val targetZoom: Float
                        val hudLabel: String

                        if (scaleMode == VideoScaleMode.CROP) {
                            // In CROP mode, base framing already fills container width.
                            // A pinch outward snaps to 1.34x to eliminate 2.35:1 letterbox bars.
                            val cinemaTarget = 1.34f
                            when {
                                currentZoom < 1.15f -> {
                                    targetZoom = 1.0f
                                    hudLabel = "Crop to fit"
                                }
                                currentZoom in 1.15f..1.60f -> {
                                    targetZoom = cinemaTarget
                                    hudLabel = "Cinema fill (2.35:1)"
                                }
                                else -> {
                                    targetZoom = currentZoom
                                    hudLabel = String.format(Locale.US, "Zoom %.1fx", currentZoom)
                                }
                            }
                        } else {
                            // In FIT mode:
                            // Snap 1: 1.0f (Original)
                            // Snap 2: fillScale (Zoomed to fill container)
                            // Snap 3: cinemaTarget (Cinema fill for 2.35:1 letterbox bars)
                            val hasFill = fillScale > 1.06f
                            val cinemaTarget = if (videoRatio in 1.7f..1.85f) fillScale * 1.34f else fillScale * 1.18f

                            when {
                                currentZoom < 1.08f -> {
                                    targetZoom = 1.0f
                                    hudLabel = "Original"
                                }
                                hasFill && currentZoom in 1.08f..(fillScale + (cinemaTarget - fillScale) * 0.45f) -> {
                                    targetZoom = fillScale
                                    hudLabel = "Zoomed to fill"
                                }
                                currentZoom in (fillScale + (cinemaTarget - fillScale) * 0.45f)..(cinemaTarget * 1.25f) -> {
                                    targetZoom = cinemaTarget
                                    hudLabel = "Cinema fill (2.35:1)"
                                }
                                else -> {
                                    targetZoom = currentZoom
                                    hudLabel = String.format(Locale.US, "Zoom %.1fx", currentZoom)
                                }
                            }
                        }

                        if (abs(currentZoom - targetZoom) > 0.01f) {
                            coroutineScope.launch {
                                AppHaptics.click()
                                gestureHudText = hudLabel
                                if (targetZoom <= 1.02f || targetZoom == fillScale || targetZoom == 1.34f) {
                                    panOffset = Offset.Zero
                                }
                                Animatable(currentZoom).animateTo(
                                    targetValue = targetZoom,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) {
                                    zoomScale = value
                                }
                            }
                        } else {
                            gestureHudText = hudLabel
                        }
                    }
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

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(
                        if (scaleMode == VideoScaleMode.FIT) {
                            // Contain, not width-first. Pinning the width and deriving the height
                            // (`fillMaxWidth().aspectRatio`) is what the box used to do, and for any
                            // film squarer than the screen the derived height is taller than the
                            // screen — measured on a Note10+ with a 1.85 film: a 3040x1647 box on a
                            // 3040x1440 display. Nothing clamps it, so the picture overflows by 103px
                            // top and bottom, and the band it loses at the bottom is exactly where the
                            // film's subtitles live. Bare `aspectRatio` takes the largest box of the
                            // film's shape that fits *both* ways, which is the letterbox the mode's
                            // name already promises.
                            Modifier.aspectRatio(videoRatio)
                        } else {
                            Modifier.fillMaxSize()
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
                    .onSizeChanged { containerPx = it }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .aspectRatio(videoRatio)
                        .graphicsLayer {
                            if (fitW > 0 && fitH > 0) {
                                when (scaleMode) {
                                    VideoScaleMode.FIT -> {
                                        scaleX = 1f
                                        scaleY = 1f
                                    }
                                    VideoScaleMode.STRETCH -> {
                                        scaleX = containerW / fitW.toFloat()
                                        scaleY = containerH / fitH.toFloat()
                                    }
                                    VideoScaleMode.CROP -> {
                                        val cover = max(
                                            containerW / fitW.toFloat(),
                                            containerH / fitH.toFloat()
                                        )
                                        scaleX = cover
                                        scaleY = cover
                                    }
                                }
                            }
                        }
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
                }

                if (!vlcEngine.isFirstFrameRendered || isTransitionRunning) {
                    // What the film wears until its first frame exists. This used to be a flat grey
                    // panel with a drawn film strip on it, which reads as a broken player rather than a
                    // waiting one — and the wide shot that says what the film is has been on the device
                    // since the shelf first resolved it.
                    if (loadingArt != null) {
                        Image(
                            bitmap = loadingArt.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        MediaThumbnailImage(
                            uri = video.uri,
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = seekStageText,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = sizeToastVisible && !isInPipMode,
            enter = fadeIn(tween(450)),
            exit = fadeOut(tween(370)),
            modifier = Modifier.align(BiasAlignment(0f, -0.48f))
        ) {
            // The reference's stadium announcement, in the same solid scrim the seek and gesture
            // HUDs wear — a blur over the letterbox band would only ever render black, since there
            // is no picture behind the pill to frost. Opacity only: it neither slides nor scales.
            // The label outlives `visible`, or the exit fade would shrink to an empty circle while
            // the text disappears first.
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.76f)
            ) {
                Text(
                    text = sizeToastLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
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
                        color = if (isBoostGauge) OneUiAccent else Color.White
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
                                    .background(if (isBoostGauge) OneUiAccent else Color.White)
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
                        tint = OneUiAccent,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Every touch inside the chrome restarts the hide countdown, which is why the
                    // scrubber, the buttons and the tools row never needed to report it themselves.
                    // Nothing is consumed: the film's own tap and swipe handlers sit under this box.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            controlsTouchStamp++
                            isTouchingControls = true
                            var stillHeld = true
                            while (stillHeld) {
                                stillHeld = awaitPointerEvent().changes.any { it.pressed }
                            }
                            isTouchingControls = false
                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onSizeChanged { topControlsHeight = it.height }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                        .padding(
                            start = controlsLeft,
                            end = controlsRight,
                            top = statusBarTop + 12.dp,
                            bottom = 36.dp
                        )
                ) {
                    IconButton(
                        onClick = {
                            android.util.Log.i("ArcPlayer", "player closed: back arrow")
                            if (!prefsStore.backgroundAudioEnabled) vlcEngine.pause()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close player",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isFav = prefsStore.favoriteIds.contains(video.id)
                        IconButton(onClick = {
                            AppHaptics.favorite(!isFav)
                            prefsStore.toggleFavorite(video.id)
                        }) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                                tint = if (isFav) OneUiAccent else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { isControlsLocked = true }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock controls",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isWaitingForPicture,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = centerControlsOffset)
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerControlButton(
                            contentDescription = "Previous episode or video",
                            size = 52.dp,
                            enabled = hasPrevious,
                            onClick = {
                                AppHaptics.click()
                                previousVideo?.let(onSelectAdjacentVideo)
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Previous,
                                contentDescription = null,
                                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        PlayerControlButton(
                            contentDescription = if (vlcEngine.isPlaying) "Pause" else "Play",
                            size = 64.dp,
                            onClick = {
                                AppHaptics.click()
                                vlcEngine.togglePlayPause()
                            }
                        ) {
                            Icon(
                                imageVector = if (vlcEngine.isPlaying) HugeIcons.Pause else HugeIcons.Play,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        PlayerControlButton(
                            contentDescription = "Next episode or video",
                            size = 52.dp,
                            enabled = hasNext,
                            onClick = {
                                AppHaptics.click()
                                nextVideo?.let(onSelectAdjacentVideo)
                            }
                        ) {
                            Icon(
                                imageVector = HugeIcons.Next,
                                contentDescription = null,
                                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bottomControlsHeight = it.height }
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                            )
                        )
                        .padding(
                            start = controlsLeft,
                            end = controlsRight,
                            top = 36.dp,
                            bottom = navBarBottom + 12.dp
                        )
                ) {
                    val seekbarInteractionSource = remember { MutableInteractionSource() }
                    val isSeekbarPressed by seekbarInteractionSource.collectIsPressedAsState()
                    val isSeekbarDragged by seekbarInteractionSource.collectIsDraggedAsState()
                    val isScrubbing = isSeekbarPressed || isSeekbarDragged || scrubFraction != null

                    val titleAlpha by animateFloatAsState(
                        targetValue = if (isScrubbing) 0f else 1f,
                        animationSpec = tween(140),
                        label = "playerTitleAlpha"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(
                                if (isLandscape && (nextUpEarly || nextUpSeconds > 0)) 0.48f else 1f
                            )
                            .graphicsLayer { alpha = titleAlpha }
                    ) {
                        Text(
                            text = playerTitle,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!playerSubtitle.isNullOrBlank()) {
                            Text(
                                text = playerSubtitle,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val animatedTrackStroke by animateDpAsState(
                        targetValue = if (isScrubbing) 8.5.dp else 4.5.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "seekbarStroke"
                    )

                    val animatedThumbRadius by animateDpAsState(
                        targetValue = if (isScrubbing) 8.5.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "seekbarThumb"
                    )

                    val animatedHaloRadius by animateDpAsState(
                        targetValue = if (isScrubbing) 15.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "seekbarHalo"
                    )

                    val localMediaUri = remember(openedMedia, cloudSource) {
                        if (cloudSource == null && openedMedia?.uri != null && openedMedia?.uri != Uri.EMPTY) {
                            openedMedia?.uri
                        } else null
                    }
                    val previewSession = remember(localMediaUri) {
                        localMediaUri?.let { SeekPreviewSession(context.applicationContext, it) }
                    }
                    DisposableEffect(previewSession) {
                        onDispose {
                            previewSession?.close()
                        }
                    }

                    LaunchedEffect(previewSession) {
                        previewSession?.warmUp(vlcEngine.currentPositionMs)
                    }

                    val currentScrubFraction = scrubFraction
                        ?: (vlcEngine.currentPositionMs.toFloat() / vlcEngine.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    val scrubTargetMs = (currentScrubFraction * vlcEngine.durationMs).toLong().coerceIn(0L, vlcEngine.durationMs)
                    val bucketMs = remember(scrubTargetMs) { (scrubTargetMs / 2500L) * 2500L }

                    var previewBitmap by remember(localMediaUri) { mutableStateOf<Bitmap?>(null) }

                    LaunchedEffect(bucketMs, localMediaUri, isScrubbing, previewSession) {
                        if (!isScrubbing || localMediaUri == null || previewSession == null) {
                            if (!isScrubbing) {
                                delay(220L)
                                previewBitmap = null
                            }
                            return@LaunchedEffect
                        }
                        val cached = SeekPreviewCache.get(localMediaUri, bucketMs)
                        if (cached != null) {
                            previewBitmap = cached
                            previewSession.prefetchNeighbors(bucketMs, vlcEngine.durationMs)
                            return@LaunchedEffect
                        }
                        val frame = previewSession.getFrame(bucketMs)
                        if (frame != null) {
                            previewBitmap = frame
                            previewSession.prefetchNeighbors(bucketMs, vlcEngine.durationMs)
                        }
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val totalW = maxWidth
                        val previewW = 152.dp
                        val previewH = if (videoRatio > 0.5f) {
                            (previewW / videoRatio.coerceIn(1.2f, 2.4f))
                        } else 86.dp

                        val previewAlpha by animateFloatAsState(
                            targetValue = if (isScrubbing) 1f else 0f,
                            animationSpec = tween(140),
                            label = "previewAlpha"
                        )
                        val previewScale by animateFloatAsState(
                            targetValue = if (isScrubbing) 1f else 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "previewScale"
                        )

                        if (previewAlpha > 0.01f) {
                            val bmp = previewBitmap
                            val showThumbnail = localMediaUri != null && bmp != null
                            val bubbleW = if (showThumbnail) previewW else 84.dp
                            val bubbleH = if (showThumbnail) previewH else 34.dp

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset {
                                        val totalPx = totalW.roundToPx()
                                        val bubbleWPx = bubbleW.roundToPx()
                                        val bubbleHPx = bubbleH.roundToPx()
                                        val thumbCenterPx = (totalPx * currentScrubFraction).toInt()
                                        val leftPx = (thumbCenterPx - bubbleWPx / 2).coerceIn(0, (totalPx - bubbleWPx).coerceAtLeast(0))
                                        val topPx = -bubbleHPx - 14.dp.roundToPx()
                                        IntOffset(leftPx, topPx)
                                    }
                                    .graphicsLayer {
                                        alpha = previewAlpha
                                        scaleX = previewScale
                                        scaleY = previewScale
                                        transformOrigin = TransformOrigin(0.5f, 1f)
                                    }
                            ) {
                                if (showThumbnail) {
                                    val nonNullBmp = bmp ?: return@Box
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF141318),
                                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                                        shadowElevation = 12.dp,
                                        modifier = Modifier
                                            .width(previewW)
                                            .height(previewH)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Image(
                                                bitmap = nonNullBmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(28.dp)
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.BottomCenter
                                            ) {
                                                Text(
                                                    text = formatDuration(scrubTargetMs),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF141318).copy(alpha = 0.92f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                        shadowElevation = 8.dp
                                    ) {
                                        Text(
                                            text = formatDuration(scrubTargetMs),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Slider(
                            value = scrubFraction
                                ?: (vlcEngine.currentPositionMs.toFloat() / vlcEngine.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                scrubFraction = fraction
                                // A tick every 3.5% of the run. Denser than that and a long drag buzzes
                                // instead of ratcheting, which is the one thing that tells a finger how far
                                // it has travelled without the viewer reading a number.
                                if (abs(fraction - lastScrubTick) >= 0.035f) {
                                    lastScrubTick = fraction
                                    AppHaptics.tick()
                                }
                            },
                            onValueChangeFinished = {
                                val fraction = scrubFraction
                                scrubFraction = null
                                if (fraction != null) {
                                    AppHaptics.click()
                                    vlcEngine.seekTo((fraction * vlcEngine.durationMs).toLong())
                                }
                            },
                            interactionSource = seekbarInteractionSource,
                            thumb = { Spacer(modifier = Modifier.size(if (isScrubbing) 24.dp else 16.dp)) },
                            track = { slider ->
                                Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
                                    val trackWidth = size.width
                                    val centerY = center.y
                                    val playedEnd = Offset(trackWidth * slider.value, centerY)
                                    val strokePx = animatedTrackStroke.toPx()

                                    drawLine(
                                        color = Color.White.copy(alpha = 0.32f),
                                        start = Offset(0f, centerY),
                                        end = Offset(trackWidth, centerY),
                                        strokeWidth = strokePx,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(0f, centerY),
                                        end = playedEnd,
                                        strokeWidth = strokePx,
                                        cap = StrokeCap.Round
                                    )
                                    if (animatedThumbRadius > 0.5.dp) {
                                        val haloPx = animatedHaloRadius.toPx()
                                        if (haloPx > 0f) {
                                            drawCircle(
                                                color = Color.White.copy(alpha = 0.24f),
                                                radius = haloPx,
                                                center = playedEnd
                                            )
                                        }
                                        drawCircle(
                                            color = Color.White,
                                            radius = animatedThumbRadius.toPx(),
                                            center = playedEnd
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .semantics { contentDescription = "Playback position" }
                        )
                    }
                    val timeText = "${formatDuration(scrubFraction?.let { (it * vlcEngine.durationMs).toLong() } ?: vlcEngine.currentPositionMs)} / ${formatDuration(vlcEngine.durationMs)}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlayerControlButton(
                                contentDescription = "Audio tracks",
                                onClick = {
                                    trackSheet =
                                        if (trackSheet == TrackSheet.AUDIO) TrackSheet.NONE else TrackSheet.AUDIO
                                }
                            ) {
                                PlayerToolIcon(PlayerTool.TRACKS)
                            }
                            PlayerControlButton(
                                contentDescription = "Subtitles",
                                onClick = {
                                    trackSheet =
                                        if (trackSheet == TrackSheet.SUBTITLES) TrackSheet.NONE else TrackSheet.SUBTITLES
                                }
                            ) {
                                PlayerToolIcon(PlayerTool.SUBTITLES)
                            }
                            PlayerControlButton(
                                contentDescription = "Video size: ${scaleMode.label}",
                                onClick = {
                                    scaleMode = VideoScaleMode.entries[(scaleMode.ordinal + 1) % VideoScaleMode.entries.size]
                                    sizeToastLabel = scaleMode.label
                                    sizeToastVisible = true
                                    sizeToastStamp++
                                }
                            ) {
                                VideoSizeModeIcon(scaleMode)
                            }
                            AnimatedVisibility(
                                visible = expandedPlayerTools,
                                enter = expandHorizontally(tween(200)) + fadeIn(tween(160)),
                                exit = shrinkHorizontally(tween(200)) + fadeOut(tween(120))
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PlayerControlButton(
                                        contentDescription = "Rotate",
                                        onClick = {
                                            activity?.requestedOrientation = if (isLandscape) {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            }
                                        }
                                    ) {
                                        PlayerToolIcon(PlayerTool.ROTATE)
                                    }
                                    PlayerControlButton(
                                        contentDescription = "Picture in picture",
                                        onClick = {
                                            runCatching {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .build()
                                                activity?.enterPictureInPictureMode(params)
                                            }
                                        }
                                    ) {
                                        PlayerToolIcon(PlayerTool.PIP)
                                    }
                                }
                            }
                            PlayerControlButton(
                                contentDescription = if (expandedPlayerTools) "Collapse controls" else "Expand controls",
                                onClick = { expandedPlayerTools = !expandedPlayerTools }
                            ) {
                                Icon(
                                    imageVector = HugeIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            rotationZ = if (expandedPlayerTools) 190f else 0f
                                        }
                                )
                            }
                        }
                        if (!compactToolbar) {
                            Text(
                                text = timeText,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                    if (compactToolbar) {
                        Text(
                            text = timeText,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = (nextUpSeconds > 0 || nextUpEarly) && !isInPipMode && !isControlsLocked,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    start = controlsLeft,
                    end = controlsRight,
                    bottom = nextUpBottom
                )
                .then(if (isLandscape) Modifier.fillMaxWidth(0.48f) else Modifier)
        ) {
            nextVideo?.let { upcoming ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .onSizeChanged { nextUpCardHeight = it.height }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                // Counting down means the player reached the end and this will hand over
                                // by itself; without a number it is only being offered.
                                text = if (nextUpSeconds > 0) "Next up in ${nextUpSeconds}s" else "Next up",
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
                                nextUpEarly = false
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
                                nextUpEarly = false
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = trackSheet == TrackSheet.AUDIO && !isInPipMode,
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
            TrackSheetCard {
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
            }
        }

        AnimatedVisibility(
            visible = trackSheet == TrackSheet.SUBTITLES && !isInPipMode,
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
            TrackSheetCard {
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

                // Unlike sync, these two cannot be answered by a running player at all: libVLC speaks
                // them when it builds an instance, so each tap builds a new one around the same second of
                // the same film. The brief reload is the cost of the change, and the reason the steps are
                // small.
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            vlcEngine.subtitleLiftPercent == 0 -> "Subtitle Position: Default"
                            vlcEngine.subtitleLiftPercent > 0 -> "Subtitle Position: +${vlcEngine.subtitleLiftPercent}%"
                            else -> "Subtitle Position: ${vlcEngine.subtitleLiftPercent}%"
                        },
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayerChipButton(
                            text = "-4%",
                            onClick = { vlcEngine.adjustSubtitleLift(-SUBTITLE_LIFT_STEP_PERCENT) }
                        )
                        PlayerChipButton(
                            text = "Reset",
                            onClick = { vlcEngine.resetSubtitleLift() }
                        )
                        PlayerChipButton(
                            text = "+4%",
                            onClick = { vlcEngine.adjustSubtitleLift(SUBTITLE_LIFT_STEP_PERCENT) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitle Size: ${vlcEngine.subtitleSizePercent}%",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayerChipButton(
                            text = "-20%",
                            onClick = { vlcEngine.adjustSubtitleSize(-SUBTITLE_SIZE_STEP_PERCENT) }
                        )
                        PlayerChipButton(
                            text = "Reset",
                            onClick = { vlcEngine.resetSubtitleSize() }
                        )
                        PlayerChipButton(
                            text = "+20%",
                            onClick = { vlcEngine.adjustSubtitleSize(SUBTITLE_SIZE_STEP_PERCENT) }
                        )
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
private fun PlayerControlButton(
    contentDescription: String,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(size)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * The size button draws the mode it is in, not a generic resize glyph: a pair of arrows driving
 * inward along the diagonal for fit, the frame pulled open along that diagonal for stretch, a
 * frame gapped at the corner the arrow escapes through for crop. It swaps on the same frame as
 * the picture cut, so the button and the picture always agree about what the viewer is looking at.
 */
@Composable
private fun VideoSizeModeIcon(mode: VideoScaleMode) {
    val glyph = when (mode) {
        VideoScaleMode.FIT -> HugeIcons.Fit
        VideoScaleMode.STRETCH -> HugeIcons.Stretch
        VideoScaleMode.CROP -> HugeIcons.Crop
    }
    Icon(
        imageVector = glyph,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(24.dp)
    )
}

/** Which track sheet is standing, if any: the two buttons can never both be open. */
private enum class TrackSheet { NONE, AUDIO, SUBTITLES }

private enum class PlayerTool { TRACKS, SUBTITLES, ROTATE, PIP }

@Composable
private fun PlayerToolIcon(tool: PlayerTool) {
    when (tool) {
        PlayerTool.TRACKS -> ToolGlyph(HugeIcons.AudioTracks)
        PlayerTool.SUBTITLES -> ToolGlyph(HugeIcons.Subtitles)
        PlayerTool.ROTATE -> ToolGlyph(HugeIcons.Rotate)
        PlayerTool.PIP -> ToolGlyph(HugeIcons.PictureInPicture)
    }
}

@Composable
private fun ToolGlyph(glyph: ImageVector) {
    Icon(
        imageVector = glyph,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(24.dp)
    )
}

/**
 * The one card shape both track sheets wear. Splitting audio from captions means two overlays that
 * have to stay pixel-identical apart from what is inside them, including while one fades out and the
 * other fades in over the same spot.
 */
@Composable
private fun TrackSheetCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF21C1C20),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            content = content
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
        modifier = modifier
            .background(SlotWaitingTint)
            .jellySkeletonSweep()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val bottomGradient = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                    startY = h * 0.55f,
                    endY = h
                )
                onDrawBehind {
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
                containerColor = OneUiSelectedFill,
                contentColor = OneUiOnSelectedFill
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
            text = "No films on this phone",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = OneUiTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Movies and series kept in your own folders appear here once the collection recognises them.",
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
    // Fixed width, not fillMaxWidth: with the labels gone a full-width bar reads as a
    // long thin stadium. 63.dp cells around 24.dp icons is what the capsule wants.
    val rowInteractionSource = remember { MutableInteractionSource() }
    val tabSources = remember(BottomNavTab.entries) {
        BottomNavTab.entries.associateWith { MutableInteractionSource() }
    }
    val rowPressed by rowInteractionSource.collectIsPressedAsState()
    val tabsPressed = BottomNavTab.entries.map { tabSources.getValue(it).collectIsPressedAsState() }

    // Arc Music squashes the whole capsule under the finger, not only the cell that was touched, so
    // every cell's pressed state is read up here. The empty click behind it is what makes a press on
    // the glass between two icons count as a press on the bar.
    val barScale by animateFloatAsState(
        targetValue = if (rowPressed || tabsPressed.any { it.value }) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navbar_jelly_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .width(264.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .jellyGlass(
                hazeState = hazeState,
                backdrop = backdrop,
                tintTransparency = 0.4f,
                noiseFactor = 0.06f,
                shape = RoundedCornerShape(28.dp)
            )
            // The bar needs an edge or it disappears into the black page under it, but it needs the
            // outline role, not white: a 50% white ring over black reads as #808080 and looks like a
            // sticker outline rather than glass.
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
            }
            .clickable(
                interactionSource = rowInteractionSource,
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
            val tabCount = BottomNavTab.entries.size
            val selectedIndex = BottomNavTab.entries.indexOf(selectedTab).coerceIn(0, tabCount - 1)
            val cellWidth = maxWidth / tabCount
            val pillWidth = 44.dp
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
                    .offset { IntOffset(x = animatedOffset.roundToPx(), y = 0) }
                    .size(pillWidth)
                    .background(OneUiTextPrimary.copy(alpha = 0.09f), CircleShape)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val select = {
                        AppHaptics.click()
                        onTabSelected(tab)
                    }
                    BottomBarTabCell(
                        tab = tab,
                        isSelected = tab == selectedTab,
                        interactionSource = tabSources.getValue(tab),
                        modifier = Modifier.weight(1f),
                        onPressed = select,
                        onClick = select
                    )
                }
            }
        }
}

/**
 * One tab: the pill sliding underneath it is drawn by the bar, so this owns only the glyph.
 *
 * Arc Music's three-part highlight — outlined and filled cuts crossed over each other, the tint
 * easing to full strength, the icon standing up by five percent — is what makes the selection read as
 * the tab filling in rather than a disc arriving next to it.
 *
 * [onPressed] runs at touch-down and [onClick] is the fallback for callers that never emit a press,
 * which matters because a page cannot begin moving until the frame after the state write. A finger
 * rests on glass for a tenth of a second before lifting, and a transition that waits for the release
 * spends that time doing nothing.
 */
@Composable
private fun BottomBarTabCell(
    tab: BottomNavTab,
    isSelected: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onPressed: () -> Unit,
    onClick: () -> Unit
) {
    // A plain field, not state: nothing renders from it, it only stops the release that follows a
    // press from selecting the tab a second time.
    val firedOnPress = remember { booleanArrayOf(false) }
    val currentOnPressed by rememberUpdatedState(onPressed)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { firedOnPress[0] = true; currentOnPressed() }
                is PressInteraction.Cancel -> firedOnPress[0] = false
                else -> Unit
            }
        }
    }

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) OneUiTextPrimary else OneUiTextSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "nav_icon_tint_${tab.label}"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_icon_scale_${tab.label}"
    )
    val (outline, fill) = when (tab) {
        BottomNavTab.HOME -> HugeIcons.Home to HugeIcons.HomeFilled
        BottomNavTab.SEARCH -> HugeIcons.Search to HugeIcons.SearchFilled
        BottomNavTab.LIBRARY -> HugeIcons.Library to HugeIcons.LibraryFilled
        BottomNavTab.SETTINGS -> HugeIcons.Settings to HugeIcons.SettingsFilled
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                selected = isSelected
                // The visible label is gone, so the name lives in accessibility instead. It stays out
                // of the two Icon calls below, which are composed together through most of the
                // crossfade and would otherwise announce the tab twice.
                contentDescription = tab.label
            }
            .jellyClick(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                scaleDownTo = 0.88f,
                onClick = {
                    if (!firedOnPress[0]) onClick()
                    firedOnPress[0] = false
                }
            )
    ) {
        Crossfade(
            targetState = isSelected,
            animationSpec = tween(durationMillis = 200),
            label = "nav_icon_crossfade_${tab.label}"
        ) { showFill ->
            Icon(
                imageVector = if (showFill) fill else outline,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
    }
}
