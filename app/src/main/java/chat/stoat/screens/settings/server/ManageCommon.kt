package chat.stoat.screens.settings.server

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.Roles
import chat.stoat.api.routes.microservices.autumn.uploadToAutumn
import chat.stoat.screens.settings.SettingsIcon
import io.ktor.http.ContentType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = actions
            )
        },
        floatingActionButton = floatingActionButton
    ) { pv ->
        Box(Modifier.imePadding()) {
            content(pv)
        }
    }
}

@Composable
fun ManageEntry(
    title: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    danger: Boolean = false
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = {
            SettingsIcon(danger = danger) {
                Icon(painter = painterResource(icon), contentDescription = null)
            }
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
fun ManageError(error: String?) {
    if (error == null) return
    Text(
        error,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dangerous: Boolean = true,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                text?.let { Text(it) }
                extra()
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (dangerous) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        }
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    maxLength: Int = 32,
    allowEmpty: Boolean = false
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it.take(maxLength) },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.testTag("manage_text_input")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value.trim()) },
                enabled = allowEmpty || value.isNotBlank()
            ) { Text(confirmLabel) }
        }
    )
}

/**
 * Copy a picked file into the cache and upload it to [tag] on autumn, returns the file id
 */
suspend fun uploadPicked(
    context: Context,
    uri: Uri,
    tag: String,
    onProgress: (Float) -> Unit = {}
): String {
    val mime = context.contentResolver.getType(uri)
    val name = uri.lastPathSegment ?: tag
    val file = File(context.cacheDir, name.substringAfterLast('/'))
    file.outputStream().use { output ->
        context.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(output) }
    }
    return uploadToAutumn(
        file,
        name,
        tag,
        ContentType.parse(mime ?: "image/*"),
        onProgress = { soFar, outOf -> onProgress(soFar.toFloat() / outOf.toFloat()) }
    )
}

/**
 * Own permissions in a server
 */
fun selfServerPermissions(serverId: String): Long {
    val server = StoatAPI.serverCache[serverId] ?: return 0L
    val member = StoatAPI.selfId?.let { StoatAPI.members.getMember(serverId, it) } ?: return 0L
    return Roles.permissionFor(server, member)
}

/**
 * Own highest role rank in a server, lower is higher, owners are above everything
 */
fun selfServerRank(serverId: String): Double {
    val server = StoatAPI.serverCache[serverId] ?: return Double.MAX_VALUE
    if (server.owner == StoatAPI.selfId) return Double.NEGATIVE_INFINITY
    val member = StoatAPI.selfId?.let { StoatAPI.members.getMember(serverId, it) }
        ?: return Double.MAX_VALUE
    return member.roles.orEmpty().mapNotNull { server.roles?.get(it)?.rank }.minOrNull()
        ?: Double.MAX_VALUE
}

/**
 * Highest role rank of a member, lower is higher
 */
fun memberServerRank(serverId: String, userId: String): Double {
    val server = StoatAPI.serverCache[serverId] ?: return Double.MAX_VALUE
    if (server.owner == userId) return Double.NEGATIVE_INFINITY
    val member = StoatAPI.members.getMember(serverId, userId) ?: return Double.MAX_VALUE
    return member.roles.orEmpty().mapNotNull { server.roles?.get(it)?.rank }.minOrNull()
        ?: Double.MAX_VALUE
}

data class PermissionEntry(
    val bit: PermissionBit,
    @StringRes val name: Int,
    @StringRes val description: Int
)

data class PermissionGroup(@StringRes val title: Int, val entries: List<PermissionEntry>)

