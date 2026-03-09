package now.link.periphyllidae.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class JnaMpvBridge(
    private val loader: MpvNativeLoader = MpvNativeLoader,
    private val bindingsFactory: () -> MpvJnaBindings = { MpvJnaBindings.load() },
    private val localeConfigurator: () -> Unit = { defaultLocaleConfigurator() },
) : MpvBridge {
    private var statusListener: ((MpvPlaybackStatus) -> Unit)? = null
    private var initialized = false
    private var bindings: MpvJnaBindings? = null
    private var handle: Pointer? = null
    private var eventLoopThread: Thread? = null
    private val eventLoopRunning = AtomicBoolean(false)
    private val manualStopRequested = AtomicBoolean(false)

    override fun initialize() {
        if (initialized) return

        localeConfigurator()
        loader.load()
        val mpv = bindingsFactory()
        val createdHandle = mpv.mpv_create() ?: error("mpv_create returned null handle")
        check(mpv.mpv_set_option_string(createdHandle, "vid", "no") == 0) { "failed to set mpv option: vid=no" }
        check(mpv.mpv_set_option_string(createdHandle, "force-window", "no") == 0) { "failed to set mpv option: force-window=no" }
        val result = mpv.mpv_initialize(createdHandle)
        check(result == 0) { "mpv_initialize failed with code $result" }

        commandWithHandle(mpv, createdHandle, "set audio-display no")

        bindings = mpv
        handle = createdHandle
        initialized = true
        startEventLoop(mpv, createdHandle)
        statusListener?.invoke(MpvPlaybackStatus.IDLE)
    }

    override fun loadUrl(url: String) {
        manualStopRequested.set(false)
        val escapedUrl = url.replace("\\", "\\\\").replace("\"", "\\\"")
        command("loadfile \"$escapedUrl\" replace")
        statusListener?.invoke(MpvPlaybackStatus.BUFFERING)
        statusListener?.invoke(MpvPlaybackStatus.PLAYING)
    }

    override fun pause(paused: Boolean) {
        command("set pause ${if (paused) "yes" else "no"}")
        statusListener?.invoke(if (paused) MpvPlaybackStatus.PAUSED else MpvPlaybackStatus.PLAYING)
    }

    override fun stop() {
        manualStopRequested.set(true)
        command("stop")
        statusListener?.invoke(MpvPlaybackStatus.IDLE)
    }

    override fun seekTo(positionMillis: Long) {
        val seconds = positionMillis / 1000.0
        command("seek $seconds absolute")
    }

    override fun currentPositionMillis(): Long {
        initialize()
        val mpv = bindings ?: return 0L
        val currentHandle = handle ?: return 0L

        val pointer = mpv.mpv_get_property_string(currentHandle, "time-pos") ?: return 0L
        return try {
            pointer.getString(0).toDoubleOrNull()?.times(1000.0)?.toLong() ?: 0L
        } finally {
            mpv.mpv_free(pointer)
        }
    }

    override fun setVolume(volume: Float) {
        val percent = (volume.coerceIn(0f, 1f) * 100f)
        command("set volume $percent")
    }

    override fun close() {
        eventLoopRunning.set(false)
        eventLoopThread?.join(300)
        eventLoopThread = null

        val mpv = bindings
        val currentHandle = handle
        if (initialized && mpv != null && currentHandle != null) {
            mpv.mpv_terminate_destroy(currentHandle)
        }
        bindings = null
        handle = null
        initialized = false
        manualStopRequested.set(false)
        statusListener?.invoke(MpvPlaybackStatus.FINISHED)
    }

    override fun observePlaybackStatus(onStatus: (MpvPlaybackStatus) -> Unit) {
        statusListener = onStatus
    }

    private fun command(command: String) {
        initialize()
        val mpv = bindings ?: error("MPV bindings not initialized")
        val currentHandle = handle ?: error("MPV handle not initialized")
        commandWithHandle(mpv, currentHandle, command)
    }

    private fun commandWithHandle(mpv: MpvJnaBindings, currentHandle: Pointer, command: String) {
        val result = mpv.mpv_command_string(currentHandle, command)
        check(result == 0) { "mpv command failed ($result): $command" }
    }

    private fun startEventLoop(mpv: MpvJnaBindings, currentHandle: Pointer) {
        if (eventLoopRunning.get()) return

        eventLoopRunning.set(true)
        eventLoopThread = thread(start = true, isDaemon = true, name = "mpv-event-loop") {
            while (eventLoopRunning.get()) {
                val eventPointer = mpv.mpv_wait_event(currentHandle, 0.1) ?: continue
                val event = MpvEvent.fromPointer(eventPointer)
                when (event.event_id) {
                    MpvEventId.END_FILE -> {
                        val endFileEvent = event.data?.let { MpvEventEndFile.fromPointer(it) }
                        val endReason = endFileEvent?.reason

                        // Manual stop can still race with END_FILE events; consume its marker if present.
                        val wasManualStop = manualStopRequested.getAndSet(false)

                        when {
                            endReason == MpvEndFileReason.EOF && !wasManualStop -> {
                                // Natural track completion only
                                statusListener?.invoke(MpvPlaybackStatus.FINISHED)
                            }
                            else -> {
                                // Ignore STOP/REDIRECT/QUIT/ERROR END_FILE events to avoid accidental double-skip
                                // during manual track switches or replacement.
                            }
                        }
                    }

                    MpvEventId.SHUTDOWN -> break
                    MpvEventId.NONE -> Unit
                }
            }
            eventLoopRunning.set(false)
        }
    }

    private interface LibC : Library {
        fun setlocale(category: Int, locale: String): String?
    }

    private companion object {
        const val LC_NUMERIC = 4

        private fun defaultLocaleConfigurator() {
            val libcName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "msvcrt" else "c"
            val libc = Native.load(libcName, LibC::class.java)
            libc.setlocale(LC_NUMERIC, "C")
        }
    }
}
