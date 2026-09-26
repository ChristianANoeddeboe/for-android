package chat.stoat.screens.settings.bots

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
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
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.has
import chat.stoat.api.routes.bots.EditBotBody
import chat.stoat.api.routes.bots.OwnedBot
import chat.stoat.api.routes.bots.createBot
import chat.stoat.api.routes.bots.deleteBot
import chat.stoat.api.routes.bots.editBot
import chat.stoat.api.routes.bots.editBotAvatar
import chat.stoat.api.routes.bots.fetchBot
import chat.stoat.api.routes.bots.fetchOwnedBots
import chat.stoat.api.routes.bots.inviteBot
import chat.stoat.composables.generic.InlineMediaPicker
import chat.stoat.composables.generic.ListHeader
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.User
import chat.stoat.screens.settings.server.ConfirmDialog
import chat.stoat.screens.settings.server.ManageEntry
import chat.stoat.screens.settings.server.ManageError
import chat.stoat.screens.settings.server.ManageScaffold
import chat.stoat.screens.settings.server.SwitchItem
import chat.stoat.screens.settings.server.TextInputDialog
import chat.stoat.screens.settings.server.copyWithToast
import chat.stoat.screens.settings.server.selfServerPermissions
import chat.stoat.screens.settings.server.uploadPicked
import kotlinx.coroutines.launch

