package com.faforever.ice.gpgnet

interface GpgnetMessage {
    val command: String
    val args: List<Any>

    data class HostGame(val mapName: String) : GpgnetMessage {
        override val command = "HostGame"
        override val args = listOf(mapName)
    }

    data class JoinGame(val remotePlayerLogin: String, val remotePlayerId: Int, val destination: String) : GpgnetMessage {
        override val command = "JoinGame"
        override val args = listOf(destination, remotePlayerLogin, remotePlayerId)
    }

    data class ConnectToPeer(val remotePlayerLogin: String, val remotePlayerId: Int, val destination: String) : GpgnetMessage {
        override val command = "ConnectToPeer"
        override val args = listOf(destination, remotePlayerLogin, remotePlayerId)
    }

    data class DisconnectFromPeer(val remotePlayerId: Int) : GpgnetMessage {
        override val command = "DisconnectFromPeer"
        override val args = listOf(remotePlayerId)
    }

    data class ReceivedMessage(override val command: String, override val args: List<Any>) : GpgnetMessage
}