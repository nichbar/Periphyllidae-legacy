package now.link.periphyllidae.utils

import coil3.PlatformContext
import coil3.request.ImageRequest
import io.ktor.http.Url

private const val COVER_ART_CACHE_KEY_PREFIX = "cover-art"

fun buildCoverArtImageRequest(
    context: PlatformContext,
    coverArtUrl: String?,
    coverArtId: String?
): ImageRequest? {
    if (coverArtUrl == null) return null

    val cacheKey = coverArtId
        ?.takeIf { it.isNotBlank() }
        ?.let { "$COVER_ART_CACHE_KEY_PREFIX:${coverArtUrl.originCacheKey()}:$it" }

    return ImageRequest.Builder(context)
        .data(coverArtUrl)
        .apply {
            if (cacheKey != null) {
                diskCacheKey(cacheKey)
            }
        }
        .build()
}

private fun String.originCacheKey(): String {
    return runCatching {
        val url = Url(this)
        val defaultPort = url.protocol.defaultPort
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        "${url.protocol.name}://${url.host}$port"
    }.getOrDefault("unknown-origin")
}
