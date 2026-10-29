package chat.stoat.core.model.schemas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DJ capability of a text channel.
 *
 * DJ is a capability on a [ChannelType.TextChannel] (`channel.dj`), not a channel type of its own:
 * shipped clients throw on an unknown `channel_type` inside the `Ready` payload, so a new enum
 * value would break every install. A channel is a DJ channel when [Channel.dj] is not null, and it
 * behaves like a DJ channel while [enabled] is true.
 */
@Serializable
data class DjInformation(
    /** Fraction of the present members required to skip the current track (0.0 - 1.0) */
    @SerialName("skip_threshold")
    val skipThreshold: Float = 0.5f,

    /** Maximum size of a single upload, in megabytes */
    @SerialName("max_file_mb")
    val maxFileMb: Int = 50,

    /** Maximum duration of a single track, in seconds */
    @SerialName("max_track_seconds")
    val maxTrackSeconds: Int = 900,

    /** Maximum amount of tracks queued at once */
    @SerialName("max_tracks")
    val maxTracks: Int = 100,

    /** Maximum total amount of bytes stored for this channel */
    @SerialName("max_total_bytes")
    val maxTotalBytes: Long = 20L * 1024 * 1024 * 1024,

    /** Maximum amount of users which may join at once, null means unlimited */
    @SerialName("max_users")
    val maxUsers: Int? = null,

    /** Whether the DJ is currently enabled */
    val enabled: Boolean = true,
)

/** Partial update of the settings of a DJ channel (`PATCH /channels/{id}/dj/settings`) */
@Serializable
data class DataEditDjSettings(
    @SerialName("skip_threshold")
    val skipThreshold: Float? = null,
    @SerialName("max_file_mb")
    val maxFileMb: Int? = null,
    @SerialName("max_track_seconds")
    val maxTrackSeconds: Int? = null,
    @SerialName("max_tracks")
    val maxTracks: Int? = null,
    @SerialName("max_total_bytes")
    val maxTotalBytes: Long? = null,
    /** 0 removes the limit */
    @SerialName("max_users")
    val maxUsers: Int? = null,
    val enabled: Boolean? = null,
)

/** A track queued in a DJ channel */
@Serializable
data class DjTrack(
    /** Unique id of this track (`<seq>-<ulid>`) */
    val id: String,
    /**
     * Route of the streaming endpoint for the track.
     *
     * Informational only: the audio requires the signed token which is delivered in
     * `DjNowPlaying`/`DjTrackStarted`, so this must not be fetched directly.
     */
    val file: String = "",
    val title: String = "",
    @SerialName("duration_ms")
    val durationMs: Long = 0,
    val uploader: String = "",
)

/** The track currently playing in a DJ channel */
@Serializable
data class DjNowPlaying(
    val track: DjTrack,
    /** Unix timestamp (in milliseconds) at which the track started */
    @SerialName("started_at")
    val startedAt: Long = 0,
)

/** Full state of a DJ channel (`GET /channels/{id}/dj`, `dj/join`, `dj/skip`, ...) */
@Serializable
data class DjChannelState(
    @SerialName("_id")
    val id: String,
    val current: DjNowPlaying? = null,
    /** Playback position of the current track, in milliseconds */
    @SerialName("position_ms")
    val positionMs: Long = 0,
    /** Server time at which this state was generated (unix milliseconds) */
    @SerialName("server_time")
    val serverTime: Long = 0,
    val queue: List<DjTrack> = emptyList(),
    val votes: Int = 0,
    val voters: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    val settings: DjInformation = DjInformation(),
)

/** Membership of the current user in a DJ channel (`dj_states` in `Ready`) */
@Serializable
data class DjChannelMembers(
    @SerialName("_id")
    val id: String,
    val members: List<String> = emptyList(),
)
