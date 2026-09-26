package chat.stoat.screens.settings.server

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.server.BanListResponse
import chat.stoat.api.routes.server.ServerInvite
import chat.stoat.api.routes.server.createServerEmoji
import chat.stoat.api.routes.server.deleteEmoji
import chat.stoat.api.routes.server.deleteInvite
import chat.stoat.api.routes.server.fetchMembers
import chat.stoat.api.routes.server.fetchServerBans
import chat.stoat.api.routes.server.fetchServerEmojis
import chat.stoat.api.routes.server.fetchServerInvites
import chat.stoat.api.routes.server.unbanUser
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.data.STOAT_INVITES
import chat.stoat.core.model.schemas.Emoji
import chat.stoat.core.model.schemas.Member
import chat.stoat.core.model.schemas.User
import kotlinx.coroutines.launch

@Composable
private fun Loading() {
    CircularProgressIndicator(
        Modifier
            .padding(32.dp)
            .size(32.dp)
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

private val emojiNameRegex = Regex("^[a-z0-9_]+$")

@Composable
fun ServerSettingsEmojis(navController: NavController, serverId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var emojis by remember { mutableStateOf<List<Emoji>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Emoji?>(null) }

    suspend fun reload() {
        try {
            emojis = fetchServerEmojis(serverId).sortedBy { it.name }
        } catch (e: Exception) {
            error = e.message
            emojis = emptyList()
        }
    }

    LaunchedEffect(serverId) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) picked = uri
    }

    picked?.let { uri ->
        var name by remember(uri) {
            mutableStateOf(
                (uri.lastPathSegment ?: "emoji").substringAfterLast('/').substringBeforeLast('.')
                    .lowercase().replace(Regex("[^a-z0-9_]"), "_").take(32)
            )
        }
        val valid = name.isNotEmpty() && emojiNameRegex.matches(name)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.manage_emoji_upload)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    RemoteImage(
                        url = uri.toString(),
                        description = null,
                        modifier = Modifier.size(64.dp)
                    )
                    TextField(
                        value = name,
                        onValueChange = { name = it.lowercase().take(32) },
                        label = { Text(stringResource(R.string.manage_emoji_name)) },
                        supportingText = { Text(stringResource(R.string.manage_emoji_name_hint)) },
                        isError = !valid,
                        singleLine = true,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { picked = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    enabled = valid && !uploading,
                    onClick = {
                        uploading = true
                        error = null
                        scope.launch {
                            try {
                                val id = uploadPicked(context, uri, "emojis")
                                createServerEmoji(serverId, id, name)
                                picked = null
                                reload()
                            } catch (e: Exception) {
                                error = e.message
                                picked = null
                            }
                            uploading = false
                        }
                    }
                ) { Text(stringResource(R.string.manage_emoji_upload)) }
            }
        )
    }

    deleting?.let { emoji ->
        ConfirmDialog(
            title = stringResource(R.string.manage_emoji_delete_confirm, emoji.name ?: ""),
            text = null,
            confirmLabel = stringResource(R.string.manage_delete),
            onConfirm = {
                deleting = null
                scope.launch {
                    try {
                        deleteEmoji(emoji.id!!)
                        reload()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { deleting = null }
        )
    }

    ManageScaffold(
        title = stringResource(R.string.manage_emojis),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch("image/*") },
                icon = { Icon(painterResource(R.drawable.ic_add_24dp), null) },
                text = { Text(stringResource(R.string.manage_emoji_upload)) }
            )
        }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item { ManageError(error) }
            val list = emojis
            when {
                list == null -> item { Loading() }
                list.isEmpty() -> item { EmptyText(stringResource(R.string.manage_emoji_empty)) }
                else -> {
                    item {
                        EmptyText(pluralStringResource(R.plurals.manage_emoji_count, list.size, list.size))
                    }
                    items(list, key = { it.id ?: "" }) { emoji ->
                        ListItem(
                            headlineContent = { Text(":${emoji.name}:") },
                            supportingContent = {
                                emoji.creatorID?.let { StoatAPI.userCache[it]?.username }?.let {
                                    Text(stringResource(R.string.manage_uploaded_by, it))
                                }
                            },
                            leadingContent = {
                                RemoteImage(
                                    url = "$STOAT_FILES/emojis/${emoji.id}",
                                    description = emoji.name,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { deleting = emoji }) {
                                    Icon(
                                        painterResource(R.drawable.ic_delete_24dp),
                                        stringResource(R.string.manage_delete)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerSettingsInvites(navController: NavController, serverId: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var invites by remember { mutableStateOf<List<ServerInvite>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        try {
            invites = fetchServerInvites(serverId)
            // Resolve creator names
            val missing = invites.orEmpty().mapNotNull { it.creator }
                .filter { StoatAPI.userCache[it] == null }.distinct()
            missing.forEach { runCatching { chat.stoat.api.routes.user.fetchUser(it) } }
        } catch (e: Exception) {
            error = e.message
            invites = emptyList()
        }
    }

    ManageScaffold(
        title = stringResource(R.string.manage_invites),
        onBack = { navController.popBackStack() }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item { ManageError(error) }
            val list = invites
            when {
                list == null -> item { Loading() }
                list.isEmpty() -> item { EmptyText(stringResource(R.string.manage_invites_empty)) }
                else -> items(list, key = { it.code }) { invite ->
                    val creator = invite.creator?.let { StoatAPI.userCache[it] }
                    val channel = invite.channel?.let { StoatAPI.channelCache[it] }
                    ListItem(
                        headlineContent = { Text(invite.code) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    channel?.name?.let { "#$it" },
                                    creator?.let { it.displayName ?: it.username }
                                ).joinToString(" · ")
                            )
                        },
                        leadingContent = {
                            creator?.let {
                                UserAvatar(
                                    username = it.username ?: "",
                                    userId = it.id ?: "",
                                    avatar = it.avatar,
                                    size = 32.dp
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        deleteInvite(invite.code)
                                        invites = invites?.filter { it.code != invite.code }
                                    } catch (e: Exception) {
                                        error = e.message
                                    }
                                }
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24dp),
                                    stringResource(R.string.manage_invite_revoke)
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            copyWithToast(context, clipboard, "$STOAT_INVITES/${invite.code}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerSettingsBans(navController: NavController, serverId: String) {
    val scope = rememberCoroutineScope()
    var bans by remember { mutableStateOf<BanListResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var unbanning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        try {
            bans = fetchServerBans(serverId)
        } catch (e: Exception) {
            error = e.message
            bans = BanListResponse(emptyList(), emptyList())
        }
    }

    unbanning?.let { userId ->
        val user = bans?.users?.firstOrNull { it.id == userId }
        ConfirmDialog(
            title = stringResource(R.string.manage_unban_confirm, user?.username ?: userId),
            text = null,
            confirmLabel = stringResource(R.string.manage_unban),
            dangerous = false,
            onConfirm = {
                unbanning = null
                scope.launch {
                    try {
                        unbanUser(serverId, userId)
                        bans = bans?.let { b ->
                            b.copy(bans = b.bans.filter { it.id.user != userId })
                        }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { unbanning = null }
        )
    }

    ManageScaffold(
        title = stringResource(R.string.manage_bans),
        onBack = { navController.popBackStack() }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item { ManageError(error) }
            val list = bans
            when {
                list == null -> item { Loading() }
                list.bans.isEmpty() -> item { EmptyText(stringResource(R.string.manage_bans_empty)) }
                else -> items(list.bans, key = { it.id.user }) { ban ->
                    val user = list.users.firstOrNull { it.id == ban.id.user }
                    ListItem(
                        headlineContent = {
                            Text(user?.let { "${it.username}#${it.discriminator}" } ?: ban.id.user)
                        },
                        supportingContent = {
                            Text(ban.reason ?: stringResource(R.string.manage_ban_no_reason))
                        },
                        leadingContent = {
                            UserAvatar(
                                username = user?.username ?: "",
                                userId = ban.id.user,
                                avatar = user?.avatar,
                                size = 32.dp
                            )
                        },
                        modifier = Modifier
                            .testTag("ban_${user?.username}")
                            .clickable { unbanning = ban.id.user }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerSettingsMembers(navController: NavController, serverId: String) {
    var all by remember { mutableStateOf<List<Pair<Member, User>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(serverId) {
        try {
            val response = fetchMembers(serverId, includeOffline = true)
            val users = response.users.associateBy { it.id }
            all = response.members.mapNotNull { member ->
                val userId = member.id?.user ?: return@mapNotNull null
                val user = StoatAPI.userCache[userId] ?: users[userId] ?: return@mapNotNull null
                (StoatAPI.members.getMember(serverId, userId) ?: member) to user
            }
        } catch (e: Exception) {
            error = e.message
            all = emptyList()
        }
    }

    val members = all.orEmpty()
        .filter { (member, user) ->
            query.isBlank() || listOfNotNull(member.nickname, user.displayName, user.username)
                .any { it.contains(query, ignoreCase = true) }
        }
        .sortedBy { (member, user) ->
            (member.nickname ?: user.displayName ?: user.username ?: "").lowercase()
        }

    ManageScaffold(
        title = stringResource(R.string.manage_members),
        onBack = { navController.popBackStack() }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.manage_members_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item { ManageError(error) }
            if (all == null) item { Loading() }
            items(members, key = { it.second.id ?: "" }) { (member, user: User) ->
                ListItem(
                    headlineContent = {
                        Text(member.nickname ?: user.displayName ?: user.username ?: "")
                    },
                    supportingContent = { Text("${user.username}#${user.discriminator}") },
                    leadingContent = {
                        UserAvatar(
                            username = user.username ?: "",
                            userId = user.id ?: "",
                            avatar = member.avatar ?: user.avatar,
                            size = 40.dp
                        )
                    },
                    modifier = Modifier
                        .testTag("member_${user.username}")
                        .clickable { navController.navigate("settings/server/$serverId/members/${user.id}") }
                )
            }
        }
    }
}
