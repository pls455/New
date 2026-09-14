package com.nova.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

class PlayerController(context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MusicService::class.java))
        ).buildAsync()

    fun playTrack(uris: List<Uri>, index: Int) {
        controllerFuture.addListener({
            runCatching {
                val controller = controllerFuture.get()
                controller.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, 0L)
                controller.prepare()
                controller.play()
            }
        }, executor)
    }

    fun play() = controllerFuture.addListener({ runCatching { controllerFuture.get().play() } }, executor)
    fun pause() = controllerFuture.addListener({ runCatching { controllerFuture.get().pause() } }, executor)
    fun release() {
        MediaController.releaseFuture(controllerFuture)
        executor.shutdown()
    }
}
