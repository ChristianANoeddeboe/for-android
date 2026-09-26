package chat.stoat.screens.settings.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.hasPermission
import chat.stoat.api.routes.server.setChannelDefaultPermissions
import chat.stoat.api.routes.server.setChannelRolePermissions
import chat.stoat.core.model.schemas.PermissionDescription
import chat.stoat.internals.extensions.rememberChannelPermissions
import chat.stoat.screens.settings.server.ManageError
import chat.stoat.screens.settings.server.ManagePermissionGroups
import chat.stoat.screens.settings.server.ManageScaffold
import chat.stoat.screens.settings.server.PermissionEditor
import chat.stoat.screens.settings.server.parseRoleColour
import chat.stoat.screens.settings.server.selfServerRank
import kotlinx.coroutines.launch

@Composable
fun ChannelSettingsPermissions(navController: NavController, channelId: String) {
    val channel = StoatAPI.channelCache[channelId]
    val server = channel?.server?.let { StoatAPI.serverCache[it] }
    val roles = server?.roles.orEmpty().entries.sortedBy { it.value.rank ?: 0.0 }

    ManageScaffold(
        title = stringResource(R.string.channel_settings_permissions),
        onBack = { navController.popBackStack() }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item {
                Text(
                    stringResource(R.string.manage_channel_permissions_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_role_default)) },
                    supportingContent = {
                        Text(
                            if (channel?.defaultPermissions != null) {
                                stringResource(R.string.manage_override_set)
                            } else {
                                stringResource(R.string.manage_override_none)
                            }
                        )
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_group_24dp), null) },
                    modifier = Modifier
                        .testTag("channel_perm_default")
                        .clickable { navController.navigate("settings/channel/$channelId/permissions/default") }
                )
            }
            items(roles, key = { it.key }) { (roleId, role) ->
                ListItem(
                    headlineContent = {
                        Text(role.name ?: roleId, color = parseRoleColour(role.colour) ?: Color.Unspecified)
                    },
                    supportingContent = {
                        Text(
                            if (channel?.rolePermissions?.containsKey(roleId) == true) {
                                stringResource(R.string.manage_override_set)
                            } else {
                                stringResource(R.string.manage_override_none)
                            }
                        )
                    },
                    leadingContent = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    parseRoleColour(role.colour) ?: MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    },
                    modifier = Modifier
                        .testTag("channel_perm_$roleId")
                        .clickable { navController.navigate("settings/channel/$channelId/permissions/$roleId") }
                )
            }
        }
    }
}

@Composable
fun ChannelPermissionOverride(navController: NavController, channelId: String, roleId: String) {
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val server = channel?.server?.let { StoatAPI.serverCache[it] }
    val isDefault = roleId == "default"
    val role = if (isDefault) null else server?.roles?.get(roleId)
    val channelPermissions by rememberChannelPermissions(channelId)
    val isOwner = server?.owner == StoatAPI.selfId

    val initial = if (isDefault) channel?.defaultPermissions else channel?.rolePermissions?.get(roleId)
    var savedAllow by remember { mutableLongStateOf(initial?.a ?: 0L) }
    var savedDeny by remember { mutableLongStateOf(initial?.d ?: 0L) }
    var allow by remember { mutableLongStateOf(savedAllow) }
    var deny by remember { mutableLongStateOf(savedDeny) }
    var error by remember { mutableStateOf<String?>(null) }

    val canEdit = channelPermissions.hasPermission(PermissionBit.ManagePermissions) &&
            (isDefault || isOwner || (role?.rank ?: 0.0) > (server?.id?.let { selfServerRank(it) } ?: 0.0))
    val changed = allow != savedAllow || deny != savedDeny

    fun save() {
        error = null
        scope.launch {
            try {
                if (isDefault) {
                    setChannelDefaultPermissions(channelId, allow, deny)
                } else {
                    setChannelRolePermissions(channelId, roleId, allow, deny)
                }
                StoatAPI.channelCache[channelId]?.let { current ->
                    StoatAPI.channelCache[channelId] = if (isDefault) {
                        current.copy(defaultPermissions = PermissionDescription(allow, deny))
                    } else {
                        current.copy(
                            rolePermissions = current.rolePermissions.orEmpty() +
                                    (roleId to PermissionDescription(allow, deny))
                        )
                    }
                }
                savedAllow = allow
                savedDeny = deny
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    ManageScaffold(
        title = if (isDefault) stringResource(R.string.manage_role_default) else role?.name ?: roleId,
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            AnimatedVisibility(visible = canEdit && changed, enter = scaleIn(), exit = scaleOut()) {
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
            PermissionEditor(
                groups = ManagePermissionGroups.channelOverrides,
                triState = true,
                allow = allow,
                deny = deny,
                onChange = { a, d ->
                    allow = a
                    deny = d
                },
                editable = { bit -> canEdit && (isOwner || channelPermissions.hasPermission(bit)) }
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
