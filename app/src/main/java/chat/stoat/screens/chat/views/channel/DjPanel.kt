package chat.stoat.screens.chat.views.channel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.dj.Dj
import chat.stoat.api.dj.DjState
import chat.stoat.api.dj.neededToSkip
import chat.stoat.api.routes.channel.DjApiException
import chat.stoat.api.routes.channel.djJoin
import chat.stoat.api.routes.channel.djLeave
import chat.stoat.api.routes.channel.djQueue
import chat.stoat.api.routes.channel.djQueueUrl
import chat.stoat.api.routes.channel.djSkip
import chat.stoat.composables.dj.DjPlayback
import chat.stoat.core.model.schemas.DjInformation
import java.io.File
import kotlinx.coroutines.launch

private const val DJ_QUEUE_PREVIEW_COUNT = 3

/** `mm:ss`, or `h:mm:ss` for tracks over an hour */
fun formatDjTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** Display name of a user id, falling back to the id itself */
@Composable
private fun djUserName(userId: String): String {
    val user = StoatAPI.userCache[userId]

    return user?.displayName ?: user?.username ?: userId
}

/**
 * Human text for a failed DJ action.
 *
 * The backend answers with a machine error type (`DjYoutubeUnsupported`, `DjQueueFull`, ...) which
 * must never reach the user verbatim; unknown types fall back to a generic line, keeping the raw
 * detail in parentheses so a report still says something useful.
 */
@Composable
private fun djErrorMessage(error: Throwable?): String? {
    if (error == null) return null

    val detail = (error as? DjApiException)?.errorType
        ?: error.message?.takeIf { it.isNotBlank() }

    return when (detail) {
        "DjYoutubeUnsupported" -> stringResource(R.string.dj_error_youtube_unsupported)
        "DjYoutubeFailed" -> stringResource(R.string.dj_error_youtube_failed)
        "DjYoutubeUnavailable" -> stringResource(R.string.dj_error_youtube_unavailable)
        "NotInDjChannel" -> stringResource(R.string.dj_error_not_member)
        "DjQueueFull" -> stringResource(R.string.dj_error_queue_full)
        "DjUploadLimit" -> stringResource(R.string.dj_error_upload_limit)
        "DjTrackTooLong" -> stringResource(R.string.dj_error_too_long)
        "DjStorageFull" -> stringResource(R.string.dj_error_storage_full)
        "DjTranscodeFailed", "DjUnavailable" ->
            stringResource(R.string.dj_error_unavailable)
        "FileTooLarge" -> stringResource(R.string.dj_error_too_large)
        "FileTooSmall" -> stringResource(R.string.dj_upload_empty)
        null, "Unknown error" -> stringResource(R.string.dj_error_generic)
        else -> stringResource(R.string.dj_error_generic_detail, detail)
    }
}

/**
 * The DJ panel of a channel: now playing, skip vote, membership and the queue.
 *
 * Shown above the messages on a channel whose `dj` capability is enabled. Users who have not joined
 * see the queue read-only and a Join button; joining unlocks uploading and skipping.
 */