object ManagePermissionGroups {
    private val general = PermissionGroup(
        R.string.manage_perm_group_general, listOf(
            PermissionEntry(PermissionBit.ManageChannel, R.string.manage_perm_manage_channel, R.string.manage_perm_manage_channel_desc),
            PermissionEntry(PermissionBit.ManageServer, R.string.manage_perm_manage_server, R.string.manage_perm_manage_server_desc),
            PermissionEntry(PermissionBit.ManagePermissions, R.string.manage_perm_manage_permissions, R.string.manage_perm_manage_permissions_desc),
            PermissionEntry(PermissionBit.ManageRole, R.string.manage_perm_manage_role, R.string.manage_perm_manage_role_desc),
            PermissionEntry(PermissionBit.ManageCustomisation, R.string.manage_perm_manage_customisation, R.string.manage_perm_manage_customisation_desc),
        )
    )
    private val members = PermissionGroup(
        R.string.manage_perm_group_members, listOf(
            PermissionEntry(PermissionBit.KickMembers, R.string.manage_perm_kick, R.string.manage_perm_kick_desc),
            PermissionEntry(PermissionBit.BanMembers, R.string.manage_perm_ban, R.string.manage_perm_ban_desc),
            PermissionEntry(PermissionBit.TimeoutMembers, R.string.manage_perm_timeout, R.string.manage_perm_timeout_desc),
            PermissionEntry(PermissionBit.AssignRoles, R.string.manage_perm_assign_roles, R.string.manage_perm_assign_roles_desc),
            PermissionEntry(PermissionBit.ChangeNickname, R.string.manage_perm_change_nickname, R.string.manage_perm_change_nickname_desc),
            PermissionEntry(PermissionBit.ManageNicknames, R.string.manage_perm_manage_nicknames, R.string.manage_perm_manage_nicknames_desc),
            PermissionEntry(PermissionBit.ChangeAvatar, R.string.manage_perm_change_avatar, R.string.manage_perm_change_avatar_desc),
            PermissionEntry(PermissionBit.RemoveAvatars, R.string.manage_perm_remove_avatars, R.string.manage_perm_remove_avatars_desc),
        )
    )
    private val channelGeneral = PermissionGroup(
        R.string.manage_perm_group_general, listOf(
            PermissionEntry(PermissionBit.ManageChannel, R.string.manage_perm_manage_channel, R.string.manage_perm_manage_channel_desc),
            PermissionEntry(PermissionBit.ManagePermissions, R.string.manage_perm_manage_permissions, R.string.manage_perm_manage_permissions_desc),
        )
    )
    private val channel = PermissionGroup(
        R.string.manage_perm_group_channel, listOf(
            PermissionEntry(PermissionBit.ViewChannel, R.string.manage_perm_view_channel, R.string.manage_perm_view_channel_desc),
            PermissionEntry(PermissionBit.ReadMessageHistory, R.string.manage_perm_read_history, R.string.manage_perm_read_history_desc),
            PermissionEntry(PermissionBit.SendMessage, R.string.manage_perm_send_message, R.string.manage_perm_send_message_desc),
            PermissionEntry(PermissionBit.ManageMessages, R.string.manage_perm_manage_messages, R.string.manage_perm_manage_messages_desc),
            PermissionEntry(PermissionBit.ManageWebhooks, R.string.manage_perm_manage_webhooks, R.string.manage_perm_manage_webhooks_desc),
            PermissionEntry(PermissionBit.InviteOthers, R.string.manage_perm_invite_others, R.string.manage_perm_invite_others_desc),
            PermissionEntry(PermissionBit.SendEmbeds, R.string.manage_perm_send_embeds, R.string.manage_perm_send_embeds_desc),
            PermissionEntry(PermissionBit.UploadFiles, R.string.manage_perm_upload_files, R.string.manage_perm_upload_files_desc),
            PermissionEntry(PermissionBit.Masquerade, R.string.manage_perm_masquerade, R.string.manage_perm_masquerade_desc),
            PermissionEntry(PermissionBit.React, R.string.manage_perm_react, R.string.manage_perm_react_desc),
            PermissionEntry(PermissionBit.MentionEveryone, R.string.manage_perm_mention_everyone, R.string.manage_perm_mention_everyone_desc),
            PermissionEntry(PermissionBit.MentionRoles, R.string.manage_perm_mention_roles, R.string.manage_perm_mention_roles_desc),
            PermissionEntry(PermissionBit.BypassSlowmode, R.string.manage_perm_bypass_slowmode, R.string.manage_perm_bypass_slowmode_desc),
        )
    )
    private val threads = PermissionGroup(
        R.string.manage_perm_group_threads, listOf(
            PermissionEntry(PermissionBit.CreatePublicThreads, R.string.manage_perm_create_public_threads, R.string.manage_perm_create_public_threads_desc),
            PermissionEntry(PermissionBit.CreatePrivateThreads, R.string.manage_perm_create_private_threads, R.string.manage_perm_create_private_threads_desc),
            PermissionEntry(PermissionBit.SendMessagesInThreads, R.string.manage_perm_send_in_threads, R.string.manage_perm_send_in_threads_desc),
            PermissionEntry(PermissionBit.ManageThreads, R.string.manage_perm_manage_threads, R.string.manage_perm_manage_threads_desc),
        )
    )
    private val voice = PermissionGroup(
        R.string.manage_perm_group_voice, listOf(
            PermissionEntry(PermissionBit.Connect, R.string.manage_perm_connect, R.string.manage_perm_connect_desc),
            PermissionEntry(PermissionBit.Speak, R.string.manage_perm_speak, R.string.manage_perm_speak_desc),
            PermissionEntry(PermissionBit.Video, R.string.manage_perm_video, R.string.manage_perm_video_desc),
            PermissionEntry(PermissionBit.Listen, R.string.manage_perm_listen, R.string.manage_perm_listen_desc),
            PermissionEntry(PermissionBit.MuteMembers, R.string.manage_perm_mute_members, R.string.manage_perm_mute_members_desc),
            PermissionEntry(PermissionBit.DeafenMembers, R.string.manage_perm_deafen_members, R.string.manage_perm_deafen_members_desc),
            PermissionEntry(PermissionBit.MoveMembers, R.string.manage_perm_move_members, R.string.manage_perm_move_members_desc),
        )
    )

