package com.faforever.ice.rpc

import com.faforever.ice.ControlPlane
import com.faforever.ice.IceAdapter
import com.faforever.ice.game.LobbyInitMode
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.peering.CoturnServer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.IllegalArgumentException
import java.net.URI

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

    override fun setIceServers(iceServers: List<Map<String, Any>>) {
        val coturnServers = iceServers.flatMap { iceServerData ->
            val urlsData = iceServerData["urls"] ?: emptyList<String>()
            val username = iceServerData["username"] as? String
            val credential = iceServerData["credential"] as? String

            if (urlsData is List<*>) {
                urlsData as List<String>
            } else {
                listOf(iceServerData["url"] as String)
            }
                .map { URI(it) }
                .map {
                    // for now, we intentionally ignore the transport parameter for UDP/TCP
                    // and the uri scheme (STUN/TURN)
                    CoturnServer(
                        hostname = it.host,
                        port = it.port,
                        username = username,
                        credential = credential,
                    )
                }
        }

        iceAdapter.setIceServers(coturnServers)
    }

    override fun quit() {
        iceAdapter.stop()
    }
}