@Composable
fun BotsSettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var bots by remember { mutableStateOf<List<Pair<OwnedBot, User?>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val owned = fetchOwnedBots()
            bots = owned.bots.map { bot -> bot to owned.users.firstOrNull { it.id == bot.id } }
        } catch (e: Exception) {
            error = e.message
            bots = emptyList()
        }
    }

    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.manage_bot_create),
            label = stringResource(R.string.manage_bot_name),
            confirmLabel = stringResource(R.string.manage_create),
            onConfirm = { name ->
                creating = false
                scope.launch {
                    try {
                        val id = createBot(name)
                        navController.navigate("settings/bots/$id")
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { creating = false }
        )
    }

    ManageScaffold(
        title = stringResource(R.string.manage_bots),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(painterResource(R.drawable.ic_add_24dp), null) },
                text = { Text(stringResource(R.string.manage_bot_create)) },
                modifier = Modifier.testTag("bots_create")
            )
        }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item { ManageError(error) }
            val list = bots
            when {
                list == null -> item { CircularProgressIndicator(Modifier.padding(32.dp)) }
                list.isEmpty() -> item {
                    Text(
                        stringResource(R.string.manage_bots_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                else -> items(list, key = { it.first.id }) { (bot, user) ->
                    ListItem(
                        headlineContent = { Text(user?.username ?: bot.id) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (bot.public) R.string.manage_bot_public else R.string.manage_bot_private
                                )
                            )
                        },
                        leadingContent = {
                            UserAvatar(
                                username = user?.username ?: "",
                                userId = bot.id,
                                avatar = user?.avatar,
                                size = 40.dp
                            )
                        },
                        modifier = Modifier
                            .testTag("bot_${user?.username}")
                            .clickable { navController.navigate("settings/bots/${bot.id}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteBotDialog(botId: String, onDismiss: () -> Unit, onError: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers = StoatAPI.serverCache.values
        .filter { server -> server.id?.let { selfServerPermissions(it) has PermissionBit.ManageServer } == true }
        .sortedBy { it.name?.lowercase() }
    val groups = StoatAPI.channelCache.values
        .filter { it.channelType == ChannelType.Group && it.owner == StoatAPI.selfId }
        .sortedBy { it.name?.lowercase() }

    fun invite(serverId: String?, groupId: String?) {
        scope.launch {
            try {
                inviteBot(botId, serverId, groupId)
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.manage_bot_invited),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onError(null)
            } catch (e: Exception) {
                onError(e.message)
            }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_bot_invite)) },
        text = {
            LazyColumn {
                if (servers.isEmpty() && groups.isEmpty()) {
                    item { Text(stringResource(R.string.manage_bot_invite_none)) }
                }
                if (servers.isNotEmpty()) {
                    item { ListHeader { Text(stringResource(R.string.manage_servers)) } }
                    items(servers, key = { it.id ?: "" }) { server ->
                        ListItem(
                            headlineContent = { Text(server.name ?: "") },
                            modifier = Modifier
                                .testTag("bot_invite_${server.name}")
                                .clickable { invite(server.id, null) }
                        )
                    }
                }
                if (groups.isNotEmpty()) {
                    item { ListHeader { Text(stringResource(R.string.manage_groups)) } }
                    items(groups, key = { it.id ?: "" }) { group ->
                        ListItem(
                            headlineContent = { Text(group.name ?: "") },
                            modifier = Modifier.clickable { invite(null, group.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun BotDetailScreen(navController: NavController, botId: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var bot by remember { mutableStateOf<OwnedBot?>(null) }
    var user by remember { mutableStateOf<User?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var interactionsUrl by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val fetched = fetchBot(botId)
        bot = fetched.bot
        user = fetched.user
        name = fetched.user.username ?: ""
        interactionsUrl = fetched.bot.interactionsUrl ?: ""
    }

    LaunchedEffect(botId) {
        try {
            reload()
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun edit(body: EditBotBody) {
        error = null
        scope.launch {
            try {
                editBot(botId, body)
                reload()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    when (dialog) {
        "reset" -> ConfirmDialog(
            title = stringResource(R.string.manage_bot_token_reset),
            text = stringResource(R.string.manage_bot_token_reset_desc),
            confirmLabel = stringResource(R.string.manage_bot_token_reset),
            onConfirm = {
                dialog = null
                edit(EditBotBody(remove = listOf("Token")))
            },
            onDismiss = { dialog = null }
        )

        "delete" -> ConfirmDialog(
            title = stringResource(R.string.manage_bot_delete_confirm, user?.username ?: ""),
            text = null,
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                dialog = null
                scope.launch {
                    try {
                        deleteBot(botId)
                        navController.popBackStack()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { dialog = null }
        )

        "invite" -> InviteBotDialog(botId, { dialog = null }) { error = it }
    }

    val current = bot
    val detailsChanged = current != null &&
            (name != (user?.username ?: "") || interactionsUrl != (current.interactionsUrl ?: ""))

    ManageScaffold(
        title = user?.username ?: stringResource(R.string.manage_bots),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            AnimatedVisibility(
                visible = detailsChanged && name.isNotBlank(),
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        edit(
                            EditBotBody(
                                name = name.takeIf { it != user?.username },
                                interactionsUrl = interactionsUrl.trim()
                                    .takeIf { it.isNotEmpty() && it != current?.interactionsUrl },
                                remove = if (interactionsUrl.isBlank() && current?.interactionsUrl != null) {
                                    listOf("InteractionsURL")
                                } else {
                                    null
                                }
                            )
                        )
                    },
                    modifier = Modifier.testTag("manage_save")
                ) {
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
            if (current == null) {
                CircularProgressIndicator(Modifier.padding(32.dp))
                return@Column
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                InlineMediaPicker(
                    currentModel = user?.avatar?.let { "$STOAT_FILES/avatars/${it.id}" },
                    onPick = { uri ->
                        uploading = true
                        scope.launch {
                            try {
                                val id = uploadPicked(context, uri, "avatars")
                                editBotAvatar(botId, id)
                                reload()
                            } catch (e: Exception) {
                                error = e.message
                            }
                            uploading = false
                        }
                    },
                    circular = true,
                    useAvatarCircularity = true,
                    canRemove = user?.avatar != null,
                    enabled = !uploading,
                    onRemove = {
                        scope.launch {
                            try {
                                editBotAvatar(botId, null)
                                reload()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (uploading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            TextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text(stringResource(R.string.manage_bot_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("bot_name")
            )
            TextField(
                value = interactionsUrl,
                onValueChange = { interactionsUrl = it.take(2048) },
                label = { Text(stringResource(R.string.manage_bot_interactions_url)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SwitchItem(
                label = stringResource(R.string.manage_bot_public),
                supporting = stringResource(R.string.manage_bot_public_desc),
                checked = current.public,
                onCheckedChange = { edit(EditBotBody(public = it)) },
                modifier = Modifier.testTag("bot_public")
            )
            SwitchItem(
                label = stringResource(R.string.manage_bot_analytics),
                supporting = stringResource(R.string.manage_bot_analytics_desc),
                checked = current.analytics,
                onCheckedChange = { edit(EditBotBody(analytics = it)) }
            )

            ListHeader { Text(stringResource(R.string.manage_bot_token)) }
            ManageEntry(
                title = stringResource(R.string.manage_bot_token_copy),
                supporting = stringResource(R.string.manage_bot_token_desc),
                icon = R.drawable.ic_content_copy_24dp,
                onClick = { copyWithToast(context, clipboard, current.token) },
                modifier = Modifier.testTag("bot_token_copy")
            )
            ManageEntry(
                title = stringResource(R.string.manage_bot_token_reset),
                icon = R.drawable.ic_key_24dp,
                onClick = { dialog = "reset" }
            )

            ListHeader { Text(stringResource(R.string.manage_bot_actions)) }
            ManageEntry(
                title = stringResource(R.string.manage_bot_invite),
                icon = R.drawable.ic_group_add_24dp,
                onClick = { dialog = "invite" },
                modifier = Modifier.testTag("bot_invite")
            )
            ManageEntry(
                title = stringResource(R.string.manage_bot_copy_id),
                icon = R.drawable.ic_content_copy_24dp,
                onClick = { copyWithToast(context, clipboard, botId) }
            )
            ManageEntry(
                title = stringResource(R.string.manage_bot_delete),
                icon = R.drawable.ic_delete_24dp,
                onClick = { dialog = "delete" },
                danger = true,
                modifier = Modifier.testTag("bot_delete")
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
