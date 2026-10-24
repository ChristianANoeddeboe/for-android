package chat.stoat.markdown

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager

/**
 * Stoat's GFM flavour.
 *
 * [enableObsidian] additionally registers the `==highlight==` parser (and, implicitly, the
 * callout handling that ChatMarkdown wires up on top of the parsed blockquotes). It defaults to
 * false so every existing caller — e.g. ChannelScreenViewModel's link extraction — keeps its exact
 * current behaviour; only the chat renderer enables it when the "Obsidian" engine is chosen.
 */
class StoatMarkdownFlavour(
    private val content: String,
    private val enableObsidian: Boolean = false,
) : GFMFlavourDescriptor() {
    override val sequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> {
            val custom = mutableListOf<SequentialParser>(
                MentionSequentialParser(content),
                TimestampSequentialParser(content),
                CustomEmoteSequentialParser(content),
            )
            if (enableObsidian) custom += HighlightSequentialParser(content)
            return custom + super@StoatMarkdownFlavour.sequentialParserManager.getParserSequence()
        }
    }
}
