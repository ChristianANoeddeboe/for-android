package chat.stoat.screens.chat.views.thread

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ChannelUtils
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.Roles
import chat.stoat.api.internals.ULID
import chat.stoat.api.internals.has
import chat.stoat.api.routes.channel.CreateThreadBody
import chat.stoat.api.routes.channel.EditThreadBody
import chat.stoat.api.routes.channel.createThread
import chat.stoat.api.routes.channel.createThreadFromMessage
import chat.stoat.api.routes.channel.editThread
import chat.stoat.api.routes.channel.fetchActiveThreads
import chat.stoat.api.routes.channel.fetchArchivedThreads
import chat.stoat.api.routes.channel.joinThread
import chat.stoat.api.routes.channel.leaveDeleteOrCloseChannel
import chat.stoat.api.routes.channel.leaveThread
import chat.stoat.api.routes.channel.setThreadNotify
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.generic.SheetButton
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ThreadNotify
import chat.stoat.screens.chat.views.forum.ForumTagLabel
import kotlinx.coroutines.launch

/**
 * Permissions of the current user in a channel
 */
fun selfPermissions(channel: Channel): Long {
    val member = channel.server?.let { server ->
        StoatAPI.selfId?.let { StoatAPI.members.getMember(server, it) }
    }
    return Roles.permissionFor(channel, StoatAPI.userCache[StoatAPI.selfId], member)
}

/**
 * Whether the current user may rename, retag and close a thread
 */
fun canEditThread(thread: Channel, permissions: Long): Boolean {
    return permissions has PermissionBit.ManageChannel ||
            (thread.owner == StoatAPI.selfId && thread.locked != true)
}

/**
 * Whether the thread is a post in a forum channel
 */
val Channel.isForumPost: Boolean
    get() = StoatAPI.channelCache[parent]?.isForum == true

/**
 * Time since the last activity, "just now" within the first minute
 */
fun relativeActivityTime(context: Context, millis: Long): String {
    val now = System.currentTimeMillis()
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) {
        return context.getString(R.string.thread_just_now)
    }
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

