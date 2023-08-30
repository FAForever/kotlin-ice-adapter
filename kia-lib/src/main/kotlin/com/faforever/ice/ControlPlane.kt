package com.faforever.ice

import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage

/**
 * External actions to be performed on the ICE Adapter
 */
interface ControlPlane {
    fun hostGame(mapName: String)
    fun joinGame(remotePlayerLogin: String, remotePlayerId: Int)
    fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Int, offer: Boolean)
    fun disconnectFromPeer(remotePlayerId: Int)
    fun sendToGpgNet(message: GpgnetMessage)
    fun receiveIceCandidates(remotePlayerId: Int, candidatesMessage: CandidatesMessage)
}
