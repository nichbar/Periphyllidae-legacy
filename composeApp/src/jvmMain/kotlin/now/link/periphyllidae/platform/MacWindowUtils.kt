package now.link.periphyllidae.platform

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import now.link.periphyllidae.utils.LogManager

/**
 * Ensures the native NSWindow shadow is enabled on macOS.
 *
 * For transparent undecorated windows, the macOS shadow follows the alpha channel
 * of the window content. With rounded corners in the Compose content, this gives
 * a properly rounded shadow — unlike Material elevation shadows which render as
 * solid rectangles on transparent Compose Desktop surfaces.
 */
object MacWindowUtils {

    private const val TAG = "MacWindowUtils"

    private val objcLib by lazy {
        if (!System.getProperty("os.name").lowercase().contains("mac")) {
            null
        } else {
            try {
                NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib")
            } catch (e: Exception) {
                LogManager.w(TAG, "Failed to load Objective-C runtime", e)
                null
            }
        }
    }

    private val selSetHasShadow: Pointer? by lazy {
        try {
            val fn = objcLib?.getFunction("sel_registerName") ?: return@lazy null
            fn.invoke(Pointer::class.java, arrayOf("setHasShadow:")) as? Pointer
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to register setHasShadow: selector", e)
            null
        }
    }

    private val msgSend: Function? by lazy {
        try {
            objcLib?.getFunction("objc_msgSend")
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to get objc_msgSend", e)
            null
        }
    }

    fun ensureWindowShadow(window: java.awt.Window) {
        val sel = selSetHasShadow ?: return
        val send = msgSend ?: return

        try {
            val nsWindowPtr = getNSWindowPointer(window) ?: return
            // [NSWindow setHasShadow:YES]
            send.invoke(Void.TYPE, arrayOf(Pointer(nsWindowPtr), sel, 1.toByte()))
            LogManager.d(TAG, "Native window shadow enabled")
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to enable window shadow", e)
        }
    }

    private fun getNSWindowPointer(window: java.awt.Window): Long? {
        return try {
            val peerField = java.awt.Component::class.java.getDeclaredField("peer").apply {
                isAccessible = true
            }
            val peer = peerField.get(window)
            val platformWindow = peer.javaClass.getMethod("getPlatformWindow").invoke(peer)
            val nsWindowPtr = platformWindow.javaClass.getMethod("getNSWindowPtr").invoke(platformWindow)
            nsWindowPtr as? Long
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to get NSWindow pointer", e)
            null
        }
    }
}
