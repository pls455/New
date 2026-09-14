package com.nova.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class PlayerController(context: Context) {
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MusicService::class.java))
        ).buildAsync()

    fun setQueue(uris: List<Uri>) {
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            controller.setMediaItems(uris.map { MediaItem.fromUri(it) })
            controller.prepare()
        }, { it.run() })
    }

    fun play() = controllerFuture.addListener({ controllerFuture.get().play() }, { it.run() })
    fun pause() = controllerFuture.addListener({ controllerFuture.get().pause() }, { it.run() })
    fun release() = MediaController.releaseFuture(controllerFuture)
}
