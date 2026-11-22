package com.aeswox.arcplayer

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcplayer.playback.ResumeRules
import com.aeswox.arcplayer.ui.components.HugeIcons
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class TvTrackPanel { NONE, AUDIO, SUBTITLES }
private enum class TvPlaybackAction {
    PLAY, REWIND, FORWARD, TIMELINE, AUDIO, SUBTITLES, ASPECT, EXIT, RETRY, SKIP, NEXT, CANCEL_NEXT, VOLUME
}

private const val TV_SEEK_STEP_MS = 15_000L

// The host must leave Back handling to this layer while playback is mounted.
@Composable
internal fun TvPlayerControls(
    engine: ArcVlcPlayerEngine,
    videoId: Long,
    title: String,
    subtitle: String?,
    canControl: Boolean,
    waitingForPicture: Boolean,
    loadingMessage: String,
    message: String?,
    messageIsPersistent: Boolean,
    offersRetry: Boolean,
    onRetry: () -> Unit,
    aspectLabel: String,
    onCycleAspect: () -> Unit,
    bufferedToMs: Long,
    introEndMs: Long?,
    creditsStartMs: Long?,
    creditsEndMs: Long?,
    onSkipIntro: (() -> Unit)?,
    nextLabel: String?,
    onNext: (() -> Unit)?,
    nextOffered: Boolean,
    nextUpSeconds: Int,
    onCancelNext: () -> Unit,
    subtitleLiftStepPercent: Int,
    subtitleSizeStepPercent: Int,
    onDismiss: () -> Unit
) {
    var chromeVisible by remember(videoId) { mutableStateOf(true) }
    var panel by remember(videoId) { mutableStateOf(TvTrackPanel.NONE) }
    var activityStamp by remember(videoId) { mutableIntStateOf(0) }
    var usedRemote by remember(videoId) { mutableStateOf(false) }
    var lastAction by remember(videoId) { mutableStateOf(TvPlaybackAction.PLAY) }
    var hasPlayerFocus by remember { mutableStateOf(false) }
    // A center-down on the hidden receiver must not also click the newly focused button on key-up.
    var swallowCenterUp by remember { mutableStateOf(false) }
    val receiverFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val actionFocus = remember { TvPlaybackAction.entries.associateWith { FocusRequester() } }
    val canSeek = canControl && engine.durationMs > 1L
    val canToggle = canControl && !engine.isSeekWaiting
    val statusText = message ?: if (waitingForPicture) loadingMessage.ifBlank { "Loading playback…" } else null
    val availableActions = buildSet {
        add(TvPlaybackAction.EXIT)
        add(TvPlaybackAction.ASPECT)
        add(TvPlaybackAction.VOLUME)
        if (canToggle) add(TvPlaybackAction.PLAY)
        if (canSeek) {
            add(TvPlaybackAction.REWIND)
            add(TvPlaybackAction.FORWARD)
            add(TvPlaybackAction.TIMELINE)
        }
        if (canControl) {
            add(TvPlaybackAction.AUDIO)
            add(TvPlaybackAction.SUBTITLES)
        }
        if (offersRetry) add(TvPlaybackAction.RETRY)
        if (onSkipIntro != null && canControl) add(TvPlaybackAction.SKIP)
        if (onNext != null) add(TvPlaybackAction.NEXT)
        if (nextOffered) add(TvPlaybackAction.CANCEL_NEXT)
    }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var muted by remember(videoId) { mutableStateOf(false) }
    var savedVolume by remember(videoId) { mutableIntStateOf(0) }

    fun restoreFocus() {
        when {
            !chromeVisible -> receiverFocus.requestFocus()
            panel != TvTrackPanel.NONE -> panelFocus.requestFocus()
            else -> {
                val target = lastAction.takeIf { it in availableActions } ?: when {
                    offersRetry -> TvPlaybackAction.RETRY
                    canToggle -> TvPlaybackAction.PLAY
                    else -> TvPlaybackAction.EXIT
                }
                actionFocus.getValue(target).requestFocus()
            }
        }
    }

    fun touchControls() { activityStamp++ }
    fun revealControls() {
        touchControls()
        chromeVisible = true
    }
    fun seek(deltaMs: Long) {
        revealControls()
        if (canSeek) engine.seekByDelta(deltaMs)
    }
    fun togglePlayback() {
        revealControls()
        if (canToggle) engine.togglePlayPause()
    }
    fun toggleMute() {
        revealControls()
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (muted) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume.coerceIn(0, max), 0)
            muted = false
        } else {
            savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            muted = true
        }
    }
    fun actionModifier(action: TvPlaybackAction): Modifier = Modifier
        .focusRequester(actionFocus.getValue(action))
        .onFocusChanged {
            if (it.isFocused) {
                lastAction = action
                touchControls()
            }
        }

    // Mode transitions restore the exact initiating control, not an arbitrary first child.
    LaunchedEffect(chromeVisible, panel, videoId) {
        withFrameNanos { }
        restoreFocus()
    }
    // Recover disappearing controls without stealing focus from a surviving track.
    LaunchedEffect(hasPlayerFocus, availableActions) {
        withFrameNanos { }
        if (!hasPlayerFocus || (panel == TvTrackPanel.NONE && lastAction !in availableActions)) {
            restoreFocus()
        }
    }
    LaunchedEffect(canToggle) {
        // Close is the safe focus target before media exists; once the first picture arrives,
        // center should pause it, unless the viewer has already navigated somewhere deliberately.
        if (canToggle && !usedRemote && panel == TvTrackPanel.NONE) {
            lastAction = TvPlaybackAction.PLAY
            withFrameNanos { }
            if (chromeVisible && panel == TvTrackPanel.NONE && !usedRemote) {
                actionFocus.getValue(TvPlaybackAction.PLAY).requestFocus()
            }
        }
    }
    LaunchedEffect(engine.isPlaying) {
        if (!engine.isPlaying) chromeVisible = true
    }
    LaunchedEffect(offersRetry, messageIsPersistent) {
        if (offersRetry || messageIsPersistent) {
            if (offersRetry && !chromeVisible) lastAction = TvPlaybackAction.RETRY
            revealControls()
        }
    }
    LaunchedEffect(nextOffered) {
        if (nextOffered && !chromeVisible) {
            lastAction = TvPlaybackAction.NEXT
            revealControls()
        }
    }
    LaunchedEffect(chromeVisible, panel, activityStamp, engine.isPlaying,
        waitingForPicture, messageIsPersistent, offersRetry, nextUpSeconds > 0) {
        if (!chromeVisible || panel != TvTrackPanel.NONE || !engine.isPlaying ||
            waitingForPicture || messageIsPersistent || offersRetry || nextUpSeconds > 0
        ) return@LaunchedEffect
        delay(5_000L)
        chromeVisible = false
    }

    // Do not intercept KEYCODE_BACK as well: one physical press must unwind only one level.
    BackHandler {
        when {
            panel != TvTrackPanel.NONE -> { panel = TvTrackPanel.NONE; touchControls() }
            chromeVisible -> chromeVisible = false
            else -> onDismiss()
        }
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(receiverFocus)
            .onFocusChanged { hasPlayerFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val code = native.keyCode
                val down = native.action == KeyEvent.ACTION_DOWN
                val up = native.action == KeyEvent.ACTION_UP
                val center = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (center && swallowCenterUp) {
                    if (up) swallowCenterUp = false
                    return@onPreviewKeyEvent true
                }
                if (down) {
                    usedRemote = true
                    touchControls()
                }
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (down && native.repeatCount == 0) {
                            revealControls()
                            if (canToggle && (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                    (code == KeyEvent.KEYCODE_MEDIA_PLAY && !engine.isPlaying) ||
                                    (code == KeyEvent.KEYCODE_MEDIA_PAUSE && engine.isPlaying))) {
                                engine.togglePlayPause()
                            }
                        }
                        down || up
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (down) seek(if (code == KeyEvent.KEYCODE_MEDIA_REWIND) -TV_SEEK_STEP_MS else TV_SEEK_STEP_MS)
                        down || up
                    }
                    else -> if (!chromeVisible && (center || code in listOf(
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN))) {
                        if (down && native.repeatCount == 0) {
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> { lastAction = TvPlaybackAction.TIMELINE; seek(-TV_SEEK_STEP_MS) }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> { lastAction = TvPlaybackAction.TIMELINE; seek(TV_SEEK_STEP_MS) }
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> revealControls()
                                else -> { swallowCenterUp = true; lastAction = TvPlaybackAction.PLAY; togglePlayback() }
                            }
                        }
                        true
                    } else false
                }
            }
            .focusProperties { canFocus = !chromeVisible }
            .focusable()
    ) {
        // The reference's bare filename over the picture; the bar below owns everything else.
        if (chromeVisible && panel == TvTrackPanel.NONE) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 20.dp, start = 140.dp, end = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = Color.White.copy(alpha = .7f), fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TvPlaybackButton("Close", { touchControls(); onDismiss() },
                actionModifier(TvPlaybackAction.EXIT).align(Alignment.TopEnd).padding(20.dp))
        }

        // Delivery errors remain readable even after Back hides chrome; Up reveals Retry again.
        if (statusText != null && panel == TvTrackPanel.NONE) {
            Text(
                statusText + if (!chromeVisible && offersRetry) "\nPress Up to try again" else "",
                color = Color.White, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp).widthIn(max = 720.dp)
                    .tvGlass(CircleShape, .8f).padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        if (chromeVisible && panel == TvTrackPanel.NONE) {
            TvReveal(
                Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 24.dp).widthIn(max = 1100.dp).fillMaxWidth()
            ) {
                Column(
                    Modifier.tvGlass(alpha = .55f).padding(horizontal = 24.dp, vertical = 16.dp).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (offersRetry || onSkipIntro != null || onNext != null || nextOffered) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (offersRetry) {
                                TvPlaybackButton("Try again", { touchControls(); onRetry() }, actionModifier(TvPlaybackAction.RETRY))
                            }
                            if (onSkipIntro != null) {
                                TvPlaybackButton("Skip intro", { touchControls(); onSkipIntro() },
                                    actionModifier(TvPlaybackAction.SKIP), enabled = canControl)
                            }
                            if (onNext != null) {
                                TvPlaybackButton(if (nextUpSeconds > 0) "Play next · ${nextUpSeconds}s" else "Next episode",
                                    { touchControls(); onNext() }, actionModifier(TvPlaybackAction.NEXT))
                            }
                            if (nextOffered) {
                                TvPlaybackButton("Keep watching", { touchControls(); onCancelNext() },
                                    actionModifier(TvPlaybackAction.CANCEL_NEXT))
                                nextLabel?.let { Text(it, color = Color.White.copy(alpha = .7f), fontSize = 15.sp) }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvVolumeButton(muted, ::toggleMute, actionModifier(TvPlaybackAction.VOLUME))
                        TvSeekButton(false, { seek(-TV_SEEK_STEP_MS) }, actionModifier(TvPlaybackAction.REWIND), canSeek)
                        TvIconButton(
                            if (engine.isPlaying) HugeIcons.Pause else HugeIcons.Play,
                            if (engine.isPlaying) "Pause" else "Play",
                            ::togglePlayback, actionModifier(TvPlaybackAction.PLAY), enabled = canToggle, large = true,
                        )
                        TvSeekButton(true, { seek(TV_SEEK_STEP_MS) }, actionModifier(TvPlaybackAction.FORWARD), canSeek)
                        TvIconButton(HugeIcons.Subtitles, "Subtitles", { touchControls(); panel = TvTrackPanel.SUBTITLES },
                            actionModifier(TvPlaybackAction.SUBTITLES), enabled = canControl,
                            active = engine.selectedSubtitleTrackId != -1)
                        TvIconButton(HugeIcons.AudioTracks, "Audio tracks", { touchControls(); panel = TvTrackPanel.AUDIO },
                            actionModifier(TvPlaybackAction.AUDIO), enabled = canControl)
                        TvIconButton(HugeIcons.Stretch, "Aspect · $aspectLabel", { touchControls(); onCycleAspect() },
                            actionModifier(TvPlaybackAction.ASPECT))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(tvPlaybackTime(engine.currentPositionMs), color = Color.White, fontSize = 16.sp)
                        TvPlaybackTimeline(
                            positionMs = engine.currentPositionMs,
                            durationMs = engine.durationMs,
                            bufferedToMs = if (engine.pausedByViewer) bufferedToMs else 0L,
                            introEndMs = introEndMs,
                            creditsStartMs = creditsStartMs,
                            creditsEndMs = creditsEndMs,
                            enabled = canSeek,
                            onSeek = ::seek,
                            onClick = ::togglePlayback,
                            modifier = actionModifier(TvPlaybackAction.TIMELINE).weight(1f),
                        )
                        Text(
                            if (engine.durationMs > 1L) "−${tvPlaybackTime((engine.durationMs - engine.currentPositionMs).coerceAtLeast(0L))}" else "--:--",
                            color = Color.White.copy(alpha = .7f), fontSize = 16.sp,
                        )
                    }
                }
            }
        } else if (chromeVisible) {
            TvReveal(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(0.88f)
            ) {
                Column(
                    Modifier.tvGlass(alpha = .82f).padding(22.dp).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (panel == TvTrackPanel.AUDIO) "Audio tracks" else "Subtitles",
                            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        TvPlaybackButton("Done", { panel = TvTrackPanel.NONE; touchControls() },
                            Modifier.focusRequester(panelFocus))
                    }
                    if (statusText != null) {
                        Text(statusText, color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
                    }
                    if (offersRetry) {
                        TvPlaybackButton("Try again", {
                            touchControls()
                            panel = TvTrackPanel.NONE
                            onRetry()
                        })
                    }
                    // All rows participate in focus search, even below the viewport. Foundation's
                    // focusable click targets bring themselves into this bounded scrolling column.
                    key(panel) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (panel == TvTrackPanel.AUDIO) {
                                if (engine.audioTracks.isEmpty()) {
                                    Text("No alternate audio tracks", color = Color.White.copy(alpha = .7f), fontSize = 17.sp)
                                }
                                engine.audioTracks.forEach { track ->
                                    key(track.id) {
                                        TvPlaybackButton(track.name, { touchControls(); engine.selectAudioTrack(track.id) },
                                            Modifier.fillMaxWidth(), selected = track.id == engine.selectedAudioTrackId)
                                    }
                                }
                            } else {
                                TvPlaybackButton("Off", { touchControls(); engine.selectSubtitleTrack(-1) },
                                    Modifier.fillMaxWidth(), selected = engine.selectedSubtitleTrackId == -1)
                                if (engine.subtitleTracks.none { it.id != -1 }) {
                                    Text("No subtitle tracks available", color = Color.White.copy(alpha = .7f), fontSize = 17.sp)
                                }
                                engine.subtitleTracks.filter { it.id != -1 }.forEach { track ->
                                    key(track.id) {
                                        TvPlaybackButton(track.name, { touchControls(); engine.selectSubtitleTrack(track.id) },
                                            Modifier.fillMaxWidth(), selected = track.id == engine.selectedSubtitleTrackId)
                                    }
                                }
                                TvSubtitleAdjustment("Subtitle delay: ${engine.subtitleDelayMs} ms", "−100 ms", "+100 ms",
                                    { touchControls(); engine.adjustSubtitleDelay(-100L) },
                                    { touchControls(); engine.adjustSubtitleDelay(0L) },
                                    { touchControls(); engine.adjustSubtitleDelay(100L) })
                                TvSubtitleAdjustment("Subtitle lift: ${engine.subtitleLiftPercent}%",
                                    "−$subtitleLiftStepPercent%", "+$subtitleLiftStepPercent%",
                                    { touchControls(); engine.adjustSubtitleLift(-subtitleLiftStepPercent) },
                                    { touchControls(); engine.resetSubtitleLift() },
                                    { touchControls(); engine.adjustSubtitleLift(subtitleLiftStepPercent) })
                                TvSubtitleAdjustment("Subtitle size: ${engine.subtitleSizePercent}%",
                                    "−$subtitleSizeStepPercent%", "+$subtitleSizeStepPercent%",
                                    { touchControls(); engine.adjustSubtitleSize(-subtitleSizeStepPercent) },
                                    { touchControls(); engine.resetSubtitleSize() },
                                    { touchControls(); engine.adjustSubtitleSize(subtitleSizeStepPercent) })
                            }
                        }
                    }
                }
            }
        } else if (onSkipIntro != null || nextOffered) {
            Text(
                text = if (nextOffered) {
                    if (nextUpSeconds > 0) "Next in ${nextUpSeconds}s · Up for controls" else "Next episode available · Up for controls"
                } else "Skip intro available · Up for controls",
                color = Color.White, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
                    .tvGlass(CircleShape, .8f).padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun TvPlaybackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tv_playback_button",
    )
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                when {
                    focused && enabled -> Color.White
                    selected -> Color.White.copy(alpha = .18f)
                    else -> Color.White.copy(alpha = .07f)
                }
            )
            .border(2.dp, if (focused && enabled) Color.White else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            color = if (focused && enabled) Color.Black else Color.White.copy(alpha = if (enabled) 1f else .35f),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

@Composable
private fun TvIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tv_icon_button",
    )
    Box(
        modifier.size(if (large) 64.dp else 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                when {
                    focused && enabled -> Color.White
                    active -> Color.White.copy(alpha = .16f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused && enabled) Color.White else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null,
            tint = if (focused && enabled) Color.Black else Color.White.copy(alpha = if (enabled) 1f else .35f),
            modifier = Modifier.size(if (large) 30.dp else 24.dp))
    }
}

