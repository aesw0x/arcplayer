package com.aeswox.arcplayer.playback

import android.content.Context
import org.json.JSONObject

/**
 * `resume_positions` written one film at a time.
 *
 * The whole map is one preference string, and until a TV could post its own place into it this process
 * only ever rewrote the entire string from a map it had read once at launch. That is safe while one
 * writer exists and a lie as soon as two do: the living room's minute lands in the string, the phone's
 * next playback tick — a second and a half later — pours its older copy over it, and the film is back
 * where the viewer left it that morning. So nothing here writes from a remembered map. Every change reads
 * the string back off disk, alters the one entry it was asked to alter, and puts the whole thing back.
 *
 * The lock covers the gap between that read and that write. [com.aeswox.arcplayer.MainActivity] ticks on
 * the main thread and the LAN server answers on its own worker, and SharedPreferences is thread safe
 * while the JSON wrapped around it is not: two interleaved read-modify-writes are one lost film.
 */
object ResumeBlob {

    /** The single preference key the whole map lives under. */
    const val KEY = "resume_positions"

    private const val PREFS = "arc_player_prefs"

    private val lock = Any()

    /** Every film left part-way through on this device, keyed by its stable id. */
    fun read(context: Context): Map<Long, Long> =
        parse(prefs(context).getString(KEY, "{}"))

    /**
     * Where [id] was left off.
     *
     * A position that no longer belongs to a film — one in its closing minutes, or one on a clip too short
     * to be worth returning to — is absent rather than zero, so [record] taking it out and [put] putting
     * one in are the same operation as far as the caller is concerned.
     */
    fun positionOf(context: Context, id: Long): Long = read(context)[id] ?: 0L

    /** Leave a film somewhere in it, without touching where any other film was left. */
    fun put(context: Context, id: Long, positionMs: Long) = mutate(context) { it[id] = positionMs }

    /** Say this film is at its end, or was never really started, so nothing offers to return to it. */
    fun remove(context: Context, id: Long) = mutate(context) { it.remove(id) }

    /**
     * Take the place a TV reported and keep only the part worth keeping.
     *
     * The rule is the phone's own, not a copy of it: a viewer who watched to the credits on the sofa and
     * closes the app should come home to a ticked film rather than to "Resume at 1:58:04", and one who
     * posted a position from the first two seconds of a clip should not be offered a jump into nothing.
     * [ResumeRules.decide] already draws both lines for the local player.
     */
    fun record(context: Context, id: Long, positionMs: Long, durationMs: Long) =
        when (ResumeRules.decide(positionMs, durationMs)) {
            ResumeOutcome.STORE -> put(context, id, positionMs)
            ResumeOutcome.DROP -> remove(context, id)
            ResumeOutcome.KEEP -> Unit
        }

    /** Forget every place on this device, which is what clearing the history means. */
    fun clear(context: Context) {
        synchronized(lock) { prefs(context).edit().remove(KEY).apply() }
    }

    private fun mutate(context: Context, change: (MutableMap<Long, Long>) -> Unit) {
        synchronized(lock) {
            val current = read(context).toMutableMap()
            change(current)
            write(context, current)
        }
    }

    private fun write(context: Context, positions: Map<Long, Long>) {
        val obj = JSONObject()
        positions.forEach { (id, positionMs) -> obj.put(id.toString(), positionMs) }
        prefs(context).edit().putString(KEY, obj.toString()).apply()
    }

    private fun parse(text: String?): Map<Long, Long> {
        val json = runCatching { JSONObject(text ?: "{}") }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val positionMs = json.optLong(key, 0L)
            if (positionMs > 0L) id to positionMs else null
        }.toMap()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
