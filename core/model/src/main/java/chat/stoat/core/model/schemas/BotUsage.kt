package chat.stoat.core.model.schemas

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single usage window published by a bot: either one of its provider quota
 * windows (percent + reset instant) or one of its own consumption windows
 * (tokens/calls/spend).
 */
@Serializable
@Parcelize
data class BotUsageWindow(
    val label: String = "",
    val period: String? = null,
    @SerialName("used_percent")
    val usedPercent: Int? = null,
    @SerialName("resets_at")
    val resetsAt: String? = null,
    val detail: String? = null,
    val tokens: Long? = null,
    val calls: Long? = null,
    @SerialName("cost_cents")
    val costCents: Long? = null
) : Parcelable

/**
 * Usage limits a bot publishes for the inference account it runs on.
 */
@Serializable
@Parcelize
data class BotUsage(
    val provider: String = "",
    val model: String? = null,
    val plan: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val windows: List<BotUsageWindow> = emptyList(),
    val local: List<BotUsageWindow> = emptyList(),
    val notes: List<String> = emptyList()
) : Parcelable {

    val isEmpty: Boolean
        get() = windows.isEmpty() && local.isEmpty() && notes.isEmpty()
}
