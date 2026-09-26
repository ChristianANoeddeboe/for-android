package chat.stoat.screens.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.server.CreateChannelBody
import chat.stoat.api.routes.server.EditServerBody
import chat.stoat.api.routes.server.createServerChannel
import chat.stoat.api.routes.server.editServer
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.schemas.Category
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ChannelType
import kotlinx.coroutines.launch

/**
 * Save new categories and mirror them in the cache right away
 */
suspend fun saveCategories(serverId: String, categories: List<Category>) {
    editServer(serverId, EditServerBody(categories = categories))
    StoatAPI.serverCache[serverId]?.let {
        StoatAPI.serverCache[serverId] = it.copy(categories = categories)
    }
}

@Composable
fun channelIcon(channel: Channel?): Int = when (channel?.channelType) {
    ChannelType.VoiceChannel -> R.drawable.ic_volume_up_24dp
    ChannelType.ForumChannel -> R.drawable.ic_forum_24dp
    else -> R.drawable.ic_tag_24dp
}

/**
 * Create a text, voice or forum channel, optionally inside a category
 */
@Composable
fun CreateChannelDialog(
    serverId: String,
    onDismiss: () -> Unit,
    onCreated: (Channel) -> Unit,
    initialCategory: String? = null
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Text") }
    var category by remember { mutableStateOf(initialCategory) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val categories = StoatAPI.serverCache[serverId]?.categories.orEmpty()
    val types = listOf(
        "Text" to stringResource(R.string.manage_channel_type_text),
        "Voice" to stringResource(R.string.manage_channel_type_voice),
        "Forum" to stringResource(R.string.manage_channel_type_forum)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_channel_create)) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = type == value,
                            onClick = { type = value },
                            shape = SegmentedButtonDefaults.itemShape(index, types.size),
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
                TextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text(stringResource(R.string.manage_channel_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("create_channel_name")
                )
                if (categories.isNotEmpty()) {
                    OptionPickerItem(
                        stringResource(R.string.manage_category),
                        listOf<Pair<String?, String>>(null to stringResource(R.string.manage_category_none)) +
                                categories.map { it.id to (it.title ?: "") },
                        category,
                        { category = it }
                    )
                }
                ManageError(error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val channel = createServerChannel(
                                serverId,
                                CreateChannelBody(type = type, name = name.trim())
                            )
                            val target = category
                            if (target != null && channel.id != null) {
                                val current = StoatAPI.serverCache[serverId]?.categories.orEmpty()
                                saveCategories(serverId, current.map {
                                    if (it.id == target) it.copy(channels = it.channels.orEmpty() + channel.id!!)
                                    else it
                                })
                            }
                            onCreated(channel)
                        } catch (e: Exception) {
                            error = e.message
                        }
                        busy = false
                    }
                },
                modifier = Modifier.testTag("create_channel_confirm")
            ) { Text(stringResource(R.string.manage_create)) }
        }
    )
}

