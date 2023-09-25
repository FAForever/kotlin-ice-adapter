package com.faforever.ice.rpc

import com.faforever.ice.ControlPlane
import com.faforever.ice.IceAdapter
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.fasterxml.jackson.databind.ObjectMapper

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
        iceAdapter.setLobbyInitMode(lobbyInitMode)
    }

    override fun iceMsg(remotePlayerId: Int, candidatesMessage: CandidatesMessage) {
        iceAdapter.receiveIceCandidates(remotePlayerId, candidatesMessage)
    }

    override fun sendToGpgNet(message: GpgnetMessage) {
        iceAdapter.sendToGpgNet(message)
    }

    override fun setIceServers(iceServers: List<Map<String, Any>>) {
        iceAdapter.setIceServers(iceServers)
    }

    override fun quit() {
        iceAdapter.stop()
    }
}
