package com.faforever.ice.rpc

import com.faforever.ice.ControlPlane
import com.faforever.ice.IceAdapter
import com.faforever.ice.game.LobbyInitMode
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.peering.CoturnServer
import java.net.URI

/**
 * Handles calls from JsonRPC (the faf client)
 */
class RpcHandler(
    private val iceAdapter: IceAdapter,
) : ControlPlane {

    override fun hostGame(mapName: String) {
        iceAdapter.hostGame(mapName)
    }

    override fun joinGame(remotePlayerLogin: String, remotePlayerId: Int) {
        iceAdapter.joinGame(remotePlayerLogin, remotePlayerId)
    }

    override fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Int, offer: Boolean) {
        iceAdapter.connectToPeer(remotePlayerLogin, remotePlayerId, offer)
    }

    override fun disconnectFromPeer(remotePlayerId: Int) {
        iceAdapter.disconnectFromPeer(remotePlayerId)
    }

    override fun setLobbyInitMode(lobbyInitMode: String) {
        iceAdapter.setLobbyInitMode(LobbyInitMode.valueOf(lobbyInitMode))
    }

    override fun iceMsg(remotePlayerId: Int, candidatesMessage: CandidatesMessage) {
        iceAdapter.receiveIceCandidates(remotePlayerId, candidatesMessage)
    }

    override fun sendToGpgNet(message: GpgnetMessage) {
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
