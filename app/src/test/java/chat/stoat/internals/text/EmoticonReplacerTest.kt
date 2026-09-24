package chat.stoat.internals.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmoticonReplacerTest {
    private fun convertBeforeSpace(text: String): String? {
        val conversion = EmoticonReplacer.findBefore(text, text.length) ?: return null
        return text.replaceRange(conversion.start, conversion.end, conversion.emoji)
    }

    @Test
    fun convertsStandaloneEmoticons() {
        assertEquals("love you ❤️", convertBeforeSpace("love you <3"))
        assertEquals("🙂", convertBeforeSpace(":)"))
        assertEquals("line\n😄", convertBeforeSpace("line\n:D"))
        assertEquals("💔", convertBeforeSpace("</3"))
    }

    @Test
    fun keepsTrailingPunctuation() {
        assertEquals("nice 🙂!", convertBeforeSpace("nice :)!"))
        assertEquals("ok ❤️.", convertBeforeSpace("ok <3."))
    }

    @Test
    fun findsWordBeforeCursorNotAtEnd() {
        val text = "hi <3 there"
        val conversion = EmoticonReplacer.findBefore(text, 5)
        assertEquals(EmoticonReplacer.Conversion(3, 5, "<3", "❤️"), conversion)
    }

    @Test
    fun ignoresEmoticonsInsideWordsAndUrls() {
        assertNull(convertBeforeSpace("a<3"))
        assertNull(convertBeforeSpace("https://example.com"))
        assertNull(convertBeforeSpace("see https:/"))
        assertNull(convertBeforeSpace("<3a"))
        assertNull(convertBeforeSpace("hello"))
        assertNull(convertBeforeSpace(""))
    }

    @Test
    fun ignoresCode() {
        assertNull(convertBeforeSpace("`<3"))
        assertNull(convertBeforeSpace("try `a :)"))
        assertNull(convertBeforeSpace("```\ncode :)"))
        assertNull(convertBeforeSpace("```kotlin\nval x = 1\n<3"))
        assertEquals("`code` ❤️", convertBeforeSpace("`code` <3"))
        assertEquals("```\nx\n```\n❤️", convertBeforeSpace("```\nx\n```\n<3"))
    }
}
