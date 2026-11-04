package com.aeswox.arcplayer.tvserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's half of pairing: the codes a TV quotes back, and the prompt a viewer answers.
 *
 * One process-wide holder, because two things talk to it that must agree — the server's request thread
 * (a TV asks) and whoever is holding the phone (a notification, or the Settings card, answers). The door
 * is only ever open while [TvServerService] runs, so [open] and [close] bracket its lifetime, and nothing
 * waiting outlives a close.
 */
object TvPairing {

    internal const val ACTION_ANSWER = "com.aeswox.arcplayer.tvserver.PAIR_ANSWER"
    internal const val EXTRA_CODE = "code"
    internal const val EXTRA_APPROVED = "approved"

    private const val TAG = "ArcTV-Pairing"
    private const val CHANNEL_ID = "arctv_pairing"
    private const val FIRST_SLOT_ID = 4720
    private const val SLOT_COUNT = 3
    private const val NAME_LIMIT = 40

    /** The requests waiting for a tap, oldest first — what the Settings card renders. */
    private val waiting = MutableStateFlow(emptyList<PairRequest>())
    val pending: StateFlow<List<PairRequest>> = waiting.asStateFlow()

    private val codes = PairingCodes(token = { currentKey })
    private var currentKey: String = ""
    private var app: Context? = null

    /** Code → notification slot. At most three codes wait, so three slots is the exact size needed. */
    private val slots = LinkedHashMap<String, Int>()

    /** Called when the door binds. [token] is the key an approved TV is handed. */
    fun open(context: Context, token: String) {
        app = context.applicationContext
        currentKey = token
        ensureChannel(context.applicationContext)
    }

    /** Called when the door closes: nobody can poll, so nothing is worth remembering. */
    fun close() {
        app?.let { context ->
            manager(context)?.let { notificationManager ->
                slots.values.forEach { notificationManager.cancel(FIRST_SLOT_ID + it) }
            }
        }
        app = null
        currentKey = ""
        slots.clear()
        codes.forgetAll()
        waiting.value = emptyList()
    }

    /** A TV on the LAN wants in. Remembers the request, shows the code, and asks the viewer. */
    fun request(tvName: String, tvModel: String): PairRequest {
        val named = tvName.trim().takeIf { it.isNotEmpty() } ?: "TV"
        val request = codes.request(named.take(NAME_LIMIT), tvModel.trim().take(NAME_LIMIT))
        publish()
        askViewer(request)
        Log.i(TAG, "pairing request ${request.code} from ${request.tvName}")
        return request
    }

    /** What the TV polling with this code should be told. */
    fun outcome(code: String): PairOutcome = codes.outcome(code)

    /**
     * Re-reads the waiting list, and retires whatever has passed its deadline — row and prompt together.
     *
     * Nothing schedules this: a code expires on its own after 120 s and the TV simply stops being told
     * `pending`, but a screen left open would keep offering Allow beside a code that can no longer be
     * approved. So the Settings card asks once a second while it is showing something.
     */
    fun sweep() {
        publish()
    }

    /**
     * The viewer answered. True when this code was still waiting for an answer.
     *
     * A code that already expired still clears its own notification: the shade can hold a prompt far past
     * its 120 s, and a tap on a dead prompt should make it go away rather than do nothing.
     */
    fun answer(code: String, approved: Boolean): Boolean {
        val answered = codes.answer(code, approved)
        publish()
        Log.i(TAG, "pairing $code ${if (approved) "approved" else "denied"} (wasWaiting=$answered)")
        return answered
    }

    private fun publish() {
        val stillWaiting = codes.pending()
        // A prompt outliving its code is the one thing this list cannot show: the fourth request in a row
        // evicts the first, and a shade that still offers Allow for a code the phone has forgotten reads as
        // a phone that will not answer. Expiry does the same, and answers already fell out above.
        slots.keys.filterNot { code -> stillWaiting.any { it.code == code } }.forEach { dead ->
            slots.remove(dead)?.let { slot -> app?.let { manager(it)?.cancel(FIRST_SLOT_ID + slot) } }
        }
        waiting.value = stillWaiting
    }

    /**
     * The prompt itself.
     *
     * Public on the lock screen on purpose — the phone is in a pocket and the television is across the room,
     * so the code has to be readable without unlocking. With notification permission denied this silently
     * goes nowhere, which is why the same list is rendered in Settings.
     */
    private fun askViewer(request: PairRequest) {
        val context = app ?: return
        val manager = manager(context) ?: return
        val slot = slotFor(request.code)

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tapped = if (open == null) null else
            PendingIntent.getActivity(context, slot, open, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("TV wants to connect")
            .setContentText("${request.tvName} · code ${request.code}")
            .setContentIntent(tapped)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Leave the shade on its own at the deadline. [sweep] can only do it while a screen is open, and
            // the usual case is a phone in a pocket whose prompt nobody answered.
            .setTimeoutAfter(PairingCodes.VALIDITY_MS)
            .addAction(0, "Allow", pendingAnswer(context, slot * 2, request.code, approved = true))
            .addAction(0, "Deny", pendingAnswer(context, slot * 2 + 1, request.code, approved = false))
            .build()

        runCatching { manager.notify(FIRST_SLOT_ID + slot, notification) }
    }

    /**
     * A button that answers the code it is printed next to.
     *
     * Two things about this are Android traps rather than choices. `PendingIntent.getBroadcast` matches on
     * action and component and *ignores extras*, so every Allow in slot 0 is the same object and the second
     * one would otherwise keep the first code its creator was handed — a tap on a new TV's prompt answering
     * an old code. And the slot number alone is not a unique request code either, because slots are reused,
     * so the code rides in the data as well.
     */
    private fun pendingAnswer(context: Context, requestId: Int, code: String, approved: Boolean): PendingIntent {
        val intent = Intent(context, TvPairingReceiver::class.java)
            .setAction(ACTION_ANSWER)
            .setData(Uri.fromParts("arctv-pair", "$code.${if (approved) "allow" else "deny"}", null))
            .putExtra(EXTRA_CODE, code)
            .putExtra(EXTRA_APPROVED, approved)
        return PendingIntent.getBroadcast(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun slotFor(code: String): Int {
        slots[code]?.let { return it }
        val free = (0 until SLOT_COUNT).firstOrNull { taken -> slots.values.none { it == taken } } ?: 0
        slots[code] = free
        return free
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel(context: Context) {
        val manager = manager(context) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV pairing requests", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}

/**
 * Turns a notification button back into an answer.
 *
 * Not exported, and reachable only from this app's own PendingIntents — a pairing decision cannot be
 * pressed by some other install.
 */
class TvPairingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TvPairing.ACTION_ANSWER) return
        val code = intent.getStringExtra(TvPairing.EXTRA_CODE) ?: return
        TvPairing.answer(code, intent.getBooleanExtra(TvPairing.EXTRA_APPROVED, false))
    }
}
