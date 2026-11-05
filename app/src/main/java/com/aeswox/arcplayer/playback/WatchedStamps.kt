package com.aeswox.arcplayer.playback

import android.content.Context
import org.json.JSONObject

/**
 * `cloud_watched_at`: when each file was last reached for, keyed by its stable id.
 *
 * Two writers now — the phone's own player and a television's progress posts — so nothing here writes the
 * whole map from a remembered copy. The player used to do exactly that, and a living-room stamp would
 * have survived only until the phone's next playback poured its older map over it. Every change reads the
 * string back off disk, alters the one entry it was asked to alter, and puts the whole thing back, for the
 * same reason [ResumeBlob] does: SharedPreferences is thread safe while the JSON wrapped around it is not.
 */
object WatchedStamps {

    /** The single preference key the whole map lives under. */
    const val KEY = "cloud_watched_at"

    private const val PREFS = "arc_player_prefs"

    private val lock = Any()

    /** Every file ever opened on this device or its television, newest stamp per id. */
    fun read(context: Context): Map<Long, Long> {
        val json = runCatching {
            JSONObject(prefs(context).getString(KEY, "{}") ?: "{}")
        }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val at = json.optLong(key, 0L)
            if (at > 0L) id to at else null
        }.toMap()
    }

    /** Say this file is the one most recently reached for, without touching any other file's stamp. */
    fun stamp(context: Context, id: Long, at: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val current = read(context).toMutableMap()
            current[id] = at
            val obj = JSONObject()
            current.forEach { (key, value) -> obj.put(key.toString(), value) }
            prefs(context).edit().putString(KEY, obj.toString()).apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
