package now.link.periphyllidae.platform

import java.io.File

object MpvNativeLoader {
    @Volatile
    private var loaded: Boolean = false

    internal const val MPV_LIBRARY_PATH_PROPERTY = "periphyllidae.mpv.library.path"

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

    internal fun searchRootPaths(baseDir: String, resourcesDir: String? = null): List<String> {
        val roots = mutableListOf(
            "$baseDir/composeApp/appResources/mpv",
            "$baseDir/appResources/mpv",
        )

        if (!resourcesDir.isNullOrBlank()) {
            roots += "$resourcesDir/mpv"
            roots += resourcesDir
        }

        return roots.distinct()
    }

    internal fun appendJnaLibraryPath(path: String, existing: String): String = when {
        existing.isBlank() -> path
        existing.split(File.pathSeparator).contains(path) -> existing
        else -> "$path${File.pathSeparator}$existing"
    }

    fun load() {
        if (loaded) return

        val subdir = resolveResourceSubdir(System.getProperty("os.name"), System.getProperty("os.arch"))
        val candidateNames = listOf("libmpv.dylib", "libmpv.2.dylib", "libmpv.so", "libmpv.so.2", "mpv-2.dll", "libmpv.dll")

        val baseDir = System.getProperty("user.dir")
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val searchRoots = searchRootPaths(baseDir, resourcesDir)
        val externalCandidate = searchRoots
            .asSequence()
            .flatMap { root ->
                candidateNames.asSequence().flatMap { name ->
                    sequenceOf(File(root, "$subdir/$name"), File(root, name))
                }
            }
            .firstOrNull { it.exists() }

        if (externalCandidate != null) {
            val parentDir = externalCandidate.parentFile.absolutePath
            val currentPath = System.getProperty("jna.library.path", "")
            System.setProperty("jna.library.path", appendJnaLibraryPath(parentDir, currentPath))
            System.setProperty(MPV_LIBRARY_PATH_PROPERTY, externalCandidate.absolutePath)
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
            System.setProperty(MPV_LIBRARY_PATH_PROPERTY, resourceCandidate.absolutePath)
            System.load(resourceCandidate.absolutePath)
            loaded = true
            return
        }

        throw IllegalStateException("Unable to locate libmpv for $subdir")
    }
}

