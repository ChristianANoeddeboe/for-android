package chat.stoat.screens.settings.server

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.activities.StoatTweenFloat
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.server.EditServerBody
import chat.stoat.api.routes.server.editServer
import chat.stoat.composables.generic.InlineMediaPicker
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.SystemMessages
import kotlinx.coroutines.launch

@Composable
fun ServerSettingsOverview(navController: NavController, serverId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = StoatAPI.serverCache[serverId]

    var name by remember { mutableStateOf(server?.name ?: "") }
    var description by remember { mutableStateOf(server?.description ?: "") }
    var systemMessages by remember { mutableStateOf(server?.systemMessages ?: SystemMessages()) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    val changed = server != null && (
            name != (server.name ?: "") ||
                    description != (server.description ?: "") ||
                    systemMessages != (server.systemMessages ?: SystemMessages())
            )

    val textChannels = server?.channels.orEmpty()
        .mapNotNull { StoatAPI.channelCache[it] }
        .filter { it.channelType == ChannelType.TextChannel }
    val channelOptions =
        listOf<Pair<String?, String>>(null to stringResource(R.string.manage_system_messages_off)) +
                textChannels.map { it.id to "#${it.name}" }

    fun upload(tag: String, uri: Uri?) {
        error = null
        scope.launch {
            uploading = tag
            progress = 0f
            try {
                if (uri == null) {
                    editServer(
                        serverId,
                        EditServerBody(remove = listOf(if (tag == "icons") "Icon" else "Banner"))
                    )
                } else {
                    val id = uploadPicked(context, uri, tag) { progress = it }
                    editServer(
                        serverId,
                        if (tag == "icons") EditServerBody(icon = id) else EditServerBody(banner = id)
                    )
                }
            } catch (e: Exception) {
                error = e.message
            }
            uploading = null
        }
    }

    fun save() {
        error = null
        scope.launch {
            try {
                val remove = mutableListOf<String>()
                if (description.isBlank() && !server?.description.isNullOrEmpty()) remove += "Description"
                editServer(
                    serverId,
                    EditServerBody(
                        name = name.takeIf { it != server?.name },
                        description = description.takeIf { it.isNotBlank() && it != server?.description },
                        systemMessages = systemMessages.takeIf { it != server?.systemMessages },
                        remove = remove.ifEmpty { null }
                    )
                )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    ManageScaffold(
        title = stringResource(R.string.manage_overview),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            AnimatedVisibility(
                visible = changed && name.isNotBlank(),
                enter = scaleIn(animationSpec = StoatTweenFloat),
                exit = scaleOut(animationSpec = StoatTweenFloat)
            ) {
                FloatingActionButton(
                    onClick = { save() },
                    modifier = Modifier.testTag("manage_save")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24dp),
                        contentDescription = stringResource(R.string.manage_save)
                    )
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
            ListHeader { Text(stringResource(R.string.manage_server_icon)) }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                InlineMediaPicker(
                    currentModel = server?.icon?.let { "$STOAT_FILES/icons/${it.id}" },
                    onPick = { upload("icons", it) },
                    circular = true,
                    canRemove = server?.icon != null,
                    enabled = uploading == null,
                    onRemove = { upload("icons", null) },
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                )
            }
            AnimatedVisibility(visible = uploading != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            ManageError(error)

            TextField(
                label = { Text(stringResource(R.string.manage_server_name)) },
                value = name,
                onValueChange = { name = it.take(32) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("server_overview_name")
            )
            TextField(
                label = { Text(stringResource(R.string.manage_server_description)) },
                value = description,
                onValueChange = { description = it.take(1024) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ListHeader { Text(stringResource(R.string.manage_server_banner)) }
            InlineMediaPicker(
                currentModel = server?.banner?.let { "$STOAT_FILES/banners/${it.id}" },
                onPick = { upload("banners", it) },
                canRemove = server?.banner != null,
                enabled = uploading == null,
                onRemove = { upload("banners", null) },
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
            )

            ListHeader { Text(stringResource(R.string.manage_system_messages)) }
            OptionPickerItem(
                stringResource(R.string.manage_system_messages_joined),
                channelOptions,
                systemMessages.userJoined,
                { systemMessages = systemMessages.copy(userJoined = it) }
            )
            OptionPickerItem(
                stringResource(R.string.manage_system_messages_left),
                channelOptions,
                systemMessages.userLeft,
                { systemMessages = systemMessages.copy(userLeft = it) }
            )
            OptionPickerItem(
                stringResource(R.string.manage_system_messages_kicked),
                channelOptions,
                systemMessages.userKicked,
                { systemMessages = systemMessages.copy(userKicked = it) }
            )
            OptionPickerItem(
                stringResource(R.string.manage_system_messages_banned),
                channelOptions,
                systemMessages.userBanned,
                { systemMessages = systemMessages.copy(userBanned = it) }
            )
        }
    }
}
