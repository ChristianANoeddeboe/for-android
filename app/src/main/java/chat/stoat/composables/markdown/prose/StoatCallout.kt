package chat.stoat.composables.markdown.prose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Obsidian-style callouts for chat messages (`> [!type] Title` followed by a body).
 *
 * The GFM parser already parses such a marker as a plain [org.intellij.markdown.MarkdownElementTypes.BLOCK_QUOTE]
 * whose first line is the literal `[!type] Title` text. ChatMarkdown overrides the `blockQuote`
 * component and, when the marker matches, routes here instead of the default blockquote. This draws
 * a rounded, tinted box with a coloured accent bar on the left (the colour varies by callout type,
 * mirroring Obsidian's palette and the web client's obsidian.css), a bold title, then the remaining
 * body rendered through the normal chat markdown pipeline so mentions, emotes, links etc. still work.
 */
data class StoatCallout(
    val type: String,
    val title: String,
    val body: String,
)

/**
 * The callout types the web client recognises (Obsidian's set). Anything else renders as a plain
 * blockquote, exactly like the web renderer.
 */
private val CALLOUT_TYPES = setOf(
    "note", "abstract", "summary", "tldr", "info", "todo",
    "tip", "hint", "important", "success", "check", "done",
    "question", "help", "faq",
    "warning", "caution", "attention",
    "failure", "fail", "missing", "danger", "error", "bug",
    "example", "quote",
)

/**
 * Parses the raw text of a blockquote node. Returns a [StoatCallout] when its first line is a
 * supported `> [!type] Title?` marker, otherwise null (=> render a normal blockquote).
 */
fun parseStoatCallout(rawBlockQuote: String): StoatCallout? {
    val lines = rawBlockQuote.split('\n')
    val firstLine = lines.first().trim()
    if (!firstLine.startsWith(">")) return null
    val marker = firstLine.removePrefix(">").trim()

    if (!marker.startsWith("[!")) return null
    val close = marker.indexOf(']')
    if (close <= 2) return null
    val type = marker.substring(2, close).lowercase()
    if (type !in CALLOUT_TYPES) return null

    val rest = marker.substring(close + 1).trim()
    val title = if (rest.isNotEmpty()) rest else type

    // Body = every remaining line with its leading blockquote marker stripped.
    val body = lines.drop(1)
        .map { line -> line.trimStart().removePrefix(">").trimStart() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    return StoatCallout(type, title, body)
}

/** Accent colour for a callout type, mirroring the web client's obsidian.css palette. */
@Composable
private fun accentFor(type: String): Color = when (type) {
    "note", "todo" -> MaterialTheme.colorScheme.primary
    "abstract", "summary", "tldr", "info" -> MaterialTheme.colorScheme.tertiary
    "tip", "hint", "important", "success", "check", "done" -> Color(0xff4caf50)
    "question", "help", "faq" -> Color(0xff03a9f4)
    "warning", "caution", "attention" -> Color(0xffff9800)
    "failure", "fail", "missing", "danger", "error", "bug" -> Color(0xfff44336)
    "example" -> MaterialTheme.colorScheme.secondary
    "quote" -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun StoatCallout(
    callout: StoatCallout,
    serverId: String?,
    fontSizeMultiplier: Float,
) {
    val accent = accentFor(callout.type)
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.08f), shape)
    ) {
        // Left accent bar (Obsidian's border-inline-start).
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Min)
                .background(accent, shape)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = callout.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontSizeMultiplier,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            if (callout.body.isNotEmpty()) {
                Column(Modifier.padding(top = 6.dp)) {
                    ChatMarkdown(
                        content = callout.body,
                        serverId = serverId,
                        fontSizeMultiplier = fontSizeMultiplier,
                    )
                }
            }
        }
    }
}