    val server = listOf(general, members, channel, threads, voice)
    val channelOverrides = listOf(channelGeneral, channel, threads, voice)
}

enum class PermState { Deny, Neutral, Allow }

/**
 * Permission list, tri-state (allow/neutral/deny) or plain toggles when [triState] is false
 *
 * [editable] decides per bit whether the user may change it
 */
@Composable
fun PermissionEditor(
    groups: List<PermissionGroup>,
    triState: Boolean,
    allow: Long,
    deny: Long,
    onChange: (allow: Long, deny: Long) -> Unit,
    editable: (PermissionBit) -> Boolean
) {
    groups.forEach { group ->
        chat.stoat.composables.generic.ListHeader {
            Text(stringResource(group.title))
        }
        group.entries.forEach { entry ->
            val bit = entry.bit.value
            val enabled = editable(entry.bit)
            if (triState) {
                val state = when {
                    allow and bit == bit -> PermState.Allow
                    deny and bit == bit -> PermState.Deny
                    else -> PermState.Neutral
                }
                ListItem(
                    headlineContent = { Text(stringResource(entry.name)) },
                    supportingContent = {
                        androidx.compose.foundation.layout.Column {
                            Text(stringResource(entry.description))
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                PermState.entries.forEachIndexed { index, option ->
                                    SegmentedButton(
                                        selected = state == option,
                                        enabled = enabled,
                                        onClick = {
                                            val a = allow and bit.inv()
                                            val d = deny and bit.inv()
                                            when (option) {
                                                PermState.Allow -> onChange(a or bit, d)
                                                PermState.Deny -> onChange(a, d or bit)
                                                PermState.Neutral -> onChange(a, d)
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index,
                                            PermState.entries.size
                                        ),
                                        icon = {},
                                        label = {
                                            Icon(
                                                painter = painterResource(
                                                    when (option) {
                                                        PermState.Deny -> R.drawable.ic_close_24dp
                                                        PermState.Neutral -> R.drawable.ic_remove_24dp
                                                        PermState.Allow -> R.drawable.ic_check_24dp
                                                    }
                                                ),
                                                contentDescription = stringResource(
                                                    when (option) {
                                                        PermState.Deny -> R.string.manage_perm_deny
                                                        PermState.Neutral -> R.string.manage_perm_neutral
                                                        PermState.Allow -> R.string.manage_perm_allow
                                                    }
                                                ),
                                                tint = if (state == option) when (option) {
                                                    PermState.Deny -> MaterialTheme.colorScheme.error
                                                    PermState.Allow -> MaterialTheme.colorScheme.primary
                                                    PermState.Neutral -> MaterialTheme.colorScheme.onSurface
                                                } else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            } else {
                val checked = allow and bit == bit
                ListItem(
                    headlineContent = { Text(stringResource(entry.name)) },
                    supportingContent = { Text(stringResource(entry.description)) },
                    trailingContent = {
                        Switch(
                            checked = checked,
                            enabled = enabled,
                            onCheckedChange = {
                                onChange(if (it) allow or bit else allow and bit.inv(), 0L)
                            }
                        )
                    },
                    modifier = Modifier.clickable(enabled = enabled) {
                        onChange(if (!checked) allow or bit else allow and bit.inv(), 0L)
                    }
                )
            }
        }
    }
}

/**
 * Human readable duration for slowmode and archive options
 */
fun formatSeconds(context: Context, seconds: Long): String {
    return when {
        seconds <= 0L -> context.getString(R.string.manage_off)
        seconds % 86400L == 0L -> context.resources.getQuantityString(
            R.plurals.manage_days, (seconds / 86400L).toInt(), (seconds / 86400L).toInt()
        )

        seconds % 3600L == 0L -> context.resources.getQuantityString(
            R.plurals.manage_hours, (seconds / 3600L).toInt(), (seconds / 3600L).toInt()
        )

        seconds % 60L == 0L -> context.resources.getQuantityString(
            R.plurals.manage_minutes, (seconds / 60L).toInt(), (seconds / 60L).toInt()
        )

        else -> context.resources.getQuantityString(
            R.plurals.manage_seconds, seconds.toInt(), seconds.toInt()
        )
    }
}

/**
 * List row showing the current choice, opens a dialog with all [options]
 */
@Composable
fun <T> OptionPickerItem(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(options.firstOrNull { it.first == selected }?.second ?: "")
        },
        modifier = modifier.clickable(enabled = enabled) { open = true }
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(options.size) { index ->
                        val (value, name) = options[index]
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = false
                                    onSelect(value)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = value == selected,
                                onClick = {
                                    open = false
                                    onSelect(value)
                                }
                            )
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun SwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}

/**
 * Copy text and show a toast where the system doesn't confirm it
 */
fun copyWithToast(
    context: Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    text: String
) {
    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
    if (chat.stoat.internals.Platform.needsShowClipboardNotification()) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.copied),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
