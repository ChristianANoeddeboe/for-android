package chat.stoat.api.routes.channel

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.api.internals.ULID
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ForumSortOrder
import chat.stoat.core.model.schemas.ThreadList
import chat.stoat.core.model.schemas.ThreadMember
import chat.stoat.core.model.schemas.ThreadNotify
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class CreateThreadMessage(
    val content: String,
    val nonce: String = ULID.makeNext(),
    val attachments: List<String>? = null
)

@Serializable
data class CreateThreadBody(
    val name: String,
    val private: Boolean = false,
    val invitable: Boolean = true,
    @SerialName("auto_archive_minutes")
    val autoArchiveMinutes: Int? = null,
    val message: CreateThreadMessage? = null,
    @SerialName("applied_tags")
    val appliedTags: List<String> = emptyList()
)

@Serializable
data class EditThreadBody(
    val name: String? = null,
    val archived: Boolean? = null,
    val locked: Boolean? = null,
    val pinned: Boolean? = null,
    val invitable: Boolean? = null,
    @SerialName("auto_archive_minutes")
    val autoArchiveMinutes: Int? = null,
    @SerialName("applied_tags")
    val appliedTags: List<String>? = null
)

@Serializable
private data class EditThreadMemberBody(val notify: ThreadNotify)

class ThreadApiException(val type: String) : Exception(type)

/**
 * Throw the API error in a failed response, return its body otherwise
 */
private suspend fun HttpResponse.bodyOrThrow(): String {
    val body = bodyAsText()
    if (!status.isSuccess()) {
        val type = runCatching {
            StoatJson.decodeFromString(StoatAPIError.serializer(), body).type
        }.getOrDefault(status.toString())
        throw ThreadApiException(type)
    }
    return body
}

private fun ingestChannel(body: String): Channel {
    val channel = StoatJson.decodeFromString(Channel.serializer(), body)
    StoatAPI.channelCache[channel.id!!] = channel
    return channel
}

/**
 * Store the threads, memberships, starter messages and authors of a thread list
 */
private fun ingestThreadList(body: String): ThreadList {
    val list = StoatJson.decodeFromString(ThreadList.serializer(), body)
    list.threads.forEach { thread ->
        val existing = StoatAPI.channelCache[thread.id!!]
        StoatAPI.channelCache[thread.id!!] = existing?.mergeWithPartial(thread) ?: thread
    }
    list.members.forEach { StoatAPI.threadMembers[it.id.thread] = it }
    list.messages.forEach { message -> message.id?.let { StoatAPI.messageCache[it] = message } }
    list.users.forEach { user -> user.id?.let { StoatAPI.userCache.putIfAbsent(it, user) } }
    return list
}

/**
 * Start a thread in a text channel, or create a post in a forum channel
 */
suspend fun createThread(
    channelId: String,
    body: CreateThreadBody,
    idempotencyKey: String = ULID.makeNext()
): Channel {
    val response = StoatHttp.post("/channels/$channelId/threads".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
        header("Idempotency-Key", idempotencyKey)
    }
    return ingestChannel(response.bodyOrThrow())
}

/**
 * Start a public thread from a message, the thread gets the message's id
 */
suspend fun createThreadFromMessage(
    channelId: String,
    messageId: String,
    name: String,
    autoArchiveMinutes: Int? = null
): Channel {
    val response = StoatHttp.post("/channels/$channelId/messages/$messageId/threads".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateThreadBody(name = name, autoArchiveMinutes = autoArchiveMinutes))
    }
    return ingestChannel(response.bodyOrThrow())
}

suspend fun fetchActiveThreads(channelId: String): ThreadList {
    return ingestThreadList(
        StoatHttp.get("/channels/$channelId/threads/active".api()).bodyOrThrow()
    )
}

suspend fun fetchArchivedThreads(
    channelId: String,
    private: Boolean? = null,
    before: String? = null,
    limit: Int? = null
): ThreadList {
    return ingestThreadList(
        StoatHttp.get("/channels/$channelId/threads/archived".api()) {
            private?.let { parameter("private", it) }
            before?.let { parameter("before", it) }
            limit?.let { parameter("limit", it) }
        }.bodyOrThrow()
    )
}

/**
 * List the posts of a forum, pinned posts first
 */
suspend fun searchForumPosts(
    channelId: String,
    tags: List<String> = emptyList(),
    sort: ForumSortOrder? = null,
    archived: Boolean? = null,
    before: String? = null,
    limit: Int? = null
): ThreadList {
    return ingestThreadList(
        StoatHttp.get("/channels/$channelId/threads/search".api()) {
            tags.forEach { parameter("tags", it) }
            sort?.let { parameter("sort", it.name) }
            archived?.let { parameter("archived", it) }
            before?.let { parameter("before", it) }
            limit?.let { parameter("limit", it) }
        }.bodyOrThrow()
    )
}

suspend fun editThread(threadId: String, body: EditThreadBody): Channel {
    val response = StoatHttp.patch("/channels/$threadId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return ingestChannel(response.bodyOrThrow())
}

suspend fun joinThread(threadId: String) {
    StoatHttp.put("/channels/$threadId/thread_members/@me".api()).bodyOrThrow()
}

suspend fun leaveThread(threadId: String) {
    StoatHttp.delete("/channels/$threadId/thread_members/@me".api()).bodyOrThrow()
    StoatAPI.threadMembers.remove(threadId)
}

suspend fun addThreadMember(threadId: String, userId: String) {
    StoatHttp.put("/channels/$threadId/thread_members/$userId".api()).bodyOrThrow()
}

suspend fun removeThreadMember(threadId: String, userId: String) {
    StoatHttp.delete("/channels/$threadId/thread_members/$userId".api()).bodyOrThrow()
}

suspend fun fetchThreadMembers(threadId: String): List<ThreadMember> {
    return StoatJson.decodeFromString(
        ListSerializer(ThreadMember.serializer()),
        StoatHttp.get("/channels/$threadId/thread_members".api()).bodyOrThrow()
    )
}

suspend fun setThreadNotify(threadId: String, notify: ThreadNotify) {
    val response = StoatHttp.patch("/channels/$threadId/thread_members/@me".api()) {
        contentType(ContentType.Application.Json)
        setBody(EditThreadMemberBody(notify))
    }
    val member = StoatJson.decodeFromString(ThreadMember.serializer(), response.bodyOrThrow())
    StoatAPI.threadMembers[threadId] = member
}
