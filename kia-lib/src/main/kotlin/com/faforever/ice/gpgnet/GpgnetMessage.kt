package com.faforever.ice.gpgnet

interface GpgnetMessage {
    val command: String
    val args: List<Any>

    data class HostGame(val mapName: String) : GpgnetMessage {
        companion object {
            const val COMMAND = "HostGame"
        }

        override val command = COMMAND
        override val args = listOf(mapName)
    }

    data class JoinGame(val remotePlayerLogin: String, val remotePlayerId: Int, val destination: String) :
        GpgnetMessage {
        companion object {
            const val COMMAND = "JoinGame"
        }

        override val command = COMMAND
        override val args = listOf(destination, remotePlayerLogin, remotePlayerId)
    }

    data class ConnectToPeer(val remotePlayerLogin: String, val remotePlayerId: Int, val destination: String) :
        GpgnetMessage {
        companion object {
            const val COMMAND = "ConnectToPeer"
        }

        override val command = COMMAND
        override val args = listOf(destination, remotePlayerLogin, remotePlayerId)
    }

    data class DisconnectFromPeer(val remotePlayerId: Int) : GpgnetMessage {
        companion object {
            const val COMMAND = "DisconnectFromPeer"
        }

        override val command = COMMAND
        override val args = listOf(remotePlayerId)
    }

    data class ReceivedMessage(override val command: String, override val args: List<Any>) : GpgnetMessage {
        fun tryParse() = when (command) {
            HostGame.COMMAND -> HostGame(args[0] as String)
            JoinGame.COMMAND -> JoinGame(args[1] as String, args[2] as Int, args[0] as String)
            ConnectToPeer.COMMAND -> ConnectToPeer(args[1] as String, args[2] as Int, args[0] as String)
            DisconnectFromPeer.COMMAND -> DisconnectFromPeer(args[0] as Int)
            else -> this
        }
    }
}