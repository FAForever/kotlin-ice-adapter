package com.faforever.ice.gpgnet

import com.faforever.ice.game.LobbyInitMode
import com.faforever.ice.game.GameState as GameStateEnum

interface GpgnetMessage {
    interface ToGameMessage : GpgnetMessage
    interface FromGameMessage : GpgnetMessage

    val command: String
    val args: List<Any>

    data class CreateLobby(
        val lobbyInitMode: LobbyInitMode,
        val lobbyPort: Int,
        val localPlayerName: String,
        val localPlayerId: Int,
        val unknownParameter: Int = 1,
    ) : ToGameMessage {
        companion object {
            const val COMMAND = "CreateLobby"
        }

        override val command = COMMAND
        override val args = listOf(
            lobbyInitMode.faId,
            lobbyPort,
            localPlayerName,
            localPlayerId,
            unknownParameter,
        )
    }

    data class HostGame(val mapName: String) : ToGameMessage {
        companion object {
            const val COMMAND = "HostGame"
        }

        override val command = COMMAND
        override val args = listOf(mapName)
    }

    data class JoinGame(val remotePlayerLogin: String, val remotePlayerId: Int, val destination: String) :
        ToGameMessage {
        companion object {
            const val COMMAND = "JoinGame"
        }

        override val command = COMMAND
        override val args = listOf(destination, remotePlayerLogin, remotePlayerId)
    }

    data class ConnectToPeer(val remotePlayerLogin: String, val remotePlayerId: Int, val destination: String) :
        ToGameMessage {
        companion object {
            const val COMMAND = "ConnectToPeer"
        }

        override val command = COMMAND
        override val args = listOf(destination, remotePlayerLogin, remotePlayerId)
    }

    data class DisconnectFromPeer(val remotePlayerId: Int) : ToGameMessage {
        companion object {
            const val COMMAND = "DisconnectFromPeer"
        }

        override val command = COMMAND
        override val args = listOf(remotePlayerId)
    }

    data class GameState(val gameState: GameStateEnum) : FromGameMessage {
        companion object {
            const val COMMAND = "GameState"
        }

        override val command = COMMAND
        override val args = listOf(gameState.gpgnetString)
    }

    data class GameEnded(
        override val command: String = COMMAND,
    ) : FromGameMessage {
        companion object {
            const val COMMAND = "GameEnded"
        }

        override val args = listOf<Any>()
    }

    data class ReceivedMessage(override val command: String, override val args: List<Any>) : GpgnetMessage {
        fun tryParse() = when (command) {
            // Lobby messages
            CreateLobby.COMMAND -> CreateLobby(
                LobbyInitMode.parseFromFaId(args[0] as Int),
                args[1] as Int,
                args[2] as String,
                args[3] as Int,
                args[4] as Int,
            )

            HostGame.COMMAND -> HostGame(args[0] as String)
            JoinGame.COMMAND -> JoinGame(args[1] as String, args[2] as Int, args[0] as String)
            ConnectToPeer.COMMAND -> ConnectToPeer(args[1] as String, args[2] as Int, args[0] as String)
            DisconnectFromPeer.COMMAND -> DisconnectFromPeer(args[0] as Int)
            // Game messages
            GameState.COMMAND -> GameState(GameStateEnum.parseGpgnetString(args[0] as String))
            GameEnded.COMMAND -> GameEnded()
            else -> this
        }
    }
}
