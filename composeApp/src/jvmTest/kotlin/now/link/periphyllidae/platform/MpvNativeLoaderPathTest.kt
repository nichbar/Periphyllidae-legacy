package now.link.periphyllidae.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvNativeLoaderPathTest {
    @Test
    fun searchRootsIncludeProjectAndComposeAppVariants() {
        val roots = MpvNativeLoader.searchRootPaths("/tmp/project")

        assertEquals(
            listOf(
                "/tmp/project/composeApp/appResources/mpv",
                "/tmp/project/appResources/mpv",
            ),
            roots,
        )
    }
}
