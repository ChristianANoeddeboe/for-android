package chat.stoat.api.dj

import android.os.SystemClock
import androidx.compose.runtime.mutableStateMapOf
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.channel.djFetch
import chat.stoat.core.model.schemas.DjChannelState
import chat.stoat.core.model.schemas.DjInformation
import chat.stoat.core.model.schemas.DjNowPlaying
import chat.stoat.core.model.schemas.DjTrack
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Client-side state of a DJ channel: settings, membership, now playing, queue and skip votes.
 *
 * This mirrors the backend's `GET /channels/{id}/dj` snapshot, then follows the DJ events which the
 * backend publishes to joined members. It is analogous to `StoatAPI.voiceStateCache`: compose reads
 * it directly from [channels], so panels recompose when the state changes.
 */
data class DjState(
    val channelId: String,
    val settings: DjInformation = DjInformation(),
    val current: DjNowPlaying? = null,
    val queue: List<DjTrack> = emptyList(),
    val votes: Int = 0,
    /** Votes needed to skip, `max(1, ceil(members * skip_threshold))` */
    val needed: Int = 1,
    val voters: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    /** Signed, short-lived URL of the current track (only sent to joined members) */
    val streamUrl: String? = null,
    /** Playback position which was reported at [syncedAtElapsedMs] */
    val syncedPositionMs: Long = 0,
    /** `SystemClock.elapsedRealtime()` at which [syncedPositionMs] was measured */
    val syncedAtElapsedMs: Long = 0,
    val lastUploadError: String? = null,
) {
    val memberCount: Int
        get() = members.size

    val durationMs: Long
        get() = current?.track?.durationMs ?: 0

    fun isMember(userId: String?): Boolean = userId != null && members.contains(userId)

    fun hasVoted(userId: String?): Boolean = userId != null && voters.contains(userId)

    /** The current track's playback position, advanced with the monotonic clock since the sync */
    fun positionMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        if (current == null) return 0
        // Membership-only states (from `Ready.dj_states`) have no position yet
        if (syncedAtElapsedMs <= 0) return syncedPositionMs

        val elapsed = (nowElapsedMs - syncedAtElapsedMs).coerceAtLeast(0)
        val position = syncedPositionMs + elapsed
        val duration = durationMs

        return if (duration > 0) position.coerceIn(0, duration) else position.coerceAtLeast(0)
    }

    /** Votes which are required to skip the current track */
    fun computeNeeded(): Int = neededToSkip(memberCount, settings.skipThreshold)
}

/** Mirrors the backend's skip tally: `max(1, ceil(present_members * skip_threshold))` */
fun neededToSkip(memberCount: Int, skipThreshold: Float): Int {
    return maxOf(1, ceil(memberCount * skipThreshold.toDouble()).toInt())
}

object Dj {
    /** State per channel, keyed by channel id */
    val channels = mutableStateMapOf<String, DjState>()

    private val _uploadErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Upload failures reported by the backend (`DjUploadFailed`) */
    val uploadErrors: SharedFlow<String> = _uploadErrors

    fun state(channelId: String): DjState? = channels[channelId]

    fun isJoined(channelId: String): Boolean {
        return channels[channelId]?.isMember(StoatAPI.selfId) == true
    }

    fun reset() {
        channels.clear()
    }

    /** Refresh a channel's state from the API */
    suspend fun refresh(channelId: String) {
        apply(djFetch(channelId))
    }

    /** Replace a channel's state with a REST snapshot (`GET`, `join`, `skip`, `settings`) */
    fun apply(state: DjChannelState) {
        val previous = channels[state.id]
        val sameTrack = previous?.current?.track?.id == state.current?.track?.id

        channels[state.id] = DjState(
            channelId = state.id,
            settings = state.settings,
            current = state.current,
            queue = state.queue,
            votes = state.votes,
            needed = neededToSkip(state.members.size, state.settings.skipThreshold),
            voters = state.voters,
            members = state.members,
            // The snapshot carries no stream URL; keep the one from the events for the same track
            streamUrl = previous?.streamUrl?.takeIf { sameTrack && state.current != null },
            syncedPositionMs = state.positionMs,
            syncedAtElapsedMs = SystemClock.elapsedRealtime(),
            lastUploadError = previous?.lastUploadError,
        )

        syncChannelSettings(state.id, state.settings)
    }

    /** `Ready.dj_states`: the DJ channels this user has joined */
    fun setMembership(channelId: String, members: List<String>) {
        val state = channels[channelId]
        if (state == null) {
            channels[channelId] = DjState(
                channelId = channelId,
                members = members,
                needed = neededToSkip(members.size, 0.5f),
                syncedAtElapsedMs = SystemClock.elapsedRealtime(),
            )
            return
        }

        update(channelId) {
            it.copy(
                members = members,
                needed = neededToSkip(members.size, it.settings.skipThreshold)
            )
        }
    }

