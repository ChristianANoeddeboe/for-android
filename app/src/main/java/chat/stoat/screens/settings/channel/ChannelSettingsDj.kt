package chat.stoat.screens.settings.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.dj.Dj
import chat.stoat.api.routes.channel.djDisable
import chat.stoat.api.routes.channel.djEditSettings
import chat.stoat.api.routes.channel.djEnable
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.DataEditDjSettings
import chat.stoat.core.model.schemas.DjInformation
import chat.stoat.screens.chat.views.channel.formatDjTime
import chat.stoat.screens.settings.server.ManageError
import chat.stoat.screens.settings.server.ManageScaffold
import chat.stoat.screens.settings.server.SwitchItem
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Channel settings > DJ.
 *
 * Enables/disables the DJ capability of a text channel and edits its limits. Every field mirrors the
 * backend's validation: `skip_threshold` 0..1, `max_file_mb` 1..128, `max_track_seconds` 5..3600,
 * `max_tracks` 1..1000, `max_users` 0..1000 (0 = unlimited).
 */
@Composable
fun ChannelSettingsDj(navController: NavController, channelId: String) {
    val scope = rememberCoroutineScope()
    val channel = StoatAPI.channelCache[channelId]
    val isTextChannel = channel?.channelType == ChannelType.TextChannel

    var dj by remember { mutableStateOf(channel?.dj) }
    var skipThreshold by remember { mutableStateOf(channel?.dj?.skipThreshold ?: 0.5f) }
    var maxFileMb by remember { mutableStateOf((channel?.dj?.maxFileMb ?: 50).toString()) }
    var maxTrackSeconds by remember {
        mutableStateOf((channel?.dj?.maxTrackSeconds ?: 900).toString())
    }
    var maxTracks by remember { mutableStateOf((channel?.dj?.maxTracks ?: 100).toString()) }
    var maxUsers by remember { mutableStateOf((channel?.dj?.maxUsers ?: 0).toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    fun applySettings(settings: DjInformation?) {
        dj = settings
        settings ?: return

        skipThreshold = settings.skipThreshold
        maxFileMb = settings.maxFileMb.toString()
        maxTrackSeconds = settings.maxTrackSeconds.toString()
        maxTracks = settings.maxTracks.toString()
        maxUsers = (settings.maxUsers ?: 0).toString()
    }

    fun setEnabled(enabled: Boolean) {
        if (busy) return

        scope.launch {
            busy = true
            error = null

            try {
                val state = if (enabled) djEnable(channelId) else djDisable(channelId)
                applySettings(state.settings)
                Dj.settingsUpdated(channelId, state.settings)
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    val current = dj
    val changed = current != null && (
            skipThreshold != current.skipThreshold ||
                    maxFileMb.toIntOrNull() != current.maxFileMb ||
                    maxTrackSeconds.toIntOrNull() != current.maxTrackSeconds ||
                    maxTracks.toIntOrNull() != current.maxTracks ||
                    maxUsers.toIntOrNull() != (current.maxUsers ?: 0)
            )

    fun save() {
        val fileMb = maxFileMb.toIntOrNull() ?: return
        val trackSeconds = maxTrackSeconds.toIntOrNull() ?: return
        val tracks = maxTracks.toIntOrNull() ?: return
        val users = maxUsers.toIntOrNull() ?: return

        if (fileMb !in 1..128) {
            error = "Max file size must be between 1 and 128 MB"
            return
        }
        if (trackSeconds !in 5..3600) {
            error = "Max track length must be between 5 and 3600 seconds"
            return
        }
        if (tracks !in 1..1000) {
            error = "Max queued tracks must be between 1 and 1000"
            return
        }
        if (users !in 0..1000) {
            error = "Max listeners must be between 0 and 1000"
            return
        }

        scope.launch {
            busy = true
            error = null

            try {
                val state = djEditSettings(
                    channelId,
                    DataEditDjSettings(
                        skipThreshold = skipThreshold,
                        maxFileMb = fileMb,
                        maxTrackSeconds = trackSeconds,
                        maxTracks = tracks,
                        maxUsers = users
                    )
                )

                applySettings(state.settings)
                Dj.settingsUpdated(channelId, state.settings)
                saved = true
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    ManageScaffold(
        title = stringResource(R.string.dj_settings),
        onBack = { navController.popBackStack() },
        floatingActionButton = {
            AnimatedVisibility(
                visible = changed || saved,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        saved = false
                        save()
                    },
                    modifier = Modifier.testTag("dj_settings_save")
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check_24dp),
                        stringResource(R.string.dj_settings_save)
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
            ManageError(error)

            if (!isTextChannel) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dj_settings_unsupported))
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.dj_settings_channel_type,
                                channel?.channelType?.value ?: "?"
                            )
                        )
                    }
                )
            } else if (dj == null) {
                ListHeader { Text(stringResource(R.string.dj_settings)) }
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.dj_settings_enable))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.dj_settings_enable_desc))
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_headset_24dp),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .testTag("dj_settings_enable")
                        .padding(vertical = 4.dp)
                )
                Button(
                    onClick = { setEnabled(true) },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("dj_settings_enable_button")
                ) {
                    Text(stringResource(R.string.dj_settings_enable))
                }
            } else {

            SwitchItem(
                label = stringResource(R.string.dj_settings_enabled),
                supporting = stringResource(R.string.dj_settings_enabled_desc),
                checked = dj?.enabled == true,
                enabled = !busy,
                onCheckedChange = { setEnabled(it) },
                modifier = Modifier.testTag("dj_settings_switch")
            )

            ListHeader { Text(stringResource(R.string.dj_settings_section_playback)) }
            Text(
                text = stringResource(R.string.dj_settings_skip_threshold),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                text = stringResource(
                    R.string.dj_settings_skip_threshold_desc,
                    (skipThreshold * 100).roundToInt()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Slider(
                value = skipThreshold,
                onValueChange = { skipThreshold = it },
                valueRange = 0f..1f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("dj_settings_skip_threshold")
            )

            ListHeader { Text(stringResource(R.string.dj_settings_limits)) }
            DjNumberField(
                label = stringResource(R.string.dj_settings_max_file_mb),
                value = maxFileMb,
                onValueChange = { maxFileMb = it },
                testTag = "dj_settings_max_file_mb"
            )
            DjNumberField(
                label = stringResource(R.string.dj_settings_max_track_seconds),
                value = maxTrackSeconds,
                onValueChange = { maxTrackSeconds = it },
                testTag = "dj_settings_max_track_seconds",
                supporting = formatDjTime((maxTrackSeconds.toLongOrNull() ?: 0) * 1000)
            )
            DjNumberField(
                label = stringResource(R.string.dj_settings_max_tracks),
                value = maxTracks,
                onValueChange = { maxTracks = it },
                testTag = "dj_settings_max_tracks"
            )
            DjNumberField(
                label = stringResource(R.string.dj_settings_max_users),
                value = maxUsers,
                onValueChange = { maxUsers = it },
                testTag = "dj_settings_max_users"
            )
            Text(
                text = "Channel storage limit: ${dj?.maxTotalBytes?.div(1024 * 1024) ?: 0} MiB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun DjNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    supporting: String? = null,
) {
    TextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(10)) },
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(testTag)
    )
}
