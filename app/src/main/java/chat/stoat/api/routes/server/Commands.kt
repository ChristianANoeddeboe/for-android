package chat.stoat.api.routes.server

import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Bot slash command registered on a server.
 */
@Serializable
data class Command(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("server_id")
    val serverId: String? = null,
    @SerialName("owner_id")
    val ownerId: String
)

/**
 * Fetch the bot slash commands registered on a server, used by clients to
 * populate the `/` autocomplete.
 */
suspend fun fetchServerCommands(serverId: String): List<Command> {
    val response = StoatHttp.get("/commands/$serverId".api())
    return StoatJson.decodeFromString(
        ListSerializer(Command.serializer()),
        response.bodyOrThrowApi()
    )
}
