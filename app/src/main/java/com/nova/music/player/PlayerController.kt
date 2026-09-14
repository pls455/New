package com.nova.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CopyOnWriteArraySet

/**
 * UI-facing controller for Media3.
 * MediaController APIs are main-thread APIs, so every controller call is dispatched
 * through Context.getMainExecutor(). This avoids Media3's wrong-thread exception.
 */
class PlayerController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = appContext.mainExecutor
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        ).buildAsync()

    @Volatile
    private var controller: MediaController? = null

    init {
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get().also { it.addListener(playerListener) }
                notifyState()
                notifyModes()
            }.onFailure { notifyError(it) }
        }, mainExecutor)
    }

    interface Listener {
        fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long, durationMs: Long, currentIndex: Int)
        fun onModeChanged(shuffleEnabled: Boolean, repeatMode: Int)
        fun onPlayerError(message: String) {}
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = notifyState()
        override fun onPlaybackStateChanged(playbackState: Int) = notifyState()
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = notifyState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = notifyState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = notifyModes()
        override fun onRepeatModeChanged(repeatMode: Int) = notifyModes()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = notifyError(error)
    }

    private fun notifyState() {
        val c = controller ?: return
        val duration = c.duration.coerceAtLeast(0L)
        listeners.forEach {
            it.onPlaybackStateChanged(
                c.isPlaying,
                c.currentPosition.coerceAtLeast(0L),
                duration,
                c.currentMediaItemIndex
            )
        }
    }

    private fun notifyModes() {
        val c = controller ?: return
        listeners.forEach { it.onModeChanged(c.shuffleModeEnabled, c.repeatMode) }
    }

    private fun notifyError(error: Throwable) {
        listeners.forEach { it.onPlayerError(error.message ?: "Playback error") }
    }

    fun addListener(listener: Listener) {
        listeners += listener
        mainExecutor.execute {
            notifyState()
            notifyModes()
        }
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun refreshState() {
        mainExecutor.execute { notifyState() }
    }

    fun playTrack(uris: List<Uri>, index: Int) = withController { c ->
        if (uris.isEmpty()) return@withController
        val safeIndex = index.coerceIn(0, uris.lastIndex)
        c.setMediaItems(uris.map { MediaItem.fromUri(it) }, safeIndex, 0L)
        c.prepare()
        c.playWhenReady = true
        c.play()
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun togglePlayPause() = withController { controller ->
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() = withController { controller ->
        if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
        else if (controller.mediaItemCount > 0) controller.seekTo(0)
        controller.play()
    }

    fun previous() = withController { controller ->
        if (controller.currentPosition > 3000L || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0)
        } else {
            controller.seekToPreviousMediaItem()
        }
        controller.play()
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }
    fun setRepeatMode(mode: Int) = withController {
        it.repeatMode = mode.coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
    }

    fun release() {
        mainExecutor.execute {
            controller?.removeListener(playerListener)
            controller = null
            MediaController.releaseFuture(controllerFuture)
            listeners.clear()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controllerFuture.addListener({
            runCatching { action(controller ?: controllerFuture.get()) }
                .onFailure { notifyError(it) }
        }, mainExecutor)
    }
}
