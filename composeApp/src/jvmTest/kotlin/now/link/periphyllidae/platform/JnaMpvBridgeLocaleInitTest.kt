package now.link.periphyllidae.platform

import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals

class JnaMpvBridgeLocaleInitTest {
    @Test
    fun initializeSetsNumericLocaleBeforeMpvCreate() {
        val calls = mutableListOf<String>()
        val bridge = JnaMpvBridge(
            loader = MpvNativeLoader,
            bindingsFactory = { LocaleOrderBindings(calls) },
            localeConfigurator = { calls += "locale" },
        )

        bridge.initialize()

        assertEquals(listOf("locale", "create", "init"), calls)
    }
}

private class LocaleOrderBindings(
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

    override fun mpv_command_string(handle: Pointer, command: String): Int = 0
    override fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int = 0
    override fun mpv_observe_property(handle: Pointer, userdata: Long, name: String, format: Int): Int = 0
    override fun mpv_set_wakeup_callback(handle: Pointer, callback: MpvWakeupCallback?, data: Pointer?) = Unit
    override fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer? = null
    override fun mpv_terminate_destroy(handle: Pointer) = Unit
}
