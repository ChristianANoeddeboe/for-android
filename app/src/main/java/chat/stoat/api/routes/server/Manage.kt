package chat.stoat.api.routes.server

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.Category
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.Emoji
import chat.stoat.core.model.schemas.EmojiParent
import chat.stoat.core.model.schemas.Member
import chat.stoat.core.model.schemas.Role
import chat.stoat.core.model.schemas.SystemMessages
import io.ktor.client.request.delete
import io.ktor.client.request.get
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

class ManageApiException(val type: String) : Exception(type)

/**
 * Throw the API error in a failed response, return its body otherwise
 */
suspend fun HttpResponse.bodyOrThrowApi(): String {
    val body = bodyAsText()
    if (!status.isSuccess()) {
        val type = runCatching {
            StoatJson.decodeFromString(StoatAPIError.serializer(), body).type
        }.getOrDefault(status.toString())
        throw ManageApiException(type)
    }
    return body
}

// Server

@Serializable
data class EditServerBody(
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val banner: String? = null,
    val categories: List<Category>? = null,
    @SerialName("system_messages")
    val systemMessages: SystemMessages? = null,
    val discoverable: Boolean? = null,
    val analytics: Boolean? = null,
    val remove: List<String>? = null
)

suspend fun editServer(serverId: String, body: EditServerBody) {
    StoatHttp.patch("/servers/$serverId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.bodyOrThrowApi()
}

// Roles

@Serializable
data class CreateRoleBody(val name: String, val rank: Long? = null)

@Serializable
data class NewRoleResponse(val id: String, val role: Role)

@Serializable
data class EditRoleBody(
    val name: String? = null,
    val colour: String? = null,
    val hoist: Boolean? = null,
    val remove: List<String>? = null
)

@Serializable
private data class RoleRanksBody(val ranks: List<String>)

@Serializable
private data class OverrideFieldBody(val allow: Long, val deny: Long)

@Serializable
private data class OverrideWireBody(val permissions: OverrideFieldBody)

@Serializable
private data class ServerDefaultPermissionsBody(val permissions: Long)

suspend fun createRole(serverId: String, name: String): NewRoleResponse {
    val response = StoatHttp.post("/servers/$serverId/roles".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateRoleBody(name))
    }
    return StoatJson.decodeFromString(NewRoleResponse.serializer(), response.bodyOrThrowApi())
}

