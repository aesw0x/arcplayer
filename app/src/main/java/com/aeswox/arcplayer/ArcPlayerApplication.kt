package com.aeswox.arcplayer

import android.app.Application
import android.util.Log
import com.aeswox.arcplayer.ui.haptics.AppHaptics
import com.aeswox.arcplayer.tvserver.TvServerConfigStore
import com.aeswox.arcplayer.tvserver.TvServerService

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
        openTvDoorIfNeeded()
    }

    /**
     * Hands the LAN server to a service rather than running it from here.
     *
     * Off unless the viewer turned TV access on, so a phone that never asked for the door binds no port.
     * The start is guarded because this is also the entry point when the system restarts the process after
     * a kill, and a foreground service started from *that* is refused on Android 12+ — a refusal here has
     * to cost the TV its evening, not the app its launch.
     */
    private fun openTvDoorIfNeeded() {
        if (!TvServerConfigStore.load(this).enabled) return
        runCatching { TvServerService.start(this) }
            .onFailure { Log.w(TAG, "TV access was on, but the system refused the server", it) }
    }

    private companion object {
        private const val TAG = "ArcTV-Server"
    }
}
