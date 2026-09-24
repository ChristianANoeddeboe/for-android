package chat.stoat.internals.text

/**
 * Turns typed emoticons like `<3` or `:)` into emoji, similar to Discord.
 *
 * Only standalone emoticons are converted: the emoticon must start the text or follow
 * whitespace, and may only be followed by sentence punctuation (`.`, `,`, `!`, `?`), so URLs
 * like `https://` and text like `a<3` are left alone. Inline code and code blocks, including ones
 * that are still being typed, are never touched.
 */
object EmoticonReplacer {
    data class Conversion(
        /** Index of the emoticon's first character. */
        val start: Int,
        /** Index after the emoticon's last character. */
        val end: Int,
        val emoticon: String,
        val emoji: String,
    )

    private val Emoticons: Map<String, String> = buildMap {
        fun add(emoji: String, vararg emoticons: String) = emoticons.forEach { put(it, emoji) }

        add("❤️", "<3")
        add("💔", "</3")
        add("🙂", ":)", ":-)", "(:")
        add("🙁", ":(", ":-(")
        add("😄", ":D", ":-D")
        add("😆", "xD", "XD")
        add("😉", ";)", ";-)")
        add("😛", ":P", ":p", ":-P", ":-p")
        add("😜", ";P", ";p", ";-P", ";-p")
        add("😮", ":O", ":o", ":-O", ":-o")
        add("😐", ":|", ":-|")
        add("😕", ":/", ":-/", ":\\", ":-\\")
        add("😢", ":'(", ";(", ";-(")
        add("😂", ":')")
        add("😗", ":*", ":-*")
        add("😎", "B-)")
        add("😠", ">:(", ">:-(")
        add("😇", "O:)", "o:)")
        add("😈", "]:)", ">:)")
        add("😳", ":$")
    }

    private const val TRAILING_PUNCTUATION = ".,!?"

    /**
     * Finds a standalone emoticon whose word ends at [end] (exclusive), such as the word right
     * before a space the user just typed. Returns null if there is none or it is inside code.
     */
    fun findBefore(text: CharSequence, end: Int): Conversion? {
        if (end <= 0 || end > text.length) return null

        var start = end
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        if (start == end) return null

        val word = text.substring(start, end)
        val emoticon = when {
            word in Emoticons -> word
            else -> word.trimEnd { it in TRAILING_PUNCTUATION }.takeIf { it in Emoticons }
        } ?: return null

        if (isInsideCode(text, start)) return null

        return Conversion(
            start = start,
            end = start + emoticon.length,
            emoticon = emoticon,
            emoji = Emoticons.getValue(emoticon),
        )
    }

    /** Whether [index] falls inside a code block or inline code, closed or not. */
    private fun isInsideCode(text: CharSequence, index: Int): Boolean {
        val before = text.substring(0, index)
        val fences = Regex("```").findAll(before).count()
        if (fences % 2 == 1) return true

        val line = before.substringAfterLast('\n').replace("```", "")
        return line.count { it == '`' } % 2 == 1
    }
}
