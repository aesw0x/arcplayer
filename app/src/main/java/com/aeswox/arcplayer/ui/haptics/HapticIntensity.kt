package com.aeswox.arcplayer.ui.haptics

/** How hard the app's tactile feedback hits, as the viewer sets it. */
enum class HapticIntensity(val label: String, val level: Int, val scaleFactor: Float) {
    LIGHT("Light", 0, 0.45f),
    MEDIUM("Medium", 1, 0.80f),
    HEAVY("Heavy", 2, 1.0f);

    companion object {
        fun fromName(name: String?): HapticIntensity =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
    }
}
