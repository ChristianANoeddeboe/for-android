package chat.stoat.screens.settings.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import chat.stoat.api.internals.has
import chat.stoat.api.routes.server.createRole
import chat.stoat.api.routes.server.editRoleRanks
import kotlinx.coroutines.launch

/**
 * Parse a role colour, only plain hex colours are supported, gradients fall back to null
 */
fun parseRoleColour(colour: String?): Color? {
    if (colour == null) return null
    return runCatching { Color(android.graphics.Color.parseColor(colour.trim())) }.getOrNull()
}

@Composable
fun ServerSettingsRoles(navController: NavController, serverId: String) {
    val scope = rememberCoroutineScope()
    val server = StoatAPI.serverCache[serverId]
    var showCreate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val roles = server?.roles.orEmpty().entries.sortedBy { it.value.rank ?: 0.0 }
    val selfRank = selfServerRank(serverId)
    val canManage = selfServerPermissions(serverId) has PermissionBit.ManageRole

    fun move(index: Int, by: Int) {
        val ids = roles.map { it.key }.toMutableList()
        val target = index + by
        if (target !in ids.indices) return
        val id = ids.removeAt(index)
        ids.add(target, id)
        error = null
        scope.launch {
            try {
                editRoleRanks(serverId, ids)
                StoatAPI.serverCache[serverId]?.let { current ->
                    StoatAPI.serverCache[serverId] = current.copy(
                        roles = current.roles?.mapValues { (roleId, role) ->
                            role.copy(rank = ids.indexOf(roleId).toDouble())
                        }
                    )
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = stringResource(R.string.manage_role_create),
            label = stringResource(R.string.manage_role_name),
            confirmLabel = stringResource(R.string.manage_create),
            onConfirm = { name ->
                showCreate = false
                scope.launch {
                    try {
                        val created = createRole(serverId, name)
                        navController.navigate("settings/server/$serverId/roles/${created.id}")
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { showCreate = false }
        )
    }

    ManageScaffold(
        title = stringResource(R.string.manage_roles),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            if (canManage) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(painterResource(R.drawable.ic_add_24dp), null) },
                    text = { Text(stringResource(R.string.manage_role_create)) },
                    modifier = Modifier.testTag("roles_create")
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
            itemsIndexed(roles, key = { _, entry -> entry.key }) { index, (roleId, role) ->
                // Roles at or above your own rank are locked
                val editable = canManage && (role.rank ?: 0.0) > selfRank
                ListItem(
                    headlineContent = {
                        Text(role.name ?: roleId, color = parseRoleColour(role.colour) ?: Color.Unspecified)
                    },
                    leadingContent = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    parseRoleColour(role.colour)
                                        ?: MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    },
                    trailingContent = {
                        Row {
                            val canMoveUp = editable && index > 0 &&
                                    (roles[index - 1].value.rank ?: 0.0) > selfRank
                            val canMoveDown = editable && index < roles.size - 1
                            IconButton(onClick = { move(index, -1) }, enabled = canMoveUp) {
                                Icon(
                                    painterResource(R.drawable.ic_arrow_upward_24dp),
                                    stringResource(R.string.manage_move_up)
                                )
                            }
                            IconButton(onClick = { move(index, 1) }, enabled = canMoveDown) {
                                Icon(
                                    painterResource(R.drawable.ic_arrow_downward_24dp),
                                    stringResource(R.string.manage_move_down)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .testTag("role_$roleId")
                        .clickable { navController.navigate("settings/server/$serverId/roles/$roleId") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_role_default)) },
                    supportingContent = { Text(stringResource(R.string.manage_role_default_desc)) },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_group_24dp), null)
                    },
                    modifier = Modifier
                        .testTag("role_default")
                        .clickable { navController.navigate("settings/server/$serverId/roles/default") }
                )
            }
        }
    }
}
