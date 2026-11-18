package now.link.periphyllidae

class JVMPlatform: Platform {
    override val name: String = when {
        System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "macOS"
        System.getProperty("os.name").contains("Linux", ignoreCase = true) -> "Linux"
        System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "Windows"
        else -> "Java ${System.getProperty("java.version")}"
    }
}

fun getPlatform(): Platform = JVMPlatform()