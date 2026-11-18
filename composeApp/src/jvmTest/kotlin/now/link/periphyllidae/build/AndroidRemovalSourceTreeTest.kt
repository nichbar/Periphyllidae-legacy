package now.link.periphyllidae.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AC-2: the Android source set, the MediaMP submodule, `MediampPlaybackEngine.kt` and every
 * `isAndroid` branch in `commonMain` are gone after dropping Android support.
 */
class AndroidRemovalSourceTreeTest {

    @Test
    fun noAndroidMainMediampOrIsAndroidBranchesRemain() {
        val repoRoot = repoRoot()

        assertFalse(
            File(repoRoot, "composeApp/src/androidMain").exists(),
            "composeApp/src/androidMain must not exist",
        )
        assertFalse(
            File(repoRoot, "mediamp").exists(),
            "mediamp/ submodule directory must not exist",
        )
        assertFalse(
            File(
                repoRoot,
                "composeApp/src/commonMain/kotlin/now/link/periphyllidae/platform/MediampPlaybackEngine.kt",
            ).exists(),
            "MediampPlaybackEngine.kt must not exist",
        )

        val commonMainRoot = File(repoRoot, "composeApp/src/commonMain")
        assertTrue(commonMainRoot.isDirectory, "Expected ${commonMainRoot.path} to exist")

        val kotlinFiles = commonMainRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue(kotlinFiles.isNotEmpty(), "Expected Kotlin sources under ${commonMainRoot.path}")

        val forbiddenTokens = listOf("isAndroid", "getPlatform().isAndroid", "MediampPlaybackEngine")
        val violations = kotlinFiles.mapNotNull { file ->
            val content = file.readText()
            val hits = forbiddenTokens.filter { content.contains(it) }
            if (hits.isEmpty()) null else "${file.relativeTo(repoRoot).invariantSeparatorsPath} -> ${hits.joinToString()}"
        }
        assertTrue(
            violations.isEmpty(),
            "commonMain still references Android/MediaMP:\n" + violations.joinToString("\n"),
        )

        // The two layout/playback entry points must not branch on platform anymore.
        val app = File(commonMainRoot, "kotlin/now/link/periphyllidae/App.kt")
        val musicPlayer = File(commonMainRoot, "kotlin/now/link/periphyllidae/platform/MusicPlayer.kt")
        assertTrue(app.isFile, "Expected ${app.path} to exist")
        assertTrue(musicPlayer.isFile, "Expected ${musicPlayer.path} to exist")

        val branchingViolations = listOf("isAndroid", "isDesktop").flatMap { token ->
            listOf(app, musicPlayer)
                .filter { it.readText().contains(token) }
                .map { "${it.name} still branches on `$token`" }
        }
        assertTrue(
            branchingViolations.isEmpty(),
            "Platform branches still present:\n" + branchingViolations.joinToString("\n"),
        )
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root containing settings.gradle.kts from ${System.getProperty("user.dir")}")
    }
}
