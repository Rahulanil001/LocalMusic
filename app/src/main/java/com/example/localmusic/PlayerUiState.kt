package com.example.localmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Stable
class PlayerUiState(private val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var currentMediaId by mutableStateOf(player.currentMediaItem?.mediaId)
    var shuffle by mutableStateOf(player.shuffleModeEnabled)
    var repeatMode by mutableIntStateOf(player.repeatMode)
    var position by mutableLongStateOf(player.currentPosition)
    var duration by mutableLongStateOf(player.duration.coerceAtLeast(0L))

    fun sync() {
        position = player.currentPosition
        duration = player.duration.coerceAtLeast(0L)
    }
}

/** Keeps Compose state in sync with the player running in the service. */
@Composable
fun rememberPlayerUiState(player: Player): PlayerUiState {
    val state = remember(player) { PlayerUiState(player) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                state.currentMediaId = mediaItem?.mediaId
                state.sync()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.sync()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                state.shuffle = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                state.repeatMode = repeatMode
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Update the seek bar twice a second while music is playing
    LaunchedEffect(player, state.isPlaying) {
        state.sync()
        while (state.isPlaying) {
            delay(500)
            state.sync()
        }
    }
    return state
}
