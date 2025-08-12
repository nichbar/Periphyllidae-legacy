package now.link.periphyllidae

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform