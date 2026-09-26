package chat.stoat.screens.settings.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.channel.EditChannelSettingsBody
import chat.stoat.api.routes.channel.EditWebhookBody
import chat.stoat.api.routes.channel.Webhook
import chat.stoat.api.routes.channel.createWebhook
import chat.stoat.api.routes.channel.deleteWebhook
import chat.stoat.api.routes.channel.editChannelSettings
import chat.stoat.api.routes.channel.editWebhook
import chat.stoat.api.routes.channel.fetchWebhooks
import chat.stoat.composables.generic.ListHeader
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.data.STOAT_BASE
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.ForumLayout
import chat.stoat.core.model.schemas.ForumSortOrder
import chat.stoat.core.model.schemas.ForumTag
import chat.stoat.screens.settings.server.ConfirmDialog
import chat.stoat.screens.settings.server.ManageError
import chat.stoat.screens.settings.server.ManageScaffold
import chat.stoat.screens.settings.server.OptionPickerItem
import chat.stoat.screens.settings.server.SwitchItem
import chat.stoat.screens.settings.server.TextInputDialog
import chat.stoat.screens.settings.server.copyWithToast
import chat.stoat.screens.settings.server.formatSeconds
import kotlinx.coroutines.launch

@Composable
fun ChannelSettingsWebhooks(navController: NavController, channelId: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var webhooks by remember { mutableStateOf<List<Webhook>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Webhook?>(null) }
    var deleting by remember { mutableStateOf<Webhook?>(null) }

    LaunchedEffect(channelId) {
        try {
            webhooks = fetchWebhooks(channelId)
        } catch (e: Exception) {
            error = e.message
            webhooks = emptyList()
        }
    }

    fun webhookUrl(webhook: Webhook) = "$STOAT_BASE/webhooks/${webhook.id}/${webhook.token}"

    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.manage_webhook_create),
            label = stringResource(R.string.manage_webhook_name),
            confirmLabel = stringResource(R.string.manage_create),
            onConfirm = { name ->
                creating = false
                scope.launch {
                    try {
                        val webhook = createWebhook(channelId, name)
                        webhooks = webhooks.orEmpty() + webhook
                        copyWithToast(context, clipboard, webhookUrl(webhook))
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { webhook ->
        TextInputDialog(
            title = stringResource(R.string.manage_rename),
            label = stringResource(R.string.manage_webhook_name),
            confirmLabel = stringResource(R.string.manage_save),
            initial = webhook.name,
            onConfirm = { name ->
                renaming = null
                scope.launch {
                    try {
                        val updated = editWebhook(webhook.id, EditWebhookBody(name = name))
                        webhooks = webhooks?.map {
                            if (it.id == webhook.id) updated.copy(token = updated.token ?: it.token) else it
                        }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { webhook ->
        ConfirmDialog(
            title = stringResource(R.string.manage_webhook_delete_confirm, webhook.name),
            text = null,
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                deleting = null
                scope.launch {
                    try {
                        deleteWebhook(webhook.id)
                        webhooks = webhooks?.filter { it.id != webhook.id }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { deleting = null }
        )
    }

    ManageScaffold(
        title = stringResource(R.string.manage_webhooks),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(painterResource(R.drawable.ic_add_24dp), null) },
                text = { Text(stringResource(R.string.manage_webhook_create)) },
                modifier = Modifier.testTag("webhook_create")
            )
        }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item { ManageError(error) }
            val list = webhooks
            when {
                list == null -> item { CircularProgressIndicator(Modifier.padding(32.dp)) }
                list.isEmpty() -> item {
                    Text(
                        stringResource(R.string.manage_webhooks_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                else -> items(list, key = { it.id }) { webhook ->
                    ListItem(
                        headlineContent = { Text(webhook.name) },
                        supportingContent = { Text(stringResource(R.string.manage_webhook_copy_hint)) },
                        leadingContent = {
                            UserAvatar(
                                username = webhook.name,
                                userId = webhook.id,
                                avatar = webhook.avatar,
                                size = 40.dp
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = webhook }) {
                                    Icon(
                                        painterResource(R.drawable.ic_edit_24dp),
                                        stringResource(R.string.manage_rename)
                                    )
                                }
                                IconButton(onClick = { deleting = webhook }) {
                                    Icon(
                                        painterResource(R.drawable.ic_delete_24dp),
                                        stringResource(R.string.manage_delete)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .testTag("webhook_${webhook.name}")
                            .clickable {
                                if (webhook.token != null) {
                                    copyWithToast(context, clipboard, webhookUrl(webhook))
                                }
                            }
                    )
                }
            }
        }
    }
}

val slowmodeOptions = listOf(
    0L, 5L, 10L, 15L, 30L, 60L, 120L, 300L, 600L, 900L, 1800L, 3600L, 7200L, 21600L
)
val autoArchiveOptions = listOf(60, 1440, 4320, 10080)

@Composable
fun ChannelSettingsThreads(navController: NavController, channelId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val isForum = channel?.channelType == ChannelType.ForumChannel

    var slowmode by remember { mutableStateOf(channel?.slowmode ?: 0L) }
    var autoArchive by remember { mutableStateOf(channel?.defaultAutoArchiveMinutes ?: 4320) }
    var threadSlowmode by remember { mutableStateOf(channel?.defaultThreadSlowmode ?: 0L) }
    var requireTag by remember { mutableStateOf(channel?.requireTag ?: false) }
    var reaction by remember { mutableStateOf(channel?.defaultReactionEmoji ?: "") }
    var sortOrder by remember { mutableStateOf(channel?.defaultSortOrder ?: ForumSortOrder.LatestActivity) }
    var layout by remember { mutableStateOf(channel?.defaultLayout ?: ForumLayout.List) }
    var error by remember { mutableStateOf<String?>(null) }

    val current = StoatAPI.channelCache[channelId]
    val changed = current != null && (
            slowmode != (current.slowmode ?: 0L) ||
                    autoArchive != (current.defaultAutoArchiveMinutes ?: 4320) ||
                    (isForum && (
                                    threadSlowmode != (current.defaultThreadSlowmode ?: 0L) ||
                                    requireTag != (current.requireTag ?: false) ||
                                    reaction != (current.defaultReactionEmoji ?: "") ||
                                    sortOrder != (current.defaultSortOrder ?: ForumSortOrder.LatestActivity) ||
                                    layout != (current.defaultLayout ?: ForumLayout.List)
                            ))
            )

    fun save() {
        error = null
        scope.launch {
            try {
                val remove = mutableListOf<String>()
                // Thread slowmode default only exists on forum channels
                if (isForum && threadSlowmode == 0L) remove += "DefaultThreadSlowmode"
                if (isForum && reaction.isBlank()) remove += "DefaultReactionEmoji"
                editChannelSettings(
                    channelId,
                    EditChannelSettingsBody(
                        slowmode = slowmode,
                        defaultAutoArchiveMinutes = autoArchive,
                        defaultThreadSlowmode = threadSlowmode.takeIf { isForum && it > 0 },
                        requireTag = requireTag.takeIf { isForum },
                        defaultReactionEmoji = reaction.trim().takeIf { isForum && it.isNotEmpty() },
                        defaultSortOrder = sortOrder.takeIf { isForum },
                        defaultLayout = layout.takeIf { isForum },
                        remove = remove.ifEmpty { null }
                    )
                )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    ManageScaffold(
        title = stringResource(R.string.manage_thread_settings),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            AnimatedVisibility(visible = changed, enter = scaleIn(), exit = scaleOut()) {
                FloatingActionButton(onClick = { save() }, modifier = Modifier.testTag("manage_save")) {
                    Icon(painterResource(R.drawable.ic_check_24dp), stringResource(R.string.manage_save))
                }
            }
        }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ManageError(error)
            ListHeader { Text(stringResource(R.string.manage_channel_general)) }
            OptionPickerItem(
                label = stringResource(R.string.manage_slowmode),
                options = slowmodeOptions.map { it to formatSeconds(context, it) },
                selected = slowmode,
                onSelect = { slowmode = it },
                modifier = Modifier.testTag("threads_slowmode")
            )

            ListHeader { Text(stringResource(R.string.manage_thread_defaults)) }
            OptionPickerItem(
                label = stringResource(R.string.manage_auto_archive),
                options = autoArchiveOptions.map { it to formatSeconds(context, it * 60L) },
                selected = autoArchive,
                onSelect = { autoArchive = it }
            )
            if (isForum) {
                OptionPickerItem(
                    label = stringResource(R.string.manage_thread_slowmode),
                    options = slowmodeOptions.map { it to formatSeconds(context, it) },
                    selected = threadSlowmode,
                    onSelect = { threadSlowmode = it }
                )
            }

            if (isForum) {
                ListHeader { Text(stringResource(R.string.manage_forum)) }
                SwitchItem(
                    label = stringResource(R.string.manage_require_tag),
                    supporting = stringResource(R.string.manage_require_tag_desc),
                    checked = requireTag,
                    onCheckedChange = { requireTag = it }
                )
                TextField(
                    value = reaction,
                    onValueChange = { reaction = it.take(64) },
                    label = { Text(stringResource(R.string.manage_default_reaction)) },
                    supportingText = { Text(stringResource(R.string.manage_default_reaction_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                OptionPickerItem(
                    label = stringResource(R.string.manage_sort_order),
                    options = listOf(
                        ForumSortOrder.LatestActivity to stringResource(R.string.manage_sort_latest_activity),
                        ForumSortOrder.CreationDate to stringResource(R.string.manage_sort_creation_date)
                    ),
                    selected = sortOrder,
                    onSelect = { sortOrder = it }
                )
                OptionPickerItem(
                    label = stringResource(R.string.manage_layout),
                    options = listOf(
                        ForumLayout.List to stringResource(R.string.manage_layout_list),
                        ForumLayout.Gallery to stringResource(R.string.manage_layout_gallery)
                    ),
                    selected = layout,
                    onSelect = { layout = it }
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

private const val MAX_FORUM_TAGS = 20

@Composable
private fun ForumTagDialog(
    initial: ForumTag?,
    onDismiss: () -> Unit,
    onConfirm: (ForumTag) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "") }
    var moderated by remember { mutableStateOf(initial?.moderated ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.manage_tag_create else R.string.manage_tag_edit
                )
            )
        },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text(stringResource(R.string.manage_tag_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_name")
                )
                TextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(64) },
                    label = { Text(stringResource(R.string.manage_tag_emoji)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                SwitchItem(
                    label = stringResource(R.string.manage_tag_moderated),
                    supporting = stringResource(R.string.manage_tag_moderated_desc),
                    checked = moderated,
                    onCheckedChange = { moderated = it }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(
                        ForumTag(
                            id = initial?.id?.ifEmpty { null } ?: ULID.makeNext(),
                            name = name.trim(),
                            emoji = emoji.trim().ifEmpty { null },
                            moderated = moderated
                        )
                    )
                },
                modifier = Modifier.testTag("tag_confirm")
            ) { Text(stringResource(R.string.manage_save)) }
        }
    )
}

@Composable
fun ChannelSettingsForumTags(navController: NavController, channelId: String) {
    val scope = rememberCoroutineScope()
    val tags = StoatAPI.channelCache[channelId]?.availableTags.orEmpty()
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ForumTag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ForumTag?>(null) }

    fun saveTags(next: List<ForumTag>) {
        error = null
        scope.launch {
            try {
                editChannelSettings(channelId, EditChannelSettingsBody(availableTags = next))
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    if (creating) {
        ForumTagDialog(null, { creating = false }) { tag ->
            creating = false
            saveTags(tags + tag)
        }
    }
    editing?.let { original ->
        ForumTagDialog(original, { editing = null }) { tag ->
            editing = null
            saveTags(tags.map { if (it.id == original.id) tag else it })
        }
    }
    deleting?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.manage_tag_delete_confirm, tag.name),
            text = null,
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                deleting = null
                saveTags(tags.filter { it.id != tag.id })
            },
            onDismiss = { deleting = null }
        )
    }

    ManageScaffold(
        title = stringResource(R.string.manage_forum_tags),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            if (tags.size < MAX_FORUM_TAGS) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(painterResource(R.drawable.ic_add_24dp), null) },
                    text = { Text(stringResource(R.string.manage_tag_create)) },
                    modifier = Modifier.testTag("tag_create")
                )
            }
        }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item { ManageError(error) }
            item {
                Text(
                    stringResource(R.string.manage_tags_count, tags.size, MAX_FORUM_TAGS),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(tags, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = { Text(listOfNotNull(tag.emoji, tag.name).joinToString(" ")) },
                    supportingContent = {
                        if (tag.moderated) Text(stringResource(R.string.manage_tag_moderated))
                    },
                    trailingContent = {
                        IconButton(onClick = { deleting = tag }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), stringResource(R.string.manage_delete))
                        }
                    },
                    modifier = Modifier
                        .testTag("tag_${tag.name}")
                        .clickable { editing = tag }
                )
            }
        }
    }
}
