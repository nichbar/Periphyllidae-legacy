package now.link.periphyllidae.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface MpvJnaBindings : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_command_string(handle: Pointer, command: String): Int
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_observe_property(handle: Pointer, userdata: Long, name: String, format: Int): Int
    fun mpv_set_wakeup_callback(handle: Pointer, callback: MpvWakeupCallback?, data: Pointer?)
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
    fun mpv_terminate_destroy(handle: Pointer)

    companion object {
        fun load(): MpvJnaBindings {
            val explicitPath = System.getProperty(MpvNativeLoader.MPV_LIBRARY_PATH_PROPERTY)
            return if (!explicitPath.isNullOrBlank()) {
                Native.load(explicitPath, MpvJnaBindings::class.java)
            } else {
                Native.load("mpv", MpvJnaBindings::class.java)
            }
        }
    }
}

fun interface MpvWakeupCallback : Callback {
    fun invoke(data: Pointer?)
}
