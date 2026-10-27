package com.aeswox.arcplayer.ui.jelly

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aeswox.arcplayer.backdrop.backdrops.LayerBackdrop
import com.aeswox.arcplayer.backdrop.backdrops.layerBackdrop
import com.aeswox.arcplayer.backdrop.drawBackdrop
import com.aeswox.arcplayer.backdrop.effects.blur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

val JellyCornerRadius = 32.dp

/**
 * Marks the home content node as the frosted source: Haze on API 34+, the vendored
 * LayerBackdrop recorder on API 31-33.
 */
fun Modifier.jellySource(hazeState: HazeState?, backdrop: LayerBackdrop?): Modifier {
    var modifier = this
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        if (hazeState != null) modifier = modifier.haze(state = hazeState)
    } else {
        if (backdrop != null) modifier = modifier.layerBackdrop(backdrop)
    }
    return modifier
}

/**
 * Frosted Jelly glass recipe (Foundations: rendering recipe):
 * 24dp blur via Haze on API 34+, LayerBackdrop at 0.5 resolution scale on 31-33
 * (blur radius halved to match), tinted translucent fallback when neither is usable.
 *
 * INVARIANT on the 31-33 path: `backdrop` may only be passed by consumers that are
 * siblings drawn ABOVE the jellySource node. A consumer inside the recorded subtree
 * cycles the render tree and stack-overflows the RenderThread. Header pills inside
 * the scaffold pass backdrop = null and get the tinted fallback there.
 */
@Composable
fun Modifier.jellyGlass(
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    tintTransparency: Float = 0.4f,
    noiseFactor: Float = 0.06f,
    shape: Shape = RoundedCornerShape(JellyCornerRadius),
    forceFallback: Boolean = false
): Modifier {
    val bgLuminance = MaterialTheme.colorScheme.background.luminance()
    val isDark = bgLuminance < 0.05f
    val tintBase = if (isDark) Color.Black else Color.White
    val adjustedAlpha = if (isDark) (tintTransparency + 0.3f).coerceAtMost(0.85f) else tintTransparency

    return if (!forceFallback && android.os.Build.VERSION.SDK_INT >= 34) {
        if (hazeState != null) {
            this.hazeChild(
                state = hazeState,
                shape = shape,
                style = HazeStyle(
                    blurRadius = 24.dp,
                    tint = tintBase.copy(alpha = adjustedAlpha),
                    noiseFactor = noiseFactor
                )
            )
        } else {
            this.background(tintBase.copy(alpha = adjustedAlpha), shape)
        }
    } else if (backdrop != null && shape is CornerBasedShape) {
        val density = LocalDensity.current
        val blurPx = with(density) { 24.dp.toPx() } * 0.5f

        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(blurPx) },
            // The recipe is blur + tint + noise. `drawBackdrop` defaults to a white specular
            // rim and a drop shadow, and Haze draws neither, so leaving them on gives the
            // 31-33 build a bright outline the 34+ build does not have.
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(tintBase.copy(alpha = adjustedAlpha)) },
            backdropScale = 0.5f
        )
    } else {
        this.background(tintBase.copy(alpha = adjustedAlpha + 0.3f), shape)
    }
}
