package chat.stoat.core.model.data

import kotlinx.serialization.Serializable

@Serializable
data class StoatInstance(
    val api: String,
    val files: String,
    val proxy: String,
    val websocket: String,
    val webApp: String,
) {
    companion object {
        val Official = StoatInstance(
            api = "https://api.stoat.chat/0.8",
            files = "https://cdn.stoatusercontent.com",
            proxy = "https://proxy.stoatusercontent.com",
            websocket = "wss://events.stoat.chat",
            webApp = "https://stoat.chat",
        )
    }
}

object StoatInstances {
    @Volatile
    var current: StoatInstance = StoatInstance.Official

    val isOfficial: Boolean
        get() = current.api == StoatInstance.Official.api
}
