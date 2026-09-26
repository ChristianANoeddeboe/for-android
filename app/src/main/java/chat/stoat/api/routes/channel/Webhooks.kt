package chat.stoat.api.routes.channel

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.api.routes.server.bodyOrThrowApi
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ForumLayout
import chat.stoat.core.model.schemas.ForumSortOrder
import chat.stoat.core.model.schemas.ForumTag
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Webhook(
    val id: String,
    val name: String,
    val avatar: AutumnResource? = null,
    @SerialName("creator_id")
    val creatorId: String? = null,
    @SerialName("channel_id")
    val channelId: String,
    val permissions: Long = 0,
    val token: String? = null
)

@Serializable
private data class CreateWebhookBody(val name: String, val avatar: String? = null)

@Serializable
data class EditWebhookBody(
    val name: String? = null,
    val avatar: String? = null,
    val remove: List<String>? = null
)

suspend fun fetchWebhooks(channelId: String): List<Webhook> {
    val response = StoatHttp.get("/channels/$channelId/webhooks".api())
    return StoatJson.decodeFromString(
        ListSerializer(Webhook.serializer()),
        response.bodyOrThrowApi()
    )
}

suspend fun createWebhook(channelId: String, name: String): Webhook {
    val response = StoatHttp.post("/channels/$channelId/webhooks".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateWebhookBody(name))
    }
    return StoatJson.decodeFromString(Webhook.serializer(), response.bodyOrThrowApi())
}

suspend fun editWebhook(webhookId: String, body: EditWebhookBody): Webhook {
    val response = StoatHttp.patch("/webhooks/$webhookId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return StoatJson.decodeFromString(Webhook.serializer(), response.bodyOrThrowApi())
}

suspend fun deleteWebhook(webhookId: String) {
    StoatHttp.delete("/webhooks/$webhookId".api()).bodyOrThrowApi()
}

@Serializable
data class EditChannelSettingsBody(
    val slowmode: Long? = null,
    @SerialName("available_tags")
    val availableTags: List<ForumTag>? = null,
    @SerialName("require_tag")
    val requireTag: Boolean? = null,
    @SerialName("default_reaction_emoji")
    val defaultReactionEmoji: String? = null,
    @SerialName("default_sort_order")
    val defaultSortOrder: ForumSortOrder? = null,
    @SerialName("default_layout")
    val defaultLayout: ForumLayout? = null,
    @SerialName("default_thread_slowmode")
    val defaultThreadSlowmode: Long? = null,
    @SerialName("default_auto_archive_minutes")
    val defaultAutoArchiveMinutes: Int? = null,
    val remove: List<String>? = null
)

/**
 * Edit slowmode, thread defaults and forum settings of a channel
 */
suspend fun editChannelSettings(channelId: String, body: EditChannelSettingsBody) {
    val response = StoatHttp.patch("/channels/$channelId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    val channel = StoatJson.decodeFromString(
        Channel.serializer(),
        response.bodyOrThrowApi()
    )
    StoatAPI.channelCache[channelId] =
        StoatAPI.channelCache[channelId]?.mergeWithPartial(channel) ?: channel
}
