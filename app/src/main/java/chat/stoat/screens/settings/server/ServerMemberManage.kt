package chat.stoat.screens.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.has
import chat.stoat.api.routes.server.EditMemberBody
import chat.stoat.api.routes.server.banUser
import chat.stoat.api.routes.server.editMember
import chat.stoat.api.routes.server.fetchMember
import chat.stoat.api.routes.server.kickMember
import chat.stoat.api.routes.user.fetchUser
import chat.stoat.composables.generic.ListHeader
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.schemas.Member
import kotlinx.coroutines.launch
import java.time.Instant

val timeoutDurations = listOf(60L, 300L, 600L, 3600L, 86400L, 604800L)
val banDeleteDurations = listOf(0L, 3600L, 21600L, 43200L, 86400L, 259200L, 604800L)

fun isTimedOut(member: Member?): Boolean {
    val timeout = member?.timeout ?: return false
    return runCatching { Instant.parse(timeout).isAfter(Instant.now()) }.getOrDefault(false)
}

private fun formatTimeoutUntil(timeout: String): String =
    runCatching {
        java.time.format.DateTimeFormatter
            .ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
            .withZone(java.time.ZoneId.systemDefault())
            .format(Instant.parse(timeout))
    }.getOrDefault(timeout)

/**
 * Whether the current user can moderate (kick/ban/timeout/edit) the target by rank
 */
fun canModerate(serverId: String, userId: String): Boolean {
    val server = StoatAPI.serverCache[serverId] ?: return false
    if (userId == server.owner) return false
    if (userId == StoatAPI.selfId) return false
    return selfServerRank(serverId) < memberServerRank(serverId, userId)
}

private fun userLabel(userId: String): String {
    val user = StoatAPI.userCache[userId]
    return user?.displayName ?: user?.username ?: userId
}

@Composable
fun KickMemberDialog(serverId: String, userId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = stringResource(R.string.manage_kick_confirm, userLabel(userId)),
        text = error,
        confirmLabel = stringResource(R.string.manage_kick),
        onConfirm = {
            scope.launch {
                try {
                    kickMember(serverId, userId)
                    StoatAPI.members.removeMember(serverId, userId)
                    onDone()
                } catch (e: Exception) {
                    error = e.message
                }
            }
        },
        onDismiss = onDismiss
    )
}

