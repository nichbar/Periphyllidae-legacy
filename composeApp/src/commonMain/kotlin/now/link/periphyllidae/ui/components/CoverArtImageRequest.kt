package now.link.periphyllidae.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import now.link.periphyllidae.utils.buildCoverArtImageRequest

@Composable
fun rememberCoverArtImageRequest(
    coverArtUrl: String?,
    coverArtId: String?
): ImageRequest? {
    val context = LocalPlatformContext.current
    return remember(context, coverArtUrl, coverArtId) {
        buildCoverArtImageRequest(
            context = context,
            coverArtUrl = coverArtUrl,
            coverArtId = coverArtId
        )
    }
}

@Composable
fun CoverArtImage(
    coverArtUrl: String?,
    coverArtId: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    AsyncImage(
        model = rememberCoverArtImageRequest(
            coverArtUrl = coverArtUrl,
            coverArtId = coverArtId
        ),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
