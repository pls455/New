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
import java.util.concurrent.Executors

class PlayerController(context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MusicService::class.java))
        ).buildAsync()

    private var controller: MediaController? = null

    init {
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get().also { it.addListener(playerListener) }
                notifyState()
                notifyModes()
            }
        }, executor)
    }

    interface Listener {
        fun onPlaybackStateChanged(
            isPlaying: Boolean,
            positionMs: Long,
            durationMs: Long,
            currentIndex: Int
        )
        fun onModeChanged(shuffleEnabled: Boolean, repeatMode: Int)
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

    fun addListener(listener: Listener) {
        listeners += listener
        executor.execute {
            notifyState()
            notifyModes()
        }
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun playTrack(uris: List<Uri>, index: Int) = withController { c ->
        c.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, 0L)
        c.prepare()
        c.play()
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNextMediaItem(); it.play() }
    fun previous() = withController { it.seekToPreviousMediaItem(); it.play() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }
    fun setRepeatMode(mode: Int) = withController { it.repeatMode = mode }

    fun release() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        listeners.clear()
        executor.shutdown()
    }

    private fun withController(action: (MediaController) -> Unit) {
        controllerFuture.addListener({
            runCatching { action(controller ?: controllerFuture.get()) }
        }, executor)
    }
}
