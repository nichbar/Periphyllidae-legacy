package now.link.periphyllidae.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-1: after dropping Android support, no build configuration may declare Android Gradle
 * plugins/targets/properties, Android-only libraries, or MediaMP build/submodule inclusion,
 * while the Kotlin Multiplatform `jvm()` target and `compose.desktop` packaging stay configured.
 */
class AndroidRemovalBuildConfigTest {

    private val buildConfigFiles = listOf(
        "settings.gradle.kts",
        "build.gradle.kts",
        "composeApp/build.gradle.kts",
        "gradle/libs.versions.toml",
        "gradle.properties",
    )

    /** Tokens that only exist because of the Android target, Android SDK, or AGP. */
    private val forbiddenTokens = listOf(
        // Android Gradle plugins
        "androidApplication",
        "androidLibrary",
        "com.android.",
        // Android target
        "androidTarget",
        // Android-only libraries
        "androidx-media",
        "androidx-core-ktx",
        "androidx-activity-compose",
        "androidx-appcompat",
        "androidx-espresso-core",
        "androidx-testExt-junit",
        // MediaMP artifact aliases
        "mediamp-",
        // MediaMP submodule inclusion
        "includeBuild(\"mediamp\")",
        "includeBuild('mediamp')",
    )

    @Test
    fun noAndroidOrMediampBuildConfigurationRemains() {
        val repoRoot = repoRoot()

        val files = buildConfigFiles.associateWith { relativePath ->
            File(repoRoot, relativePath).also { file ->
                assertTrue(file.isFile, "Expected build config file to exist: ${file.path}")
            }
        }

        val violations = mutableListOf<String>()
        files.forEach { (relativePath, file) ->
            val content = file.readText()
            forbiddenTokens.forEach { token ->
                if (content.contains(token)) {
                    violations += "$relativePath still contains `$token`"
                }
            }
            if (ANDROID_BLOCK.containsMatchIn(content)) {
                violations += "$relativePath still contains an `android { ... }` block"
            }
        }

        // `android.*` Gradle properties must be gone from gradle.properties.
        val gradleProperties = files.getValue("gradle.properties").readText()
        listOf("android.nonTransitiveRClass", "android.useAndroidX").forEach { property ->
            if (gradleProperties.contains(property)) {
                violations += "gradle.properties still contains `$property`"
            }
        }

        // The mediamp git submodule must not be declared anymore.
        val gitmodules = File(repoRoot, ".gitmodules")
        if (gitmodules.isFile) {
            if (gitmodules.readText().contains("mediamp")) {
                violations += ".gitmodules still declares the `mediamp` submodule"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Android/MediaMP build configuration still present:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun kotlinMultiplatformJvmTargetAndComposeDesktopRemain() {
        val composeAppBuild = File(repoRoot(), "composeApp/build.gradle.kts")
        assertTrue(composeAppBuild.isFile, "Expected ${composeAppBuild.path} to exist")

        val content = composeAppBuild.readText()
        listOf("kotlinMultiplatform", "jvm()", "compose.desktop").forEach { retained ->
            assertTrue(
                content.contains(retained),
                "composeApp/build.gradle.kts must retain `$retained`",
            )
        }
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
        /** Matches a Gradle `android { ... }` configuration block (not `androidx`/`com.android.*`). */
        val ANDROID_BLOCK = Regex("""(^|\n)\s*android\s*\{""")
    }
}
