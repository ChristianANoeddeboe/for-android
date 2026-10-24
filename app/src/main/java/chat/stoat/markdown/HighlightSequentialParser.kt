package chat.stoat.markdown

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

/**
 * Turns `==text==` runs inside a plain-text token into a single [HIGHLIGHT_ELEMENT_TYPE] leaf.
 *
 * The intellij GFM tokenizer does not single `=` out, so a run like `hello ==world== there`
 * arrives as a single `MarkdownTokenTypes.TEXT` token containing the whole `==world==` span.
 * A sequential parser can only create nodes over whole tokens (not character sub-ranges), so
 * the cleanest mapping is: whenever a TEXT token *contains* a paired `==...==` span, collapse the
 * whole token into one HIGHLIGHT element. The `ChatMarkdown` annotator then splits that token into
 * highlighted and plain segments when rendering. Tokens with no valid pair are left as text, so
 * unpaired `==` renders literally (Obsidian requires both delimiters).
 */
class HighlightSequentialParser(private val content: String) : SequentialParser {

    override fun parse(
        tokens: TokensCache,
        rangesToGlue: List<IntRange>,
    ): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val delegateIndices = RangesListBuilder()
        var iterator = tokens.RangesListIterator(rangesToGlue)

        while (iterator.type != null) {
            if (iterator.type == MarkdownTokenTypes.TEXT && iterSectionContainsHighlight(iterator)) {
                result.withNode(
                    SequentialParser.Node(
                        iterator.index..iterator.index + 1,
                        HIGHLIGHT_ELEMENT_TYPE
                    )
                )
                iterator = iterator.advance()
                continue
            }
            delegateIndices.put(iterator.index)
            iterator = iterator.advance()
        }

        return result.withFurtherProcessing(delegateIndices.get())
    }

    /** True if the token text contains at least one paired `==nonEqualsText==` span. */
    private fun iterSectionContainsHighlight(section: TokensCache.Iterator): Boolean {
        val text = content.substring(section.start, section.end)
        var i = 0
        while (i < text.length) {
            val open = text.indexOf("==", i)
            if (open == -1) return false
            val close = text.indexOf("==", open + 2)
            if (close == -1) return false
            val inner = text.substring(open + 2, close)
            if (inner.isNotEmpty() && !inner.contains('=') && !inner.contains('\n')) return true
            i = close + 2
        }
        return false
    }
}