    fun memberJoined(channelId: String, userId: String) {
        update(channelId) { state ->
            if (state.members.contains(userId)) {
                state
            } else {
                val members = state.members + userId
                state.copy(
                    members = members,
                    needed = neededToSkip(members.size, state.settings.skipThreshold)
                )
            }
        }
    }

    fun memberLeft(channelId: String, userId: String) {
        update(channelId) { state ->
            if (!state.members.contains(userId)) {
                state
            } else {
                val members = state.members - userId
                state.copy(
                    members = members,
                    voters = state.voters - userId,
                    needed = neededToSkip(members.size, state.settings.skipThreshold)
                )
            }
        }
    }

    fun trackQueued(channelId: String, track: DjTrack, position: Int) {
        update(channelId) { state ->
            if (state.queue.any { it.id == track.id } || state.current?.track?.id == track.id) {
                state
            } else {
                val queue = state.queue.toMutableList()
                queue.add(position.coerceIn(0, queue.size), track)
                state.copy(queue = queue)
            }
        }
    }

    fun trackStarted(
        channelId: String,
        track: DjTrack,
        positionMs: Long,
        serverTime: Long,
        signedUrl: String,
    ) {
        update(channelId) { state ->
            // started_at is unix millis; derive it from the reported position so the monotonic
            // offset below stays the single source of truth for the client's clock.
            val startedAt = if (serverTime > positionMs) serverTime - positionMs else serverTime

            state.copy(
                current = DjNowPlaying(track = track, startedAt = startedAt),
                queue = state.queue.filterNot { it.id == track.id },
                votes = 0,
                voters = emptyList(),
                needed = neededToSkip(state.members.size, state.settings.skipThreshold),
                streamUrl = signedUrl,
                syncedPositionMs = positionMs.coerceAtLeast(0),
                syncedAtElapsedMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    fun nowPlaying(
        channelId: String,
        track: DjTrack,
        positionMs: Long,
        serverTime: Long,
        signedUrl: String,
    ) {
        val state = channels[channelId]

        if (state == null || state.current?.track?.id != track.id) {
            trackStarted(channelId, track, positionMs, serverTime, signedUrl)
            return
        }

        update(channelId) {
            it.copy(
                current = it.current?.copy(track = track),
                streamUrl = signedUrl.ifBlank { it.streamUrl ?: "" }.ifBlank { null },
                syncedPositionMs = positionMs.coerceAtLeast(0),
                syncedAtElapsedMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    fun vote(channelId: String, votes: Int, needed: Int, voters: List<String>) {
        update(channelId) {
            it.copy(votes = votes, needed = maxOf(1, needed), voters = voters)
        }
    }

    fun trackEnded(channelId: String, trackId: String) {
        update(channelId) { state ->
            if (state.current?.track?.id != trackId) {
                state
            } else {
                state.copy(
                    current = null,
                    votes = 0,
                    voters = emptyList(),
                    needed = neededToSkip(state.members.size, state.settings.skipThreshold),
                    streamUrl = null,
                    syncedPositionMs = 0,
                    syncedAtElapsedMs = SystemClock.elapsedRealtime(),
                )
            }
        }
    }

    fun queueCleared(channelId: String) {
        update(channelId) {
            it.copy(
                current = null,
                queue = emptyList(),
                votes = 0,
                voters = emptyList(),
                streamUrl = null,
                syncedPositionMs = 0,
                syncedAtElapsedMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    fun settingsUpdated(channelId: String, settings: DjInformation) {
        update(channelId) { state ->
            if (settings.enabled) {
                state.copy(
                    settings = settings,
                    needed = neededToSkip(state.members.size, settings.skipThreshold)
                )
            } else {
                // Disabling stops playback and clears the queue server-side
                state.copy(
                    settings = settings,
                    current = null,
                    queue = emptyList(),
                    votes = 0,
                    voters = emptyList(),
                    streamUrl = null,
                    syncedPositionMs = 0,
                    syncedAtElapsedMs = SystemClock.elapsedRealtime(),
                )
            }
        }

        syncChannelSettings(channelId, settings)
    }

    fun uploadFailed(channelId: String, reason: String) {
        update(channelId) { it.copy(lastUploadError = reason) }
        _uploadErrors.tryEmit(reason)
    }

    /** Forget the reported upload error once it has been shown */
    fun clearUploadError(channelId: String) {
        update(channelId) { it.copy(lastUploadError = null) }
    }

    /** Keep `channel.dj` in the channel cache in sync with the DJ settings */
    private fun syncChannelSettings(channelId: String, settings: DjInformation) {
        val channel = StoatAPI.channelCache[channelId] ?: return

        if (channel.dj != settings) {
            StoatAPI.channelCache[channelId] = channel.copy(dj = settings)
        }
    }

    private inline fun update(channelId: String, transform: (DjState) -> DjState) {
        val state = channels[channelId] ?: return
        val updated = transform(state)

        if (updated != state) {
            channels[channelId] = updated
        }
    }
}
