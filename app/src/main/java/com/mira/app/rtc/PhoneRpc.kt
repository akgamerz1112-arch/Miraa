package com.mira.app.rtc

import com.mira.app.control.ScreenControl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Handles one RPC call from the agent by running the existing [ScreenControl] code.
 * Never throws: every outcome becomes {"ok":..,"message":..}. [allowed] is the owner's "Control do" switch.
 */
class PhoneRpc(
    private val allowed: () -> Boolean,
    private val onAction: (String) -> Unit,
) {
    suspend fun handle(method: String, payload: String): String {
        return when (val p = RpcLogic.parse(method, payload)) {
            is RpcLogic.Parsed.Refuse -> RpcLogic.reply(false, p.message)
            is RpcLogic.Parsed.Call -> {
                if (!allowed()) return RpcLogic.reply(false, RpcLogic.CONTROL_OFF)
                onAction(ScreenControl.describe(p.tool, p.args))
                val result = withTimeoutOrNull(TIMEOUT_MS) {
                    suspendCancellableCoroutine<Pair<Boolean, String>> { cont ->
                        var done = false
                        ScreenControl.run(p.tool, p.args) { ok, msg ->
                            if (!done) {
                                done = true
                                if (cont.isActive) cont.resume(ok to msg)
                            }
                        }
                    }
                }
                if (result == null) RpcLogic.reply(false, RpcLogic.TIMEOUT) else RpcLogic.reply(result.first, result.second)
            }
        }
    }

    private companion object {
        // Agent waits 20 s (config phone_rpc_timeout_seconds); answer a little earlier.
        const val TIMEOUT_MS = 15_000L
    }
}
