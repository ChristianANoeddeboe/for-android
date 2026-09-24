package chat.stoat.api.instance

import android.content.Context
import androidx.core.content.edit
import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.settings.LoadedSettings
import chat.stoat.core.model.data.StoatInstance
import chat.stoat.persistence.KVStorage
import chat.stoat.core.model.data.StoatInstances
import io.ktor.client.plugins.retry
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import java.net.URI

class InvalidInstanceException(message: String) : Exception(message)

object InstanceManager {
    private const val PREFS_NAME = "stoat_instance"
    private const val KEY_INSTANCE = "instance"
    private val OFFICIAL_HOSTS = setOf("stoat.chat", "api.stoat.chat", "app.revolt.chat", "api.revolt.chat")

    private val INSTANCE_SCOPED_KEYS = setOf(
        "sessionToken", "sessionId", "selfId", "selfName", "selfAvatarUrl",
        "fcmToken", "currentDestination",
    )
    private val INSTANCE_SCOPED_PREFIXES = listOf("lastChannel/", "draftContent/")

    /**
     * Restores the persisted instance. Must run before anything reads the STOAT_* endpoints,
     * which is why this is synchronous and backed by SharedPreferences rather than DataStore.
     */
    fun load(context: Context) {
        val raw = prefs(context).getString(KEY_INSTANCE, null) ?: return
        StoatInstances.current = try {
            StoatJson.decodeFromString(StoatInstance.serializer(), raw)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Stored instance is invalid, using official: ${e.asLog()}" }
            StoatInstance.Official
        }
    }

    /**
     * Makes [instance] the active instance. Cached data and per-account keys belong to the
     * previous instance, so they are wiped when the instance actually changes.
     */
    suspend fun switchTo(context: Context, instance: StoatInstance) {
        if (instance == StoatInstances.current) return

        StoatAPI.logout()
        KVStorage(context).removeWhere { key ->
            key in INSTANCE_SCOPED_KEYS || INSTANCE_SCOPED_PREFIXES.any { key.startsWith(it) }
        }
        LoadedSettings.reset()
        set(context, instance)
    }

    private fun set(context: Context, instance: StoatInstance) {
        StoatInstances.current = instance
        prefs(context).edit {
            if (instance == StoatInstance.Official) {
                remove(KEY_INSTANCE)
            } else {
                putString(KEY_INSTANCE, StoatJson.encodeToString(StoatInstance.serializer(), instance))
            }
        }
    }

    /**
     * Resolves user input such as `chat.example.com` or `https://chat.example.com/api` to a
     * full instance by querying the API root, which advertises every other endpoint.
     */
    suspend fun resolve(input: String): StoatInstance {
        val base = normalise(input)
        if (URI(base).host.lowercase() in OFFICIAL_HOSTS) return StoatInstance.Official

        val candidates = listOf(base, "$base/api", "$base/0.8").distinct()

        for (candidate in candidates) {
            val root = fetchRoot(candidate) ?: continue
            return fromRoot(candidate, root)
        }

        throw InvalidInstanceException("No Stoat API found at $base")
    }

    private fun normalise(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) throw InvalidInstanceException("Instance URL is empty")
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (e: Exception) {
            throw InvalidInstanceException("Invalid instance URL")
        }
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            throw InvalidInstanceException("Invalid instance URL")
        }
        return withScheme
    }

    private suspend fun fetchRoot(apiUrl: String): JsonObject? = try {
        val response = StoatHttp.get("$apiUrl/") {
            // Probing several candidates; fail fast instead of retrying each one.
            retry { noRetry() }
        }
        if (!response.status.isSuccess()) {
            null
        } else {
            StoatJson.parseToJsonElement(response.bodyAsText()).jsonObject
                .takeIf { "revolt" in it && "features" in it }
        }
    } catch (e: Exception) {
        logcat(LogPriority.DEBUG) { "No API root at $apiUrl: ${e.asLog()}" }
        null
    }

    private fun fromRoot(apiUrl: String, root: JsonObject): StoatInstance {
        val features = root["features"]!!.jsonObject
        fun featureUrl(name: String) =
            features[name]?.jsonObject?.get("url")?.jsonPrimitive?.content?.trimEnd('/')

        val origin = URI(apiUrl).let { "${it.scheme}://${it.authority}" }
        val websocket = root["ws"]?.jsonPrimitive?.content?.trimEnd('/')
            ?: throw InvalidInstanceException("Instance does not advertise a WebSocket URL")

        return StoatInstance(
            api = apiUrl,
            files = featureUrl("autumn") ?: "$origin/autumn",
            proxy = featureUrl("january") ?: "$origin/january",
            websocket = websocket,
            webApp = root["app"]?.jsonPrimitive?.content?.trimEnd('/')
                ?.takeIf { it.isNotBlank() } ?: origin,
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
