package now.link.periphyllidae.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvNativeLoaderJnaPathTest {
    @Test
    fun appendJnaLibraryPathAddsDirectory() {
        val result = MpvNativeLoader.appendJnaLibraryPath("/tmp/lib", existing = "")
        assertEquals("/tmp/lib", result)
    }
}
