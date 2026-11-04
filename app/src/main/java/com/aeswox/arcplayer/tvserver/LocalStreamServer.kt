package com.aeswox.arcplayer.tvserver

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * The HTTP surface a TV talks to.
 *
 * Three rules shape this file:
 *
 * 1. **The token is a query parameter.** AVPlay's `open(url)` cannot send headers, so the media request
 *    has no other way to authenticate. The JSON routes take the header too, which keeps `curl` pleasant.
 * 2. **`Content-Length` is the size the film has, not the size of what has arrived.** Advertising less
 *    makes every seek a fresh 200 and breaks the player's idea of where it is.
 * 3. **Preflight answers before authentication.** A `POST` with a JSON body makes the TV's webview send an
 *    `OPTIONS` first; a 401 on that reads as an opaque network error rather than a rejected token.
 */
class LocalStreamServer(
    port: Int,
    private val token: String,
    private val deviceName: String,
    private val libraryItems: () -> List<TvShelfItem>,
    private val shelfCounts: () -> ShelfCounts,
    private val openStream: (String) -> ByteSource?,
    private val savedPositions: () -> Map<Long, Long>,
    private val savePosition: (id: String, positionMs: Long, durationMs: Long) -> Boolean,
    private val openCaption: (String) -> File?,
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ArcTV-Server"
        private const val MIME_JSON = "application/json; charset=utf-8"
        private const val MIME_TEXT = "text/plain; charset=utf-8"
        private const val VERSION = 1

        /**
         * How many films are pushed at once.
         *
         * Two viewers is the most this phone should serve, and one per film is the most that helps: a
         * second reader of the same file means two different offsets competing for the same bytes.
         */
        private const val MAX_STREAMS = 2
    }

    private val streams = StreamGate(MAX_STREAMS)

    override fun serve(session: IHTTPSession): Response {
        val response = try {
            route(session)
        } catch (failure: Throwable) {
            Log.w(TAG, "failed: ${session.method} ${session.uri}", failure)
            plain(Response.Status.INTERNAL_ERROR, "server error")
        }
        return withCors(response)
    }

    private fun route(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) return plain(Response.Status.OK, "")

        val path = session.uri.trimEnd('/').ifEmpty { "/" }
        return when {
            path == "/api/ping" -> ping()
            // Pairing answers before authentication, which is the only deliberate exception in this file:
            // a TV that is asking to be let in has nothing yet to authenticate with. What it holds is a
            // code, and a code is a label rather than a secret until somebody with the phone taps Allow.
            path == "/api/pair/request" && session.method == Method.POST -> pairRequest(session)
            path == "/api/pair/status" -> pairStatus(session)
            path == "/api/library" -> if (authorized(session)) library() else unauthorized()
            path == "/api/shelf" -> if (authorized(session)) shelf() else unauthorized()
            // A sofa posting its place is the one write a TV gets to make. Everything it can ask for is
            // something the phone already decided, and this is the one thing it knows that the phone does
            // not: where the viewer stopped.
            path == "/api/progress" && session.method == Method.POST -> saveProgress(session)
            path == "/api/progress" -> if (authorized(session)) progress() else unauthorized()
            path.startsWith("/api/subtitles/") -> captions(session, path.removePrefix("/api/subtitles/"))
            path.startsWith("/artwork/") -> artwork(session, path.removePrefix("/artwork/"))
            path.startsWith("/stream/") -> stream(session, path.removePrefix("/stream/"))
            else -> plain(Response.Status.NOT_FOUND, "not found")
        }
    }

    /**
     * What the LAN sweep is looking for.
     *
     * Unauthenticated on purpose: the answer is a device name and nothing about the library, and the
     * sweep that finds this phone cannot know a token it has not been given yet.
     */
    private fun ping(): Response = json(
        JSONObject()
            .put("service", "arcplayer")
            .put("version", VERSION)
            .put("device", deviceName)
            .put("pairing", "open"),
    )

    /**
     * A TV asking to be let in, and the code it will quote back.
     *
     * The body is read through `parseBody`, which is where NanoHTTPD puts a raw JSON POST — not in
     * `parameters`, which only ever holds form-encoded fields. A name the TV does not send is not a
     * reason to refuse it; the phone shows "TV" and the viewer still decides. A body that is not an
     * object at all is refused, because minting a code for garbage means every probe from the LAN
     * buzzes the phone.
     */
    private fun pairRequest(session: IHTTPSession): Response {
        val tv = jsonBody(session)
            ?: return plain(Response.Status.BAD_REQUEST, "expected a JSON object")

        val request = TvPairing.request(
            tvName = tv.optString("tvName"),
            tvModel = tv.optString("tvModel"),
        )
        return json(
            JSONObject()
                .put("code", request.code)
                .put("expiresAt", request.expiresAt),
        )
    }

    /**
     * What a polling TV is told about the code it holds.
     *
     * `approved` is the only branch that carries the key, and it appears only after a human tapped Allow.
     * Every other answer — no, gone, or a code this phone never issued — says nothing about the library.
     */
    private fun pairStatus(session: IHTTPSession): Response {
        val outcome = TvPairing.outcome(session.parameters["c"]?.firstOrNull().orEmpty())
        val body = when (outcome) {
            is PairOutcome.Pending -> JSONObject().put("state", "pending")
                .put("tvName", outcome.tvName)
            is PairOutcome.Approved -> JSONObject().put("state", "approved")
                .put("token", outcome.token)
                .put("device", outcome.device)
            PairOutcome.Denied -> JSONObject().put("state", "denied")
            PairOutcome.Expired -> JSONObject().put("state", "expired")
        }
        return json(body)
    }

    private fun library(): Response {
        val items = JSONArray()
        libraryItems().forEach { item ->
            items.put(
                JSONObject()
                    // A string, not a number: a stable id is a 64-bit Telegram-derived long, and the TV
                    // parses this with JavaScript, which rounds anything past 2^53. Asked for
                    // -3217499465312568008 it returned a URL for ...568000 — the same film read as a
                    // different one, and a 404 no log on the phone explains. The path segment is read back
                    // with toLongOrNull(), so the digits travel unchanged.
                    .put("id", item.stableId.toString())
                    .put("fileName", item.fileName)
                    .put("title", item.title)
                    .put("subtitle", item.subtitle ?: JSONObject.NULL)
                    .put("year", item.year ?: JSONObject.NULL)
                    .put("overview", item.overview)
                    .put("genres", JSONArray(item.genres))
                    .put("posterUrl", item.posterFile?.let {
                        artworkUrl(item.stableId, "poster")
                    } ?: JSONObject.NULL)
                    .put("backdropUrl", item.backdropFile?.let {
                        artworkUrl(item.stableId, "backdrop")
                    } ?: JSONObject.NULL)
                    .put("sizeBytes", item.sizeBytes)
                    .put("durationMs", item.durationMs)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("mimeType", item.contentType)
                    .put("resumeMs", item.resumeMs)
                    // Always a number, never absent: the film page calls toFixed() on this straight off
                    // the response and a missing field is a blank page on the TV.
                    .put("bitrateMbps", item.bitrateMbps)
                    // Both always present. A television deciding whether to warn a viewer about a film
                    // cannot tell an absent flag from a false one, and a missing `needsRemux` reads as a
                    // film that will play; `audioCodec` is the exception, because a name that states no
                    // audio has nothing honest to say and a badge drawn from a guess is worse than none.
                    .put("audioCodec", item.audioCodec ?: JSONObject.NULL)
                    .put("needsRemux", item.needsRemux)
            )
        }
        return json(
            JSONObject()
                .put("version", VERSION)
                .put("generatedAt", System.currentTimeMillis())
                .put("items", items),
        )
    }

    /**
     * Which gate emptied [library].
     *
     * A diagnostic rather than a feature, and authenticated like the library: four gates can all answer
     * zero the same way to a TV, and a phone whose shelf holds whole films but whose library does not is
     * otherwise unfalsifiable, because the release build strips every log line that could have said so.
     */
    private fun shelf(): Response {
        val counts = shelfCounts()
        return json(
            JSONObject()
                // Strings for the ids: both are 64-bit and a JavaScript reader would round them.
                .put("chatId", counts.chatId.toString())
                .put("manifestHeaderChatId", counts.manifestHeaderChatId)
                .put("manifestRows", counts.manifestRows)
                .put("ledgerRows", counts.ledgerRows)
                .put("keptComplete", counts.keptComplete)
                .put("keptInCatalog", counts.keptInCatalog)
                .put("served", counts.served),
        )
    }

    /**
     * Where every film on this phone was left off.
     *
     * [library] already carries one film's place; this carries the whole shelf's in a single round trip,
     * which is what a television wants when it decides what to put first. The keys are strings for the
     * same reason the ids are: a 64-bit number read through JavaScript rounds to the nearest multiple of
     * two and two different films end up sharing a bookmark.
     */
    private fun progress(): Response {
        val positions = JSONObject()
        savedPositions().forEach { (id, positionMs) -> positions.put(id.toString(), positionMs) }
        return json(JSONObject().put("positions", positions))
    }

    /**
     * The place a TV watched up to, kept only when it is worth keeping.
     *
     * A number from the sofa goes through the phone's own rule rather than a looser one: the last ninety
     * seconds of a film is an ending and not a place to come back to, and a viewer who sat through the
     * credits should find the film ticked at home, not offered "Resume at 1:58:04". [savePosition] answers
     * false only for an id this phone is not holding, which is a `404` worth seeing — a place kept for a
     * film the viewer cannot open is a ghost in Continue Watching.
     */
    private fun saveProgress(session: IHTTPSession): Response {
        if (!authorized(session)) return unauthorized()
        val body = jsonBody(session)
            ?: return plain(Response.Status.BAD_REQUEST, "expected a JSON object")

        val id = body.optString("id").toLongOrNull()
            ?: return plain(Response.Status.BAD_REQUEST, "expected a film id")
        val positionMs = body.number("positionMs")
            ?: return plain(Response.Status.BAD_REQUEST, "expected a position")
        // The television measures a length off the container as it plays, and a film whose name carries no
        // duration is one the phone has never opened either. Without the phone's own figure the reported
        // one decides whether this is a pause or an ending, which is the best either side can do.
        val durationMs = body.number("durationMs") ?: 0L

        if (!savePosition(id.toString(), positionMs, durationMs)) {
            return plain(Response.Status.NOT_FOUND, "")
        }
        return plain(Response.Status.NO_CONTENT, "")
    }

    /**
     * The caption for one film, as this phone already holds it.
     *
     * Plain text, because a television's player is handed a *path* rather than a stream: the app fetches
     * this and points AVPlay at the file it saved, and a caption the browser has to guess the type of is a
     * caption nobody reads. `404` when the phone has never cached one — asking Telegram here would be a
     * second job for the one socket that is feeding the film next door.
     */
    private fun captions(session: IHTTPSession, id: String): Response {
        if (!authorized(session)) return unauthorized()
        val file = openCaption(id)?.takeIf { it.isFile } ?: return plain(Response.Status.NOT_FOUND, "")

        val body: InputStream = if (session.method == Method.HEAD) {
            ByteArrayInputStream(ByteArray(0))
        } else {
            file.inputStream()
        }
        val response = newFixedLengthResponse(Response.Status.OK, MIME_TEXT, body, file.length())
        // Nothing here may sit in a cache: a caption forwarded into the channel an hour later has to reach
        // a television that already played this film once today.
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    /** A raw JSON body, which is where NanoHTTPD puts a `POST` of that shape — not in `parameters`. */
    private fun jsonBody(session: IHTTPSession): JSONObject? = runCatching {
        val files = HashMap<String, String>()
        session.parseBody(files)
        JSONObject(files["postData"] ?: return null)
    }.getOrNull()

    /**
     * One number out of a body, or null when the sender did not mean one.
     *
     * Read as a double rather than a long, because JSON has one number type and a client that sends
     * `4215000.0` — which is what a JavaScript rounding slip looks like on the wire — is a client whose
     * intent is still obvious.
     */
    private fun JSONObject.number(name: String): Long? =
        optDouble(name, Double.NaN).takeIf { !it.isNaN() }?.toLong()

    private fun artworkUrl(id: Long, kind: String): String =
        "/artwork/$id/$kind?k=" + URLEncoder.encode(token, "UTF-8")

    private fun artwork(session: IHTTPSession, idAndKind: String): Response {
        if (!authorized(session)) return unauthorized()
        val parts = idAndKind.split('/')
        if (parts.size != 2) return plain(Response.Status.NOT_FOUND, "")
        val id = parts[0].toLongOrNull() ?: return plain(Response.Status.NOT_FOUND, "")
        val item = libraryItems().firstOrNull { it.stableId == id }
            ?: return plain(Response.Status.NOT_FOUND, "")
        val file = when (parts[1]) {
            "poster" -> item.posterFile
            "backdrop" -> item.backdropFile
            else -> null
        }?.takeIf { it.isFile } ?: return plain(Response.Status.NOT_FOUND, "")

        val contentType = file.inputStream().buffered().use {
            URLConnection.guessContentTypeFromStream(it)
        } ?: "image/jpeg"
        val body: InputStream = if (session.method == Method.HEAD) {
            ByteArrayInputStream(ByteArray(0))
        } else {
            file.inputStream()
        }
        val response = newFixedLengthResponse(Response.Status.OK, contentType, body, file.length())
        response.addHeader("Cache-Control", "private, max-age=86400")
        return response
    }

    private fun stream(session: IHTTPSession, id: String): Response {
        if (!authorized(session)) return unauthorized()

        // A HEAD asks for the shape of a film rather than its bytes, so it takes no slot: a viewer already
        // watching must not be dropped because somebody else checked a file's size.
        val head = session.method == Method.HEAD
        val opened = openStream(id) ?: return plain(Response.Status.NOT_FOUND, "")
        val source = if (head) opened else (streams.admit(id, opened) ?: return refused(session, id))

        val size = source.sizeBytes
        val header = session.headers["range"]
        val parsed = RangeHeader.parse(header, size)
        if (parsed is RangeResult.Unsatisfiable) {
            source.close()
            val response = plain(Response.Status.RANGE_NOT_SATISFIABLE, "")
            response.addHeader("Content-Range", "bytes */$size")
            return response
        }

        val range = parsed as RangeResult.Satisfiable
        val length = range.endExclusive - range.start
        val partial = !header.isNullOrBlank()
        // The body of a HEAD is an *empty* stream rather than a null one, because NanoHTTPD answers a null
        // body with `Content-Length: 0` — and a zero-length film is a file no player will open. The handle
        // goes straight back, so no byte of the film is read.
        val body: InputStream = if (head) {
            source.close()
            ByteArrayInputStream(ByteArray(0))
        } else {
            RangeInputStream(source, range.start, range.endExclusive)
        }

        val response = newFixedLengthResponse(
            if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            source.contentType,
            body,
            length,
        )
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Cache-Control", "no-store")
        if (partial) {
            response.addHeader("Content-Range", "bytes ${range.start}-${range.endExclusive - 1}/$size")
        }
        Log.i(TAG, "${session.method} $id range=${header ?: "full"} len=$length of $size")
        return response
    }

    private fun authorized(session: IHTTPSession): Boolean {
        if (token.isBlank()) return false
        val supplied = session.parameters["k"]?.firstOrNull()
            ?: session.headers["x-arc-token"]
            ?: return false
        return MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8),
        )
    }

    private fun unauthorized(): Response = plain(Response.Status.UNAUTHORIZED, "")

    private fun refused(session: IHTTPSession, id: String): Response {
        // A warning rather than the debug-only line above: "the TV says the phone is busy" has to be
        // findable on a release build, and a refusal is the one answer this server gives that no log shows.
        Log.w(TAG, "${session.method} $id refused, $MAX_STREAMS streams already running")
        val response = plain(Response.Status.SERVICE_UNAVAILABLE, "stream limit reached")
        response.addHeader("Retry-After", "1")
        return response
    }

    private fun json(value: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, MIME_JSON, value.toString())

    private fun plain(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, MIME_TEXT, body)

    private fun withCors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Range, X-Arc-Token, Content-Type")
        response.addHeader(
            "Access-Control-Expose-Headers",
            "Content-Range, Content-Length, Accept-Ranges",
        )
        return response
    }
}

