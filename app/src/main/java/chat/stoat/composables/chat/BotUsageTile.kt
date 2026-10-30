package chat.stoat.composables.chat

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.core.model.schemas.BotUsage
import chat.stoat.core.model.schemas.BotUsageWindow
import kotlinx.datetime.Instant

/**
 * Compact preview shown inside a collapsed [chat.stoat.composables.sheets.SheetTile]:
 * provider plus the first couple of quota windows.
 */
@Composable
fun BotUsagePreview(usage: BotUsage) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = subtitle(usage),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (usage.windows.isEmpty()) {
            val note = usage.notes.firstOrNull()
                ?: stringResource(R.string.bot_usage_unavailable)
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            for (window in usage.windows) {
                val used = window.usedPercent
                val suffix = if (used == null) {
                    stringResource(R.string.bot_usage_unavailable)
                } else {
                    "${100 - used}% left"
                }

                Text(
                    text = "${window.label} · $suffix",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Full expanded content: every provider quota window with a bar and its reset
 * instant, then the bot's own recent consumption.
 */
@Composable
fun BotUsageContent(usage: BotUsage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = subtitle(usage),
            style = MaterialTheme.typography.labelMedium
        )

        for (window in usage.windows) {
            LimitRow(window)
        }

        if (usage.local.isNotEmpty()) {
            Text(
                text = stringResource(R.string.bot_usage_this_bot),
                style = MaterialTheme.typography.labelMedium
            )

            for (window in usage.local) {
                Text(
                    text = stringResource(
                        R.string.bot_usage_consumption,
                        window.label,
                        window.calls ?: 0L,
                        formatTokens(window.tokens)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        for (note in usage.notes) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        usage.updatedAt?.let {
            Text(
                text = stringResource(R.string.bot_usage_updated, relative(it)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LimitRow(window: BotUsageWindow) {
    val used = window.usedPercent

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = window.label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (used == null) {
                    stringResource(R.string.bot_usage_unavailable)
                } else {
                    "${100 - used}% left · $used% used"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (used != null) {
            LinearProgressIndicator(
                progress = { (used.coerceIn(0, 100)) / 100f },
                color = usageColor(used),
                // The Material3 stop indicator renders as a stray dot past the
                // track end at this width; the percentage label carries it.
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth()
            )
        }

        val resetsAt = window.resetsAt
        if (resetsAt != null) {
            Text(
                text = stringResource(R.string.bot_usage_resets, relative(resetsAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            window.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun subtitle(usage: BotUsage): String {
    val parts = listOfNotNull(
        usage.provider.ifBlank { null },
        usage.plan,
        usage.model
    )
    return parts.joinToString(" · ")
}

@Composable
private fun usageColor(used: Int): Color = when {
    used >= 90 -> MaterialTheme.colorScheme.error
    used >= 70 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun formatTokens(value: Long?): String = when {
    value == null -> "0"
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> "${Math.round(value / 1_000.0)}k"
    else -> value.toString()
}

private fun relative(iso: String?): String {
    if (iso.isNullOrBlank()) return "unknown"

    return try {
        val millis = Instant.parse(iso).toEpochMilliseconds()
        val raw = DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()

        // DateUtils capitalises "In 2 hours"; only that prefix is lowered so an
        // absolute date keeps its month capitalised ("Nov 11, 2026").
        if (raw.startsWith("In ")) "in " + raw.removePrefix("In ") else raw
    } catch (e: Exception) {
        iso
    }
}
