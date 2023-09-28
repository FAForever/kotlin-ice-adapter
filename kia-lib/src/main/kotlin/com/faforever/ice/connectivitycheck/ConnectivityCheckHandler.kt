package com.faforever.ice.connectivitycheck

/**
 * Callback interface for peers to notify about connectivity related events
 */
sealed interface ConnectivityCheckHandler {
    enum class Status {
        ALIVE,
        ECHO_REQUIRED,
        ECHO_PENDING,
        DEAD,
    }

    val status: Status

    fun echoReceived()
    fun disconnected()
}
