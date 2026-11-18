package now.link.periphyllidae

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-1: every single-target `expect`/`actual` pair has been folded away, so `commonMain` declares
 * no `expect` declarations and `jvmMain` declares no `actual` modifiers, and the commonMain files
 * that only existed to host `expect` shims are deleted.
 */
class ExpectActualFoldingTest {

    @Test
    fun commonMainHasNoExpectDeclarationsAndJvmMainHasNoActualModifiers() {
        val repoRoot = repoRoot()

        val commonMainFiles = kotlinFiles(File(repoRoot, "composeApp/src/commonMain"))
        val jvmMainFiles = kotlinFiles(File(repoRoot, "composeApp/src/jvmMain"))

        val expectViolations = declarationViolations(commonMainFiles, repoRoot, EXPECT_DECLARATION)
        assertTrue(
            expectViolations.isEmpty(),
            "composeApp/src/commonMain still contains expect declarations:\n" + expectViolations.joinToString("\n"),
        )

        val actualViolations = declarationViolations(jvmMainFiles, repoRoot, ACTUAL_DECLARATION)
        assertTrue(
            actualViolations.isEmpty(),
            "composeApp/src/jvmMain still contains actual modifiers:\n" + actualViolations.joinToString("\n"),
        )

        // Files that only existed to host the removed expect declarations must be gone.
        val deletedFiles = listOf(
            "composeApp/src/commonMain/kotlin/now/link/periphyllidae/di/CommonModule.kt",
            "composeApp/src/commonMain/kotlin/now/link/periphyllidae/DefaultComponentContext.kt",
            "composeApp/src/commonMain/kotlin/now/link/periphyllidae/ui/components/DesktopToolbarContent.kt",
            "composeApp/src/commonMain/kotlin/now/link/periphyllidae/platform/SystemTrayManager.kt",
            "composeApp/src/jvmMain/kotlin/now/link/periphyllidae/ui/screens/MouseDragScrollable.jvm.kt",
        )
        val stillPresent = deletedFiles.filter { File(repoRoot, it).exists() }
        assertTrue(
            stillPresent.isEmpty(),
            "Files holding folded expect/actual declarations must be deleted:\n" + stillPresent.joinToString("\n"),
        )
    }

    /** Collects every Kotlin source under [root], rejecting a missing/empty source set. */
    private fun kotlinFiles(root: File): List<File> {
        assertTrue(root.isDirectory, "Expected source set directory to exist: ${root.path}")
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue(files.isNotEmpty(), "Expected Kotlin sources under ${root.path}")
        return files
    }

    /** Reports `path:line -> line text` for each comment-free match of [declaration]. */
    private fun declarationViolations(files: List<File>, repoRoot: File, declaration: Regex): List<String> =
        files.flatMap { file ->
            val code = file.readText().withoutComments()
            declarations(code, declaration).map { (line, snippet) ->
                "${file.relativeTo(repoRoot).invariantSeparatorsPath}:$line -> $snippet"
            }
        }

    private fun declarations(code: String, declaration: Regex): List<Pair<Int, String>> {
        val lines = code.lines()
        return declaration.findAll(code).map { match ->
            val line = code.take(match.range.first).count { it == '\n' } + 1
            line to lines[line - 1].trim()
        }.toList()
    }

    /** Blanks out comments while preserving line numbering. */
    private fun String.withoutComments(): String =
        replace(BLOCK_COMMENT) { comment -> comment.value.replace(NON_NEWLINE, " ") }
            .replace(LINE_COMMENT, " ")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root containing settings.gradle.kts from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val EXPECT_DECLARATION = Regex("""\bexpect\s+(?:fun|val|var|class|interface|object)\b""")
        val ACTUAL_DECLARATION = Regex("""\bactual\s+(?:fun|val|var|class|interface|object)\b""")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val NON_NEWLINE = Regex("""[^\n]""")
    }
}
