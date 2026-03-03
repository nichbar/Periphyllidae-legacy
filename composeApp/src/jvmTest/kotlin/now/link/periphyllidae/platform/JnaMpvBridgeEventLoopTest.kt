package now.link.periphyllidae.platform

import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JnaMpvBridgeEventLoopTest {
    @Test
    fun emitsFinishedOnEndFileEvent() {
        val bindings = EventBindings(includeEndFileEvent = true)
        val bridge = JnaMpvBridge(
            loader = MpvNativeLoader,
            bindingsFactory = { bindings },
            localeConfigurator = {},
        )

        val statuses = mutableListOf<MpvPlaybackStatus>()
        bridge.observePlaybackStatus { statuses += it }

        bridge.initialize()
        bridge.loadUrl("https://example/stream")

        waitForCondition { statuses.contains(MpvPlaybackStatus.FINISHED) }

        assertTrue(statuses.contains(MpvPlaybackStatus.FINISHED))
        bridge.close()
    }

    @Test
    fun stopDoesNotEmitFinishedFromEndFileEvent() {
        val bindings = EventBindings(includeEndFileEvent = true)
        val bridge = JnaMpvBridge(
            loader = MpvNativeLoader,
            bindingsFactory = { bindings },
            localeConfigurator = {},
        )

        val statuses = mutableListOf<MpvPlaybackStatus>()
        bridge.observePlaybackStatus { statuses += it }

        bridge.initialize()
        bridge.loadUrl("https://example/stream")
        bridge.stop()

        // Give event loop time to consume END_FILE after manual stop
        Thread.sleep(120)

        val finishedCount = statuses.count { it == MpvPlaybackStatus.FINISHED }
        assertEquals(0, finishedCount)
        bridge.close()
    }

    @Test
    fun readsCurrentPositionFromMpvProperty() {
        val bindings = EventBindings(includeEndFileEvent = false, timePos = "12.5")
        val bridge = JnaMpvBridge(
            loader = MpvNativeLoader,
            bindingsFactory = { bindings },
            localeConfigurator = {},
        )

        bridge.initialize()
        val position = bridge.currentPositionMillis()

        assertEquals(12500L, position)
        bridge.close()
    }

    private fun waitForCondition(timeoutMs: Long = 600, checkEveryMs: Long = 25, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(checkEveryMs)
        }
    }
}

private class EventBindings(
    includeEndFileEvent: Boolean,
    private val timePos: String = "0",
) : MpvJnaBindings {
    private val handle = Pointer(1)
    private var shutdown = false

    private val noneEventMemory = createEventMemory(MpvEventId.NONE)
    private val endFileEventMemory = createEventMemory(MpvEventId.END_FILE)
    private val shutdownEventMemory = createEventMemory(MpvEventId.SHUTDOWN)

    private val eventQueue = ArrayDeque<Pointer>().apply {
        if (includeEndFileEvent) add(endFileEventMemory)
    }

    override fun mpv_create(): Pointer? = handle

    override fun mpv_initialize(handle: Pointer): Int = 0

    override fun mpv_command_string(handle: Pointer, command: String): Int = 0

    override fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int = 0

    override fun mpv_observe_property(handle: Pointer, userdata: Long, name: String, format: Int): Int = 0

    override fun mpv_set_wakeup_callback(handle: Pointer, callback: MpvWakeupCallback?, data: Pointer?) = Unit

    override fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer? {
        if (shutdown) return shutdownEventMemory
        return eventQueue.removeFirstOrNull() ?: noneEventMemory
    }

    override fun mpv_get_property_string(handle: Pointer, name: String): Pointer? {
        val value = if (name == "time-pos") timePos else ""
        val mem = Memory((value.length + 1).toLong())
        mem.setString(0, value)
        return mem
    }

    override fun mpv_free(data: Pointer) = Unit

    override fun mpv_terminate_destroy(handle: Pointer) {
        shutdown = true
    }

    private fun createEventMemory(eventId: Int): Pointer {
        val pointerSize = NativeSize.pointerBytes
        val memory = Memory((4 + 4 + 8 + pointerSize).toLong())
        memory.setInt(0, eventId)
        memory.setInt(4, 0)
        memory.setLong(8, 0L)
        if (pointerSize == 8) {
            memory.setLong(16, 0L)
        } else {
            memory.setInt(16, 0)
        }
        return memory
    }
}

private object NativeSize {
    val pointerBytes: Int = if (System.getProperty("os.arch").contains("64")) 8 else 4
}
