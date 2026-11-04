package com.mira.app.rtc

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * One LiveKit room connection: joins with the token, publishes the mic, registers the nine RPC methods,
 * and reports connection state. Callbacks arrive on the main thread.
 *
 * LiveKit API names used here come from the current docs/API reference (SDK 2.30.0):
 * LiveKit.create, Room.connect(url, token), localParticipant.setMicrophoneEnabled, Room.registerRpcMethod,
 * Room.events, RoomEvent.{ParticipantConnected, ParticipantDisconnected, Disconnected, FailedToConnect,
 * Reconnecting, Reconnected}, agent = any other participant (isAgent extension package unknown). NOT yet compiled or run on a device.
 */
class LiveKitSession(
    context: Context,
    private val rpc: PhoneRpc,
    private val onState: (ConnState) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    @Volatile private var muted = false
    // Every token gets its own private room, so any other participant there is the MIRA agent.
    private var others = 0
    private var closed = false

    fun start(serverUrl: String, token: String, startMuted: Boolean) {
        muted = startMuted
        val r = LiveKit.create(appContext)
        room = r

        // Only the nine known methods are registered; the SDK itself answers any other method as unsupported.
        for (method in RpcLogic.METHODS.keys) {
            r.registerRpcMethod(method) { data -> rpc.handle(method, data.payload) }
        }

        // Subscribe to events BEFORE connecting so the agent joining is never missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            r.events.collect { ev -> handleEvent(r, ev) }
        }

        onState(ConnState.CONNECTING)
        scope.launch {
            try {
                r.connect(serverUrl, token)
                if (closed) return@launch
                r.localParticipant.setMicrophoneEnabled(!muted)
                others = r.remoteParticipants.size
                onState(if (others > 0) ConnState.AGENT_JOINED else ConnState.WAITING_AGENT)
            } catch (e: Exception) {
                if (!closed) fail("Room join nahi hua: ${e.message?.take(120) ?: "unknown error"}")
            }
        }
    }

    private fun handleEvent(r: Room, ev: RoomEvent) {
        if (closed) return
        when (ev) {
            is RoomEvent.ParticipantConnected -> {
                others += 1
                onState(ConnState.AGENT_JOINED)
            }
            is RoomEvent.ParticipantDisconnected -> {
                others = (others - 1).coerceAtLeast(0)
                if (others == 0) onState(ConnState.WAITING_AGENT)
            }
            is RoomEvent.Reconnecting -> onState(ConnState.RECONNECTING)
            is RoomEvent.Reconnected ->
                onState(if (others > 0) ConnState.AGENT_JOINED else ConnState.WAITING_AGENT)
            is RoomEvent.FailedToConnect -> fail("Connect nahi ho paayi: ${ev.error.message?.take(120) ?: ""}")
            is RoomEvent.Disconnected -> fail("Connection toot gaya. Orb tap karke dobara try karo.")
            else -> Unit
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        val r = room ?: return
        scope.launch {
            try { r.localParticipant.setMicrophoneEnabled(!value) } catch (_: Exception) { }
        }
    }

    private fun fail(message: String) {
        if (closed) return
        stop()
        onError(message)
    }

    /** Idempotent. Leaves the room and frees the SDK objects. */
    fun stop() {
        if (closed) return
        closed = true
        try { room?.disconnect() } catch (_: Exception) { }
        try { room?.release() } catch (_: Exception) { }
        room = null
        scope.cancel()
    }
}