/** The reference's circular-arrow fifteen: a 300° arc with a head, mirrored for rewind. */
@Composable
private fun TvSeekButton(forward: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tv_seek_button",
    )
    val ink = if (focused && enabled) Color.Black else Color.White.copy(alpha = if (enabled) 1f else .35f)
    Box(
        modifier.size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused && enabled) Color.White else Color.Transparent)
            .border(2.dp, if (focused && enabled) Color.White else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (forward) "Forward fifteen seconds" else "Rewind fifteen seconds" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).graphicsLayer { if (!forward) scaleX = -1f }) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                val r = (size.minDimension - stroke) / 2f - 1.dp.toPx()
                val c = Offset(size.width / 2f, size.height / 2f)
                drawArc(ink, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                val end = Math.toRadians(240.0)
                val tip = Offset(c.x + r * cos(end).toFloat(), c.y + r * sin(end).toFloat())
                val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat())
                val head = 4.5.dp.toPx()
                fun rot(o: Offset, a: Float) = Offset(o.x * cos(a) - o.y * sin(a), o.x * sin(a) + o.y * cos(a))
                val back = tangent * -1f
                drawLine(ink, tip, tip + rot(back, 0.5f) * head, stroke, StrokeCap.Round)
                drawLine(ink, tip, tip + rot(back, -0.5f) * head, stroke, StrokeCap.Round)
            }
        }
        Text("15", color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvVolumeButton(muted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tv_volume_button",
    )
    val ink = if (focused) Color.Black else Color.White
    Box(
        modifier.size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Transparent)
            .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (muted) "Unmute" else "Mute" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            val body = Path().apply {
                moveTo(3.dp.toPx(), 9.dp.toPx())
                lineTo(7.dp.toPx(), 9.dp.toPx())
                lineTo(12.dp.toPx(), 4.5.dp.toPx())
                lineTo(12.dp.toPx(), 19.5.dp.toPx())
                lineTo(7.dp.toPx(), 15.dp.toPx())
                lineTo(3.dp.toPx(), 15.dp.toPx())
                close()
            }
            drawPath(body, ink)
            if (muted) {
                drawLine(ink, Offset(15.dp.toPx(), 9.dp.toPx()), Offset(21.dp.toPx(), 15.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(ink, Offset(21.dp.toPx(), 9.dp.toPx()), Offset(15.dp.toPx(), 15.dp.toPx()), stroke, StrokeCap.Round)
            } else {
                drawArc(ink, startAngle = -40f, sweepAngle = 80f, useCenter = false,
                    topLeft = Offset(10.dp.toPx(), 7.dp.toPx()), size = Size(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(ink, startAngle = -40f, sweepAngle = 80f, useCenter = false,
                    topLeft = Offset(13.dp.toPx(), 4.5.dp.toPx()), size = Size(15.dp.toPx(), 15.dp.toPx()),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun TvSubtitleAdjustment(
    label: String,
    minusLabel: String,
    plusLabel: String,
    onMinus: () -> Unit,
    onReset: () -> Unit,
    onPlus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(label, color = Color.White, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvPlaybackButton(minusLabel, onMinus)
            TvPlaybackButton("Reset", onReset)
            TvPlaybackButton(plusLabel, onPlus)
        }
    }
}

@Composable
private fun TvPlaybackTimeline(
    positionMs: Long,
    durationMs: Long,
    bufferedToMs: Long,
    introEndMs: Long?,
    creditsStartMs: Long?,
    creditsEndMs: Long?,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val knownLength = durationMs > 1L
    val fraction = (positionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(28.dp).onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val direction = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -TV_SEEK_STEP_MS
                    KeyEvent.KEYCODE_DPAD_RIGHT -> TV_SEEK_STEP_MS
                    else -> return@onPreviewKeyEvent false
                }
                if (enabled && native.action == KeyEvent.ACTION_DOWN) onSeek(direction)
                true
            }
            .clip(CircleShape)
            .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = "Playback position. Left or right seeks fifteen seconds"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val y = center.y
            val stroke = (if (focused) 6.dp else 4.dp).toPx()
            fun band(from: Float, to: Float, color: Color) {
                if (to > from) drawLine(color, Offset(size.width * from, y), Offset(size.width * to, y), stroke, StrokeCap.Round)
            }
            fun point(timeMs: Long) = (timeMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            band(0f, 1f, Color.White.copy(alpha = 0.25f))
            // The Hero already judged these marks. As on phone, don't place them against a placeholder
            // length; a catalogue runtime may justify an offer, but not a fabricated timeline position.
            if (durationMs >= ResumeRules.MIN_TRACKABLE_DURATION_MS) {
                introEndMs?.let { band(0f, point(it), TvColors.Accent) }
                if (creditsStartMs != null && creditsEndMs != null) {
                    val start = point(creditsStartMs)
                    band(start, maxOf(point(creditsEndMs), start + 0.015f).coerceAtMost(1f), TvColors.Accent)
                }
            }
            band(fraction, point(bufferedToMs), Color.White.copy(alpha = 0.6f))
            band(0f, fraction, Color.White)
            drawCircle(Color.White, (if (focused) 6.dp else 4.dp).toPx(), Offset(size.width * fraction, y))
        }
    }
}

private fun tvPlaybackTime(timeMs: Long): String {
    val seconds = timeMs.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    val tail = (seconds % 60L).toString().padStart(2, '0')
    return if (minutes >= 60L) "${minutes / 60L}:${(minutes % 60L).toString().padStart(2, '0')}:$tail"
        else "$minutes:$tail"
}