@Composable
fun DjPanel(
    channelId: String,
    state: DjState?,
    playback: DjPlayback,
    settings: DjInformation,
    canJoin: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val selfId = StoatAPI.selfId
    val joined = state?.isMember(selfId) == true
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    val current = state?.current
    val position = playback.positionMs.takeIf { it > 0 } ?: (state?.positionMs() ?: 0)
    val duration = current?.track?.durationMs ?: 0
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val votes = state?.votes ?: 0
    val needed = state?.needed ?: 1

    fun act(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null

            try {
                action()
            } catch (e: Exception) {
                // The backend drops membership when the websocket closes (app backgrounded), so a
                // failed action re-reads the authoritative state instead of leaving the panel stale
                runCatching { Dj.refresh(channelId) }
                error = e
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("dj_panel")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_headset_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dj_channel_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (settings.maxUsers == null) {
                    stringResource(R.string.dj_listeners, state?.memberCount ?: 0)
                } else {
                    stringResource(
                        R.string.dj_listeners_limited,
                        state?.memberCount ?: 0,
                        settings.maxUsers ?: 0
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))

            when {
                joined -> TextButton(
                    enabled = !busy,
                    onClick = {
                        act {
                            djLeave(channelId)
                            selfId?.let { Dj.memberLeft(channelId, it) }
                        }
                    },
                    modifier = Modifier.testTag("dj_leave")
                ) {
                    Text(stringResource(R.string.dj_leave))
                }

                canJoin -> TextButton(
                    enabled = !busy,
                    onClick = { act { Dj.apply(djJoin(channelId)) } },
                    modifier = Modifier.testTag("dj_join")
                ) {
                    Text(stringResource(R.string.dj_join))
                }
            }
        }

        if (current == null) {
            Text(
                text = stringResource(R.string.dj_nothing_playing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Text(
                text = current.track.title.ifBlank { stringResource(R.string.dj_untitled) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("dj_now_playing")
            )
            Text(
                text = stringResource(
                    R.string.dj_uploaded_by,
                    djUserName(current.track.uploader)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${formatDjTime(position)} / ${formatDjTime(duration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (joined) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            act {
                                // The snapshot which a vote returns can predate the skip event, so
                                // only the tally is taken from it and playback stays event-driven
                                val state = djSkip(channelId)
                                Dj.setMembership(channelId, state.members)
                                Dj.vote(
                                    channelId = channelId,
                                    votes = state.votes,
                                    needed = neededToSkip(
                                        state.members.size,
                                        state.settings.skipThreshold
                                    ),
                                    voters = state.voters
                                )
                            }
                        },
                        modifier = Modifier.testTag("dj_skip")
                    ) {
                        Text(stringResource(R.string.dj_skip_votes, votes, needed))
                    }
                }
            }
        }

        if (joined) {
            val queue = state.queue

            if (queue.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.dj_up_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                queue.take(DJ_QUEUE_PREVIEW_COUNT).forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = track.title.ifBlank { track.id },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatDjTime(track.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (queue.size > DJ_QUEUE_PREVIEW_COUNT) {
                    Text(
                        text = stringResource(
                            R.string.dj_queue_more,
                            queue.size - DJ_QUEUE_PREVIEW_COUNT
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val message = djErrorMessage(error) ?: state?.lastUploadError
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Upload control which replaces the message composer on a DJ channel: picks an audio file (`audio`
 * mime type) or takes a YouTube link, and queues it. Requires an active membership and the
 * `UploadFiles` permission.
 *
 * The link path is not instant: the server downloads the video with `yt-dlp` and transcodes it, so
 * the field stays disabled with a spinner until the track is queued.
 */
@Composable
fun DjUploadField(
    channelId: String,
    state: DjState?,
    settings: DjInformation,
    canUpload: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val joined = state?.isMember(StoatAPI.selfId) == true
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var link by remember { mutableStateOf("") }
    var addingLink by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    val busy = uploading || addingLink
    val canAdd = joined && canUpload && !busy

    fun queueLink() {
        val url = link.trim()
        if (url.isEmpty() || !canAdd) return

        scope.launch {
            addingLink = true
            error = null

            try {
                djQueueUrl(channelId = channelId, url = url)
                link = ""
                // The queue is authoritative, so re-read it instead of guessing the position
                runCatching { Dj.refresh(channelId) }
            } catch (e: Exception) {
                error = e
            } finally {
                addingLink = false
            }
        }
    }

    fun displayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult

        val name = displayName(uri) ?: "track.m4a"

        scope.launch {
            uploading = true
            progress = 0f
            error = null

            var file: File? = null

            try {
                val suffix = name.substringAfterLast('.', "m4a")
                file = File(context.cacheDir, "dj-upload-${System.currentTimeMillis()}.$suffix")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }

                // Opening the system picker backgrounds the app, which closes the websocket and
                // makes the backend drop the DJ membership on socket close. Joining again is
                // idempotent and re-adds us before the upload is authorised.
                Dj.apply(djJoin(channelId))

                val maxBytes = settings.maxFileMb.toLong() * 1024L * 1024L
                if (file.length() <= 0L) {
                    throw Exception(context.getString(R.string.dj_upload_empty))
                }
                if (file.length() > maxBytes) {
                    throw Exception(
                        context.getString(R.string.dj_upload_too_large, settings.maxFileMb)
                    )
                }

                djQueue(
                    channelId = channelId,
                    file = file,
                    title = name.substringBeforeLast('.').ifBlank { name },
                    fileName = name,
                    onProgress = { sent, total ->
                        progress = if (total > 0) sent.toFloat() / total else 0f
                    }
                )

                // The queue is authoritative, so re-read it instead of guessing the position
                runCatching { Dj.refresh(channelId) }
            } catch (e: Exception) {
                error = e
            } finally {
                file?.delete()
                uploading = false
                progress = 0f
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("dj_upload_field")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = canAdd,
                onClick = { picker.launch(arrayOf("audio/*")) },
                modifier = Modifier.testTag("dj_upload")
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_attach_file_24dp),
                    contentDescription = stringResource(R.string.dj_upload)
                )
            }

            Text(
                text = when {
                    !joined -> stringResource(R.string.dj_upload_join_first)
                    !canUpload -> stringResource(R.string.dj_upload_no_permission)
                    uploading -> stringResource(R.string.dj_uploading)
                    addingLink -> stringResource(R.string.dj_link_adding)
                    else -> stringResource(
                        R.string.dj_upload_hint,
                        settings.maxFileMb,
                        settings.maxTrackSeconds / 60
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        }

        if (uploading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp)
        ) {
            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    error = null
                },
                enabled = canAdd,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.dj_link_hint),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { queueLink() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dj_link_field")
            )

            Spacer(Modifier.width(4.dp))

            TextButton(
                enabled = canAdd && link.isNotBlank(),
                onClick = { queueLink() },
                modifier = Modifier.testTag("dj_link_add")
            ) {
                if (addingLink) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.dj_link_add))
                }
            }
        }

        Text(
            text = stringResource(R.string.dj_link_only_video),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )

        if (addingLink) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        djErrorMessage(error)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}
