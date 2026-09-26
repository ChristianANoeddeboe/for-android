package chat.stoat.api.routes.bots

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.api.routes.server.bodyOrThrowApi
import chat.stoat.core.model.schemas.User
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class OwnedBot(
    @SerialName("_id")
    val id: String,
    val owner: String,
    val token: String,
    val public: Boolean = false,
    val analytics: Boolean = false,
    val discoverable: Boolean = false,
    @SerialName("interactions_url")
    val interactionsUrl: String? = null
)

@Serializable
data class OwnedBotsResponse(val bots: List<OwnedBot>, val users: List<User>)

@Serializable
data class FetchBotResponse(val bot: OwnedBot, val user: User)

@Serializable
private data class CreateBotBody(val name: String)

@Serializable
data class EditBotBody(
    val name: String? = null,
    val public: Boolean? = null,
    val analytics: Boolean? = null,
    @SerialName("interactions_url")
    val interactionsUrl: String? = null,
    val remove: List<String>? = null
)

private fun cacheUser(user: User) {
    user.id?.let { id ->
        StoatAPI.userCache[id] = StoatAPI.userCache[id]?.mergeWithPartial(user) ?: user
    }
}

suspend fun fetchOwnedBots(): OwnedBotsResponse {
    val response = StoatHttp.get("/bots/@me".api())
    val owned = StoatJson.decodeFromString(OwnedBotsResponse.serializer(), response.bodyOrThrowApi())
    owned.users.forEach(::cacheUser)
    return owned
}

suspend fun fetchBot(botId: String): FetchBotResponse {
    val response = StoatHttp.get("/bots/$botId".api())
    val fetched = StoatJson.decodeFromString(FetchBotResponse.serializer(), response.bodyOrThrowApi())
    cacheUser(fetched.user)
    return fetched
}

/**
 * Create a bot, returns the new bot's id
 */
suspend fun createBot(name: String): String {
    val response = StoatHttp.post("/bots/create".api()) {
        contentType(ContentType.Application.Json)
        setBody(CreateBotBody(name))
    }
    // The bot fields are flattened next to "user"
    val created = StoatJson.decodeFromString(OwnedBot.serializer(), response.bodyOrThrowApi())
    return created.id
}

suspend fun editBot(botId: String, body: EditBotBody) {
    StoatHttp.patch("/bots/$botId".api()) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.bodyOrThrowApi()
}

suspend fun deleteBot(botId: String) {
    StoatHttp.delete("/bots/$botId".api()).bodyOrThrowApi()
}

/**
 * Add a bot to a server or group, pass exactly one of [serverId] and [groupId]
 */
suspend fun inviteBot(botId: String, serverId: String? = null, groupId: String? = null) {
    StoatHttp.post("/bots/$botId/invite".api()) {
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject {
            serverId?.let { put("server", it) }
            groupId?.let { put("group", it) }
        })
    }.bodyOrThrowApi()
}

@Serializable
private data class EditBotUserBody(
    val avatar: String? = null,
    val remove: List<String>? = null
)

/**
 * Change or remove a bot's avatar, bots are edited through their user
 */
suspend fun editBotAvatar(botId: String, avatarId: String?) {
    val response = StoatHttp.patch("/users/$botId".api()) {
        contentType(ContentType.Application.Json)
        setBody(
            if (avatarId != null) EditBotUserBody(avatar = avatarId)
            else EditBotUserBody(remove = listOf("Avatar"))
        )
    }
    cacheUser(StoatJson.decodeFromString(User.serializer(), response.bodyOrThrowApi()))
}