fun showThreadError(context: Context, e: Exception) {
    Toast.makeText(
        context,
        context.getString(R.string.thread_error, e.message),
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * Chip shown below a message that started a thread
 */
@Composable
fun ThreadChip(threadId: String) {
    val thread = StoatAPI.channelCache[threadId] ?: return
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { scope.launch { ActionChannel.send(Action.SwitchChannel(threadId)) } }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chat_24dp),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = thread.name ?: "",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = pluralStringResource(
                R.plurals.forum_post_messages,
                thread.messageCount ?: 0,
                thread.messageCount ?: 0
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Dialog to start a thread, from a message or on its own
 *
 * @param channelId Text channel to start the thread in
 * @param messageId Message to start the thread from, null for a standalone thread
 */
@Composable
fun CreateThreadDialog(channelId: String, messageId: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val permissions = remember(channelId) { channel?.let { selfPermissions(it) } ?: 0L }
    val starter = messageId?.let { StoatAPI.messageCache[it] }

    var name by remember {
        mutableStateOf(starter?.content?.lineSequence()?.firstOrNull()?.take(100) ?: "")
    }
    var private by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val idempotencyKey = remember { ULID.makeNext() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.thread_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text(stringResource(R.string.thread_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (messageId == null && permissions has PermissionBit.CreatePrivateThreads) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.toggleable(
                            value = private,
                            role = Role.Switch,
                            onValueChange = { private = it }
                        )
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.thread_private))
                            Text(
                                text = stringResource(R.string.thread_private_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = private, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        try {
                            val thread = if (messageId != null) {
                                createThreadFromMessage(channelId, messageId, name.trim())
                            } else {
                                createThread(
                                    channelId,
                                    CreateThreadBody(name = name.trim(), private = private),
                                    idempotencyKey
                                )
                            }
                            onDismiss()
                            ActionChannel.send(Action.SwitchChannel(thread.id!!))
                        } catch (e: Exception) {
                            showThreadError(context, e)
                        } finally {
                            submitting = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.thread_create_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Actions for a thread or forum post
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadOptionsSheet(threadId: String, onHideSheet: suspend () -> Unit) {
    val thread = StoatAPI.channelCache[threadId] ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = selfPermissions(thread)
    val manage = permissions has PermissionBit.ManageChannel
    val canEdit = canEditThread(thread, permissions)
    val forumPost = thread.isForumPost
    val member = StoatAPI.threadMembers[threadId]

    var showRename by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                onHideSheet()
            } catch (e: Exception) {
                showThreadError(context, e)
            }
        }
    }

    Column(Modifier.padding(bottom = 16.dp)) {
        Text(
            text = thread.name ?: "",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (member == null) {
            SheetButton(
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_login_24dp), contentDescription = null)
                },
                headlineContent = {
                    Text(stringResource(if (forumPost) R.string.thread_follow else R.string.thread_join))
                },
                onClick = { run { joinThread(threadId) } }
            )
        } else {
            Text(
                text = stringResource(R.string.thread_notifications),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val current = member.notify.takeIf { it != ThreadNotify.Default } ?: ThreadNotify.All
            listOf(
                ThreadNotify.All to R.string.thread_notify_all,
                ThreadNotify.Mentions to R.string.thread_notify_mentions,
                ThreadNotify.None to R.string.thread_notify_none
            ).forEach { (value, label) ->
                SheetButton(
                    leadingContent = { RadioButton(selected = current == value, onClick = null) },
                    headlineContent = { Text(stringResource(label)) },
                    onClick = { run { setThreadNotify(threadId, value) } }
                )
            }
            SheetButton(
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_logout_24dp), contentDescription = null)
                },
                headlineContent = {
                    Text(stringResource(if (forumPost) R.string.thread_unfollow else R.string.thread_leave))
                },
                onClick = { run { leaveThread(threadId) } }
            )
        }

        if (canEdit) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetButton(
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_edit_24dp), contentDescription = null)
                },
                headlineContent = { Text(stringResource(R.string.thread_rename)) },
                onClick = { showRename = true }
            )
            if (forumPost && !StoatAPI.channelCache[thread.parent]?.availableTags.isNullOrEmpty()) {
                SheetButton(
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_tag_24dp), contentDescription = null)
                    },
                    headlineContent = { Text(stringResource(R.string.thread_edit_tags)) },
                    onClick = { showTags = true }
                )
            }
            val archived = thread.archived == true
            SheetButton(
                leadingContent = {
                    Icon(
                        painterResource(if (archived) R.drawable.ic_unarchive_24dp else R.drawable.ic_archive_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(
                        stringResource(
                            when {
                                archived && forumPost -> R.string.thread_open_post
                                archived -> R.string.thread_open
                                forumPost -> R.string.thread_close_post
                                else -> R.string.thread_close
                            }
                        )
                    )
                },
                onClick = { run { editThread(threadId, EditThreadBody(archived = !archived)) } }
            )
        }

        if (manage) {
            val locked = thread.locked == true
            SheetButton(
                leadingContent = {
                    Icon(
                        painterResource(if (locked) R.drawable.ic_lock_open_24dp else R.drawable.ic_lock_24dp),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text(stringResource(if (locked) R.string.thread_unlock else R.string.thread_lock))
                },
                onClick = { run { editThread(threadId, EditThreadBody(locked = !locked)) } }
            )
            if (forumPost) {
                val pinned = thread.pinned == true
                SheetButton(
                    leadingContent = {
                        Icon(
                            painterResource(if (pinned) R.drawable.ic_keep_off_24dp else R.drawable.ic_keep_24dp),
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(stringResource(if (pinned) R.string.thread_unpin_post else R.string.thread_pin_post))
                    },
                    onClick = { run { editThread(threadId, EditThreadBody(pinned = !pinned)) } }
                )
            }
            SheetButton(
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = null)
                },
                headlineContent = {
                    Text(stringResource(if (forumPost) R.string.thread_delete_post else R.string.thread_delete))
                },
                dangerous = true,
                onClick = { showDelete = true }
            )
        }
    }

    if (showRename) {
        var name by remember { mutableStateOf(thread.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.thread_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    showRename = false
                    run { editThread(threadId, EditThreadBody(name = name.trim())) }
                }) { Text(stringResource(R.string.thread_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showTags) {
        val forum = StoatAPI.channelCache[thread.parent]
        val selectable = forum?.availableTags
            ?.filter { !it.moderated || manage || thread.appliedTags?.contains(it.id) == true }
            ?: emptyList()
        val tags = remember { mutableStateListOf(*(thread.appliedTags ?: emptyList()).toTypedArray()) }
        AlertDialog(
            onDismissRequest = { showTags = false },
            title = { Text(stringResource(R.string.thread_edit_tags)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectable.forEach { tag ->
                        FilterChip(
                            selected = tag.id in tags,
                            enabled = !tag.moderated || manage,
                            onClick = {
                                if (tag.id in tags) tags.remove(tag.id)
                                else if (tags.size < 5) tags.add(tag.id)
                            },
                            label = { ForumTagLabel(tag) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = forum?.requireTag != true || tags.isNotEmpty(),
                    onClick = {
                        showTags = false
                        run { editThread(threadId, EditThreadBody(appliedTags = tags.toList())) }
                    }
                ) { Text(stringResource(R.string.thread_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTags = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = {
                Text(stringResource(if (forumPost) R.string.thread_delete_post else R.string.thread_delete))
            },
            text = { Text(stringResource(R.string.thread_delete_confirm, thread.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    val parent = thread.parent
                    run {
                        leaveDeleteOrCloseChannel(threadId)
                        parent?.let { ActionChannel.send(Action.SwitchChannel(it)) }
                    }
                }) {
                    Text(
                        stringResource(R.string.thread_delete_submit),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * Threads of a text channel: joined, active and archived
 */
@Composable
fun ThreadsListSheet(channelId: String, onHideSheet: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId] ?: return
    val permissions = remember(channelId) { selfPermissions(channel) }

    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val archivedIds = remember { mutableStateListOf<String>() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(channelId, tab) {
        loading = true
        try {
            when (tab) {
                1 -> fetchActiveThreads(channelId)
                2 -> {
                    val list = fetchArchivedThreads(channelId, limit = 50)
                    archivedIds.clear()
                    archivedIds.addAll(list.threads.mapNotNull { it.id })
                }
            }
        } catch (e: Exception) {
            showThreadError(context, e)
        } finally {
            loading = false
        }
    }

    val threads = when (tab) {
        0 -> ChannelUtils.joinedThreads(channelId)
        1 -> StoatAPI.channelCache.values
            .filter { it.isThread && it.parent == channelId && it.archived != true }
            .sortedByDescending { it.lastMessageID ?: it.id }

        else -> archivedIds.mapNotNull { StoatAPI.channelCache[it] }.filter { it.archived == true }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.threads),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (permissions has PermissionBit.CreatePublicThreads) {
                TextButton(onClick = { showCreate = true }) {
                    Text(stringResource(R.string.thread_create))
                }
            }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.threads_joined, R.string.threads_active, R.string.threads_archived)
                .forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(stringResource(label)) }
                    )
                }
        }
        LazyColumn(Modifier.heightIn(min = 200.dp, max = 600.dp)) {
            if (loading && threads.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { CircularProgressIndicator() }
                }
            } else if (threads.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.threads_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            items(threads, key = { it.id!! }) { thread ->
                ThreadRow(thread) {
                    scope.launch {
                        onHideSheet()
                        ActionChannel.send(Action.SwitchChannel(thread.id!!))
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateThreadDialog(channelId = channelId, messageId = null, onDismiss = {
            showCreate = false
            scope.launch { onHideSheet() }
        })
    }
}

@Composable
private fun ThreadRow(thread: Channel, onClick: () -> Unit) {
    val context = LocalContext.current
    val lastActivity = remember(thread.lastMessageID, thread.id) {
        runCatching { ULID.asTimestamp(thread.lastMessageID ?: thread.id!!) }.getOrNull()
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (thread.private == true) R.drawable.ic_lock_24dp else R.drawable.ic_chat_24dp
                ),
                contentDescription = null
            )
        },
        headlineContent = {
            Text(thread.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    pluralStringResource(
                        R.plurals.forum_post_messages,
                        thread.messageCount ?: 0,
                        thread.messageCount ?: 0
                    ),
                    lastActivity?.let {
                        relativeActivityTime(context, it)
                    }
                ).joinToString(" · ")
            )
        }
    )
}
