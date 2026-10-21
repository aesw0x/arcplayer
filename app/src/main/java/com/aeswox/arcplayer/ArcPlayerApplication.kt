package com.aeswox.arcplayer

import android.app.Application
import com.aeswox.arcplayer.ui.haptics.AppHaptics

/**
 * Holds the one piece of process state that has to exist before any composition does.
 *
 * The haptics engine needs a vibrator handle, and acquiring it from inside a composable would mean
 * acquiring it again after every configuration change. The viewer's own haptics settings are pushed
 * into the engine by [ArcPreferencesStore], which stays the only reader of that file.
 */
class ArcPlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppHaptics.init(this)
    }
}
