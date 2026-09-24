package chat.stoat.internals.text

import chat.stoat.core.model.schemas.User
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionResolverTest {
    private val chris = User(id = "01CHRIS", username = "Christian", discriminator = "1234")
    private val test = User(id = "01TEST", username = "test", discriminator = "6100")
    private val otherTest = User(id = "01OTHERTEST", username = "Test", discriminator = "0001")
    private val users = listOf(chris, test)

    private fun replace(
        content: String,
        users: Collection<User> = this.users,
        inContext: Set<String> = emptySet()
    ) = MentionResolver.replaceMentions(content, users) { it in inContext }

    @Test
    fun replacesMentionWithDiscriminator() {
        assertEquals("hi <@01CHRIS>", replace("hi @Christian#1234"))
    }

    @Test
    fun replacesMentionWithoutDiscriminatorIgnoringCase() {
        assertEquals("hi <@01CHRIS> and <@01TEST>", replace("hi @christian and @TEST"))
    }

    @Test
    fun keepsTrailingPunctuation() {
        assertEquals("thanks <@01TEST>.", replace("thanks @test."))
        assertEquals("(<@01TEST>)", replace("(@test)"))
    }

    @Test
    fun leavesUnknownAndMassMentionsAlone() {
        assertEquals("@nobody @everyone @online", replace("@nobody @everyone @online"))
    }

    @Test
    fun doesNotMatchInsideEmailAddresses() {
        assertEquals("mail test@test.dk", replace("mail test@test.dk"))
    }

    @Test
    fun handlesMultipleLines() {
        assertEquals("hey\n<@01TEST>", replace("hey\n@test"))
    }

    @Test
    fun wrongDiscriminatorIsLeftAlone() {
        assertEquals("@test#9999", replace("@test#9999"))
    }

    @Test
    fun ambiguousUsernameUsesContextThenExactCase() {
        val all = users + otherTest
        assertEquals("<@01OTHERTEST>", replace("@test", all, inContext = setOf("01OTHERTEST")))
        assertEquals("<@01TEST>", replace("@test", all))
        assertEquals("@TEST", replace("@TEST", all))
        assertEquals("<@01OTHERTEST>", replace("@test#0001", all))
    }

    @Test
    fun doesNotReplacePrefixOfLongerMention() {
        val chr = User(id = "01CHR", username = "chr", discriminator = "0002")
        assertEquals("<@01CHR> <@01CHRIS>", replace("@chr @christian", users + chr))
    }
}
