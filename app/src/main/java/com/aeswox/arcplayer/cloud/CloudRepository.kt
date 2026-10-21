package com.aeswox.arcplayer.cloud

import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.aeswox.arcplayer.stream.DeliveredRun
import com.aeswox.arcplayer.stream.StreamWindow
import com.aeswox.arcplayer.subtitle.SubtitleMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the shelf channel is called. It shows up in Telegram's own chat list, so it names the app
 * rather than the contents — one person's shelf is not only movies, and a channel called "Movies"
 * says nothing about which app put it there.
 */
const val SHELF_CHANNEL_TITLE = "Arc Player Server"

/** The title every shelf made before the channel started carrying the app's name. */
const val LEGACY_SHELF_CHANNEL_TITLE = "Movies"

/**
 * Everything the Cloud tab asks Telegram for.
 *
 * Holds the auth state machine, turns user submissions into TDLib requests, and keeps a live
 * picture of how much of each file is on disk. Playback never goes through here: the player reads
 * the file TDLib writes, and only calls [advanceWindow] as it moves through it.
 */
class CloudRepository(
    private val bridge: TdBridge,
    private val scope: CoroutineScope,
    hasCredentials: Boolean,
    private val manifestPath: String,
    private val subtitleManifestPath: String,
    private val captionDirectory: String,
) {

    private val _authState = MutableStateFlow(CloudAuthMachine.initial(hasCredentials))
    val authState: StateFlow<CloudAuthState> = _authState.asStateFlow()

    /** Read-only backing for a video's [CloudVideoItem.chatTitle]; the shelf is the only chat that matters. */
    private val _chats = MutableStateFlow<List<CloudChat>>(emptyList())

    private val _videos = MutableStateFlow<Map<Long, List<CloudVideoItem>>>(emptyMap())
    val videos: StateFlow<Map<Long, List<CloudVideoItem>>> = _videos.asStateFlow()

    /**
     * The captions forwarded into each chat, newest first, kept apart from the shelf itself.
     *
     * They arrive on the same walk as the videos — the document filter already brings them back, and
     * the shelf simply declined them — so collecting them costs no round trip. They are held apart
     * because everything the grid does with a video would be wrong done to a caption.
     */
    private val _subtitles = MutableStateFlow<Map<Long, List<CloudSubtitleItem>>>(emptyMap())

    /** Keyed by TDLib file id, which is small and stable for the life of the client. */
    private val _progress = MutableStateFlow<Map<Int, CloudProgress>>(emptyMap())
    val progress: StateFlow<Map<Int, CloudProgress>> = _progress.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<Long, ByteArray>>(emptyMap())

    /** Small inline JPEGs keyed by [CloudVideoItem.stableId], already inside the history response. */
    val thumbnails: StateFlow<Map<Long, ByteArray>> = _thumbnails.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * The films whose seek index has already been read, keyed by TDLib file id.
     *
     * An empty table is stored for a file that turned out not to have one, so the hunt happens once
     * per file rather than on every seek.
     */
    private val cueTables = ConcurrentHashMap<Int, MkvSeekIndex.CueTable>()

    /**
     * The run of each file's bytes that has been watched landing on disk, keyed by TDLib file id.
     *
     * Telegram's report covers only the window it is filling now and blanks that report the moment a
     * new one starts, so a single reply cannot say what the file already holds. Without this memory,
     * the seam where one window finished and the next began looks like a hole, and the tick that
     * follows every forward extension cancels it and aims it back into film already on the disk.
     */
    private val deliveredRuns = ConcurrentHashMap<Int, DeliveredRun>()

    @Volatile
    private var started = false

    /**
     * Which file the player has been handed since this launch, keyed by the id it was handed for.
     *
     * The sweep needs to know what is being watched, and by then Telegram's records are no help: ask
     * about the number a film's file was written against and the answer is a record for a
     * three-kilobyte thumbnail, because those numbers were handed out again long ago. What this app
     * does know is the path it gave the player, and that is the one thing the sweep must never take.
     */
    private val inUse = ConcurrentHashMap<Int, String>()

    /** Whether this launch has handed back the copies older watches left behind. */
    @Volatile
    private var strandedSwept = false

    init {
        scope.launch {
            bridge.authInputs.collect { input -> onAuthInput(input) }
        }
        scope.launch {
            bridge.updates.collect { update -> onUpdate(update) }
        }
    }

    /**
     * Bring Telegram up if it is not up already.
     *
     * Called every time the tab gains focus, so it has to be inert after the first pass: a second
     * `setTdlibParameters` on the same client is an error rather than a no-op, and the login state
     * machine would happily ask for one if it were driven again from an authorized stage.
     *
     * Everything below runs on the repository's own thread because creating the client loads a
     * native library, which is far too slow to do from a composition.
     */
    fun begin() {
        scope.launch {
            bridge.start()
            if (started) return@launch
            started = true
            bridge.submit(CloudAuthInput.StartRequested)
        }
    }

    fun submitPhoneNumber(phoneNumber: String) = launchInput { CloudAuthInput.PhoneSubmitted(phoneNumber) }
    fun submitCode(code: String) = launchInput { CloudAuthInput.CodeSubmitted(code) }
    fun submitPassword(password: String) = launchInput { CloudAuthInput.PasswordSubmitted(password) }
    fun submitRegistration(firstName: String, lastName: String) =
        launchInput { CloudAuthInput.RegistrationSubmitted(firstName, lastName) }

    /**
     * Put the prompt back on the phone number without touching the client.
     *
     * Used after a rejection that left the state machine with no recorded retry target. Re-sending
     * `setTdlibParameters` would be rejected outright, so recovery goes through the next request the
     * user is actually allowed to make again.
     */
    fun resetToPhoneNumber() = launchInput { CloudAuthInput.PhoneNumberNeeded }

    private fun launchInput(input: () -> CloudAuthInput) {
        bridge.submit(input())
    }

    private suspend fun onAuthInput(input: CloudAuthInput) {
        val transition = CloudAuthMachine.transition(_authState.value, input)
        _authState.value = transition.state
        runEffect(transition.effect)

        if (transition.state.stage == CloudAuthStage.AUTHORIZED) {
            if (_chats.value.isEmpty()) {
                // Off the collector: a chat sweep is tens of round trips, and holding the queue during
                // it would stall any login update TDLib sends while the list is being built.
                scope.launch {
                    runCatching { refreshChats() }
                        .onFailure { Log.w(TAG, "chat refresh after auth", it) }
                }
            }
        }
    }

    private suspend fun runEffect(effect: CloudAuthEffect) {
        _busy.value = true
        try {
            when (effect) {
                CloudAuthEffect.None -> Unit
                CloudAuthEffect.SendParameters -> bridge.applyParameters()
                /**
                 * `sendPhoneNumberCode` still exists in the schema but TDLib no longer treats it as
                 * an authentication request, so it is refused with 401 before it reaches the
                 * network. `setAuthenticationPhoneNumber` is the live spelling of the same call.
                 */
                is CloudAuthEffect.RequestCode -> bridge.call(
                    TdApi.SetAuthenticationPhoneNumber(effect.phoneNumber, authenticationSettings())
                )
                is CloudAuthEffect.SubmitCode -> bridge.call(TdApi.CheckAuthenticationCode(effect.code))
                is CloudAuthEffect.SubmitPassword -> bridge.call(
                    TdApi.CheckAuthenticationPassword(effect.password)
                )
                is CloudAuthEffect.SubmitRegistration -> bridge.call(
                    TdApi.RegisterUser(effect.firstName, effect.lastName, false)
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "auth effect failed", error)
            _authState.value = CloudAuthMachine.transition(
                _authState.value,
                CloudAuthInput.Rejected(error.message ?: "Telegram rejected the request")
            ).state
        } finally {
            _busy.value = false
        }
    }

    private fun authenticationSettings(): TdApi.PhoneNumberAuthenticationSettings =
        TdApi.PhoneNumberAuthenticationSettings().apply {
            allowFlashCall = false
            allowMissedCall = false
            isCurrentPhoneNumber = false
            hasUnknownPhoneNumber = false
            allowSmsRetrieverApi = false
        }

    /**
     * Recent chats, in Telegram's own order, kept only so a video can be labelled with the chat it
     * came from.
     *
     * `getChats` now returns bare ids, so titles come from one `getChat` per id. They go out
     * concurrently: forty sequential round trips would make the tab feel broken on a phone
     * connection, and TDLib is built to interleave requests.
     */
    private suspend fun refreshChats() {
        val ids: LongArray = bridge.call(TdApi.GetChats(TdApi.ChatListMain(), CHAT_PAGE)).chatIds
            ?: LongArray(0)
        val resolved = coroutineScope {
            ids.map { chatId ->
                async {
                    runCatching { bridge.call(TdApi.GetChat(chatId)) }.getOrNull()
                }
            }.awaitAll()
        }
        _chats.value = resolved.mapNotNull { chat ->
            chat ?: return@mapNotNull null
            CloudChat(
                chatId = chat.id,
                title = chat.title.orEmpty(),
                isChannel = (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == true,
            )
        }
    }

    /**
     * Create the one chat the library is built out of.
     *
     * Telegram's own `ChatFolder` is a view over dialogs the account already has and cannot hold a
     * file, so the shelf this app can write into is a private channel: made here, joined here, nobody
     * else able to post. A channel rather than a group because a group needs a second member before
     * TDLib will make one.
     *
     * Returns the new chat's id, which the caller has to remember — nothing here stores it, the
     * repository has no preferences, and making another channel on the next launch would duplicate
     * the whole library.
     */
    suspend fun createFolder(): Long {
        val chat = bridge.call(
            TdApi.CreateNewSupergroupChat(
                /* title = */ SHELF_CHANNEL_TITLE,
                /* isForum = */ false,
                /* isChannel = */ true,
                /* description = */ "",
                /* location = */ null,
                /* messageAutoDeleteTime = */ 0,
                /* forImport = */ false
            )
        )
        // A video's card shows the title of the chat it came from, read out of the list above — and a
        // channel made a second ago was not in the list fetched before it existed.
        _chats.value = _chats.value + CloudChat(chat.id, SHELF_CHANNEL_TITLE, isChannel = true)
        return chat.id
    }

    /** The name the channel carries in Telegram's own list. Null when the chat is gone. */
    suspend fun chatTitle(chatId: Long): String? =
        runCatching { bridge.call(TdApi.GetChat(chatId)).title }.orCancelled().getOrNull()

    /**
     * Renames the shelf channel. Only ever worth calling for a channel this app made under a name it
     * no longer uses — a title the viewer chose by hand in Telegram is theirs, and this must not touch it.
     */
    suspend fun renameFolder(chatId: Long, title: String) {
        bridge.call(TdApi.SetChatTitle(chatId, title))
        _chats.value = _chats.value.map { if (it.chatId == chatId) it.copy(title = title) else it }
        Log.i(TAG, "shelf channel $chatId renamed to $title")
    }

    /** What asking the account for its shelf turned up. */
    sealed interface ShelfRead {
        /** A channel to show as the shelf. It may still hold nothing. */
        data class Ready(val chatId: Long) : ShelfRead

        /** No shelf remembered, none under either name, none reachable — there is nothing to show. */
        data object Gone : ShelfRead
    }

    /**
     * Read the shelf, moving to a channel the account really uses when the id this device remembers has
     * stopped answering.
     *
     * `getChat` keeps succeeding for a deleted channel — the number stays in the preferences and the
     * record stays in Telegram's local database — while every request for its messages is refused. So
     * only reading files proves a shelf is alive, and a live shelf that happens to be empty is asked
     * the same question: the films may be in a channel this device never made, because every install
     * used to make one of its own.
     *
     * Also renames a channel still carrying the title this app used before it had a name of its own. A
     * title changed by hand is the viewer's and is never touched.
     */
    suspend fun readShelf(storedChatId: Long): ShelfRead {
        if (storedChatId == 0L) return adoptOrKeep(null)
        restoreManifest(storedChatId)
        if (chatTitle(storedChatId) == LEGACY_SHELF_CHANNEL_TITLE) {
            runCatching { renameFolder(storedChatId, SHELF_CHANNEL_TITLE) }
                .orCancelled()
                .onFailure { Log.w(TAG, "shelf rename", it) }
        }
        val loaded = runCatching { refreshVideos(storedChatId) }.orCancelled()
        if (loaded.getOrNull().orEmpty().isNotEmpty()) return ShelfRead.Ready(storedChatId)
        if (loaded.isFailure) {
            Log.w(TAG, "shelf $storedChatId will not hand over its files", loaded.exceptionOrNull())
            return adoptOrKeep(null)
        }
        return adoptOrKeep(storedChatId)
    }

    /**
     * A channel the viewer can file films into, making one only when the account has nothing to use.
     *
     * This is what "Add from Telegram" runs before Telegram opens, so the channel named in the toast
     * exists by the time the viewer is told to forward into it. A channel that already answers is
     * reused even when it holds nothing, because a second device making a second shelf of its own is
     * how two channels end up carrying the same name and the viewer has to guess which one is real.
     */
    suspend fun ensureShelf(storedChatId: Long): Long {
        if (storedChatId != 0L && runCatching { loadVideos(storedChatId) }.orCancelled().isSuccess) {
            return storedChatId
        }
        return namedShelfChannels().firstOrNull { runCatching { loadVideos(it) }.orCancelled().isSuccess }
            ?: createFolder()
    }

    /**
     * The channel this account has that holds files, found by the name this app writes rather than by
     * the id a device wrote down for itself.
     *
     * Only a channel that answers with files counts, which is what keeps a read from trading an empty
     * shelf for another empty one and flip-flopping between the two on every revisit.
     */
    private suspend fun findShelfChannel(): Long? =
        namedShelfChannels().firstOrNull { chatId ->
            runCatching { loadVideos(chatId) }.orCancelled().getOrNull()?.isNotEmpty() == true
        }

    /**
     * Channels carrying either name this app has written, newest first.
     *
     * Compared ignoring case and surrounding spaces, because a channel made by hand in Telegram is
     * free text and "arc player server" is the same shelf to the viewer. A search answers with
     * anything *containing* the words, so the title is still checked rather than trusted.
     *
     * A channel the viewer deleted is not answered here at all — it leaves the account's chats entirely
     * — which is why the id left behind in the preferences has to be tried on its own and ruled out by
     * failing to hand over its messages.
     */
    private suspend fun namedShelfChannels(): List<Long> {
        val out = ArrayList<Long>()
        for (title in listOf(SHELF_CHANNEL_TITLE, LEGACY_SHELF_CHANNEL_TITLE)) {
            val found = runCatching { bridge.call(TdApi.SearchChats(title, SHELF_LOOKUP_LIMIT)).chatIds }
                .orCancelled().getOrNull()?.asList().orEmpty()
            for (chatId in found) {
                if (chatId in out) continue
                val chat = runCatching { bridge.call(TdApi.GetChat(chatId)) }.orCancelled().getOrNull()
                    ?: continue
                val isChannel = (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == true
                val sameName = chat.title?.trim()?.equals(title, ignoreCase = true) == true
                if (sameName && isChannel) out += chatId
            }
        }
        Log.i(TAG, "shelf candidates=$out")
        return out
    }

    private suspend fun adoptOrKeep(fallbackChatId: Long?): ShelfRead {
        findShelfChannel()?.let { return ShelfRead.Ready(it) }
        return fallbackChatId?.let { ShelfRead.Ready(it) } ?: ShelfRead.Gone
    }

    /**
     * `runCatching` around a suspending call keeps the exception a cancelled coroutine throws, and a
     * shelf that was only left mid-read is not a shelf that refused. Without this the log blames the
     * channel for the tab going away.
     */
    private fun <T> Result<T>.orCancelled(): Result<T> = also { result ->
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    }

    /**
     * Take a video out of the folder again.
     *
     * Revoked rather than deleted for here only: a shelf the viewer curates has to be empty for
     * Telegram too, or the next launch finds the film waiting in the app's own chat list. The bytes
     * already downloaded stay in TDLib's directory until it clears them itself.
     */
    suspend fun removeFromFolder(video: CloudVideoItem, folderChatId: Long) {
        bridge.call(TdApi.DeleteMessages(folderChatId, longArrayOf(video.messageId), true))
        forgetChat(folderChatId)
    }

    /**
     * Everything watchable in a chat, newest first.
     *
     * Telegram has no "give me the videos in this chat" call, but `searchChatMessages` with a filter
     * and an empty query is what the official apps use for their media tabs: the server does the
     * filtering, so a channel that posts one film among fifty text messages still shows it. Walking
     * history instead can only ever see the last few hundred messages.
     *
     * Two filters rather than one, because a clip forwarded as media arrives as `messageVideo` while
     * a film sent as a file arrives as `messageDocument` — and on the chats that actually hold
     * feature-length video, the documents are nearly all of it.
     *
     * Results are cached per chat because re-requesting is the difference between a tab that feels
     * instant and one that spins on every revisit.
     */
    suspend fun loadVideos(chatId: Long, force: Boolean = false): List<CloudVideoItem> {
        if (!force) _videos.value[chatId]?.let { return it }
        val messages = searchByFilter(chatId, TdApi.SearchMessagesFilterVideo()) +
            searchByFilter(chatId, TdApi.SearchMessagesFilterDocument())
        val pendingThumbnails = HashMap<Long, ByteArray>()
        val items = messages
            .mapNotNull { message -> toCloudVideo(chatId, message, pendingThumbnails) }
            .sortedByDescending { it.messageId }
        val captions = messages
            .mapNotNull { message -> toSubtitle(chatId, message) }
            .sortedByDescending { it.messageId }
        _videos.value = _videos.value + (chatId to items)
        _subtitles.value = _subtitles.value + (chatId to captions)
        scheduleStrandedSweep()
        if (pendingThumbnails.isNotEmpty()) {
            _thumbnails.value = _thumbnails.value + pendingThumbnails
        }
        // An empty shelf and a shelf that failed to read look identical on screen, so the counts and
        // the chat's own name go to the log: which channel was asked, what Telegram answered with,
        // and how much of that turned into a card.
        Log.i(
            TAG,
            "load chat=$chatId title=${_chats.value.firstOrNull { it.chatId == chatId }?.title} " +
                "messages=${messages.size} items=${items.size} captions=${captions.size}"
        )
        return items
    }

    /**
     * Re-reads a chat the viewer may have just forwarded into, keeping whatever is on screen until the
     * answer arrives.
     *
     * The shelf is written to from Telegram, not from here, so an answer from ten seconds ago is
     * already the wrong answer after a trip to the other app. Dropping the cached list before asking
     * made that re-read blank the grid for its own duration, so the fresh list is published whole at the
     * end instead — and written down on the way out, which is what lets the next launch draw its cards
     * before this walk has even started.
     */
    suspend fun refreshVideos(chatId: Long): List<CloudVideoItem> {
        val items = loadVideos(chatId, force = true)
        writeManifest(chatId, items)
        writeSubtitleManifest(chatId)
        return items
    }

    /**
     * The shelf as the last read left it, published so the grid has something to lay out.
     *
     * Only ever asked for a chat nothing is held for, so a revisit inside one session never re-reads
     * the file it is already looking at.
     *
     * Public because the screen asks for it as the app comes up rather than when the tab is opened: a
     * read that starts at the tap cannot finish before that tap has drawn something, and what it drew
     * was "Reading your shelf…" over cards that were already on the disk.
     */
    suspend fun restoreManifest(chatId: Long) {
        if (chatId == 0L) return
        if (!_videos.value.containsKey(chatId)) {
            val text = withContext(Dispatchers.IO) {
                runCatching { File(manifestPath).readText() }.getOrNull()
            }
            if (text != null) {
                val items = ShelfManifest.decode(text, chatId)
                if (items.isNotEmpty()) {
                    _videos.value = _videos.value + (chatId to items)
                    Log.i(TAG, "manifest chat=$chatId drew ${items.size} cards before the walk")
                }
            }
        }
        // Read on its own guard rather than riding along with the videos: a caption is only asked for
        // once a film is already open, and by then the cards have long since been drawn from disk.
        if (!_subtitles.value.containsKey(chatId)) {
            val captionText = withContext(Dispatchers.IO) {
                runCatching { File(subtitleManifestPath).readText() }.getOrNull()
            } ?: return
            val captions = SubtitleManifest.decode(captionText, chatId)
            if (captions.isNotEmpty()) {
                _subtitles.value = _subtitles.value + (chatId to captions)
                Log.i(TAG, "captions chat=$chatId recovered ${captions.size} from disk")
            }
        }
    }

    private suspend fun writeSubtitleManifest(chatId: Long) {
        val captions = _subtitles.value[chatId].orEmpty()
        val text = SubtitleManifest.encode(chatId, captions)
        withContext(Dispatchers.IO) {
            runCatching { File(subtitleManifestPath).writeText(text) }
        }.onFailure { Log.w(TAG, "captions chat=$chatId could not be written", it) }
    }

    private suspend fun writeManifest(chatId: Long, items: List<CloudVideoItem>) {
        val text = ShelfManifest.encode(chatId, items)
        withContext(Dispatchers.IO) {
            runCatching { File(manifestPath).writeText(text) }
        }.onFailure { Log.w(TAG, "manifest chat=$chatId could not be written", it) }
    }

    /**
     * The same file as the open session's Telegram describes it.
     *
     * [CloudVideoItem.fileId] is a number TDLib's current database hands out, so the one on a manifest
     * item is a claim carried over from last launch rather than a fact. Reading the message again is
     * cheap — TDLib holds the channel's history itself — and answers with the number this client is
     * listening for, which is what a tap that beat the shelf walk needs in order to open at all.
     *
     * Any failure here returns the item unchanged rather than nothing: a link too busy to answer is not
     * evidence the retired id is wrong, and the number from last launch is the only one there is.
     */
    suspend fun resolveLive(video: CloudVideoItem): CloudVideoItem {
        if (!video.isFromManifest) return video
        val message = withTimeoutOrNull(ROUND_TRIP_TIMEOUT_MS) {
            runCatching { bridge.call(TdApi.GetMessage(video.chatId, video.messageId)) }.getOrNull()
        } ?: return video
        val found = HashMap<Long, ByteArray>()
        val fresh = toCloudVideo(video.chatId, message, found) ?: return video
        if (found.isNotEmpty()) _thumbnails.value = _thumbnails.value + found
        return fresh.copy(chatTitle = fresh.chatTitle.ifBlank { video.chatTitle })
    }

    /**
     * One filtered search, walked back through the chat until it runs dry.
     *
     * A single `searchChatMessages` answers with whatever it can assemble immediately — five
     * messages out of forty-nine is normal — and hands back `next_from_message_id` as the cursor for
     * the older ones. `totalCount` is the size of the whole result, so it is what says whether
     * stopping early actually cost anything.
     */
    private suspend fun searchByFilter(
        chatId: Long,
        filter: TdApi.SearchMessagesFilter,
    ): List<TdApi.Message> {
        val out = ArrayList<TdApi.Message>()
        var fromMessageId = NEWEST_MESSAGE_ANCHOR
        var pages = 0
        while (pages < MAX_SEARCH_PAGES) {
            pages++
            val page = bridge.call(
                TdApi.SearchChatMessages(
                    /* chatId = */ chatId,
                    /* topicId = */ null,
                    /* query = */ "",
                    /* senderId = */ null,
                    /* fromMessageId = */ fromMessageId,
                    /* offset = */ 0,
                    /* limit = */ MESSAGE_PAGE,
                    /* filter = */ filter
                )
            )
            val messages = page.messages.orEmpty().asList()
            if (messages.isEmpty()) break
            out += messages
            val next = page.nextFromMessageId
            if (next == 0L || next == fromMessageId || out.size >= page.totalCount) break
            fromMessageId = next
        }
        return out
    }

    private fun toCloudVideo(
        chatId: Long,
        message: TdApi.Message,
        thumbnails: MutableMap<Long, ByteArray>,
    ): CloudVideoItem? {
        val source = when (val content = message.content) {
            is TdApi.MessageVideo -> content.video?.let {
                CloudFileSource(
                    file = it.video,
                    fileName = it.fileName,
                    mimeType = it.mimeType,
                    durationMs = it.duration * 1000L,
                    width = it.width,
                    height = it.height,
                    minithumbnail = it.minithumbnail,
                    supportsStreaming = it.supportsStreaming,
                )
            }

            is TdApi.MessageDocument -> content.document?.takeIf { it.isWatchable() }?.let {
                // A file sent as a document carries no duration or frame size in the schema; the
                // player learns both once the container is open.
                CloudFileSource(
                    file = it.document,
                    fileName = it.fileName,
                    mimeType = it.mimeType,
                    durationMs = 0L,
                    width = 0,
                    height = 0,
                    minithumbnail = it.minithumbnail,
                    supportsStreaming = true,
                )
            }

            else -> null
        } ?: return null
        val file = source.file ?: return null
        // TDLib's own File.id is handed out per session, so the key is built from the server's unique
        // id instead. Without that, every relaunch looks like a different video and nothing cached —
        // resume position, favorites, a catalogue match — survives.
        val stableId = CloudIds.forVideo(
            chatId,
            message.id,
            file.remote?.uniqueId?.takeIf { it.isNotBlank() }
                ?: file.remote?.id?.takeIf { it.isNotBlank() }
                ?: file.id.toString()
        )
        source.minithumbnail?.data?.takeIf { it.isNotEmpty() }?.let { thumbnails[stableId] = it }
        return CloudVideoItem(
            stableId = stableId,
            chatId = chatId,
            chatTitle = _chats.value.firstOrNull { it.chatId == chatId }?.title.orEmpty(),
            messageId = message.id,
            fileId = file.id,
            title = source.fileName?.takeIf { it.isNotBlank() } ?: "Video ${message.id}",
            sizeBytes = file.size,
            durationMs = source.durationMs,
            width = source.width,
            height = source.height,
            mimeType = source.mimeType ?: "video/mp4",
            supportsStreaming = source.supportsStreaming,
        )
    }

    /** Whether a Telegram "file" is actually something the player can watch. */
    private fun TdApi.Document.isWatchable(): Boolean {
        val mime = mimeType?.lowercase()?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
        if (mime != null) return mime.startsWith("video/")
        val name = fileName?.lowercase().orEmpty()
        return VIDEO_FILE_EXTENSIONS.any { name.endsWith(it) }
    }

    /**
     * The other half of [isWatchable]: a document the shelf declines because it is words.
     *
     * The video filter and the document filter were already both being walked, so a caption costs this
     * one pass over the messages Telegram handed back and no request of its own.
     */
    private fun toSubtitle(chatId: Long, message: TdApi.Message): CloudSubtitleItem? {
        val document = (message.content as? TdApi.MessageDocument)?.document ?: return null
        val name = document.fileName?.takeIf { it.isNotBlank() } ?: return null
        if (!SubtitleMatch.isSubtitle(name)) return null
        val file = document.document ?: return null
        return CloudSubtitleItem(
            stableId = CloudIds.forVideo(
                chatId,
                message.id,
                file.remote?.uniqueId?.takeIf { it.isNotBlank() }
                    ?: file.remote?.id?.takeIf { it.isNotBlank() }
                    ?: file.id.toString()
            ),
            chatId = chatId,
            messageId = message.id,
            fileId = file.id,
            fileName = name,
            sizeBytes = file.size,
        )
    }

    /**
     * The caption for [video], written where the player can read it, or null when there is none.
     *
     * Asked for while the picture is already moving rather than before it starts: a caption that lands
     * two seconds late is still a caption, and one that holds up the first frame costs every opening
     * for the sake of the few films that have a subtitle waiting.
     *
     * The bytes are copied out of Telegram's own folder rather than read in place, because that folder
     * belongs to Telegram — it deletes from it when the storage budget is over, and a caption pulled
     * out from under a playing film is a track that vanishes mid-scene.
     */
    suspend fun captionFileFor(video: CloudVideoItem): File? {
        var candidates = _subtitles.value[video.chatId].orEmpty()
        if (candidates.isEmpty()) {
            // Nothing has been read for this chat yet. The captions ride on the same walk as the
            // cards, so this asks for the shelf rather than inventing a request of its own.
            runCatching { loadVideos(video.chatId) }
            candidates = _subtitles.value[video.chatId].orEmpty()
        }
        if (candidates.isEmpty()) return null
        val wanted = SubtitleMatch.pick(
            video = video.title,
            subtitles = candidates.map { it.fileName },
            language = Locale.getDefault().language
        ) ?: return null
        val match = candidates.firstOrNull { it.fileName == wanted } ?: return null
        return downloadCaption(if (match.isFromManifest) resolveLiveCaption(match) else match)
    }

    /**
     * The caption's file id as the open session knows it, when the one on hand came off disk.
     *
     * Same bargain as [resolveLive] makes for a video: TDLib hands out file ids from whichever
     * database is current, so a number carried over from last launch has to be re-asked before it can
     * be downloaded.
     */
    private suspend fun resolveLiveCaption(subtitle: CloudSubtitleItem): CloudSubtitleItem {
        val message = withTimeoutOrNull(ROUND_TRIP_TIMEOUT_MS) {
            runCatching { bridge.call(TdApi.GetMessage(subtitle.chatId, subtitle.messageId)) }.getOrNull()
        } ?: return subtitle
        return toSubtitle(subtitle.chatId, message) ?: subtitle
    }

    private suspend fun downloadCaption(subtitle: CloudSubtitleItem): File? {
        val extension = subtitle.fileName.substringAfterLast('.', "srt").lowercase()
        val target = File(captionDirectory, "${subtitle.stableId}.$extension")
        if (target.length() > 0L) return target
        val source = awaitCaption(subtitle.fileId) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                target.parentFile?.mkdirs()
                File(source).copyTo(target, overwrite = true)
            }.getOrNull()?.takeIf { it.length() > 0L }
        }
    }

    /**
     * Ask Telegram for the whole of a small file and say where it landed.
     *
     * `offline = true` with no range, which is the opposite of how a film is fetched: a caption has to
     * arrive entire or it parses as rubbish from the first cut, and there is nothing to stream while
     * waiting for it.
     */
    private suspend fun awaitCaption(fileId: Int): String? {
        val record = tryCall(TdApi.GetFile(fileId)) ?: return null
        if (record.local?.isDownloadingCompleted != true) {
            runCatching { bridge.call(TdApi.DownloadFile(fileId, PRIORITY, 0L, 0L, true)) }
        }
        val giveUpAt = SystemClock.elapsedRealtime() + CAPTION_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < giveUpAt) {
            val now = tryCall(TdApi.GetFile(fileId)) ?: return null
            val path = now.local?.path?.takeIf { it.isNotBlank() }
            if (now.local?.isDownloadingCompleted == true && path != null) return path
            delay(POLL_INTERVAL_MS)
        }
        Log.w(TAG, "caption file=$fileId did not land in ${CAPTION_TIMEOUT_MS}ms")
        return null
    }

    /**
     * The on-disk file to hand the player, once enough of it has actually landed to open.
     *
     * Returning as soon as TDLib names a path is not good enough: the file exists the moment the
     * download starts, but a player pointed at it with zero bytes reads it as broken. So this waits
     * for the first bytes of the window and gives up after a while rather than hanging the tab.
     */
    suspend fun localPathFor(video: CloudVideoItem): String? {
        // Claimed before anything is waited for. The sweep hands back the copies no watch of this
        // session owns, and an open still waiting for its head is indistinguishable from one: the wait
        // is up to twelve seconds and the sweep walks the whole shelf inside that, deleting as it goes.
        inUse[video.fileId] = OPENING
        val record = bridge.call(TdApi.GetFile(video.fileId))
        // Telegram's own size outlives its local record, so read it here and not after a possible drop.
        // A record that does not know the size is not a licence to hand over an empty file: the shelf
        // was told it by the message the film arrived in, and that answer does not go stale.
        val size = (record?.size ?: 0L).takeIf { it > 0L } ?: video.sizeBytes
        var file = record
        if (!headCouldBeRead(file, video.sizeBytes)) file = dropRecord(video)
        if (existingPath(file) == null) fetchHead(video)
        var path = awaitUsablePath(video.fileId, size)
        if (path == null) {
            // Nothing landed while the window was aimed at the head, which is how a record that
            // believes a long-dead "complete" flag answers: it has nothing left to fetch. Forgetting it
            // and aiming again is the one way back, and it is tried once rather than turned into a loop.
            Log.w(TAG, "no head for file ${video.fileId}, asking Telegram to forget the record")
            dropRecord(video)
            fetchHead(video)
            path = awaitUsablePath(video.fileId, size)
        }
        val usable = path ?: run {
            inUse.remove(video.fileId)
            return null
        }
        inUse[video.fileId] = usable
        Log.i(
            TAG,
            "open file=${video.fileId} handed over with ${allocatedBytes(usable)} of $size bytes on disk, " +
                "record complete=${record?.local?.isDownloadingCompleted} active=${record?.local?.isDownloadingActive}"
        )
        // The player measures the file once, when it opens, and a half fetched file is only as long
        // as the furthest byte Telegram has written. Without this, every later part of the film sits
        // "past the end" as far as the player is concerned and a seek into it never comes back. The
        // holes stay sparse, so the disk only ever holds what has actually arrived.
        if (size > 0L) {
            runCatching { RandomAccessFile(usable, "rw").use { it.setLength(size) } }
                .onFailure { Log.w(TAG, "could not stretch $usable to $size", it) }
        }
        prepareIndexForOpen(video)
        return usable
    }

    private suspend fun fetchHead(video: CloudVideoItem) {
        bridge.call(
            TdApi.DownloadFile(video.fileId, PRIORITY, 0L, CloudWindowPlanner.DEFAULT_WINDOW_BYTES, false)
        )
    }

    /**
     * Whether the head of a named file is actually on the device.
     *
     * A record that names no path has simply not started, which is not a lie. One that names a path
     * with nothing allocated behind it is: Telegram keeps calling a file complete after something else
     * has taken its bytes — the storage clear, the system purging the folder, a shell stretched by an
     * earlier open. Measured here, a 1 GB film's record claimed 674 MB had landed while the path it
     * named had never been written at all, and a player pointed at that path shows black and waits.
     *
     * A third case sits between the two and needs its own test: a file whose allocation is real but sits
     * at the far end, left there by a watch whose last fetch was the cue table. Blocks are counted
     * wherever they are in the file, so allocation on its own calls that head readable when it is a hole.
     */
    private suspend fun headCouldBeRead(record: TdApi.File?, shelfSizeBytes: Long): Boolean {
        val path = existingPath(record) ?: return true
        val known = (record?.size ?: 0L).takeIf { it > 0L } ?: shelfSizeBytes
        val needed = if (known > 0L) minOf(HEAD_READ_BYTES, known) else HEAD_READ_BYTES
        return allocatedBytes(path) >= needed && !headIsHole(path)
    }

    /**
     * Take a lying record away from Telegram and let it start the film again.
     *
     * Through `DeleteFile`, never by touching the path: this is the one case where the bytes are gone
     * but the record is still being believed, and only Telegram can stop believing it.
     */
    private suspend fun dropRecord(video: CloudVideoItem): TdApi.File? {
        Log.w(TAG, "dropping stale record for file ${video.fileId}")
        tryCall(TdApi.CancelDownloadFile(video.fileId, false))
        tryCall(TdApi.DeleteFile(video.fileId))
        deliveredRuns.remove(video.fileId)
        _progress.value = _progress.value - video.fileId
        return tryCall(TdApi.GetFile(video.fileId))
    }

    /**
     * Put the file's seek index on disk while the player is still waiting to be handed the file.
     *
     * The player reads a Matroska index of its own when it opens, and that index is what turns a
     * "go to 19:47" into a byte to read. If the cue table is still a hole at that moment the player
     * gets no index and every later seek is silently dropped: measured on device, the scrubber went
     * to the target, the fetch delivered the bytes there, and the film carried on playing from
     * wherever it had been. Waiting for a fraction of a megabyte now is what makes seeking work at
     * all, and nothing is competing for the download window while we wait, because the player has
     * not started reading yet.
     *
     * Failing to get one is not a reason to hold up playback: the proportional byte guess still
     * answers our own seeks, so this gives up on a budget and puts the window back on the head.
     */
    private suspend fun prepareIndexForOpen(video: CloudVideoItem) {
        if (cueTables.containsKey(video.fileId)) return
        val startedAt = SystemClock.elapsedRealtime()
        var tries = 0
        // One try is not a hunt. Both ends of the file have to be on disk before an index can be
        // read — the header names the cue table, the tail holds it — and either answers "not yet" a
        // moment before it answers "here". Handing the player a file whose index is still a hole is
        // what makes a film sit dark forever with no error: it cannot build a seek table, so it never
        // reports a duration, and the ticks that would drag the download window along never start.
        val table = withTimeoutOrNull(OPEN_INDEX_BUDGET_MS) {
            var found: MkvSeekIndex.CueTable? = null
            while (found == null && !cueTables.containsKey(video.fileId)) {
                tries++
                found = runCatching { cueTableFor(video, requireOpeningLanded = false) }
                    .onFailure { Log.w(TAG, "open-time index failed for ${video.fileId}", it) }
                    .getOrNull()
                if (found == null) delay(POLL_INTERVAL_MS)
            }
            found
        }
        Log.i(
            TAG,
            "index for open file=${video.fileId} found=${table != null} size=${video.sizeBytes} " +
                "tries=$tries took=${SystemClock.elapsedRealtime() - startedAt}ms"
        )
        // Whatever the hunt did to the window, playback starts at the beginning.
        aimWindowAt(video, 0L)
    }

    private suspend fun awaitUsablePath(fileId: Int, fileSizeBytes: Long): String? {
        // Deadlines are measured on the clock, not by adding up the delays: a poll is a TDLib round
        // trip and one can take seconds while Telegram is busy.
        val giveUpAt = SystemClock.elapsedRealtime() + FIRST_WINDOW_TIMEOUT_MS
        // The header is what the index hunt reads first, and a file that has only its first kilobyte
        // reads as a broken container. An unknown size still has to leave something to read: zeroes
        // identify no container, and a path handed over empty parks the player at 0 KB/s for good.
        val needBytes = if (fileSizeBytes > 0L) minOf(HEAD_READ_BYTES, fileSizeBytes) else HEAD_READ_BYTES
        while (true) {
            val path = bridge.call(TdApi.GetFile(fileId))?.local?.path?.takeIf { it.isNotBlank() }
            // What the record *reports* about its bytes is not the question: a window re-aimed by a seek
            // counts some of them twice, and a record that outlived its file reports the whole film as
            // present. Allocation under the path is what a player can actually read — and even that is
            // not enough on its own, because it counts blocks wherever they sit in the file. A hunt that
            // landed the cue table leaves a file with plenty allocated and a hole where the header
            // should be, and a player handed that reads zeroes, reports no container and no duration,
            // and then nothing ever asks it to try again: measured on device as a black frame parked at
            // 0 KB/s with the download window never moving.
            if (path != null && allocatedBytes(path) >= needBytes && !headIsHole(path)) return path
            if (SystemClock.elapsedRealtime() >= giveUpAt) return null
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * Called by the player as it reads, to keep Telegram's download window ahead of the play head.
     */
    suspend fun advanceWindow(video: CloudVideoItem, readPositionBytes: Long) {
        if (seekDepth.get() > 0) return
        aimWindowAt(video, readPositionBytes)
    }

    /**
     * How many bytes the player could read right now at and ahead of [readPositionBytes], or null
     * when Telegram did not answer inside a round trip.
     *
     * The stall watchdog asks this on its own because the play head has stopped, and that is exactly
     * the moment the window's usual reporting route goes quiet. Only bytes actually on disk count:
     * a ranged fetch in flight reports an empty prefix, which is the honest answer when the question
     * is whether there is enough film to press play again.
     */
    suspend fun readableAhead(video: CloudVideoItem, readPositionBytes: Long): Long? {
        val facts = pollFacts(video.fileId) ?: return null
        publishProgress(video.fileId, facts)
        return StreamWindow.readableBytes(
            position = readPositionBytes,
            size = facts.fileSizeBytes,
            windowStart = facts.downloadOffset,
            windowLength = facts.downloadedPrefixSize,
            completed = facts.isDownloadingCompleted,
            alreadyDelivered = deliveredRuns[video.fileId] ?: DeliveredRun.NONE
        )
    }

    private suspend fun aimWindowAt(video: CloudVideoItem, readPositionBytes: Long) {
        val facts = pollFacts(video.fileId) ?: return
        // Whoever moves the window has just asked Telegram about the file, so this is the poll that
        // can answer the loading line. A readout fed only by the waits goes still the moment nothing
        // is waiting, which reads as a dead link even when the window is being re-aimed fine.
        publishProgress(video.fileId, facts)
        val run = deliveredRuns[video.fileId] ?: DeliveredRun.NONE
        val command = CloudWindowPlanner.plan(
            readPositionBytes = readPositionBytes,
            facts = facts,
            alreadyDelivered = run
        )
        // The run is on this line because a wrong "nothing to do" here is a frozen picture, and the
        // only way to tell a correct one from a stale memory is to see both spans together. The three
        // record flags are here for the same reason: every way this answer can be "None" is a belief
        // about the file, and a park in front of a black player is not diagnosable without knowing
        // which belief fired.
        Log.i(
            TAG,
            "advance read=$readPositionBytes window=${facts.downloadOffset}+${facts.downloadedPrefixSize} " +
                "size=${facts.fileSizeBytes} active=${facts.isDownloadingActive} " +
                "done=${facts.isDownloadingCompleted} run=$run -> $command"
        )
        when (command) {
            is WindowCommand.None -> Unit
            is WindowCommand.Fetch -> cappedCall(
                TdApi.DownloadFile(video.fileId, PRIORITY, command.offset, command.limit, false)
            )
        }
    }

    /**
     * One TDLib round trip, given [ROUND_TRIP_TIMEOUT_MS] to answer, or null when it did not.
     *
     * Requests queue behind the transfer already running, and while a 64 megabyte window is in flight
     * a reply can wait considerably longer than the wait that asked for it is allowed to spend:
     * measured, a cue-table budget of 45 seconds that took 117, because the deadline only got looked
     * at after each round trip came back. Giving up on the answer does not undo the request, so
     * nothing is lost by skipping a late one — every caller reads the state again on its next poll.
     */
    private suspend fun <T : TdApi.Object> cappedCall(function: TdApi.Function<T>): T? =
        withTimeoutOrNull(ROUND_TRIP_TIMEOUT_MS) { bridge.call(function) }

    /**
     * [cappedCall] that also treats a refusal as no answer.
     *
     * The storage survey asks about every watched file in turn, and one stale file id — TDLib hands
     * those out again after it rebuilds its database — is a reason to skip that file, not a reason to
     * abandon the sweep or to leave the shelf's byte count at nothing.
     */
    private suspend fun <T : TdApi.Object> tryCall(function: TdApi.Function<T>): T? =
        runCatching { cappedCall(function) }.orCancelled().getOrNull()

    private suspend fun pollFacts(fileId: Int): WindowFacts? =
        cappedCall(TdApi.GetFile(fileId))?.toWindowFacts()?.also { rememberDelivered(fileId, it) }

    /**
     * Adds one reply's landed span to the remembered run for this file.
     *
     * Called from every poll so the memory is the same no matter which watcher happened to ask, and
     * so a span that arrives during a stall check also counts. An in-flight window reports an empty
     * prefix, which extends nothing — that is exactly the reply that would otherwise erase the record
     * of the window it replaced.
     */
    private fun rememberDelivered(fileId: Int, facts: WindowFacts) {
        val run = (deliveredRuns[fileId] ?: DeliveredRun.NONE)
            .extendedBy(facts.downloadOffset, facts.downloadOffset + facts.downloadedPrefixSize)
        deliveredRuns[fileId] = run
    }

    /**
     * Counts the seek work in flight.
     *
     * A file gets one download window, and the play head keeps ticking while a seek waits for its
     * bytes. Each of those ticks aims the window back at where playback is, which interrupts the
     * fetch the seek is waiting on and can starve it long enough to give up on bytes that were on
     * their way. Observed on device: the seek's fetch and the tick's fetch alternating a second
     * apart. So the ticks stand down for the duration of a seek.
     */
    private val seekDepth = AtomicInteger()

    private suspend fun <T> seeking(block: suspend () -> T): T {
        seekDepth.incrementAndGet()
        try {
            return block()
        } finally {
            seekDepth.decrementAndGet()
        }
    }

    /**
     * Where in the file the bytes for a moment of the film are.
     *
     * Uses the file's own cue table when one has been read, because a constant-bitrate guess drifts
     * by more than a download window on a long encode. Nothing is fetched here: the position ticks
     * that call it arrive many times a second, and a round trip inside one would starve playback.
     */
    fun cachedByteOffset(video: CloudVideoItem, positionMs: Long, durationMs: Long): Long =
        byteOffset(positionMs, durationMs, video.sizeBytes, cueTables[video.fileId])

    /**
     * [cachedByteOffset] for a seek, which is the one moment where fetching the index is worth it.
     */
    suspend fun awaitByteOffsetForSeek(video: CloudVideoItem, positionMs: Long, durationMs: Long): Long =
        seeking { byteOffset(positionMs, durationMs, video.sizeBytes, cueTableFor(video)) }

    private fun byteOffset(positionMs: Long, durationMs: Long, sizeBytes: Long, table: MkvSeekIndex.CueTable?): Long {
        val fromTable = table?.byteFor(positionMs, sizeBytes)
        if (fromTable != null) return fromTable
        // Past the last cue the table stops answering, which is the part of a film most worth seeking
        // to. The end of the file stands in as one more point, answered here rather than folded into
        // the table because the length is often still unknown when the table is first read.
        if (table != null && !table.isEmpty && durationMs > 0L && positionMs < durationMs) {
            val last = table.timesMs.size - 1
            val lastTime = table.timesMs[last]
            val lastByte = table.clusterBytes[last]
            if (positionMs >= lastTime && sizeBytes > lastByte) {
                return lastByte + (positionMs - lastTime) * (sizeBytes - lastByte) / (durationMs - lastTime)
            }
        }
        return CloudWindowPlanner.readPositionBytes(positionMs, durationMs, sizeBytes)
    }

    /**
     * The film's seek index, read off disk and remembered, or null when this file has no usable one.
     *
     * Two small reads: the header names the byte where the cue table starts, and the table itself is
     * a fraction of a megabyte at the tail of the file. Pointing the window there interrupts
     * whatever else is arriving, so this runs at most once per file and a failure is remembered too
     * — an MP4 has no cue table and would otherwise pay for the hunt on every single seek.
     */
    private suspend fun cueTableFor(
        video: CloudVideoItem,
        requireOpeningLanded: Boolean = true,
    ): MkvSeekIndex.CueTable? {
        cueTables[video.fileId]?.let { return it.ifUsable() }
        if (video.sizeBytes <= 0L) return null
        if (requireOpeningLanded && !isOpeningLanded(video)) return null
        val path = localPath(video.fileId) ?: return null
        val table = runCatching { readCueTable(video, path) }
            .onFailure { Log.w(TAG, "cue index failed for ${video.fileId}", it) }
            .getOrNull()
        if (table != null) {
            cueTables[video.fileId] = table
            return table
        }
        // An MP4 has no cue table, and looking for one is a file read every seek, so that verdict is
        // worth keeping. A header that has not landed yet is the one failure worth another try, and
        // "has not landed" has to be tested rather than inferred from the magic being absent: a hole
        // where the head should be reads as zeroes, which is no more EBML than an MP4 header is.
        // Remembering that one answer cost a film its index for the rest of the session — measured, the
        // open-time hunt gave up after a single 158 ms try of a fifteen second budget, and every seek
        // after it fell back to the constant-bitrate guess.
        if (!hasMkvMagic(path) && !headIsHole(path)) cueTables[video.fileId] = EMPTY_CUE_TABLE
        return null
    }

    /**
     * Whether the bytes at the very start of the file have arrived, which is when the index hunt is
     * safe to make.
     *
     * The cue table sits near the end of the film and Telegram gives a file one download window, so
     * hunting for it drags that window away from wherever it was. While the player is still reading
     * the head to open the container that is the wrong trade: the opening never finishes, the video
     * track stays dark, and only the audio already buffered plays on. Observed on device for the
     * resume seek of a file that had barely started downloading. The proportional byte guess answers
     * a seek that happens this early, so the hunt waits for a moment when nothing is under it.
     */
    private suspend fun isOpeningLanded(video: CloudVideoItem): Boolean {
        val facts = bridge.call(TdApi.GetFile(video.fileId)).toWindowFacts()
        return StreamWindow.readableBytes(
            position = 0L,
            size = facts.fileSizeBytes,
            windowStart = facts.downloadOffset,
            windowLength = facts.downloadedPrefixSize,
            completed = facts.isDownloadingCompleted
        ) >= minOf(CloudWindowPlanner.LOW_WATER_BYTES, facts.fileSizeBytes)
    }

    private suspend fun localPath(fileId: Int): String? = existingPath(bridge.call(TdApi.GetFile(fileId)))

    /**
     * Read the container header and the cue table off disk.
     *
     * Both the reads and the parse go on the IO dispatcher. This runs inside the seek gesture, whose
     * scope is the main thread, and the phone's own dropbox recorded what that costs: five ANRs with
     * the main thread still inside the parser.
     */
    private suspend fun readCueTable(video: CloudVideoItem, path: String): MkvSeekIndex.CueTable? {
        val layout = withContext(Dispatchers.IO) {
            RandomAccessFile(path, "r").use { file ->
                val head = ByteArray(file.length().coerceAtMost(HEAD_READ_BYTES).toInt())
                file.readFully(head)
                MkvSeekIndex.readLayout(head)
            }
        }
        // Both refusals look the same from outside — "this file has no index" — and are nothing like
        // each other: one is a header that has not landed, the other is a size that disagrees with
        // the file on disk. Without the reason the second reads as the first.
        if (layout == null) {
            Log.i(TAG, "no readable header in file ${video.fileId} yet")
            return null
        }
        if (layout.cuesOffset !in 0L until video.sizeBytes) {
            Log.i(TAG, "cue offset ${layout.cuesOffset} is not inside ${video.sizeBytes} for file ${video.fileId}")
            return null
        }
        if (!awaitCuesWindow(video, layout.cuesOffset)) return null
        val length = minOf(CUES_READ_BYTES, video.sizeBytes - layout.cuesOffset).toInt()
        // The file was stretched to its full length when playback opened, so this read is in bounds
        // even though everything ahead of the cue table is still a hole.
        val buffer = withContext(Dispatchers.IO) {
            RandomAccessFile(path, "r").use { file ->
                file.seek(layout.cuesOffset)
                ByteArray(length).also { file.readFully(it) }
            }
        }
        val table = withContext(Dispatchers.IO) { MkvSeekIndex.readCueTable(buffer, layout) } ?: return null
        Log.i(TAG, "cue table for ${video.fileId}: ${table.timesMs.size} points, ${layout.cuesOffset}")
        return table
    }

    /** Ask Telegram for the cue table and wait until it is on disk. */
    private suspend fun awaitCuesWindow(video: CloudVideoItem, cuesOffset: Long): Boolean {
        val room = video.sizeBytes - cuesOffset
        if (room <= 0L) return false
        val limit = minOf(CUES_READ_BYTES, room)
        val ask = suspend {
            cappedCall(TdApi.DownloadFile(video.fileId, PRIORITY, cuesOffset, limit, false))
        }
        ask()
        // Each poll is a TDLib round trip that can itself take seconds while the network is busy, so
        // the deadline is measured on the clock and no poll is waited for longer than the budget
        // allows.
        val giveUpAt = SystemClock.elapsedRealtime() + CUES_WINDOW_TIMEOUT_MS
        val watch = RedirectWatch(cuesOffset)
        while (true) {
            val facts = pollFacts(video.fileId)
            if (facts != null) {
                publishProgress(video.fileId, facts)
                // The window is placed exactly on the element, so "the whole of it has arrived" is
                // the test; anything short would read a hole at the far end of the buffer.
                if (StreamWindow.readableBytes(
                        position = cuesOffset,
                        size = facts.fileSizeBytes,
                        windowStart = facts.downloadOffset,
                        windowLength = facts.downloadedPrefixSize,
                        completed = facts.isDownloadingCompleted
                    ) >= limit
                ) {
                    return true
                }
                if (watch.shouldRedirect(facts, giveUpAt)) {
                    Log.i(TAG, "redirect file=${video.fileId} wanted=$cuesOffset (cue table)")
                    cappedCall(TdApi.CancelDownloadFile(video.fileId, false))
                    // The play-head planner would aim a whole window here, so the exact range is
                    // asked for again by hand.
                    ask()
                }
            }
            if (SystemClock.elapsedRealtime() >= giveUpAt) return false
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun hasMkvMagic(path: String): Boolean = runCatching {
        RandomAccessFile(path, "r").use { file ->
            if (file.length() < 4L) return@use false
            val first = ByteArray(4).also { file.readFully(it) }
            first.contentEquals(MKV_MAGIC)
        }
    }.getOrDefault(false)

    /**
     * Whether the start of the file is a hole rather than a header this app did not recognise.
     *
     * A windowed fetch leaves the file its full length with nothing written behind the bytes that
     * landed, so the two cases look identical from the magic alone and only a read tells them apart.
     * Anything unreadable counts as a hole: the verdict it would support is the one that is remembered,
     * and a wrong "this file has no index" is permanent for the session while a wrong "try again" costs
     * one more read.
     */
    private suspend fun headIsHole(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            RandomAccessFile(path, "r").use { file ->
                val probe = ByteArray(MKV_MAGIC.size)
                file.read(probe) < probe.size || probe.all { it == 0.toByte() }
            }
        }.getOrDefault(true)
    }

    private fun MkvSeekIndex.CueTable?.ifUsable(): MkvSeekIndex.CueTable? =
        this?.takeIf { !it.isEmpty }

    /**
     * Point the window at the bytes a seek is about to ask for, then wait until they are on disk.
     *
     * A half fetched file has holes, and the player reads it as an ordinary file, so a seek past
     * what Telegram has delivered is not a stall but the end of the movie: nothing recovers it on
     * its own. Answering false means the fetch is still not there after every budget, which the
     * player reports as a wait rather than as a move back to where the seek started.
     */
    suspend fun awaitBytesForSeek(video: CloudVideoItem, positionBytes: Long): Boolean =
        seeking { awaitSeekWindow(video, positionBytes) }

    private suspend fun awaitSeekWindow(video: CloudVideoItem, positionBytes: Long): Boolean {
        // Film that has already landed is not a download to wait for. Asking for it again would break
        // off the window that is carrying the part which has not, which is what a resume onto a half
        // cached file costs: the seek spends its budget beside a window dragged back onto bytes that
        // are already on disk, and the picture never starts.
        val delivered = deliveredRuns[video.fileId] ?: DeliveredRun.NONE
        val roomAtOpen = (video.sizeBytes - positionBytes).coerceAtLeast(0L)
        if (delivered.covers(positionBytes) &&
            delivered.to - positionBytes >= minOf(CloudWindowPlanner.LOW_WATER_BYTES, roomAtOpen)
        ) {
            Log.i(TAG, "seek served from disk file=${video.fileId} at=$positionBytes")
            return true
        }
        // A committed seek is a decision, not a nudge. Whatever was arriving for the position the
        // play head left is wanted nowhere now, and TDLib will not move a running window by itself:
        // the planner reads a live download as already covering its business and answers "nothing to
        // do". So the cancel comes first and the aim is planned from what is actually on disk.
        redirectWindow(video, positionBytes)
        var deadline = SystemClock.elapsedRealtime() + SEEK_WINDOW_TIMEOUT_MS
        var watch = RedirectWatch(positionBytes)
        var attemptsLeft = SEEK_WINDOW_ATTEMPTS
        while (true) {
            val facts = pollFacts(video.fileId)
            if (facts != null) {
                publishProgress(video.fileId, facts)
                // One byte at the target is not enough to start decoding: the player reads forward
                // from there, and the bytes past what has landed are sparse zeroes, which come out
                // on screen as a mosaic rather than as a stall.
                val roomLeft = (facts.fileSizeBytes - positionBytes).coerceAtLeast(0L)
                val neededAhead = minOf(CloudWindowPlanner.LOW_WATER_BYTES, roomLeft)
                if (StreamWindow.readableBytes(
                        position = positionBytes,
                        size = facts.fileSizeBytes,
                        windowStart = facts.downloadOffset,
                        windowLength = facts.downloadedPrefixSize,
                        completed = facts.isDownloadingCompleted,
                        alreadyDelivered = deliveredRuns[video.fileId] ?: DeliveredRun.NONE
                    ) >= neededAhead
                ) {
                    return true
                }
                if (watch.shouldRedirect(facts, deadline)) {
                    redirectWindow(video, positionBytes)
                }
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                if (attemptsLeft <= 1) return false
                attemptsLeft--
                Log.i(TAG, "seek budget expired file=${video.fileId} attemptsLeft=$attemptsLeft")
                // The bytes are on their way either way, so an expired budget is not a refused seek:
                // the fetch is broken off, aimed again from what did land, and given another budget.
                deadline = SystemClock.elapsedRealtime() + SEEK_WINDOW_TIMEOUT_MS
                watch = RedirectWatch(positionBytes)
                redirectWindow(video, positionBytes)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * Breaks off a fetch that is landing in the wrong place and points it at the wanted bytes again.
     *
     * TDLib will not move a running window by itself, and a plain download request for a new range
     * does not interrupt one either — [CloudWindowPlanner] reads a live download as covering the whole
     * window it asked for and answers "nothing to do". So the cancel has to come first, and only then
     * can the aim be re-planned from what is actually on disk.
     */
    private suspend fun redirectWindow(video: CloudVideoItem, positionBytes: Long) {
        Log.i(TAG, "redirect file=${video.fileId} wanted=$positionBytes")
        cappedCall(TdApi.CancelDownloadFile(video.fileId, false))
        aimWindowAt(video, positionBytes)
    }

    /**
     * Samples one wait for [StreamWindow.needsRedirect], and gives each restart the same grace to
     * prove itself before another one is considered.
     */
    private class RedirectWatch(private val positionBytes: Long) {
        private var baselineBytes: Long? = null
        private var baselineAtMs = 0L

        fun shouldRedirect(facts: WindowFacts, giveUpAtMs: Long): Boolean {
            val now = SystemClock.elapsedRealtime()
            val bytes = facts.downloadedSizeBytes
            val baseline = baselineBytes
            if (baseline == null) {
                baselineBytes = bytes
                baselineAtMs = now
                return false
            }
            if (!StreamWindow.needsRedirect(
                    position = positionBytes,
                    windowStart = facts.downloadOffset,
                    windowLength = facts.downloadedPrefixSize,
                    landedBytes = bytes - baseline,
                    waitedMs = now - baselineAtMs,
                    budgetLeftMs = giveUpAtMs - now
                )
            ) {
                return false
            }
            baselineBytes = bytes
            baselineAtMs = now
            return true
        }
    }

    /**
     * Give a film's bytes back and forget everything the app learned about them.
     *
     * Called when the player puts a film down, and when another film takes its place — an audience of
     * one does not keep two shelves of downloads. Telegram holds every original, so the next watch is a
     * re-fetch rather than a loss, and nothing here needs a cap or a plan: there is only ever the one
     * copy that was just on screen.
     *
     * The bytes have to leave through Telegram, never by touching the path. A local record that names a
     * gone file is worse than a missing download — the next open reads that record, finds the path it
     * trusts, and plays a hole. `DeleteFile` drops the record with the bytes, so it is the only door.
     * The download is broken off first because TDLib refuses to delete a file it is still writing, and
     * the two memories the file was keyed on go with it: a stale delivered-run makes the window planner
     * answer "already have it" about a file that holds nothing, and a stale cue table makes the next
     * open skip the index hunt entirely.
     */
    suspend fun forgetCopy(video: CloudVideoItem) {
        val fileId = video.fileId
        tryCall(TdApi.CancelDownloadFile(fileId, false))
        val deleted = tryCall(TdApi.DeleteFile(fileId)) != null
        deliveredRuns.remove(fileId)
        cueTables.remove(fileId)
        inUse.remove(fileId)
        _progress.value = _progress.value - fileId
        Log.i(TAG, "forget title=${video.title} file=$fileId deleted=$deleted")
    }

    /**
     * Once this launch has a shelf list straight from Telegram, hand back the copies no watch of this
     * session owns.
     *
     * Fired from the walk and not from the manifest that renders the grid while the walk runs. The
     * manifest's ids are a cache of an older conversation with Telegram, and measured on a phone they
     * mostly no longer answer: 58 of 61 came back refused, which is a sweep that reclaims nothing while
     * reporting itself done. Storage has no deadline, so it waits for ids that are live.
     */
    private fun scheduleStrandedSweep() {
        if (strandedSwept) return
        scope.launch {
            runCatching { sweepStrandedDownloads() }
                .onFailure { Log.w(TAG, "stranded download sweep", it) }
        }
    }

    /**
     * Put down every shelf copy Telegram still reports as local and this session has not asked for.
     *
     * A watch that ends at the back arrow already released its file. The watches that end because the
     * app was swiped out of Recents do not, and on the two installs this was measured on they had left
     * 1.2 GB and 1.9 GB behind. Those bytes are reachable, but only through Telegram: the shelf's own
     * ids name them, and `DeleteFile` unlinks them *and* retires the record.
     *
     * The second half is the whole reason not to reach for the filesystem. The first version of this
     * listed the folder and deleted what the listing said was unclaimed, which reclaimed the space and
     * then hung the next open of every film it touched: the record goes on reporting the file complete,
     * so [headCouldBeRead] waves it through, [localPathFor] stretches a shell of the right length over
     * the hole, and the planner sees nothing to fetch — measured as `advance read=0 window=0+0 -> None`
     * in front of a black player sitting at 0 KB/s. Only Telegram can stop believing its own record.
     *
     * So a file this app cannot name with a live id stays, however much it costs. Each id is believed
     * only when its record's own size agrees with the shelf's, because Telegram hands numbers out again
     * and a stale one usually resolves to a thumbnail.
     */
    private suspend fun sweepStrandedDownloads() {
        if (strandedSwept) return
        val shelf = _videos.value.values.flatten()
        if (shelf.isEmpty()) return
        strandedSwept = true
        var put = 0
        var held = 0L
        val left = mutableMapOf<DownloadSweep.Verdict, Int>()
        val examples = mutableMapOf<DownloadSweep.Verdict, String>()
        for (item in shelf) {
            val claim = DownloadSweep.Claim(item.fileId, item.sizeBytes)
            val answer = runCatching { cappedCall(TdApi.GetFile(item.fileId)) }
            val file = answer.getOrNull()
            val facts = file?.toWindowFacts()
            val record = facts?.let {
                DownloadSweep.Record(
                    claimedSizeBytes = it.fileSizeBytes,
                    reportsLocalBytes = it.downloadedSizeBytes > 0L || it.isDownloadingCompleted,
                    isDownloading = it.isDownloadingActive,
                )
            }
            // Read live, and read again after the round trip: a film the viewer tapped while Telegram
            // was answering is no longer a leftover, and the walk takes long enough for that to happen
            // on a shelf this size.
            when (val verdict = DownloadSweep.verdict(claim, record, inUse.containsKey(item.fileId))) {
                DownloadSweep.Verdict.STRANDED -> {
                    forgetCopy(item)
                    put++
                    // Telegram's own count of what it was holding, not the length of the film: a copy
                    // left behind by a killed watch is one window, and summing shelf sizes reported 37 GB
                    // reclaimed on a device that was carrying 134 MB.
                    held += facts?.downloadedSizeBytes ?: 0L
                }
                else -> {
                    left[verdict] = (left[verdict] ?: 0) + 1
                    // One illustration of each kind of refusal. The tally says how many copies this
                    // launch left alone and nothing about why, and "58 no answer" is two different
                    // failures — an id Telegram has never heard of, and a client that was not ready to
                    // be asked — with nothing but the reply to tell them apart.
                    examples.putIfAbsent(
                        verdict,
                        if (answer.isFailure) {
                            "file=${item.fileId} refused: ${answer.exceptionOrNull()?.message}"
                        } else {
                            "file=${item.fileId} size=${facts?.fileSizeBytes} downloaded=${facts?.downloadedSizeBytes} " +
                                "complete=${facts?.isDownloadingCompleted} active=${facts?.isDownloadingActive} " +
                                "named=${existingPath(file) != null}"
                        }
                    )
                }
            }
        }
        val reasons = left.entries.sortedByDescending { it.value }.joinToString { (verdict, count) ->
            "$count ${verdict.name.lowercase().replace('_', ' ')} — ${examples[verdict]}"
        }
        Log.i(
            TAG,
            "sweep put down $put of ${shelf.size} shelf copies, $held bytes their records claimed" +
                if (reasons.isEmpty()) "" else " (left: $reasons)"
        )
    }

    /**
     * What one file is actually costing the device.
     *
     * The blocks the filesystem has allocated, not the length the file claims: a windowed fetch leaves
     * holes, and a film whose head and tail have both been read reports its whole length while holding a
     * fraction of it. Two of the shells measured here claimed 3 GB and had four kilobytes in them.
     */
    private fun allocatedBytes(path: String): Long = runCatching { Os.stat(path).st_blocks * 512L }
        .getOrDefault(0L)

    fun forgetChat(chatId: Long) {
        _videos.value = _videos.value - chatId
    }

    private fun existingPath(file: TdApi.File?): String? =
        file?.local?.path?.takeIf { it.isNotBlank() }

    private fun TdApi.File.toWindowFacts(): WindowFacts {
        val local = this.local
        return WindowFacts(
            fileSizeBytes = this.size,
            downloadOffset = local?.downloadOffset ?: 0L,
            downloadedPrefixSize = local?.downloadedPrefixSize ?: 0L,
            isDownloadingActive = local?.isDownloadingActive == true,
            isDownloadingCompleted = local?.isDownloadingCompleted == true,
            downloadedSizeBytes = local?.downloadedSize ?: 0L,
        )
    }

    /**
     * Hands what a wait learned about the file to whatever is watching it. The wait is already paying
     * for the round trip, and an update Telegram pushed before the seek started is not enough to tell
     * the player how far the download has come since.
     */
    private fun publishProgress(fileId: Int, facts: WindowFacts) {
        _progress.value = _progress.value + (
            fileId to CloudProgress(
                downloadedBytes = facts.downloadedPrefixSize,
                totalBytes = facts.fileSizeBytes,
                isDownloading = facts.isDownloadingActive,
                isComplete = facts.isDownloadingCompleted,
                bytesOnDisk = facts.downloadedSizeBytes,
            )
            )
    }

    private fun onUpdate(update: TdApi.Object) {
        if (update !is TdApi.UpdateFile) return
        val file = update.file ?: return
        val local = file.local
        _progress.value = _progress.value + (
            file.id to CloudProgress(
                downloadedBytes = local?.downloadedPrefixSize ?: 0L,
                totalBytes = file.size,
                isDownloading = local?.isDownloadingActive == true,
                isComplete = local?.isDownloadingCompleted == true,
                bytesOnDisk = local?.downloadedSize ?: 0L,
            )
            )
    }

    private companion object {
        const val TAG = "ArcCloud"
        const val CHAT_PAGE = 60

        /** Channels carrying the shelf's name worth checking; there is realistically one or two. */
        const val SHELF_LOOKUP_LIMIT = 10

        /** 100 is the most `searchChatMessages` will answer in one call. */
        const val MESSAGE_PAGE = 100

        /**
         * How far back one filter is chased before the tab settles. A busy channel can hold thousands
         * of clips, and paging all of them would spend the open on round trips nobody watches.
         */
        const val MAX_SEARCH_PAGES = 8

        /**
         * `searchChatMessages` reads 0 as "start at the newest message and work backwards"; the
         * response's `next_from_message_id` is the cursor for anything older.
         */
        const val NEWEST_MESSAGE_ANCHOR = 0L

        /**
         * TDLib download priorities run 1 to 32, higher meaning faster, and 32 is the level the
         * official apps use for the file currently on screen. 0 is not a priority at all; TDLib
         * refuses the request outright.
         */
        const val PRIORITY = 32

        const val POLL_INTERVAL_MS = 150L

        /** How long to wait for the first bytes of a window before telling the user to come back. */
        const val FIRST_WINDOW_TIMEOUT_MS = 12_000L

        /**
         * How long opening waits for the seek index. It is a fraction of a megabyte and the player is
         * not reading yet, so on a working link this is most of a second; the budget is what it is
         * allowed to become instead, because a film that opens a minute late and seeks is worse than
         * one that opens now and cannot.
         */
        const val OPEN_INDEX_BUDGET_MS = 15_000L

        /**
         * How long a seek waits for Telegram to carry the play head's new byte range. Longer than
         * the opening window: a seek can land hundreds of megabytes into a film, which cancels the
         * window that was downloading and makes the CDN start again from a cold offset. Measured on
         * a 2.9 GB film, a jump of half a gigabyte needed more than 30 seconds to have six megabytes
         * on disk, so a budget that size only ever produced refused seeks while the bytes carried on
         * arriving just after the wait gave up. Giving up is still better than hanging: the play head
         * stays where it was and keeps playing.
         */
        const val SEEK_WINDOW_TIMEOUT_MS = 60_000L

        /**
         * How many budgets one seek gets before it is reported as undelivered. A slow link that is
         * carrying bytes is not a failure, and the viewer would rather wait than be told the part is
         * unavailable; three attempts is three minutes, past which the fetch is dead rather than
         * slow and the play head stays paused at the target with the wait still on screen.
         */
        const val SEEK_WINDOW_ATTEMPTS = 3

        /**
         * How long a TDLib reply is allowed to take inside a wait. Long enough for a round trip on
         * an idle client by a wide margin, short enough that one queued behind a 64 megabyte
         * transfer cannot stretch a budget the way a measured 117 seconds against 45 did.
         */
        const val ROUND_TRIP_TIMEOUT_MS = 8_000L

        /**
         * How long a caption is allowed to take arriving. A subtitle is tens of kilobytes, so on a
         * working link this is one round trip; the budget exists for the link that is not working,
         * where waiting any longer than the film has already taken to open says the caption was not
         * coming and the picture should simply be left alone.
         */
        const val CAPTION_TIMEOUT_MS = 20_000L

        /**
         * How long the cue table waits. The table itself is small, but it sits near the end of the
         * file, so reading it cancels the window that was downloading the opening megabytes and
         * Telegram's CDN has to seek again; on a 2.9 GB film that first transfer measured about 40
         * seconds. Giving up early would drop every later seek back onto the constant-bitrate guess,
         * which is the thing this index exists to replace, so the wait is allowed to be long. It is
         * a once per file cost and the player shows "Fetching that part from Telegram…" while it
         * runs; later seeks use the cached table and commit in a second and a half.
         */
        const val CUES_WINDOW_TIMEOUT_MS = 45_000L

        /** Enough of a Matroska header to read the seek head and the timecode scale out of. */
        const val HEAD_READ_BYTES = 64L * 1024

        /**
         * How much of the cue table to fetch. A two and a half hour film's is a hundred kilobytes,
         * and this leaves room for one cue point per frame of a much longer one.
         */
        const val CUES_READ_BYTES = 1L * 1024 * 1024

        /** EBML's signature, the only thing that says a file is worth sending to the Matroska reader. */
        val MKV_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

        /** Stored for a file that has no cue table, so the hunt is not repeated on every seek. */
        val EMPTY_CUE_TABLE = MkvSeekIndex.CueTable(LongArray(0), LongArray(0))

        /**
         * Stands in the in-use map for a file whose open has not been handed a path yet.
         *
         * Only the keys of that map are ever read — the sweep asks "is this film being watched", never
         * "where is it" — so an open in progress can claim its file before it knows the name Telegram
         * will give it.
         */
        const val OPENING = ""

        /** Used only when Telegram labels a file `application/octet-stream`, which plenty of them are. */
        val VIDEO_FILE_EXTENSIONS = listOf(
            ".mkv", ".mp4", ".mov", ".m4v", ".webm", ".avi", ".ts", ".wmv", ".flv", ".3gp"
        )
    }
}

/** The parts of `messageVideo` and `messageDocument` that a [CloudVideoItem] is built from. */
private class CloudFileSource(
    val file: TdApi.File?,
    val fileName: String?,
    val mimeType: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val minithumbnail: TdApi.Minithumbnail?,
    val supportsStreaming: Boolean,
)
