package com.faforever.ice.peering

/**
 * IceState, does not match WebRTC states, represents peer connection "lifecycle"
 */
enum class IceState(val message: String) {
    NEW("new"),
    GATHERING("gathering"),
    AWAITING_CANDIDATES("awaitingCandidates"),
    CHECKING("checking"),
    CONNECTED("connected"),
    COMPLETED("completed"),
    DISCONNECTED("disconnected");
}