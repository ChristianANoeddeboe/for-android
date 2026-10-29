package chat.stoat.api.routes.channel

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.api.internals.ULID
import chat.stoat.core.model.schemas.DataEditDjSettings
import chat.stoat.core.model.schemas.DjChannelState
import chat.stoat.core.model.schemas.DjTrack
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import kotlinx.serialization.SerializationException

/**
 * DJ channel routes.
 *
 * A DJ channel is a `TextChannel` with a `dj` capability; it is read-only for messages, members join
 * explicitly (voice-style) and share a FIFO queue of uploaded audio tracks.
 */

/** Name of the multipart form field the backend expects for a DJ upload */
private const val DJ_UPLOAD_FIELD = "file"

private fun djError(body: String): Exception {
    val type = try {
        StoatJson.decodeFromString(StoatAPIError.serializer(), body).type
    } catch (e: SerializationException) {
        null
    }

    return Exception(type ?: "Unknown error")
}

private inline fun <reified T> decodeDj(body: String): T {
    return try {
        StoatJson.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw djError(body)
    }
}

private suspend fun HttpResponse.djState(): DjChannelState {
    val body = bodyAsText()
    if (!status.isSuccess()) throw djError(body)
    return decodeDj(body)
}

private fun djPath(channelId: String, suffix: String) = "/channels/$channelId/dj$suffix".api()

/** # Enable DJ: turns a text channel into a DJ channel (or re-enables a disabled one) */
suspend fun djEnable(channelId: String): DjChannelState {
    return StoatHttp.post(djPath(channelId, "/enable")).djState()
}

/** # Disable DJ: the channel behaves like a normal text channel again, settings are kept */
suspend fun djDisable(channelId: String): DjChannelState {
    return StoatHttp.post(djPath(channelId, "/disable")).djState()
}

/** # Join DJ Channel: returns the channel's full state */
suspend fun djJoin(channelId: String): DjChannelState {
    return StoatHttp.post(djPath(channelId, "/join")).djState()
}

/** # Leave DJ Channel: idempotent */
suspend fun djLeave(channelId: String) {
    val response = StoatHttp.post(djPath(channelId, "/leave"))
    if (!response.status.isSuccess()) throw djError(response.bodyAsText())
}

/** # Fetch DJ Channel State: read-only for users which have not joined */
suspend fun djFetch(channelId: String): DjChannelState {
    return StoatHttp.get(djPath(channelId, "")).djState()
}

/** # Vote to Skip the current track, idempotent per (track, user) */
suspend fun djSkip(channelId: String): DjChannelState {
    return StoatHttp.post(djPath(channelId, "/skip")).djState()
}

/** # Edit DJ Channel Settings: requires ManageChannel */
suspend fun djEditSettings(channelId: String, data: DataEditDjSettings): DjChannelState {
    val response = StoatHttp.patch(djPath(channelId, "/settings")) {
        contentType(ContentType.Application.Json)
        setBody(StoatJson.encodeToString(DataEditDjSettings.serializer(), data))
    }

    return response.djState()
}

/** Guess the content type of a DJ upload from its file name */
private fun audioContentType(fileName: String): ContentType {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3", "mpeg", "mpga" -> ContentType("audio", "mpeg")
        "m4a", "aac" -> ContentType("audio", "mp4")
        "ogg", "oga", "opus" -> ContentType("audio", "ogg")
        "wav" -> ContentType("audio", "wav")
        "flac" -> ContentType("audio", "flac")
        "webm" -> ContentType("audio", "webm")
        else -> ContentType("audio", "mpeg")
    }
}

/**
 * # Add Track to DJ Queue
 *
 * Uploads an audio file which the backend transcodes to MP3 and appends to the channel's queue.
 * Requires an active membership of the channel and the `UploadFiles` permission.
 *
 * @param title display title, defaults to the file name on the server
 * @param onProgress receives (bytes sent, total bytes)
 */
suspend fun djQueue(
    channelId: String,
    file: File,
    title: String? = null,
    fileName: String = file.name,
    onProgress: (Long, Long) -> Unit = { _, _ -> }
): DjTrack {
    val response = StoatHttp.post(djPath(channelId, "/queue")) {
        parameter("title", title?.takeIf { it.isNotBlank() } ?: fileName)
        header("Idempotency-Key", ULID.makeNext())
        setBody(
            MultiPartFormDataContent(
                formData {
                    append(
                        DJ_UPLOAD_FIELD,
                        file.readBytes(),
                        Headers.build {
                            append(HttpHeaders.ContentType, audioContentType(fileName).toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        }
                    )
                }
            )
        )
        onUpload { bytesSentTotal, contentLength ->
            contentLength?.let { onProgress(bytesSentTotal, it) }
        }
    }

    val body = response.bodyAsText()
    if (!response.status.isSuccess()) throw djError(body)
    return decodeDj(body)
}
