package com.aeswox.arcplayer.ui.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Frosted Jelly tactile feedback: the press a control already shows gets a matching knock, so a
 * surface that compresses under a finger also answers to it.
 *
 * Every pattern is built from hardware primitives and played only if the motor really has them,
 * because a phone without `PRIMITIVE_CLICK` support would otherwise buzz a generic waveform where a
 * crisp knock belongs. `minSdk` is 31, so the composition API needs no version ladder — only the
 * per-device capability check.
 */
object AppHaptics {

    @Volatile
    var isEnabled: Boolean = true

    @Volatile
    var intensity: HapticIntensity = HapticIntensity.MEDIUM

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        val manager = context.applicationContext
            .getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibrator = manager?.defaultVibrator
    }

    /** Crisp knock for controls, primary buttons and navigation. */
    fun click(scaleOverride: Float? = null) {
        if (!isEnabled) return
        knock(scaleOverride ?: intensity.scaleFactor)
    }

    /** Micro-tick for scrubbing and for stepping through a short list of choices. */
    fun tick() {
        if (!isEnabled) return
        val scale = intensity.scaleFactor
        vibrate(
            composition(Step(VibrationEffect.Composition.PRIMITIVE_TICK, scale * 0.7f))
                ?: VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        )
    }

    /** Heavy thud for the actions that cannot be undone. */
    fun destructive() {
        if (!isEnabled) return
        val scale = intensity.scaleFactor
        vibrate(
            composition(
                Step(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale * 0.6f),
                Step(VibrationEffect.Composition.PRIMITIVE_THUD, scale, delay = 20)
            )
                ?: composition(Step(VibrationEffect.Composition.PRIMITIVE_THUD, scale))
                ?: VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        )
    }

    /**
     * Favouriting rises and then pops; taking the favourite back is only a softer click. The two
     * directions have to feel different, or the viewer cannot tell which state they just landed in
     * without looking at the glyph.
     */
    fun favorite(isFavorited: Boolean) {
        if (!isEnabled) return
        if (!isFavorited) {
            knock(intensity.scaleFactor * 0.75f)
            return
        }
        val scale = intensity.scaleFactor
        vibrate(
            composition(
                Step(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, scale * 0.5f),
                Step(VibrationEffect.Composition.PRIMITIVE_CLICK, scale, delay = 15)
            )
                ?: VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        )
    }

    /** The lift of a long press, before the sheet it opens has arrived. */
    fun grab() {
        if (!isEnabled) return
        vibrate(
            composition(Step(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, intensity.scaleFactor))
                ?: VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        )
    }

    fun toggle(checked: Boolean) {
        if (checked) click() else tick()
    }

    /** Played by the Settings row so a change of intensity can be felt rather than guessed at. */
    fun preview(target: HapticIntensity) = knock(target.scaleFactor)

    fun previewDestructive(target: HapticIntensity) {
        val scale = target.scaleFactor
        vibrate(
            composition(
                Step(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale * 0.6f),
                Step(VibrationEffect.Composition.PRIMITIVE_THUD, scale, delay = 20)
            )
                ?: VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        )
    }

    private fun knock(scale: Float) {
        vibrate(
            composition(Step(VibrationEffect.Composition.PRIMITIVE_CLICK, scale))
                ?: VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        )
    }

    private data class Step(val primitive: Int, val scale: Float, val delay: Int = 0)

    /** Null when the motor cannot play one of [steps], which is the caller's cue to fall back. */
    private fun composition(vararg steps: Step): VibrationEffect? {
        val vib = vibrator ?: return null
        if (!vib.areAllPrimitivesSupported(*steps.map { it.primitive }.toIntArray())) return null
        var builder = VibrationEffect.startComposition()
        steps.forEach { builder = builder.addPrimitive(it.primitive, it.scale.coerceIn(0.1f, 1f), it.delay) }
        return builder.compose()
    }

    private fun vibrate(effect: VibrationEffect) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching { vib.vibrate(effect) }
    }
}
