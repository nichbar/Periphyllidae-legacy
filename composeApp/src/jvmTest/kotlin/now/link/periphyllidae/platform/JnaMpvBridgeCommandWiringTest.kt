package now.link.periphyllidae.platform

import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals

class JnaMpvBridgeCommandWiringTest {
    @Test
    fun loadPauseResumeStopAreForwardedAsMpvCommands() {
        val calls = mutableListOf<String>()
        val bindings = FakeBindings(calls)

        val bridge = JnaMpvBridge(
            loader = MpvNativeLoader,
            bindingsFactory = { bindings },
        )

        bridge.initialize()
        bridge.loadUrl("https://example/stream")
        bridge.pause(true)
        bridge.pause(false)
        bridge.stop()

        assertEquals(
            listOf(
                "create",
                "opt:vid=no",
                "opt:force-window=no",
                "init",
                "cmd:set audio-display no",
                "cmd:loadfile \"https://example/stream\" replace",
                "cmd:set pause yes",
                "cmd:set pause no",
                "cmd:stop",
            ),
            calls,
        )
    }
}

private class FakeBindings(
    private val calls: MutableList<String>,
) : MpvJnaBindings {
    private val handle = Pointer(1)

    override fun mpv_create(): Pointer? {
        calls += "create"
        return handle
    }

    override fun mpv_initialize(handle: Pointer): Int {
        calls += "init"
        return 0
    }

    override fun mpv_command_string(handle: Pointer, command: String): Int {
        calls += "cmd:$command"
        return 0
    }

    override fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int {
        calls += "opt:$name=$value"
        return 0
    }

    override fun mpv_observe_property(handle: Pointer, userdata: Long, name: String, format: Int): Int = 0

    override fun mpv_set_wakeup_callback(handle: Pointer, callback: MpvWakeupCallback?, data: Pointer?) = Unit

    override fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer? = null

    override fun mpv_terminate_destroy(handle: Pointer) = Unit
}
