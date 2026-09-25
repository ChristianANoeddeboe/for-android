package chat.stoat.core.model.schemas

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class MessagesInChannel(
    val messages: List<Message>? = null,
    val users: List<User>? = null,
    val members: List<Member>? = null
)

@Serializable
data class ServerUserChoice(
    val server: String,
    val user: String
)

@Serializable
data class Member(
    @SerialName("_id")
    val id: ServerUserChoice? = null,

    @SerialName("joined_at")
    val joinedAt: String? = null,

    val avatar: AutumnResource? = null,
    val roles: List<String>? = null,
    val nickname: String? = null,
    val pronouns: String? = null,

    val timeout: String? = null
) {
    fun mergeWithPartial(other: Member): Member {
        return Member(
            id = other.id ?: id,
            joinedAt = other.joinedAt ?: joinedAt,
            avatar = other.avatar ?: avatar,
            roles = other.roles ?: roles,
            nickname = other.nickname ?: nickname,
            pronouns = other.pronouns ?: pronouns,
            timeout = other.timeout ?: timeout
        )
    }

    fun timeoutTimestamp(): Instant? {
        return timeout?.let { Instant.parse(it) }
    }
}

@Serializable
data class Channel(
    @SerialName("_id")
    val id: String? = null,
    @SerialName("channel_type")
    val channelType: ChannelType? = null,
    val user: String? = null,
    val name: String? = null,
    val owner: String? = null,
    val description: String? = null,
    val recipients: List<String>? = null,
    val icon: AutumnResource? = null,
    @SerialName("last_message_id")
    val lastMessageID: String? = null,
    val active: Boolean? = null,
    val permissions: Long? = null,
    val server: String? = null,
    @SerialName("role_permissions")
    val rolePermissions: Map<String, PermissionDescription>? = null,
    @SerialName("default_permissions")
    val defaultPermissions: PermissionDescription? = null,
    val nsfw: Boolean? = null,
    val voice: VoiceInformation? = null,
    val slowmode: Long? = null,

    // Text and forum channels
    @SerialName("default_auto_archive_minutes")
    val defaultAutoArchiveMinutes: Int? = null,

    // Forum channels
    @SerialName("available_tags")
    val availableTags: List<ForumTag>? = null,
    @SerialName("require_tag")
    val requireTag: Boolean? = null,
    @SerialName("default_reaction_emoji")
    val defaultReactionEmoji: String? = null,
    @SerialName("default_sort_order")
    val defaultSortOrder: ForumSortOrder? = null,
    @SerialName("default_layout")
    val defaultLayout: ForumLayout? = null,
    @SerialName("default_thread_slowmode")
    val defaultThreadSlowmode: Long? = null,

    // Threads (forum posts are threads in a forum channel)
    val parent: String? = null,
    val private: Boolean? = null,
    val invitable: Boolean? = null,
    @SerialName("applied_tags")
    val appliedTags: List<String>? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    val locked: Boolean? = null,
    @SerialName("auto_archive_minutes")
    val autoArchiveMinutes: Int? = null,
    @SerialName("archived_at")
    val archivedAt: String? = null,
    @SerialName("message_count")
    val messageCount: Int? = null,
    @SerialName("member_count")
    val memberCount: Int? = null,

    val type: String? = null // this is _only_ used for websocket events!
) {
    val isThread: Boolean
        get() = channelType == ChannelType.Thread

    val isForum: Boolean
        get() = channelType == ChannelType.ForumChannel


    fun mergeWithPartial(partial: Channel): Channel {
        return Channel(
            channelType = partial.channelType ?: channelType,
            id = partial.id ?: id,
            user = partial.user ?: user,
            name = partial.name ?: name,
            owner = partial.owner ?: owner,
            description = partial.description ?: description,
            recipients = partial.recipients ?: recipients,
            icon = partial.icon ?: icon,
            lastMessageID = partial.lastMessageID ?: lastMessageID,
            active = partial.active ?: active,
            permissions = partial.permissions ?: permissions,
            server = partial.server ?: server,
            rolePermissions = partial.rolePermissions ?: rolePermissions,
            defaultPermissions = partial.defaultPermissions ?: defaultPermissions,
            nsfw = partial.nsfw ?: nsfw,
            voice = partial.voice ?: voice,
            slowmode = partial.slowmode ?: slowmode,
            defaultAutoArchiveMinutes = partial.defaultAutoArchiveMinutes
                ?: defaultAutoArchiveMinutes,
            availableTags = partial.availableTags ?: availableTags,
            requireTag = partial.requireTag ?: requireTag,
            defaultReactionEmoji = partial.defaultReactionEmoji ?: defaultReactionEmoji,
            defaultSortOrder = partial.defaultSortOrder ?: defaultSortOrder,
            defaultLayout = partial.defaultLayout ?: defaultLayout,
            defaultThreadSlowmode = partial.defaultThreadSlowmode ?: defaultThreadSlowmode,
            parent = partial.parent ?: parent,
            private = partial.private ?: private,
            invitable = partial.invitable ?: invitable,
            appliedTags = partial.appliedTags ?: appliedTags,
            pinned = partial.pinned ?: pinned,
            archived = partial.archived ?: archived,
            locked = partial.locked ?: locked,
            autoArchiveMinutes = partial.autoArchiveMinutes ?: autoArchiveMinutes,
            archivedAt = partial.archivedAt ?: archivedAt,
            messageCount = partial.messageCount ?: messageCount,
            memberCount = partial.memberCount ?: memberCount,
            type = partial.type ?: type
        )
    }
}

