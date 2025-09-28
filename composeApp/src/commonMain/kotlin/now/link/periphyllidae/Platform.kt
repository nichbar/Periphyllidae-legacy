package now.link.periphyllidae

interface Platform {
    val name: String

    val isAndroid: Boolean
        get() = name.contains("Android")
}

expect fun getPlatform(): Platform