package com.nova.music.ui

import android.content.ContentResolver
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private suspend fun loadAlbumArt(resolver: ContentResolver, albumId: Long) = withContext(Dispatchers.IO) {
    if (albumId <= 0L) return@withContext null
    val uri = "content://media/external/audio/albumart/$albumId".toUri()
    runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
}

@Composable
fun NovaAlbumArt(
    albumId: Long,
    modifier: Modifier = Modifier,
    size: Dp,
    rounded: Dp = 18.dp
) {
    val resolver = LocalContext.current.contentResolver
    val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, albumId) {
        value = loadAlbumArt(resolver, albumId)
    }.value

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(rounded))
            .background(Brush.linearGradient(listOf(Color(0xFF332F62), Color(0xFF151722)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = "Album artwork", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color(0xFFB8AEFF))
        }
    }
}
