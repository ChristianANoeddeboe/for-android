package chat.stoat.composables.markdown.prose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow

/**
 * Obsidian-style markdown tables for chat messages.
 *
 * The renderer's default table cells are single-line with `TextOverflow.Ellipsis`, so on narrow
 * phone screens every column collapses and long cells get cut off with "…" (e.g. "Sweep 58 typ…").
 * This override lets cell text wrap to as many lines as it needs, so the full table is always
 * readable. Very wide tables keep the upstream library's automatic horizontal scroll, exactly like
 * Obsidian: nothing is truncated, over-wide tables get a horizontal scrollbar instead.
 */
@Composable
fun StoatTable(model: MarkdownComponentModel) {
    MarkdownTable(
        content = model.content,
        node = model.node,
        style = model.typography.table,
        headerBlock = { content, header, tableWidth, style ->
            MarkdownTableHeader(
                content = content,
                header = header,
                tableWidth = tableWidth,
                style = style,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        },
        rowBlock = { content, header, tableWidth, style ->
            MarkdownTableRow(
                content = content,
                header = header,
                tableWidth = tableWidth,
                style = style,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        },
    )
}