package now.link.periphyllidae.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

class JnaMpvBridge(
    private val loader: MpvNativeLoader = MpvNativeLoader,
    private val bindingsFactory: () -> MpvJnaBindings = { MpvJnaBindings.load() },
    private val localeConfigurator: () -> Unit = { defaultLocaleConfigurator() },
) : MpvBridge {
    private var statusListener: ((MpvPlaybackStatus) -> Unit)? = null
    private var initialized = false
    private var bindings: MpvJnaBindings? = null
    private var handle: Pointer? = null

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
        statusListener?.invoke(MpvPlaybackStatus.IDLE)
    }

    override fun loadUrl(url: String) {
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
        command("stop")
        statusListener?.invoke(MpvPlaybackStatus.IDLE)
    }

    override fun seekTo(positionMillis: Long) {
        val seconds = positionMillis / 1000.0
        command("seek $seconds absolute")
    }

    override fun currentPositionMillis(): Long = 0L

    override fun setVolume(volume: Float) {
        val percent = (volume.coerceIn(0f, 1f) * 100f)
        command("set volume $percent")
    }

    override fun close() {
        val mpv = bindings
        val currentHandle = handle
        if (initialized && mpv != null && currentHandle != null) {
            mpv.mpv_terminate_destroy(currentHandle)
        }
        bindings = null
        handle = null
        initialized = false
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

    private fun configureNumericLocaleC() {
        val libcName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "msvcrt" else "c"
        val libc = Native.load(libcName, LibC::class.java)
        libc.setlocale(LC_NUMERIC, "C")
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
