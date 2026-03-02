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

    @Test
    fun searchRootsIncludePackagedResourcesDirVariants() {
        val roots = MpvNativeLoader.searchRootPaths(
            baseDir = "/tmp/project",
            resourcesDir = "/Applications/Periphyllidae.app/Contents/app/resources",
        )

        assertEquals(
            listOf(
                "/tmp/project/composeApp/appResources/mpv",
                "/tmp/project/appResources/mpv",
                "/Applications/Periphyllidae.app/Contents/app/resources/mpv",
                "/Applications/Periphyllidae.app/Contents/app/resources",
            ),
            roots,
        )
    }
}
