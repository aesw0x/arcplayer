package com.aeswox.arcplayer.cloud

import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The one place that knows TDLib exists.
 *
 * Everything above this speaks [CloudAuthInput] and plain data classes. That boundary is what
 * keeps the login flow testable without a network and lets the rest of the app survive a TDLib
 * upgrade: only this file has to change when the schema does.
 */
class TdBridge(
    private val apiId: Int,
    private val apiHash: String,
    private val databaseDirectory: String,
    private val filesDirectory: String,
) {

    private var client: Client? = null

    /**
     * TDLib updates other than authorization, which arrives on a TDLib worker thread.
     *
     * Conflated to the newest value per subscriber because the only consumers are "what is the
     * download window now" reads, where a stale event is worse than a dropped one.
     */
    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    private val _authInputs = MutableSharedFlow<CloudAuthInput>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val authInputs: SharedFlow<CloudAuthInput> = _authInputs.asSharedFlow()

    /**
     * Queue a user action for the login machine.
     *
     * Submissions take this door rather than calling the repository directly so that everything
     * feeding the state machine arrives on one collector. TDLib answers on its own worker thread,
     * and two coroutines doing read-modify-write on the same state is how a fresh start ends up
     * sending `setTdlibParameters` twice.
     */
    fun submit(input: CloudAuthInput) {
        _authInputs.tryEmit(input)
    }

    val isStarted: Boolean get() = client != null

    /**
     * TDLib clients are process-lifetime objects; this binding exposes no close or destroy.
     *
     * Starting is idempotent, so the repository can call this every time the Cloud tab opens. It is
     * also slow: creating a client loads and initializes `libtdjni`, so callers must be off the main
     * thread. The lock is what keeps two concurrent first-calls from building two clients.
     */
    @Synchronized
    fun start() {
        if (client != null) return
        quietLogger()
        client = Client.create(
            /* clientHandler = */ { result -> onTdObject(result) },
            /* extraHandler = */ null,
            /* exceptionHandler = */ { error ->
                Log.e(TAG, "TDLib fatal", error)
                submit(CloudAuthInput.Rejected(error.message ?: "TDLib crashed"))
            }
        )
    }

    /**
     * Cut TDLib's chatter down.
     *
     * TDLib's internal logger writes straight to logcat under the tag `DLTD`, and a cold start that
     * opens the shelf printed 100,501 of those lines inside the ~1.6 s the app is coming up. They are
     * routine traffic at verbosity 3 and 4 — every request sent, every update received, every byte
     * position — while the same capture held 2 lines at 2 and none below. Going through
     * [Client.execute] rather than a client request is what TDLib asks for here: the log functions are
     * synchronous, global to the process, and this has to take effect before the client has finished
     * starting. A failure only costs us quiet, so it must not cost the start.
     */
    private fun quietLogger() {
        runCatching { Client.execute(TdApi.SetLogVerbosityLevel(MAX_LOG_VERBOSITY)) }
            .onFailure { Log.w(TAG, "TDLib logger is still verbose", it) }
    }

    private fun onTdObject(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            mapAuthorizationState(obj.authorizationState)?.let { submit(it) }
            return
        }
        _updates.tryEmit(obj)
    }

    private fun mapAuthorizationState(state: TdApi.AuthorizationState?): CloudAuthInput? = when (state) {
        is TdApi.AuthorizationStateWaitTdlibParameters -> CloudAuthInput.StartRequested
        is TdApi.AuthorizationStateWaitPhoneNumber -> CloudAuthInput.PhoneNumberNeeded
        is TdApi.AuthorizationStateWaitCode -> CloudAuthInput.CodeNeeded(mapCodeInfo(state.codeInfo))
        is TdApi.AuthorizationStateWaitPassword -> CloudAuthInput.PasswordNeeded
        is TdApi.AuthorizationStateWaitRegistration -> CloudAuthInput.RegistrationNeeded
        is TdApi.AuthorizationStateReady -> CloudAuthInput.Authorized
        is TdApi.AuthorizationStateClosed, is TdApi.AuthorizationStateClosing -> CloudAuthInput.Closed
        is TdApi.AuthorizationStateLoggingOut -> CloudAuthInput.Closed
        else -> null
    }

    private fun mapCodeInfo(info: TdApi.AuthenticationCodeInfo?): CodeHint {
        val type = info?.type
        val length = when (type) {
            is TdApi.AuthenticationCodeTypeSms -> type.length
            is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length
            else -> 0
        }
        return CodeHint(
            codeLength = length,
            digitsOnly = type is TdApi.AuthenticationCodeTypeSms ||
                type is TdApi.AuthenticationCodeTypeCall ||
                type is TdApi.AuthenticationCodeTypeFlashCall ||
                type is TdApi.AuthenticationCodeTypeMissedCall,
            allowFlashCall = type is TdApi.AuthenticationCodeTypeFlashCall,
            allowMissedCall = type is TdApi.AuthenticationCodeTypeMissedCall,
        )
    }

    /**
     * Send one request and suspend until TDLib answers it.
     *
     * The request goes out the moment this suspends and cancellation does not take it back, but the
     * *wait* is cancellable: TDLib queues a reply behind whatever transfer is running, and a
     * non-cancellable suspension handed a caller a round trip that could sit unanswered for the best
     * part of two minutes, which is how a 45 second budget came to spend 117. Dropping a late answer
     * costs nothing here — every caller that cares re-reads the state on its next poll.
     */
    suspend fun <T : TdApi.Object> call(function: TdApi.Function<T>): T = suspendCancellableCoroutine { continuation ->
        val target = client
        if (target == null) {
            continuation.resumeWithException(IllegalStateException("TDLib client is not running"))
            return@suspendCancellableCoroutine
        }
        target.send(function, Client.ResultHandler { result ->
            if (!continuation.isActive) return@ResultHandler
            when (result) {
                is TdApi.Error -> continuation.resumeWithException(
                    TelegramError(result.code, result.message ?: "TDLib error")
                )
                else -> @Suppress("UNCHECKED_CAST")
                    continuation.resume(result as T)
            }
        })
    }

    /** Fire the initial parameters. Called once, in response to `waitTdlibParameters`. */
    suspend fun applyParameters() {
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = this@TdBridge.databaseDirectory
            filesDirectory = this@TdBridge.filesDirectory
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            this.apiId = this@TdBridge.apiId
            this.apiHash = this@TdBridge.apiHash
            systemLanguageCode = "en"
            deviceModel = Build.MODEL ?: "Android"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = APP_VERSION
        }
        call(parameters)
    }

    class TelegramError(val code: Int, override val message: String) : Exception(message)

    private companion object {
        const val TAG = "ArcTdBridge"
        const val APP_VERSION = "1.0.0"
        const val MAX_LOG_VERBOSITY = 2
    }
}
