package chat.stoat.composables.dj

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import chat.stoat.api.dj.Dj
import chat.stoat.api.dj.DjState
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** How far the local player may drift from the server's position before it is re-seeked */
private const val MAX_DRIFT_MS = 500L

/** How often the local player is compared against the server's clock and the UI is refreshed */
private const val SYNC_INTERVAL_MS = 500L

/** Playback state of the channel-scoped [ExoPlayer], exposed to the DJ panel */
class DjPlayback {
    var isPlaying by mutableStateOf(false)
        internal set
    var isBuffering by mutableStateOf(false)
        internal set
    var positionMs by mutableLongStateOf(0L)
        internal set
    var durationMs by mutableLongStateOf(0L)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
}

/**
 * Channel-scoped DJ player.
 *
 * Foreground only: there is no `MediaSessionService` and no `mediaPlayback` foreground service, so
 * playback stops as soon as the channel screen leaves composition. The player is unlocked and
 * prepared by the Join button (a user gesture), then kept honest with the server clock: the
 * `position_ms` + `server_time` of the state (from `TrackStarted`, the 15 s `NowPlaying` heartbeat,
 * or a `GET /dj` snapshot) is advanced locally, and the local player is re-seeked when it drifts by
 * more than [MAX_DRIFT_MS].
 *
 * @param active whether this client is a joined member of the channel; nothing is prepared if not
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberDjPlayback(
    channelId: String,
    state: DjState?,
    active: Boolean,
): DjPlayback {
    val context = LocalContext.current
    val playback = remember(channelId) { DjPlayback() }
    var reload by remember(channelId) { mutableIntStateOf(0) }

    val player = remember(channelId) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                playback.isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playback.isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                playback.error = error.errorCodeName
                // A signed stream URL may have expired or the connection dropped: drop the prepared
                // item so the next snapshot (or the refresh below) prepares it again.
                player.stop()
                player.clearMediaItems()
                reload += 1
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val latestState by rememberUpdatedState(state)
    val latestActive by rememberUpdatedState(active)
    val trackId = state?.current?.track?.id
    val streamUrl = state?.streamUrl

    // Prepare the current track. Re-runs when the track changes, on an error retry, or when joining.
    LaunchedEffect(channelId, trackId, streamUrl, active, reload) {
        if (!active || trackId == null || streamUrl.isNullOrBlank()) {
            player.stop()
            player.clearMediaItems()
            playback.positionMs = 0
            playback.durationMs = 0
            playback.error = null
            return@LaunchedEffect
        }

        if (player.currentMediaItem?.mediaId != trackId) {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(trackId)
                    .build()
            )
            player.prepare()
            player.seekTo(latestState?.positionMs() ?: 0L)
            player.playWhenReady = true
            playback.error = null
        }
    }

    // A failed playback refreshes the snapshot, which carries a freshly signed URL; the error is
    // cleared once an item prepares successfully again.
    LaunchedEffect(playback.error) {
        if (playback.error == null) return@LaunchedEffect

        if (active) {
            delay(1_000)
            runCatching { Dj.refresh(channelId) }
        }
    }

    // Follow the server clock: refresh the progress readout and correct drift.
    LaunchedEffect(channelId, trackId, active) {
        while (isActive) {
            val current = latestState

            if (!latestActive || current?.current == null) {
                playback.positionMs = 0
                playback.durationMs = 0
                delay(SYNC_INTERVAL_MS)
                continue
            }

            val expected = current.positionMs()
            playback.durationMs = current.durationMs

            if (player.currentMediaItem?.mediaId == current.current.track.id) {
                if (player.isPlaying) {
                    if (abs(player.currentPosition - expected) > MAX_DRIFT_MS) {
                        player.seekTo(expected)
                    }

                    playback.positionMs = player.currentPosition.coerceAtLeast(0)
                } else {
                    playback.positionMs = expected
                }
            } else {
                playback.positionMs = expected
            }

            delay(SYNC_INTERVAL_MS)
        }
    }

    return playback
}
