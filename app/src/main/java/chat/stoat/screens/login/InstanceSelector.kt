package chat.stoat.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.instance.InstanceManager
import chat.stoat.core.model.data.StoatInstance
import chat.stoat.core.model.data.StoatInstances
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Shows the active instance and lets the user switch to a self-hosted one before logging in.
 */
@Composable
fun InstanceSelector(
    modifier: Modifier = Modifier,
    onChanged: (StoatInstance) -> Unit = {},
) {
    var current by remember { mutableStateOf(StoatInstances.current) }
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${stringResource(R.string.instance_label)}: ${current.displayHost()}",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.testTag("instance_label")
        )
        TextButton(
            onClick = { showDialog = true },
            modifier = Modifier.testTag("instance_change_button")
        ) {
            Text(stringResource(R.string.instance_change))
        }
    }

    if (showDialog) {
        InstanceDialog(
            initial = if (StoatInstances.isOfficial) "" else current.api,
            onDismiss = { showDialog = false },
            onSelected = {
                current = it
                showDialog = false
                onChanged(it)
            }
        )
    }
}

@Composable
private fun InstanceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSelected: (StoatInstance) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorText = stringResource(R.string.instance_dialog_error)

    fun select(resolve: suspend () -> StoatInstance) {
        loading = true
        error = null
        scope.launch {
            try {
                val instance = resolve()
                InstanceManager.switchTo(context, instance)
                onSelected(instance)
            } catch (e: Exception) {
                error = errorText
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.instance_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.instance_dialog_body))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    placeholder = { Text(stringResource(R.string.instance_dialog_hint)) },
                    singleLine = true,
                    enabled = !loading,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        if (input.isNotBlank()) select { InstanceManager.resolve(input) }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag("instance_url_field")
                )
                TextButton(
                    onClick = { select { StoatInstance.Official } },
                    enabled = !loading,
                    modifier = Modifier.testTag("instance_use_official_button")
                ) {
                    Text(stringResource(R.string.instance_dialog_use_official))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { select { InstanceManager.resolve(input) } },
                enabled = !loading && input.isNotBlank(),
                modifier = Modifier.testTag("instance_connect_button")
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(stringResource(R.string.instance_dialog_connect))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun StoatInstance.displayHost(): String =
    runCatching { URI(webApp).host }.getOrNull() ?: webApp
