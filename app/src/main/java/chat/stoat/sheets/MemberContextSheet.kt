package chat.stoat.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.channel.removeMember
import chat.stoat.composables.generic.SheetButton
import chat.stoat.internals.Platform
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.has
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.screens.settings.server.BanMemberDialog
import chat.stoat.screens.settings.server.KickMemberDialog
import chat.stoat.screens.settings.server.TimeoutMemberDialog
import chat.stoat.screens.settings.server.canModerate
import chat.stoat.screens.settings.server.isTimedOut
import chat.stoat.screens.settings.server.selfServerPermissions

@Composable
fun ColumnScope.GroupDMMemberContextSheet(
    userId: String,
    channelId: String,
    dismissSheet: suspend () -> Unit,
    onRequestUpdateMembers: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(channel) {
        if (channel == null) {
            dismissSheet()
        }
    }

    if (channel == null) return

    if (channel.owner == StoatAPI.selfId && userId != StoatAPI.selfId) {
        SheetButton(
            headlineContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Text(
                        stringResource(
                            R.string.member_context_sheet_remove_from_channel,
                            channel.name ?: stringResource(R.string.unknown)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                CompositionLocalProvider(value = LocalContentColor provides MaterialTheme.colorScheme.error) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_off_24dp),
                        contentDescription = null
                    )
                }
            },
            onClick = {
                scope.launch {
                    removeMember(channelId, userId)
                    onRequestUpdateMembers()
                    dismissSheet()
                }
            }
        )
    }

    // TODO replace with something useful (currently so that your sheet is not empty if you don't have permissions)
    SheetButton(
        headlineContent = {
            Text(stringResource(R.string.user_info_sheet_copy_id))
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(userId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )


}

@Composable
fun ColumnScope.ServerMemberContextSheet(
    userId: String,
    serverId: String,
    channelId: String,
    dismissSheet: suspend () -> Unit,
    onRequestUpdateMembers: suspend () -> Unit
) {
    val server = StoatAPI.serverCache[serverId]
    val channel = StoatAPI.channelCache[channelId]
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(server) {
        if (server == null || channel == null) {
            dismissSheet()
        }
    }

    if (server == null || channel == null) return

    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    val permissions = selfServerPermissions(serverId)
    val outranks = canModerate(serverId, userId)
    val onDone: () -> Unit = {
        dialog = null
        scope.launch {
            onRequestUpdateMembers()
            dismissSheet()
        }
    }

    when (dialog) {
        "kick" -> KickMemberDialog(serverId, userId, { dialog = null }, onDone)
        "ban" -> BanMemberDialog(serverId, userId, { dialog = null }, onDone)
        "timeout" -> TimeoutMemberDialog(serverId, userId, { dialog = null }, onDone)
    }

    val canManageMember = (outranks && listOf(
        PermissionBit.AssignRoles, PermissionBit.ManageNicknames, PermissionBit.RemoveAvatars,
        PermissionBit.KickMembers, PermissionBit.BanMembers, PermissionBit.TimeoutMembers
    ).any { permissions has it }) || (userId == StoatAPI.selfId && permissions has PermissionBit.AssignRoles)

    if (canManageMember) {
        SheetButton(
            headlineContent = { Text(stringResource(R.string.manage_member_manage)) },
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_settings_24dp), contentDescription = null)
            },
            modifier = Modifier.testTag("member_sheet_manage"),
            onClick = {
                scope.launch {
                    dismissSheet()
                    ActionChannel.send(Action.TopNavigate("settings/server/$serverId/members/$userId"))
                }
            }
        )
    }

    if (outranks && permissions has PermissionBit.TimeoutMembers && !isTimedOut(StoatAPI.members.getMember(serverId, userId))) {
        SheetButton(
            headlineContent = { Text(stringResource(R.string.manage_timeout)) },
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_timer_24dp), contentDescription = null)
            },
            modifier = Modifier.testTag("member_sheet_timeout"),
            onClick = { dialog = "timeout" }
        )
    }

    if (outranks && permissions has PermissionBit.KickMembers) {
        SheetButton(
            headlineContent = { Text(stringResource(R.string.manage_kick)) },
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_logout_24dp), contentDescription = null)
            },
            dangerous = true,
            modifier = Modifier.testTag("member_sheet_kick"),
            onClick = { dialog = "kick" }
        )
    }

    if (outranks && permissions has PermissionBit.BanMembers) {
        SheetButton(
            headlineContent = { Text(stringResource(R.string.manage_ban)) },
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_gavel_24dp), contentDescription = null)
            },
            dangerous = true,
            modifier = Modifier.testTag("member_sheet_ban"),
            onClick = { dialog = "ban" }
        )
    }


    // TODO replace with something useful (currently so that your sheet is not empty if you don't have permissions)
    SheetButton(
        headlineContent = {
            Text(stringResource(R.string.user_info_sheet_copy_id))
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(userId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )


}