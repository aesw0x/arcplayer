package com.aeswox.arcplayer.tvserver

import java.security.SecureRandom

/**
 * What a TV is told about the code it is holding, right now.
 *
 * `Approved` carries the key rather than a pointer to it, because the TV has no other way to get one —
 * that is the whole point of the handshake.
 */
sealed interface PairOutcome {
    /** Asked, not answered. The TV keeps polling and shows the code meanwhile. */
    data class Pending(val tvName: String) : PairOutcome
    data class Approved(val token: String, val device: String) : PairOutcome
    data object Denied : PairOutcome

    /** Past its 120 s, or so far gone that this code was never ours. */
    data object Expired : PairOutcome
}

/** A pairing request the phone has been asked to answer. [expiresAt] is a wall clock, for the UI. */
data class PairRequest(
    val code: String,
    val tvName: String,
    val tvModel: String,
    val expiresAt: Long,
)

/**
 * The short codes the phone issues when a TV asks to be let in.
 *
 * The TV is the untrusted party: a device on the Wi-Fi can ask, but only somebody holding the phone can
 * say yes. That makes a code a *label* rather than a secret — it exists so the two screens can be matched
 * by eye from the sofa, which is why it is six characters and 120 seconds rather than long and forever.
 *
 * Every method arrives on whichever thread got there first — NanoHTTPD's own pool, or the UI when a viewer
 * taps — so all of it happens under one lock. Nothing here reaches into Android, which is what lets the
 * whole policy be read in one file.
 */
class PairingCodes(
    private val now: () -> Long = System::currentTimeMillis,
    private val token: () -> String = { "" },
) {

    companion object {
        const val CODE_LENGTH = 6

        /** Six of these is 36^6 guesses, against a 120 s life and one approval each. */
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        /** How long a request can be answered — also how long its prompt is worth showing. */
        const val VALIDITY_MS = 120_000L

        /**
         * How long past the deadline an *answered* request keeps its answer.
         *
         * Long enough for a polling TV to read it: a `denied` that vanished at the tap would leave the TV
         * saying "the code expired" beside a phone that clearly said no.
         */
        const val ANSWER_GRACE_MS = 60_000L

        /** Waiting requests the phone holds at once; the oldest is dropped when a fourth arrives. */
        const val MAX_PENDING = 3
    }

    private enum class Answer { Pending, Approved, Denied }

    private class Record(
        val code: String,
        val tvName: String,
        val tvModel: String,
        val expiresAt: Long,
        var answer: Answer,
    ) {
        fun isPending(): Boolean = answer == Answer.Pending
        fun goneAt(cut: Long): Boolean =
            if (isPending()) cut >= expiresAt else cut >= expiresAt + ANSWER_GRACE_MS
    }

    private val random = SecureRandom()
    private val records = LinkedHashMap<String, Record>()

    /** The request a TV just made, already remembered and waiting for a tap. */
    @Synchronized
    fun request(tvName: String, tvModel: String): PairRequest {
        forgetStale()
        while (records.values.count { it.isPending() } >= MAX_PENDING) {
            val oldest = records.values.firstOrNull { it.isPending() } ?: break
            records.remove(oldest.code)
        }
        val code = newCode()
        val expiresAt = now() + VALIDITY_MS
        records[code] = Record(code, tvName, tvModel, expiresAt, Answer.Pending)
        return PairRequest(code, tvName, tvModel, expiresAt)
    }

    /** The answer for one code. A code never issued reads the same as one long gone. */
    @Synchronized
    fun outcome(code: String): PairOutcome {
        forgetStale()
        val record = records[normalize(code)] ?: return PairOutcome.Expired
        return when (record.answer) {
            Answer.Pending -> PairOutcome.Pending(record.tvName)
            Answer.Approved -> PairOutcome.Approved(token = token(), device = record.tvName)
            Answer.Denied -> PairOutcome.Denied
        }
    }

    /** The waiting requests, oldest first, for the screen the viewer answers from. */
    @Synchronized
    fun pending(): List<PairRequest> {
        forgetStale()
        return records.values.filter { it.isPending() }.map {
            PairRequest(it.code, it.tvName, it.tvModel, it.expiresAt)
        }
    }

    /** Nothing is waiting and nothing can poll: the door closed, so the answers went with it. */
    @Synchronized
    fun forgetAll() {
        records.clear()
    }

    /** True when this code was ours and still waiting — a late tap on an answered or dead code does nothing. */
    @Synchronized
    fun answer(code: String, approved: Boolean): Boolean {
        forgetStale()
        val record = records[normalize(code)] ?: return false
        if (!record.isPending()) return false
        record.answer = if (approved) Answer.Approved else Answer.Denied
        return true
    }

    /**
     * Uppercase and strip what a remote cannot type.
     *
     * The primary flow never needs this — the TV quotes the code straight back — but the fallback is a
     * human reading six characters off a television and entering them on a phone, and hyphens and spaces
     * are what people write down.
     */
    private fun normalize(code: String): String =
        code.filter { it.isLetterOrDigit() }.uppercase().take(CODE_LENGTH)

    private fun newCode(): String {
        var code = buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        // Two live codes the same would give one TV the other's answer.
        while (code in records) {
            code = buildString(CODE_LENGTH) {
                repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
            }
        }
        return code
    }

    private fun forgetStale() {
        val cut = now()
        records.values.filter { it.goneAt(cut) }.forEach { records.remove(it.code) }
    }
}
