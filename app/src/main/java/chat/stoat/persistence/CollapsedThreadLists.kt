package chat.stoat.persistence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import chat.stoat.StoatApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-channel collapse state for the joined-thread lists shown under a channel
 * in the channel drawer.
 *
 * The state is kept in memory (Compose observable) and mirrored into [KVStorage]
 * so it survives restarts. Channels default to expanded, so only collapsed
 * channel ids are stored.
 */
object CollapsedThreadLists {
    private const val KV_KEY = "drawer/collapsedThreadLists"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var hydrated = false

    var collapsed by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Load the persisted set of collapsed channels once per process
     */
    fun hydrate() {
        if (hydrated) return
        hydrated = true

        scope.launch {
            val stored = KVStorage(StoatApplication.instance).get(KV_KEY)
            val ids = stored
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()

            withContext(Dispatchers.Main) {
                collapsed = ids
            }
        }
    }

    /**
     * Whether the thread list under this channel is collapsed
     */
    fun isCollapsed(channelId: String): Boolean = channelId in collapsed

    /**
     * Expand or collapse the thread list under this channel
     */
    fun setCollapsed(channelId: String, value: Boolean) {
        val updated = if (value) collapsed + channelId else collapsed - channelId
        if (updated == collapsed) return

        collapsed = updated
        scope.launch {
            KVStorage(StoatApplication.instance).set(KV_KEY, updated.joinToString(","))
        }
    }

    /**
     * Flip the collapse state of this channel
     */
    fun toggle(channelId: String) {
        setCollapsed(channelId, !isCollapsed(channelId))
    }
}
