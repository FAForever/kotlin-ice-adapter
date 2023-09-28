package com.faforever.ice.connectivitycheck

interface ConnectivityCheckable {
    val isOfferer: Boolean

    fun sendEcho()

    fun onConnectionLost()
}
