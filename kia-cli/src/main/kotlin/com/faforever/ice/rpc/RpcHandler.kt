package com.faforever.ice.rpc

import com.faforever.ice.ControlPlane
import com.faforever.ice.IceAdapter
import com.faforever.ice.game.LobbyInitMode
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.IllegalArgumentException

private val logger = KotlinLogging.logger {}

/**
 * Handles calls from JsonRPC (the faf client)
 */
class RpcHandler(
    private val iceAdapter: IceAdapter,
    private val objectMapper: ObjectMapper,
) : ControlPlane {

    override fun hostGame(mapName: String) {
        iceAdapter.hostGame(mapName)
    }

    override fun joinGame(remotePlayerLogin: String, remotePlayerId: Long) {
        iceAdapter.joinGame(remotePlayerLogin, remotePlayerId.toInt())
    }

    override fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Long, offer: Boolean) {
        iceAdapter.connectToPeer(remotePlayerLogin, remotePlayerId.toInt(), offer)
    }

    override fun disconnectFromPeer(remotePlayerId: Long) {
        iceAdapter.disconnectFromPeer(remotePlayerId.toInt())
    }

    override fun setLobbyInitMode(lobbyInitMode: String) {
        val parsedLobbyInitMode = when (lobbyInitMode) {
            "normal" -> LobbyInitMode.NORMAL
            "auto" -> LobbyInitMode.AUTO
            else -> {
                // additional log because jjsonrpc lib will swallow it
                logger.error { "Unknown lobbyInitMode: $lobbyInitMode" }
                throw IllegalArgumentException("Unknown lobbyInitMode: $lobbyInitMode")
            }
        }
        iceAdapter.setLobbyInitMode(parsedLobbyInitMode)
    }

    override fun iceMsg(remotePlayerId: Long, message: String) {
        val candidatesMessage = objectMapper.readValue<CandidatesMessage>(message)
        iceAdapter.receiveIceCandidates(remotePlayerId.toInt(), candidatesMessage)
    }

    override fun sendToGpgNet(command: String, args: List<String>) {
        val message = GpgnetMessage.ReceivedMessage(command, args).tryParse()
        iceAdapter.sendToGpgNet(message)
    }

    override fun quit() {
        iceAdapter.stop()
    }
}
