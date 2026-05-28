package com.airecorder.android.util

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerManager @Inject constructor(
    private val player: ExoPlayer
) {
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updateState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) {
                    startProgressUpdate()
                } else {
                    stopProgressUpdate()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playbackState.value = PlaybackState.Error(error.message ?: "Unknown playback error")
            }
        })
    }

    private fun updateState() {
        val state = when (player.playbackState) {
            Player.STATE_IDLE -> PlaybackState.Idle
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> {
                if (player.isPlaying) {
                    PlaybackState.Playing(player.currentPosition, player.duration)
                } else {
                    PlaybackState.Paused(player.currentPosition, player.duration)
                }
            }
            Player.STATE_ENDED -> PlaybackState.Completed(player.duration)
            else -> PlaybackState.Idle
        }
        _playbackState.value = state
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                if (player.isPlaying) {
                    updateState()
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    fun play(file: File) {
        val mediaItem = MediaItem.fromUri(file.absolutePath)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updateState()
    }

    fun rewind(ms: Long = 10000) {
        player.seekTo((player.currentPosition - ms).coerceAtLeast(0))
        updateState()
    }

    fun forward(ms: Long = 10000) {
        player.seekTo((player.currentPosition + ms).coerceAtMost(player.duration))
        updateState()
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        updateState()
        stopProgressUpdate()
    }

    fun release() {
        stopProgressUpdate()
        player.release()
    }

    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Buffering : PlaybackState()
        data class Playing(val positionMs: Long, val durationMs: Long) : PlaybackState()
        data class Paused(val positionMs: Long, val durationMs: Long) : PlaybackState()
        data class Completed(val durationMs: Long) : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }
}