@Serializable
data class ForumTag(
    val id: String = "",
    val name: String,
    val emoji: String? = null,
    val moderated: Boolean = false
)

@Serializable
enum class ForumSortOrder {
    LatestActivity,
    CreationDate
}

@Serializable
enum class ForumLayout {
    List,
    Gallery
}

@Serializable
enum class ThreadNotify {
    Default,
    All,
    Mentions,
    None
}

@Serializable
data class ThreadMemberKey(
    val thread: String,
    val user: String
)

@Serializable
data class ThreadMember(
    @SerialName("_id")
    val id: ThreadMemberKey,
    @SerialName("joined_at")
    val joinedAt: String? = null,
    val notify: ThreadNotify = ThreadNotify.Default
)

@Serializable
data class ThreadList(
    val threads: List<Channel> = emptyList(),
    val members: List<ThreadMember> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
    val messages: List<Message> = emptyList(),
    val users: List<User> = emptyList()
)

@Serializable
data class VoiceInformation(
    @SerialName("max_users")
    val maxUsers: Int? = null
)

@Serializable
data class ChannelSlowmode(
    @SerialName("channel_id")
    val channelId: String,
    val duration: Long,
    @SerialName("retry_after")
    val retryAfter: Long
)

@Serializable
enum class ChannelType(val value: String) {
    DirectMessage("DirectMessage"),
    Group("Group"),
    SavedMessages("SavedMessages"),
    TextChannel("TextChannel"),
    VoiceChannel("VoiceChannel"),
    ForumChannel("ForumChannel"),
    Thread("Thread");

    companion object : KSerializer<ChannelType> {
        override val descriptor: SerialDescriptor
            get() {
                return PrimitiveSerialDescriptor(
                    "chat.stoat.core.model.schemas.ChannelType",
                    PrimitiveKind.STRING
                )
            }

        override fun deserialize(decoder: Decoder): ChannelType =
            when (val value = decoder.decodeString()) {
                "DirectMessage" -> DirectMessage
                "Group" -> Group
                "SavedMessages" -> SavedMessages
                "TextChannel" -> TextChannel
                "VoiceChannel" -> VoiceChannel
                "ForumChannel" -> ForumChannel
                "Thread" -> Thread
                else -> throw IllegalArgumentException("ChannelType could not parse: $value")
            }

        override fun serialize(encoder: Encoder, value: ChannelType) {
            return encoder.encodeString(value.value)
        }
    }
}

@Serializable
data class ChannelUserChoice(
    val channel: String,
    val user: String
)

@Serializable
data class ChannelUnreadResponse(
    @SerialName("_id")
    val id: ChannelUserChoice,
    val last_id: String? = null,
    val mentions: List<String>? = null
)

@Serializable
data class ChannelUnread(
    @SerialName("_id")
    val id: String,
    val last_id: String? = null,
    val mentions: List<String>? = null
)
