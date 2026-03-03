package now.link.periphyllidae.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class MpvNativeLoaderTest {
    @Test
    fun resolvesDesktopTargetFolderFromOsAndArch() {
        val path = MpvNativeLoader.resolveResourceSubdir(osName = "Mac OS X", arch = "aarch64")
        assertTrue(path.contains("macos"))
    }
}
