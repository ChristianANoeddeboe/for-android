package chat.stoat.dialogs

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.internals.update.AppUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CHECK_ATTEMPTS = 3
private const val CHECK_RETRY_DELAY = 30_000L

private sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val update: AppUpdater.AvailableUpdate) : UpdateState
    data class Downloading(val update: AppUpdater.AvailableUpdate, val progress: Float?) :
        UpdateState

    data class Ready(val update: AppUpdater.AvailableUpdate, val apk: File) : UpdateState
    data class Failed(val update: AppUpdater.AvailableUpdate) : UpdateState
}

/**
 * Checks for a newer release once per app launch and walks the user through downloading and
 * installing it.
 */
@Composable
fun AppUpdateDialog() {
    if (!AppUpdater.isSupported) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checked by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    LaunchedEffect(Unit) {
        if (checked) return@LaunchedEffect
        checked = true
        withContext(Dispatchers.IO) { AppUpdater.clearDownloads(context) }

        // The network is often not ready right after launch, so retry a few times.
        repeat(CHECK_ATTEMPTS) { attempt ->
            try {
                AppUpdater.checkForUpdate()?.let { state = UpdateState.Available(it) }
                return@LaunchedEffect
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("AppUpdateDialog", "Failed to check for updates", e)
            }
            if (attempt < CHECK_ATTEMPTS - 1) delay(CHECK_RETRY_DELAY)
        }
    }

    fun startDownload(update: AppUpdater.AvailableUpdate) {
        state = UpdateState.Downloading(update, null)
        scope.launch {
            state = try {
                val apk = AppUpdater.download(context, update) { progress ->
                    state = UpdateState.Downloading(update, progress)
                }
                UpdateState.Ready(update, apk)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("AppUpdateDialog", "Failed to download update", e)
                UpdateState.Failed(update)
            }
        }
    }

    fun install(ready: UpdateState.Ready) {
        if (AppUpdater.canInstall(context)) {
            AppUpdater.install(context, ready.apk)
        } else {
            AppUpdater.requestInstallPermission(context)
        }
    }

    val current = state
    val update = when (current) {
        is UpdateState.Available -> current.update
        is UpdateState.Downloading -> current.update
        is UpdateState.Ready -> current.update
        is UpdateState.Failed -> current.update
        UpdateState.Idle -> return
    }
    val dismiss = { state = UpdateState.Idle }

    AlertDialog(
        onDismissRequest = {
            if (current !is UpdateState.Downloading) dismiss()
        },
        title = { Text(stringResource(R.string.app_update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.app_update_body, update.version))
                when (current) {
                    is UpdateState.Downloading -> {
                        val progress = current.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            )
                        }
                    }

                    is UpdateState.Ready -> {
                        Text(
                            stringResource(R.string.app_update_ready),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    is UpdateState.Failed -> {
                        Text(
                            stringResource(R.string.app_update_failed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    else -> update.notes?.let { notes ->
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (current) {
                is UpdateState.Available, is UpdateState.Failed -> TextButton(
                    onClick = { startDownload(update) }
                ) {
                    Text(stringResource(R.string.app_update_download))
                }

                is UpdateState.Ready -> TextButton(onClick = { install(current) }) {
                    Text(stringResource(R.string.app_update_install))
                }

                else -> {}
            }
        },
        dismissButton = {
            if (current !is UpdateState.Downloading) {
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.app_update_later))
                }
            }
        }
    )
}
