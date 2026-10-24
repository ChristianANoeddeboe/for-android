package chat.stoat.markdown

import org.intellij.markdown.IElementType

/**
 * Custom inline element type for Obsidian-style `==highlight==` spans.
 *
 * Mirroring [CUSTOM_EMOTE_ELEMENT_TYPE], this is a leaf element produced by
 * [HighlightSequentialParser] (registered in [StoatMarkdownFlavour]) and consumed inside the
 * `markdownAnnotator` of `ChatMarkdown`, which renders it with a background via `withStyle`.
 *
 * The parser that produces it is only registered when the "Obsidian" markdown engine is
 * selected, so the default ("Standard") renderer never sees this type and stays unchanged.
 */
val HIGHLIGHT_ELEMENT_TYPE: IElementType = IElementType("HIGHLIGHT")