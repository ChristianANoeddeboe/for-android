package chat.stoat.screens.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.has
import chat.stoat.api.routes.server.fetchMember
import chat.stoat.api.routes.server.leaveOrDeleteServer
import chat.stoat.composables.generic.ListHeader
import kotlinx.coroutines.launch

@Composable
fun ServerSettingsHome(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scope = rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        val selfId = StoatAPI.selfId ?: return@LaunchedEffect
        if (StoatAPI.members.getMember(serverId, selfId) == null) {
            runCatching { fetchMember(serverId, selfId) }
        }
    }

    val permissions = selfServerPermissions(serverId)
    val isOwner = server?.owner == StoatAPI.selfId

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.manage_server_delete_confirm, server?.name ?: ""),
            text = stringResource(R.string.manage_server_delete_confirm_body),
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                showDelete = false
                scope.launch {
                    leaveOrDeleteServer(serverId)
                    navController.popBackStack()
                }
            },
            onDismiss = { showDelete = false }
        )
    }

    ManageScaffold(
        title = server?.name ?: stringResource(R.string.manage_server_settings),
        onBack = { navController.popBackStack() }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListHeader { Text(stringResource(R.string.manage_server_settings)) }

            if (permissions has PermissionBit.ManageServer) {
                ManageEntry(
                    stringResource(R.string.manage_overview),
                    R.drawable.ic_info_24dp,
                    { navController.navigate("settings/server/$serverId/overview") },
                    Modifier.testTag("server_settings_overview")
                )
            }
            if (permissions has PermissionBit.ManageChannel) {
                ManageEntry(
                    stringResource(R.string.manage_channels),
                    R.drawable.ic_list_24dp,
                    { navController.navigate("settings/server/$serverId/channels") },
                    Modifier.testTag("server_settings_channels")
                )
            }
            if (permissions has PermissionBit.ManageRole) {
                ManageEntry(
                    stringResource(R.string.manage_roles),
                    R.drawable.ic_shield_lock_24dp,
                    { navController.navigate("settings/server/$serverId/roles") },
                    Modifier.testTag("server_settings_roles")
                )
            }
            if (permissions has PermissionBit.ManageCustomisation) {
                ManageEntry(
                    stringResource(R.string.manage_emojis),
                    R.drawable.ic_mood_24dp,
                    { navController.navigate("settings/server/$serverId/emojis") },
                    Modifier.testTag("server_settings_emojis")
                )
            }

            ListHeader { Text(stringResource(R.string.manage_user_management)) }

            val canModerate = listOf(
                PermissionBit.KickMembers,
                PermissionBit.BanMembers,
                PermissionBit.TimeoutMembers,
                PermissionBit.AssignRoles,
                PermissionBit.ManageNicknames,
                PermissionBit.RemoveAvatars
            ).any { permissions has it }
            if (canModerate) {
                ManageEntry(
                    stringResource(R.string.manage_members),
                    R.drawable.ic_group_24dp,
                    { navController.navigate("settings/server/$serverId/members") },
                    Modifier.testTag("server_settings_members")
                )
            }
            if (permissions has PermissionBit.ManageServer) {
                ManageEntry(
                    stringResource(R.string.manage_invites),
                    R.drawable.ic_link_24dp,
                    { navController.navigate("settings/server/$serverId/invites") },
                    Modifier.testTag("server_settings_invites")
                )
            }
            if (permissions has PermissionBit.BanMembers) {
                ManageEntry(
                    stringResource(R.string.manage_bans),
                    R.drawable.ic_gavel_24dp,
                    { navController.navigate("settings/server/$serverId/bans") },
                    Modifier.testTag("server_settings_bans")
                )
            }

            if (isOwner) {
                ListHeader { Text(stringResource(R.string.manage_danger_zone)) }
                ManageEntry(
                    stringResource(R.string.manage_server_delete),
                    R.drawable.ic_delete_24dp,
                    { showDelete = true },
                    Modifier.testTag("server_settings_delete"),
                    danger = true
                )
            }
        }
    }
}
