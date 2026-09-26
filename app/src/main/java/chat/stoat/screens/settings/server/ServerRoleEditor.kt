package chat.stoat.screens.settings.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.BitDefaults
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.has
import chat.stoat.api.routes.server.EditRoleBody
import chat.stoat.api.routes.server.deleteRole
import chat.stoat.api.routes.server.editRole
import chat.stoat.api.routes.server.setRolePermissions
import chat.stoat.api.routes.server.setServerDefaultPermissions
import chat.stoat.composables.generic.ListHeader
import chat.stoat.sheets.ColourPickerSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerRoleEditor(navController: NavController, serverId: String, roleId: String) {
    val scope = rememberCoroutineScope()
    val server = StoatAPI.serverCache[serverId]
    val isDefault = roleId == "default"
    val role = if (isDefault) null else server?.roles?.get(roleId)

    val selfPermissions = selfServerPermissions(serverId)
    val isOwner = server?.owner == StoatAPI.selfId
    val editable = selfPermissions has PermissionBit.ManageRole &&
            (isDefault || (role?.rank ?: 0.0) > selfServerRank(serverId))

    val initialAllow =
        if (isDefault) server?.defaultPermissions ?: BitDefaults.Server else role?.permissions?.a ?: 0L
    val initialDeny = if (isDefault) 0L else role?.permissions?.d ?: 0L

    var name by remember(role) { mutableStateOf(role?.name ?: "") }
    var colour by remember(role) { mutableStateOf(role?.colour) }
    var hoist by remember(role) { mutableStateOf(role?.hoist ?: false) }
    var allow by remember(initialAllow) { mutableLongStateOf(initialAllow) }
    var deny by remember(initialDeny) { mutableLongStateOf(initialDeny) }
    var error by remember { mutableStateOf<String?>(null) }
    var showColourPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val detailsChanged = !isDefault && role != null &&
            (name != (role.name ?: "") || colour != role.colour || hoist != (role.hoist ?: false))
    val permissionsChanged = allow != initialAllow || deny != initialDeny

    fun save() {
        error = null
        scope.launch {
            try {
                if (detailsChanged) {
                    editRole(
                        serverId, roleId, EditRoleBody(
                            name = name.takeIf { it != role?.name },
                            colour = colour.takeIf { it != role?.colour && it != null },
                            hoist = hoist.takeIf { it != role?.hoist },
                            remove = if (colour == null && role?.colour != null) listOf("Colour") else null
                        )
                    )
                }
                if (permissionsChanged) {
                    if (isDefault) {
                        setServerDefaultPermissions(serverId, allow)
                    } else {
                        setRolePermissions(serverId, roleId, allow, deny)
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    if (showColourPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(sheetState = sheetState, onDismissRequest = { showColourPicker = false }) {
            ColourPickerSheet(
                initialValue = parseRoleColour(colour)?.toArgb() ?: 0xFFFFFFFF.toInt(),
                onColourSelected = {
                    colour = "#%06X".format(it and 0xFFFFFF)
                    showColourPicker = false
                },
                onUseDefaultColour = {
                    colour = null
                    showColourPicker = false
                },
                onDismiss = { showColourPicker = false }
            )
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.manage_role_delete_confirm, role?.name ?: ""),
            text = null,
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                showDelete = false
                scope.launch {
                    try {
                        deleteRole(serverId, roleId)
                        navController.popBackStack()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { showDelete = false }
        )
    }

    ManageScaffold(
        title = if (isDefault) stringResource(R.string.manage_role_default) else role?.name ?: "",
        onBack = { navController.popBackStack() },
        actions = {
            if (!isDefault && editable) {
                IconButton(onClick = { showDelete = true }, modifier = Modifier.testTag("role_delete")) {
                    Icon(
                        painterResource(R.drawable.ic_delete_24dp),
                        stringResource(R.string.manage_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = editable && (detailsChanged || permissionsChanged) && (isDefault || name.isNotBlank()),
                enter = scaleIn(),
                exit = scaleOut()
            ) {
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
            if (!editable) {
                Text(
                    stringResource(R.string.manage_role_locked),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (!isDefault && role != null) {
                ListHeader { Text(stringResource(R.string.manage_role_details)) }
                TextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text(stringResource(R.string.manage_role_name)) },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("role_name")
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.manage_role_colour)) },
                    supportingContent = {
                        Text(colour ?: stringResource(R.string.manage_role_colour_none))
                    },
                    trailingContent = {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    parseRoleColour(colour)
                                        ?: MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    },
                    modifier = Modifier.clickable(enabled = editable) { showColourPicker = true }
                )
                SwitchItem(
                    label = stringResource(R.string.manage_role_hoist),
                    supporting = stringResource(R.string.manage_role_hoist_desc),
                    checked = hoist,
                    onCheckedChange = { hoist = it },
                    enabled = editable
                )
            }

            PermissionEditor(
                groups = ManagePermissionGroups.server,
                triState = !isDefault,
                allow = allow,
                deny = deny,
                onChange = { a, d ->
                    allow = a
                    deny = d
                },
                // You can only hand out permissions you hold yourself
                editable = { bit -> editable && (isOwner || selfPermissions has bit) }
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
