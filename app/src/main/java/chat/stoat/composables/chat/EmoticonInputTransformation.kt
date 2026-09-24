package chat.stoat.composables.chat

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import chat.stoat.internals.text.EmoticonReplacer

/**
 * Converts emoticons to emoji as the user types, like Discord: typing `<3` followed by a space
 * turns it into ❤️. A backspace straight after a conversion brings the emoticon back, and that
 * emoticon is then left alone. Only user input runs through here, so text inserted by the app
 * (drafts, autocomplete) is never converted.
 */
class EmoticonInputTransformation : InputTransformation {
    private class Applied(
        val start: Int,
        val emoticon: String,
        val emoji: String,
        val result: String,
    )

    private var lastApplied: Applied? = null
    private var undone: Pair<Int, String>? = null

    override fun TextFieldBuffer.transformInput() {
        val original = originalText.toString()
        val text = asCharSequence().toString()
        val last = lastApplied
        lastApplied = null

        if (text.isEmpty()) {
            undone = null
            return
        }

        // Backspace right after a conversion removes the typed whitespace: restore the emoticon
        if (last != null && original == last.result) {
            val whitespaceAt = last.start + last.emoji.length
            if (whitespaceAt < original.length &&
                text == original.removeRange(whitespaceAt, whitespaceAt + 1)
            ) {
                replace(last.start, whitespaceAt, last.emoticon)
                selection = TextRange(last.start + last.emoticon.length)
                undone = last.start to last.emoticon
                return
            }
        }

        // Only react to a single whitespace character typed right after a word
        val cursor = selection
        if (text.length != original.length + 1 || !cursor.collapsed || cursor.min == 0) return
        if (!text[cursor.min - 1].isWhitespace()) return

        val conversion = EmoticonReplacer.findBefore(text, cursor.min - 1) ?: return
        if (undone == conversion.start to conversion.emoticon) return

        replace(conversion.start, conversion.end, conversion.emoji)
        selection = TextRange(cursor.min - conversion.emoticon.length + conversion.emoji.length)
        lastApplied = Applied(
            start = conversion.start,
            emoticon = conversion.emoticon,
            emoji = conversion.emoji,
            result = asCharSequence().toString(),
        )
    }

    /**
     * Converts an emoticon at the very end of [state]'s text, for messages sent without typing
     * a space after it. Returns true if the text changed.
     */
    fun convertTrailing(state: TextFieldState): Boolean {
        val text = state.text
        val conversion = EmoticonReplacer.findBefore(text, text.length) ?: return false
        if (undone == conversion.start to conversion.emoticon) return false

        state.edit { replace(conversion.start, conversion.end, conversion.emoji) }
        return true
    }
}
