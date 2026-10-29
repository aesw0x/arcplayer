package com.aeswox.arcplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aeswox.arcplayer.ui.animations.jelly
import com.aeswox.arcplayer.ui.animations.jellyClick
import kotlinx.coroutines.launch

data class MorphingMenuItem(
    val text: String,
    val icon: ImageVector? = null,
    val dividerAfter: Boolean = false,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Frosted Jelly morphing overflow menu: the 3-dot button expands from its own
 * anchor into a rounded menu card using spring physics (ported from Arc Music).
 */
@Composable
fun MorphingMenu(
    items: List<MorphingMenuItem>,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 38.dp,
    menuWidth: Dp = 230.dp,
    contentDescription: String? = "More options",
    icon: ImageVector = Icons.Default.MoreVert,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    var isOpen by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    val animProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val dividerCount = items.count { it.dividerAfter }
    val menuHeight = remember(items.size, dividerCount) { (items.size * 52 + dividerCount * 17 + 16).dp }

    fun dismiss(onFinished: (() -> Unit)? = null) {
        onFinished?.invoke()
        if (isDismissing) return
        isDismissing = true
        coroutineScope.launch {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 1.0f, stiffness = Spring.StiffnessMediumLow)
            )
            isOpen = false
            isDismissing = false
        }
    }

    fun open() {
        if (isOpen || items.isEmpty()) return
        isOpen = true
        coroutineScope.launch {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Box(
        modifier = modifier.size(buttonSize),
        contentAlignment = Alignment.Center
    ) {
        val overflowSource = remember { MutableInteractionSource() }
        IconButton(
            interactionSource = overflowSource,
            onClick = { open() },
            modifier = Modifier
                .size(buttonSize)
                .jelly(overflowSource, scaleDownTo = 0.88f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }

        if (isOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 0),
                onDismissRequest = { dismiss() },
                properties = PopupProperties(focusable = true)
            ) {
                // Fixed outer frame prevents window re-measurement stutter; the extra
                // 16dp absorbs spring overshoot without clipping the card.
                Box(
                    modifier = Modifier
                        .size(menuWidth + 16.dp, menuHeight + 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            dismiss()
                        },
                    contentAlignment = Alignment.TopEnd
                ) {
                    val rawProgress = animProgress.value
                    val progress = rawProgress.coerceIn(0f, 1f)
                    val currentWidth = lerp(buttonSize, menuWidth, rawProgress)
                    val currentHeight = lerp(buttonSize, menuHeight, rawProgress)
                    val currentCorner = lerp(buttonSize / 2, 28.dp, progress)

                    // Opaque until the card shrinks past 50%, then dissolve with a Hermite S-curve.
                    val popupAlpha = if (progress >= 0.5f) {
                        1f
                    } else {
                        val t = (progress / 0.5f).coerceIn(0f, 1f)
                        t * t * (3f - 2f * t)
                    }

                    val currentElevation = lerp(0.dp, 12.dp, progress) * popupAlpha
                    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * popupAlpha)

                    Surface(
                        modifier = Modifier
                            .size(currentWidth, currentHeight)
                            .graphicsLayer { alpha = popupAlpha }
                            .shadow(elevation = currentElevation, shape = RoundedCornerShape(currentCorner))
                            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(currentCorner))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Consume clicks inside the card so it doesn't dismiss
                            },
                        shape = RoundedCornerShape(currentCorner),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val itemsAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            if (itemsAlpha > 0.01f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .graphicsLayer {
                                            alpha = itemsAlpha
                                            translationY = (1f - itemsAlpha) * 10.dp.toPx()
                                        },
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items.forEach { item ->
                                        MorphingMenuItemRow(
                                            item = item,
                                            onItemClick = {
                                                item.onClick()
                                                dismiss()
                                            }
                                        )
                                        if (item.dividerAfter) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
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
    }
}

@Composable
private fun MorphingMenuItemRow(
    item: MorphingMenuItem,
    onItemClick: () -> Unit
) {
    val textColor = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconTint =
        if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .jellyClick { onItemClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
