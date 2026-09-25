package chat.stoat.api.realtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import chat.stoat.api.StoatAPI
import chat.stoat.voice.VoiceCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.logcat

/**
 * Keeps the websocket open only while the app is visible or in a voice call.
 *
 * The server skips push notifications for users with an open websocket, and the app does not
 * notify for messages that arrive over the socket. A socket left open in the background (or one
 * that died silently while Android froze the app) therefore swallowed notifications until the app
 * was opened again. Closing it when the app goes to the background hands delivery back to push.
 */
object RealtimeLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isForeground by mutableStateOf(false)

    /** Whether the websocket should currently be open. */
    val shouldConnect: Boolean
        get() = isForeground || VoiceCallManager.activeChannelId != null

    /** Reconnects on request (e.g. a retry button), unless the app is in the background. */
    fun reconnect() {
        if (!shouldConnect) return
        RealtimeSocket.updateDisconnectionState(DisconnectionState.Reconnecting)
        scope.launch { StoatAPI.connectWS() }
    }

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> isForeground = true
                    Lifecycle.Event.ON_STOP -> isForeground = false
                    else -> {}
                }
            }
        )

        scope.launch {
            snapshotFlow { shouldConnect }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (!StoatAPI.isLoggedIn()) return@collect

                    if (wanted) {
                        if (RealtimeSocket.disconnectionState != DisconnectionState.Connected) {
                            logcat { "App is visible, reconnecting websocket." }
                            RealtimeSocket.updateDisconnectionState(DisconnectionState.Reconnecting)
                            StoatAPI.connectWS()
                            // Catch up on anything read or received while we were away
                            launch { StoatAPI.unreads.sync() }
                        }
                    } else {
                        logcat { "App went to the background, closing websocket." }
                        StoatAPI.disconnectWS()
                    }
                }
        }
    }
}
