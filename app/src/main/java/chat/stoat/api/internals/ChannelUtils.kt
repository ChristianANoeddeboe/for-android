package chat.stoat.api.internals

import chat.stoat.api.StoatAPI
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.Server
import chat.stoat.core.model.schemas.User

sealed class CategorisedChannelList {
    data class Channel(val channel: chat.stoat.core.model.schemas.Channel) :
        CategorisedChannelList()

    data class Category(val category: chat.stoat.core.model.schemas.Category) :
        CategorisedChannelList()

    /**
     * A thread the user has joined, listed under its parent channel
     */
    data class Thread(val thread: chat.stoat.core.model.schemas.Channel) :
        CategorisedChannelList()
}

object ChannelUtils {
    /**
     * Resolves the name of a channel, preferring the name of the channel itself, then the name of the first recipient.
     * @param channel The channel to resolve the name of.
     * @return The name of the channel, or the name of the first recipient if the channel is a DM.
     * @see User.resolveDefaultName
     */
    fun resolveName(channel: Channel): String? {
        return channel.name
            ?: StoatAPI.userCache[channel.recipients?.first { u -> u != StoatAPI.selfId }]?.let {
                User.resolveDefaultName(
                    it
                )
            }
    }

    fun resolveDMPartner(channel: Channel): String? {
        return channel.recipients?.firstOrNull { u -> u != StoatAPI.selfId }
    }

    fun categoriseServerFlat(server: Server): List<CategorisedChannelList> {
        val output = mutableListOf<CategorisedChannelList>()

        val uncategorised =
            server.channels?.filter { c ->
                server.categories?.none { cat ->
                    cat.channels?.contains(
                        c
                    ) == true
                } ?: true
            }
                ?.mapNotNull { StoatAPI.channelCache[it] } ?: emptyList()
        uncategorised.forEach { addWithThreads(output, it) }

        val categories =
            server.categories?.map { CategorisedChannelList.Category(it) } ?: emptyList()
        categories.forEach {
            output.add(it)
            it.category.channels
                ?.mapNotNull { c -> StoatAPI.channelCache[c] }
                ?.forEach { c -> addWithThreads(output, c) }
        }

        return output
    }

    /**
     * Joined, open threads of a channel, most recently active first
     */
    fun joinedThreads(channelId: String): List<Channel> {
        return StoatAPI.channelCache.values
            .filter {
                it.isThread && it.parent == channelId && it.archived != true &&
                    it.id in StoatAPI.threadMembers
            }
            .sortedByDescending { it.lastMessageID ?: it.id }
    }

    private fun addWithThreads(output: MutableList<CategorisedChannelList>, channel: Channel) {
        output.add(CategorisedChannelList.Channel(channel))
        channel.id?.let { id ->
            joinedThreads(id).forEach { output.add(CategorisedChannelList.Thread(it)) }
        }
    }
}
