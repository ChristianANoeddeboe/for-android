package chat.stoat.sheets

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.has
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.generic.SheetButton
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.internals.Platform
import chat.stoat.internals.extensions.rememberChannelPermissions
import chat.stoat.screens.chat.views.thread.ThreadOptionsSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChannelContextSheet(channelId: String, onHideSheet: suspend () -> Unit) {
    val channel = StoatAPI.channelCache[channelId]
    if (channel == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    if (channel.channelType == ChannelType.Thread) {
        // Long pressing a thread in the channel list gets the same options as
        // long pressing its header inside the thread. That is a tall list for
        // moderators, so it has to really scroll (a plain Column only grew past
        // the sheet and stayed unreachable).
        LazyColumn(
            // Bounded height so the list scrolls inside the sheet instead of
            // growing past it (rows outside the sheet are not tappable)
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {
            item {
                ThreadOptionsSection(
                    threadId = channelId,
                    onHideSheet = onHideSheet,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ChannelBasicActions(channelId = channelId, onHideSheet = onHideSheet)
            }
        }
    } else {
        val coroutineScope = rememberCoroutineScope()
        val permissions by rememberChannelPermissions(channelId)

        if (
            channel.server != null &&
            (permissions has PermissionBit.ManageChannel || permissions has PermissionBit.ManageRole ||
                    permissions has PermissionBit.ManagePermissions || permissions has PermissionBit.ManageWebhooks)
        ) {
            SheetButton(
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.settings),
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_24dp),
                        contentDescription = null
                    )
                },
                onClick = {
                    coroutineScope.launch {
                        onHideSheet()
                    }
                    coroutineScope.launch {
                        delay(100)
                        ActionChannel.send(Action.TopNavigate("settings/channel/$channelId"))
                    }
                }
            )
        }

        ChannelBasicActions(channelId = channelId, onHideSheet = onHideSheet)
    }
}

/**
 * Copy ID / mark as read, the actions every channel type gets
 */
@Composable
private fun ChannelBasicActions(channelId: String, onHideSheet: suspend () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SheetButton(
        headlineContent = {
            Text(
                text = stringResource(id = R.string.channel_context_sheet_actions_copy_id),
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_identifier_copy_24dp),
                contentDescription = null
            )
        },
        onClick = {
            clipboardManager.setText(AnnotatedString(channelId))

            if (Platform.needsShowClipboardNotification()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.channel_context_sheet_actions_copy_id_copied),
                    Toast.LENGTH_SHORT
                ).show()
            }

            coroutineScope.launch {
                onHideSheet()
            }
        }
    )

    SheetButton(
        headlineContent = {
            Text(
                text = stringResource(id = R.string.channel_context_sheet_actions_mark_read),
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.ic_mark_chat_read_24dp),
                contentDescription = null
            )
        },
        onClick = {
            coroutineScope.launch {
                StoatAPI.channelCache[channelId]?.lastMessageID?.let {
                    StoatAPI.unreads.markAsRead(channelId, it, sync = true)
                }
                onHideSheet()
            }
        }
    )
}