@Composable
fun CreateCategoryDialog(serverId: String, onDismiss: () -> Unit, onError: (String?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    TextInputDialog(
        title = stringResource(R.string.manage_category_create),
        label = stringResource(R.string.manage_category_name),
        confirmLabel = stringResource(R.string.manage_create),
        onConfirm = { title ->
            onDismiss()
            scope.launch {
                try {
                    val current = StoatAPI.serverCache[serverId]?.categories.orEmpty()
                    saveCategories(
                        serverId,
                        current + Category(id = ULID.makeNext(), title = title, channels = emptyList())
                    )
                } catch (e: Exception) {
                    onError(e.message)
                }
            }
        },
        onDismiss = onDismiss
    )
}

@Composable
fun ServerSettingsChannels(navController: NavController, serverId: String) {
    val scope = rememberCoroutineScope()
    val server = StoatAPI.serverCache[serverId]
    val categories = server?.categories.orEmpty()
    val categorised = categories.flatMap { it.channels.orEmpty() }.toSet()
    val uncategorised = server?.channels.orEmpty().filter { it !in categorised }

    var error by remember { mutableStateOf<String?>(null) }
    var showCreateChannel by remember { mutableStateOf(false) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var renameCategory by remember { mutableStateOf<Category?>(null) }
    var deleteCategory by remember { mutableStateOf<Category?>(null) }
    var moveChannel by remember { mutableStateOf<String?>(null) }
    var fabMenu by remember { mutableStateOf(false) }

    fun save(newCategories: List<Category>) {
        error = null
        scope.launch {
            try {
                saveCategories(serverId, newCategories)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    /**
     * Move a channel one step, crossing into the neighbouring category at the edges
     */
    fun moveStep(channelId: String, up: Boolean) {
        val lists = categories.map { it.channels.orEmpty().toMutableList() }
        val index = lists.indexOfFirst { channelId in it }
        if (index == -1) {
            // Uncategorised channels only move down into the first category
            if (!up && lists.isNotEmpty()) {
                lists[0].add(0, channelId)
            } else return
        } else {
            val list = lists[index]
            val pos = list.indexOf(channelId)
            val target = if (up) pos - 1 else pos + 1
            if (target in list.indices) {
                list.removeAt(pos)
                list.add(target, channelId)
            } else if (up) {
                list.removeAt(pos)
                if (index > 0) lists[index - 1].add(channelId)
            } else if (index < lists.size - 1) {
                list.removeAt(pos)
                lists[index + 1].add(0, channelId)
            } else return
        }
        save(categories.mapIndexed { i, c -> c.copy(channels = lists[i]) })
    }

    fun moveCategory(index: Int, up: Boolean) {
        val target = if (up) index - 1 else index + 1
        if (target !in categories.indices) return
        val list = categories.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        save(list)
    }

    if (showCreateChannel) {
        CreateChannelDialog(serverId, { showCreateChannel = false }, { showCreateChannel = false })
    }
    if (showCreateCategory) {
        CreateCategoryDialog(serverId, { showCreateCategory = false }, { error = it })
    }
    renameCategory?.let { category ->
        TextInputDialog(
            title = stringResource(R.string.manage_category_rename),
            label = stringResource(R.string.manage_category_name),
            confirmLabel = stringResource(R.string.manage_save),
            initial = category.title ?: "",
            onConfirm = { title ->
                renameCategory = null
                save(categories.map { if (it.id == category.id) it.copy(title = title) else it })
            },
            onDismiss = { renameCategory = null }
        )
    }
    deleteCategory?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.manage_category_delete_confirm, category.title ?: ""),
            text = stringResource(R.string.manage_category_delete_confirm_body),
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                deleteCategory = null
                save(categories.filter { it.id != category.id })
            },
            onDismiss = { deleteCategory = null }
        )
    }
    moveChannel?.let { channelId ->
        run {
            AlertDialog(
                onDismissRequest = { moveChannel = null },
                title = { Text(stringResource(R.string.manage_channel_move_to)) },
                text = {
                    LazyColumn {
                        val targets = listOf<Category?>(null) + categories
                        items(targets) { target ->
                            ListItem(
                                headlineContent = {
                                    Text(target?.title ?: stringResource(R.string.manage_category_none))
                                },
                                modifier = Modifier.clickable {
                                    moveChannel = null
                                    save(categories.map { c ->
                                        val without = c.channels.orEmpty() - channelId
                                        if (c.id == target?.id) c.copy(channels = without + channelId)
                                        else c.copy(channels = without)
                                    })
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { moveChannel = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }

    ManageScaffold(
        title = stringResource(R.string.manage_channels),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = { fabMenu = true },
                    modifier = Modifier.testTag("channels_create")
                ) {
                    Icon(painterResource(R.drawable.ic_add_24dp), stringResource(R.string.manage_create))
                }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_channel_create)) },
                        onClick = {
                            fabMenu = false
                            showCreateChannel = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_category_create)) },
                        onClick = {
                            fabMenu = false
                            showCreateCategory = true
                        }
                    )
                }
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
                    stringResource(R.string.manage_channels_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (uncategorised.isNotEmpty()) {
                item { ListHeader { Text(stringResource(R.string.manage_category_none)) } }
                items(uncategorised, key = { "u$it" }) { channelId ->
                    ChannelRow(
                        channelId,
                        canUp = false,
                        canDown = categories.isNotEmpty(),
                        onUp = {},
                        onDown = { moveStep(channelId, false) },
                        onMove = { moveChannel = channelId },
                        onOpen = { navController.navigate("settings/channel/$channelId") }
                    )
                }
            }

            categories.forEachIndexed { index, category ->
                item(key = "c${category.id}") {
                    ListItem(
                        headlineContent = {
                            Text(
                                (category.title ?: "").uppercase(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        trailingContent = {
                            CategoryActions(
                                canUp = index > 0,
                                canDown = index < categories.size - 1,
                                onUp = { moveCategory(index, true) },
                                onDown = { moveCategory(index, false) },
                                onRename = { renameCategory = category },
                                onDelete = { deleteCategory = category }
                            )
                        },
                        modifier = Modifier.testTag("category_${category.title}")
                    )
                }
                items(category.channels.orEmpty(), key = { "${category.id}-$it" }) { channelId ->
                    val pos = category.channels.orEmpty().indexOf(channelId)
                    ChannelRow(
                        channelId,
                        canUp = true,
                        canDown = pos < category.channels.orEmpty().size - 1 || index < categories.size - 1,
                        onUp = { moveStep(channelId, true) },
                        onDown = { moveStep(channelId, false) },
                        onMove = { moveChannel = channelId },
                        onOpen = { navController.navigate("settings/channel/$channelId") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channelId: String,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onMove: () -> Unit,
    onOpen: () -> Unit
) {
    val channel = StoatAPI.channelCache[channelId]
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(channel?.name ?: channelId, maxLines = 1) },
        leadingContent = { Icon(painterResource(channelIcon(channel)), null) },
        trailingContent = {
            Row {
                IconButton(onClick = onUp, enabled = canUp) {
                    Icon(painterResource(R.drawable.ic_arrow_upward_24dp), stringResource(R.string.manage_move_up))
                }
                IconButton(onClick = onDown, enabled = canDown) {
                    Icon(painterResource(R.drawable.ic_arrow_downward_24dp), stringResource(R.string.manage_move_down))
                }
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.manage_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_channel_move_to)) },
                        onClick = {
                            menu = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_channel_settings)) },
                        onClick = {
                            menu = false
                            onOpen()
                        }
                    )
                }
            }
        },
        modifier = Modifier
            .testTag("channel_row_${channel?.name}")
            .clickable(onClick = onOpen)
    )
}

@Composable
private fun CategoryActions(
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row {
        IconButton(onClick = onUp, enabled = canUp) {
            Icon(painterResource(R.drawable.ic_arrow_upward_24dp), stringResource(R.string.manage_move_up))
        }
        IconButton(onClick = onDown, enabled = canDown) {
            Icon(painterResource(R.drawable.ic_arrow_downward_24dp), stringResource(R.string.manage_move_down))
        }
        IconButton(onClick = { menu = true }) {
            Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(R.string.manage_more))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_category_rename)) },
                onClick = {
                    menu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menu = false
                    onDelete()
                }
            )
        }
    }
}
