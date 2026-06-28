package com.ella.music.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun SafeCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sizePx: Int = 1200,
    showDefaultPlaceholder: Boolean = true
) {
    val context = LocalContext.current
    val request = remember(context, model, sizePx) {
        if (model is Uri || model is String) {
            ImageRequest.Builder(context)
                .data(model)
                .size(sizePx)
                .build()
        } else {
            model
        }
    }

    Box(modifier = modifier) {
        if (showDefaultPlaceholder) {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}