@Composable
fun BanMemberDialog(serverId: String, userId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var deleteSeconds by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = stringResource(R.string.manage_ban_confirm, userLabel(userId)),
        text = error,
        confirmLabel = stringResource(R.string.manage_ban),
        onConfirm = {
            scope.launch {
                try {
                    banUser(serverId, userId, reason, deleteSeconds.takeIf { it > 0 })
                    StoatAPI.members.removeMember(serverId, userId)
                    onDone()
                } catch (e: Exception) {
                    error = e.message
                }
            }
        },
        onDismiss = onDismiss,
        extra = {
            TextField(
                value = reason,
                onValueChange = { reason = it.take(1024) },
                label = { Text(stringResource(R.string.manage_ban_reason)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ban_reason")
            )
            OptionPickerItem(
                label = stringResource(R.string.manage_ban_delete_messages),
                options = banDeleteDurations.map { seconds ->
                    seconds to if (seconds == 0L) {
                        context.getString(R.string.manage_ban_delete_none)
                    } else {
                        formatSeconds(context, seconds)
                    }
                },
                selected = deleteSeconds,
                onSelect = { deleteSeconds = it }
            )
        }
    )
}

@Composable
fun TimeoutMemberDialog(serverId: String, userId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seconds by remember { mutableLongStateOf(timeoutDurations[1]) }
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(
        title = stringResource(R.string.manage_timeout_confirm, userLabel(userId)),
        text = error,
        confirmLabel = stringResource(R.string.manage_timeout),
        onConfirm = {
            scope.launch {
                try {
                    val member = editMember(
                        serverId, userId,
                        EditMemberBody(timeout = Instant.now().plusSeconds(seconds).toString())
                    )
                    StoatAPI.members.setMember(serverId, member)
                    onDone()
                } catch (e: Exception) {
                    error = e.message
                }
            }
        },
        onDismiss = onDismiss,
        extra = {
            OptionPickerItem(
                label = stringResource(R.string.manage_timeout_duration),
                options = timeoutDurations.map { it to formatSeconds(context, it) },
                selected = seconds,
                onSelect = { seconds = it }
            )
        }
    )
}

@Composable
fun ServerMemberManage(navController: NavController, serverId: String, userId: String) {
    val scope = rememberCoroutineScope()
    val server = StoatAPI.serverCache[serverId]
    var member by remember { mutableStateOf(StoatAPI.members.getMember(serverId, userId)) }
    var user by remember { mutableStateOf(StoatAPI.userCache[userId]) }
    var error by remember { mutableStateOf<String?>(null) }
    var nickname by remember { mutableStateOf(member?.nickname ?: "") }
    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId, userId) {
        try {
            if (user == null) user = fetchUser(userId)
            val fetched = fetchMember(serverId, userId, pure = true)
            StoatAPI.members.setMember(serverId, fetched)
            member = fetched
            nickname = fetched.nickname ?: ""
        } catch (e: Exception) {
            error = e.message
        }
    }

    val permissions = selfServerPermissions(serverId)
    val isOwner = server?.owner == StoatAPI.selfId
    val isSelf = userId == StoatAPI.selfId
    val outranks = canModerate(serverId, userId)
    val selfRank = selfServerRank(serverId)

    fun update(body: EditMemberBody) {
        error = null
        scope.launch {
            try {
                val updated = editMember(serverId, userId, body)
                StoatAPI.members.setMember(serverId, updated)
                member = updated
                nickname = updated.nickname ?: ""
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    when (dialog) {
        "kick" -> KickMemberDialog(serverId, userId, { dialog = null }) {
            dialog = null
            navController.popBackStack()
        }

        "ban" -> BanMemberDialog(serverId, userId, { dialog = null }) {
            dialog = null
            navController.popBackStack()
        }

        "timeout" -> TimeoutMemberDialog(serverId, userId, { dialog = null }) {
            dialog = null
            member = StoatAPI.members.getMember(serverId, userId)
        }
    }

    ManageScaffold(
        title = member?.nickname ?: user?.displayName ?: user?.username ?: "",
        onBack = { navController.popBackStack() }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ManageError(error)
            val current = member
            if (current == null) {
                CircularProgressIndicator(Modifier.padding(32.dp))
                return@Column
            }

            ListItem(
                headlineContent = { Text("${user?.username}#${user?.discriminator}") },
                supportingContent = { Text(userId) },
                leadingContent = {
                    UserAvatar(
                        username = user?.username ?: "",
                        userId = userId,
                        avatar = current.avatar ?: user?.avatar,
                        size = 48.dp
                    )
                }
            )

            // Identity
            val canEditNick = (isSelf && permissions has PermissionBit.ChangeNickname) ||
                    (permissions has PermissionBit.ManageNicknames && (outranks || isSelf))
            val canRemoveAvatar = permissions has PermissionBit.RemoveAvatars && (outranks || isSelf)
            if (canEditNick || canRemoveAvatar) {
                ListHeader { Text(stringResource(R.string.manage_member_identity)) }
            }
            if (canEditNick) {
                TextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(32) },
                    label = { Text(stringResource(R.string.manage_member_nickname)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("member_nickname")
                )
                if (nickname != (current.nickname ?: "")) {
                    TextButton(
                        onClick = {
                            update(
                                if (nickname.isBlank()) EditMemberBody(remove = listOf("Nickname"))
                                else EditMemberBody(nickname = nickname)
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("member_nickname_save")
                    ) { Text(stringResource(R.string.manage_save)) }
                }
            }
            if (canRemoveAvatar && current.avatar != null) {
                ManageEntry(
                    title = stringResource(R.string.manage_member_remove_avatar),
                    icon = R.drawable.ic_delete_24dp,
                    onClick = { update(EditMemberBody(remove = listOf("Avatar"))) }
                )
            }

            // Roles
            val canAssign = permissions has PermissionBit.AssignRoles
            val roles = server?.roles.orEmpty().entries.sortedBy { it.value.rank ?: 0.0 }
            if (roles.isNotEmpty()) {
                ListHeader { Text(stringResource(R.string.manage_roles)) }
                roles.forEach { (roleId, role) ->
                    val has = current.roles.orEmpty().contains(roleId)
                    val enabled = canAssign && (isOwner || (role.rank ?: 0.0) > selfRank)
                    val toggle = { checked: Boolean ->
                        val next = if (checked) current.roles.orEmpty() + roleId
                        else current.roles.orEmpty() - roleId
                        update(
                            if (next.isEmpty()) EditMemberBody(remove = listOf("Roles"))
                            else EditMemberBody(roles = next)
                        )
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                role.name ?: roleId,
                                color = parseRoleColour(role.colour)
                                    ?: MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = has,
                                enabled = enabled,
                                onCheckedChange = { toggle(it) },
                                modifier = Modifier.testTag("member_role_$roleId")
                            )
                        },
                        modifier = Modifier.clickable(enabled = enabled) { toggle(!has) }
                    )
                }
            }

            // Moderation
            if (!isSelf && outranks) {
                ListHeader { Text(stringResource(R.string.manage_moderation)) }
                if (permissions has PermissionBit.TimeoutMembers) {
                    if (isTimedOut(current)) {
                        ManageEntry(
                            title = stringResource(R.string.manage_timeout_remove),
                            supporting = current.timeout?.let { formatTimeoutUntil(it) },
                            icon = R.drawable.ic_timer_24dp,
                            onClick = { update(EditMemberBody(remove = listOf("Timeout"))) }
                        )
                    } else {
                        ManageEntry(
                            title = stringResource(R.string.manage_timeout),
                            icon = R.drawable.ic_timer_24dp,
                            onClick = { dialog = "timeout" },
                            modifier = Modifier.testTag("member_timeout")
                        )
                    }
                }
                if (permissions has PermissionBit.KickMembers) {
                    ManageEntry(
                        title = stringResource(R.string.manage_kick),
                        icon = R.drawable.ic_logout_24dp,
                        onClick = { dialog = "kick" },
                        danger = true,
                        modifier = Modifier.testTag("member_kick")
                    )
                }
                if (permissions has PermissionBit.BanMembers) {
                    ManageEntry(
                        title = stringResource(R.string.manage_ban),
                        icon = R.drawable.ic_gavel_24dp,
                        onClick = { dialog = "ban" },
                        danger = true,
                        modifier = Modifier.testTag("member_ban")
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
