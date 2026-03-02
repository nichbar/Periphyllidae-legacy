package now.link.periphyllidae.platform

import java.io.File

object MpvNativeLoader {
    @Volatile
    private var loaded: Boolean = false

    fun resolveResourceSubdir(osName: String, arch: String): String {
        val normalizedOs = osName.lowercase()
        val normalizedArch = arch.lowercase()

        val osToken = when {
            normalizedOs.contains("mac") -> "macos"
            normalizedOs.contains("win") -> "windows"
            normalizedOs.contains("linux") -> "linux"
            else -> error("Unsupported OS: $osName")
        }

        val archToken = when {
            normalizedArch.contains("aarch64") || normalizedArch.contains("arm64") -> "arm64"
            normalizedArch.contains("x86_64") || normalizedArch.contains("amd64") -> "x64"
            else -> error("Unsupported architecture: $arch")
        }

        return "$osToken-$archToken"
    }

    internal fun searchRootPaths(baseDir: String): List<String> = listOf(
        "$baseDir/composeApp/appResources/mpv",
        "$baseDir/appResources/mpv",
    )

    internal fun appendJnaLibraryPath(path: String, existing: String): String = when {
        existing.isBlank() -> path
        existing.split(File.pathSeparator).contains(path) -> existing
        else -> "$path${File.pathSeparator}$existing"
    }

    fun load() {
        if (loaded) return

        val subdir = resolveResourceSubdir(System.getProperty("os.name"), System.getProperty("os.arch"))
        val candidateNames = listOf("libmpv.dylib", "libmpv.so", "mpv-2.dll", "libmpv.dll")

        val baseDir = System.getProperty("user.dir")
        val searchRoots = searchRootPaths(baseDir)
        val externalCandidate = searchRoots
            .asSequence()
            .flatMap { root -> candidateNames.asSequence().map { name -> File(root, "$subdir/$name") } }
            .firstOrNull { it.exists() }

        if (externalCandidate != null) {
            val parentDir = externalCandidate.parentFile.absolutePath
            val currentPath = System.getProperty("jna.library.path", "")
            System.setProperty("jna.library.path", appendJnaLibraryPath(parentDir, currentPath))
            System.load(externalCandidate.absolutePath)
            loaded = true
            return
        }

        val resourceCandidate = candidateNames
            .asSequence()
            .mapNotNull { name ->
                MpvNativeLoader::class.java.getResourceAsStream("/mpv/$subdir/$name")?.use { stream ->
                    val file = kotlin.io.path.createTempFile("libmpv-", "-$name").toFile()
                    file.deleteOnExit()
                    file.outputStream().use { output -> stream.copyTo(output) }
                    file
                }
            }
            .firstOrNull()

        if (resourceCandidate != null) {
            val parentDir = resourceCandidate.parentFile.absolutePath
            val currentPath = System.getProperty("jna.library.path", "")
            System.setProperty("jna.library.path", appendJnaLibraryPath(parentDir, currentPath))
            System.load(resourceCandidate.absolutePath)
            loaded = true
            return
        }

        throw IllegalStateException("Unable to locate libmpv for $subdir")
    }
}
