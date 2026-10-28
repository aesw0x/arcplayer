package com.aeswox.arcplayer.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcplayer.CollectionPill
import com.aeswox.arcplayer.backdrop.backdrops.LayerBackdrop
import com.aeswox.arcplayer.ui.jelly.JellyCornerRadius
import com.aeswox.arcplayer.ui.jelly.jellyGlass
import dev.chrisbanes.haze.HazeState

/**
 * The card that says a newer build exists.
 *
 * It floats over the collection rather than sitting inside it, for the same reason the navigation capsule
 * does: this is a sibling of the recorded home node, so it can afford real frosted glass, while anything
 * inside the scrolling column would only be able to fake it. It carries four states — an offer, a
 * transfer, a hand-off to the installer, and a failure — and the card's height differs in each, which is
 * why the size animates rather than the content swapping underneath the viewer's thumb.
 */
@Composable
internal fun UpdateCard(
    visible: Boolean,
    release: Release,
    download: UpdateDownload,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) { it },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it },
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(JellyCornerRadius)
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .jellyGlass(hazeState, backdrop, tintTransparency = 0.5f)
                .border(BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)), shape)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "UPDATE",
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Arc Player ${release.version}",
                color = scheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (release.notes.isNotBlank()) {
                Text(
                    text = release.notes,
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (download) {
                is UpdateDownload.Idle -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CollectionPill("Later", Modifier.weight(1f), onClick = onDismiss)
                    CollectionPill("Update", Modifier.weight(1.4f), selected = true, onClick = onUpdate)
                }
                is UpdateDownload.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateProgress(download.fraction)
                    Text(
                        text = "Downloading… ${(download.fraction * 100).toInt()}%",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                is UpdateDownload.Ready -> CollectionPill(
                    "Install",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    selected = true,
                    onClick = onInstall
                )
                is UpdateDownload.Failed -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Couldn't download it.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    CollectionPill("Try again", selected = true, onClick = onUpdate)
                }
            }
        }
    }
}

/**
 * The transfer's fill, drawn the way the shelf draws a film's progress.
 *
 * A Material indicator brings its own track colour and corner radius, which is how one lone widget ends up
 * looking borrowed; this is the same 3dp line a Continue Watching card wears, so the update reads as part
 * of the app rather than as a system dialog that wandered in.
 */
@Composable
internal fun UpdateProgress(fraction: Float) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(onSurface.copy(alpha = 0.3f), CircleShape)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(onSurface, CircleShape)
        )
    }
}
