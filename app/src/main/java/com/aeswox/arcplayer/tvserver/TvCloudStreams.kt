package com.aeswox.arcplayer.tvserver

import com.aeswox.arcplayer.cloud.CloudRepository
import com.aeswox.arcplayer.cloud.TvOpenResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StreamDescription(
    val sizeBytes: Long,
    val contentType: String,
)

sealed interface StreamOpenResult {
    data class Opened(val source: ByteSource) : StreamOpenResult
    data object NotFound : StreamOpenResult
    data object Busy : StreamOpenResult
    data object Unavailable : StreamOpenResult
}

/** Keeps Telegram ownership across the short-lived range connections AVPlay opens for one film. */
class TvCloudStreams(private val repository: CloudRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var current: HeldSession? = null
    private var idleClose: Job? = null
    private var closed = false

    fun open(item: TvShelfItem): StreamOpenResult = runBlocking {
        mutex.withLock {
            if (closed) return@withLock StreamOpenResult.Unavailable

            current?.let { held ->
                if (held.session.stableId != item.stableId) {
                    return@withLock StreamOpenResult.Busy
                }
                idleClose?.cancel()
                held.handles++
                return@withLock opened(item, held)
            }

            when (val result = repository.openTvReadSession(item.cloudVideo)) {
                is TvOpenResult.Opened -> {
                    val held = HeldSession(result.session, handles = 1)
                    current = held
                    opened(item, held)
                }
                TvOpenResult.Busy -> StreamOpenResult.Busy
                TvOpenResult.Unavailable -> StreamOpenResult.Unavailable
            }
        }
    }

    fun close() {
        scope.launch {
            try {
                mutex.withLock {
                    if (closed) return@withLock
                    closed = true
                    idleClose?.cancel()
                    current?.session?.close()
                    current = null
                }
            } finally {
                scope.cancel()
            }
        }
    }

    private fun opened(item: TvShelfItem, held: HeldSession): StreamOpenResult.Opened {
        val handle = held.session.openHandle()
        return StreamOpenResult.Opened(
            TvCloudSource(
                sizeBytes = item.sizeBytes,
                contentType = item.contentType,
                handle = handle,
                onClose = { release(held) },
            )
        )
    }

    private fun release(held: HeldSession) {
        runBlocking {
            mutex.withLock {
                if (current !== held || closed) return@withLock
                held.handles--
                if (held.handles == 0) scheduleIdleClose(held)
            }
        }
    }

    private fun scheduleIdleClose(held: HeldSession) {
        idleClose?.cancel()
        idleClose = scope.launch {
            delay(IDLE_CLOSE_MS)
            mutex.withLock {
                if (current !== held || held.handles != 0 || closed) return@withLock
                current = null
                held.session.close()
            }
        }
    }

    private class HeldSession(
        val session: CloudRepository.TvReadSession,
        var handles: Int,
    )

    private class TvCloudSource(
        override val sizeBytes: Long,
        override val contentType: String,
        private val handle: CloudRepository.TvReadHandle,
        private val onClose: () -> Unit,
    ) : ByteSource {
        private val closed = AtomicBoolean(false)

        override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int =
            handle.readAt(offset, buffer, off, len)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            handle.close()
            onClose()
        }
    }

    private companion object {
        const val IDLE_CLOSE_MS = 20_000L
    }
}