suspend fun editRole(serverId: String, roleId: String, body: EditRoleBody) {
    StoatHttp.patch("/servers/$serverId/roles/$roleId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.bodyOrThrowApi()
}

suspend fun deleteRole(serverId: String, roleId: String) {
    StoatHttp.delete("/servers/$serverId/roles/$roleId".api()).bodyOrThrowApi()
}

/**
 * Reorder roles, [ranks] is ordered from highest (rank 0) to lowest
 */
suspend fun editRoleRanks(serverId: String, ranks: List<String>) {
    StoatHttp.patch("/servers/$serverId/roles/ranks".api()) {
        contentType(ContentType.Application.Json)
        setBody(RoleRanksBody(ranks))
    }.bodyOrThrowApi()
}

suspend fun setRolePermissions(serverId: String, roleId: String, allow: Long, deny: Long) {
    StoatHttp.put("/servers/$serverId/permissions/$roleId".api()) {
        contentType(ContentType.Application.Json)
        setBody(OverrideWireBody(OverrideFieldBody(allow, deny)))
    }.bodyOrThrowApi()
}

suspend fun setServerDefaultPermissions(serverId: String, permissions: Long) {
    StoatHttp.put("/servers/$serverId/permissions/default".api()) {
        contentType(ContentType.Application.Json)
        setBody(ServerDefaultPermissionsBody(permissions))
    }.bodyOrThrowApi()
}

// Channels

@Serializable
data class CreateChannelBody(
    val type: String,
    val name: String,
    val description: String? = null,
    val nsfw: Boolean? = null
)

suspend fun createServerChannel(serverId: String, body: CreateChannelBody): Channel {
    val response = StoatHttp.post("/servers/$serverId/channels".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    val channel = StoatJson.decodeFromString(Channel.serializer(), response.bodyOrThrowApi())
    channel.id?.let { StoatAPI.channelCache[it] = channel }
    return channel
}

suspend fun setChannelRolePermissions(channelId: String, roleId: String, allow: Long, deny: Long) {
    StoatHttp.put("/channels/$channelId/permissions/$roleId".api()) {
        contentType(ContentType.Application.Json)
        setBody(OverrideWireBody(OverrideFieldBody(allow, deny)))
    }.bodyOrThrowApi()
}

suspend fun setChannelDefaultPermissions(channelId: String, allow: Long, deny: Long) {
    StoatHttp.put("/channels/$channelId/permissions/default".api()) {
        contentType(ContentType.Application.Json)
        setBody(OverrideWireBody(OverrideFieldBody(allow, deny)))
    }.bodyOrThrowApi()
}

@Serializable
data class ServerInvite(
    val type: String? = null,
    @SerialName("_id")
    val code: String,
    val server: String? = null,
    val creator: String? = null,
    val channel: String? = null
)

suspend fun createChannelInvite(channelId: String): ServerInvite {
    val response = StoatHttp.post("/channels/$channelId/invites".api())
    return StoatJson.decodeFromString(ServerInvite.serializer(), response.bodyOrThrowApi())
}

// Emojis

suspend fun fetchServerEmojis(serverId: String): List<Emoji> {
    val response = StoatHttp.get("/servers/$serverId/emojis".api())
    return StoatJson.decodeFromString(
        ListSerializer(Emoji.serializer()),
        response.bodyOrThrowApi()
    )
}

@Serializable
private data class CreateEmojiBody(val name: String, val parent: EmojiParent, val nsfw: Boolean)

/**
 * Create an emoji from a file uploaded to the emojis bucket, the emoji takes the upload's id
 */
suspend fun createServerEmoji(serverId: String, uploadId: String, name: String): Emoji {
    val response = StoatHttp.put("/custom/emoji/$uploadId".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateEmojiBody(name, EmojiParent("Server", serverId), false))
    }
    val emoji = StoatJson.decodeFromString(Emoji.serializer(), response.bodyOrThrowApi())
    emoji.id?.let { StoatAPI.emojiCache[it] = emoji }
    return emoji
}

suspend fun deleteEmoji(emojiId: String) {
    StoatHttp.delete("/custom/emoji/$emojiId".api()).bodyOrThrowApi()
    StoatAPI.emojiCache.remove(emojiId)
}

// Invites

suspend fun fetchServerInvites(serverId: String): List<ServerInvite> {
    val response = StoatHttp.get("/servers/$serverId/invites".api())
    return StoatJson.decodeFromString(
        ListSerializer(ServerInvite.serializer()),
        response.bodyOrThrowApi()
    )
}

suspend fun deleteInvite(code: String) {
    StoatHttp.delete("/invites/$code".api()).bodyOrThrowApi()
}

// Bans

@Serializable
data class BannedUser(
    @SerialName("_id")
    val id: String,
    val username: String,
    val discriminator: String,
    val avatar: AutumnResource? = null
)

@Serializable
data class BanId(val server: String, val user: String)

@Serializable
data class ServerBan(
    @SerialName("_id")
    val id: BanId,
    val reason: String? = null
)

@Serializable
data class BanListResponse(val users: List<BannedUser>, val bans: List<ServerBan>)

@Serializable
private data class CreateBanBody(
    val reason: String? = null,
    @SerialName("delete_message_seconds")
    val deleteMessageSeconds: Long? = null
)

suspend fun fetchServerBans(serverId: String): BanListResponse {
    val response = StoatHttp.get("/servers/$serverId/bans".api())
    return StoatJson.decodeFromString(BanListResponse.serializer(), response.bodyOrThrowApi())
}

suspend fun banUser(
    serverId: String,
    userId: String,
    reason: String? = null,
    deleteMessageSeconds: Long? = null
) {
    StoatHttp.put("/servers/$serverId/bans/$userId".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateBanBody(reason?.ifBlank { null }, deleteMessageSeconds))
    }.bodyOrThrowApi()
}

suspend fun unbanUser(serverId: String, userId: String) {
    StoatHttp.delete("/servers/$serverId/bans/$userId".api()).bodyOrThrowApi()
}

// Members

@Serializable
data class EditMemberBody(
    val nickname: String? = null,
    val avatar: String? = null,
    val roles: List<String>? = null,
    val timeout: String? = null,
    val remove: List<String>? = null
)

suspend fun editMember(serverId: String, userId: String, body: EditMemberBody): Member {
    val response = StoatHttp.patch("/servers/$serverId/members/$userId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return StoatJson.decodeFromString(Member.serializer(), response.bodyOrThrowApi())
}

suspend fun kickMember(serverId: String, userId: String) {
    StoatHttp.delete("/servers/$serverId/members/$userId".api()).bodyOrThrowApi()
}