/**
 * Which films are being pushed right now, and whose reader a new request takes over from.
 *
 * Both limits protect the phone rather than the TV: the bytes leave the same storage the player reads
 * from. Two readers of one film is worse than two readers of two, so the newest request wins and the
 * previous one is closed out from under its own reader — a dropped socket is what a player recovers
 * from by re-requesting, whereas two competing offset streams are not.
 */
private class StreamGate(private val capacity: Int) {

    private val current = ConcurrentHashMap<String, ByteSource>()

    /** null when the cap is already spent on other films; otherwise a source that frees its slot on close. */
    fun admit(id: String, source: ByteSource): ByteSource? {
        if (!current.containsKey(id) && current.size >= capacity) {
            // Refused means nobody will ever read this handle, and a descriptor left open per refused
            // request is a leak that outlives the connection that caused it.
            runCatching { source.close() }
            return null
        }
        val held = guard(id, source)
        current.put(id, held)?.close()
        return held
    }

    /**
     * The slot is keyed on this wrapper rather than the source, so a displaced reader closing itself
     * cannot take the newer one's place: `remove(key, value)` only fires while this is still the current
     * holder. A source that blocks inside `readAt` has to answer a close while it waits, not after.
     */
    private fun guard(id: String, source: ByteSource): ByteSource = object : ByteSource by source {
        override fun close() {
            current.remove(id, this)
            source.close()
        }
    }
}
