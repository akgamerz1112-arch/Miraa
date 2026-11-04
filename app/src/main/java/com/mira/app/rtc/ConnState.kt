package com.mira.app.rtc

enum class ConnState { IDLE, CHECKING, CONNECTING, WAITING_AGENT, AGENT_JOINED, RECONNECTING, UPDATE_NEEDED, ERROR }

/** Pure Kotlin so it can be unit tested. */
object ConnLogic {
    fun active(s: ConnState): Boolean = when (s) {
        ConnState.IDLE, ConnState.ERROR, ConnState.UPDATE_NEEDED -> false
        else -> true
    }

    fun label(s: ConnState, muted: Boolean): String = when (s) {
        ConnState.IDLE -> "Orb tap karo, MIRA se baat shuru karo"
        ConnState.CHECKING -> "Server check ho raha hai..."
        ConnState.CONNECTING -> "Connect ho rahi hai..."
        ConnState.WAITING_AGENT -> "Connected. MIRA agent ka intezaar... (PC pe agent chalu hai?)"
        ConnState.AGENT_JOINED -> if (muted) "MIRA aa gayi. Mic band (muted)" else "MIRA aa gayi. Bolo, main sun rahi hoon"
        ConnState.RECONNECTING -> "Dobara connect ho rahi hai..."
        ConnState.UPDATE_NEEDED -> "App update karo (server ko nayi version chahiye)"
        ConnState.ERROR -> "Error. Orb tap karke dobara try karo"
    }
}
