package com.aeswox.arcplayer.ui.animations

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** Frosted Jelly Press: compression on touch, spring rebound on release. Replaces ripple. */
fun Modifier.jellyClick(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    scaleDownTo: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val actualIndication = indication ?: LocalIndication.current

    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDownTo else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "jelly_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = actualInteractionSource,
            indication = actualIndication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick
        )
}

/** Jelly Press variant for click + long-press surfaces (cards, list rows). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.jellyCombinedClickable(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    scaleDownTo: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val actualIndication = indication ?: LocalIndication.current

    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDownTo else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "jelly_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = actualInteractionSource,
            indication = actualIndication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            onClick = onClick
        )
}

/** Scale-only feedback for controls that own their click handling (e.g. IconButton). */
fun Modifier.jelly(
    interactionSource: MutableInteractionSource,
    scaleDownTo: Float = 0.95f,
    enabled: Boolean = true
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDownTo else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "jelly_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
