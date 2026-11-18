package now.link.periphyllidae

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-2: `README.md` and `AGENTS.md` document desktop JVM support only, with no Android, MediaMP or
 * ExoPlayer references left behind.
 */
class DocsAndroidRemovalTest {

    @Test
    fun readmeAndAgentsMdContainNoAndroidOrMediampReferences() {
        val repoRoot = repoRoot()

        val docs = listOf("README.md", "AGENTS.md").associateWith { relativePath ->
            File(repoRoot, relativePath).also { file ->
                assertTrue(file.isFile, "Expected documentation file to exist: ${file.path}")
            }
        }

        val violations = docs.flatMap { (relativePath, file) ->
            val content = file.readText()
            forbiddenTokens.filter { token ->
                if (token == "Android") {
                    content.contains(token, ignoreCase = true)
                } else {
                    content.contains(token)
                }
            }.map { token -> "$relativePath still documents `$token`" }
        }

        assertTrue(
            violations.isEmpty(),
            "Android/MediaMP documentation still present:\n" + violations.joinToString("\n"),
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

    private companion object {
        val forbiddenTokens = listOf(
            "Android",
            "androidMain",
            "PlatformModule.android.kt",
            "ExoPlayer",
            "MediaMP",
            "mediamp",
            "compileDebugKotlinAndroid",
            "assembleDebug",
            "minSdk",
        )
    }
}
