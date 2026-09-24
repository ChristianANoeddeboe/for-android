package chat.stoat.internals.text

import chat.stoat.core.model.schemas.User

object MentionResolver {
    // Matches @username or @username#0000 at the start of the text or after whitespace/opening bracket
    private val MentionRegex = Regex("(?<![^\\s(\\[])@((?:\\p{L}|[\\d_.-])+)(?:#([0-9]{4}))?")
    private val MassMentions = setOf("everyone", "online", "here")

    /**
     * Replaces textual user mentions with <@userId>. Mentions that cannot be resolved to exactly
     * one user are left as-is. [isInContext] is used to pick between users sharing a username.
     */
    fun replaceMentions(
        content: String,
        users: Collection<User>,
        isInContext: (String) -> Boolean
    ): String {
        return MentionRegex.replace(content) { match ->
            val name = match.groupValues[1]
            val discriminator = match.groupValues[2].ifEmpty { null }

            if (discriminator == null && name.lowercase() in MassMentions) return@replace match.value

            resolveMentionedUser(name, discriminator, users, isInContext)
                ?.let { return@replace "<@$it>" }

            // A trailing "." or "-" is more likely punctuation than part of the username
            val trimmed = name.trimEnd('.', '-')
            if (discriminator == null && trimmed != name && trimmed.isNotEmpty()) {
                resolveMentionedUser(trimmed, null, users, isInContext)
                    ?.let { return@replace "<@$it>" + name.substring(trimmed.length) }
            }

            match.value
        }
    }

    private fun resolveMentionedUser(
        name: String,
        discriminator: String?,
        users: Collection<User>,
        isInContext: (String) -> Boolean
    ): String? {
        val candidates = users.filter {
            it.id != null && it.username.equals(name, ignoreCase = true) &&
                (discriminator == null || it.discriminator == discriminator)
        }.distinctBy { it.id }

        candidates.singleOrNull()?.let { return it.id }

        // Several users share this username (possibly in different casing or with different
        // discriminators), so prefer the ones in the current server or conversation
        val inContext = candidates.filter { isInContext(it.id!!) }.ifEmpty { candidates }
        inContext.singleOrNull()?.let { return it.id }

        return inContext.filter { it.username == name }.singleOrNull()?.id
    }
}
