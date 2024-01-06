package com.faforever.ice

/**
 * External actions to be performed on the ICE Adapter.
 * Attention: The jjsonrpc library demands all numbers to be long over int
 */
interface ControlPlane {
    fun hostGame(mapName: String)
    fun joinGame(remotePlayerLogin: String, remotePlayerId: Long)
    fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Long, offer: Boolean)
    fun disconnectFromPeer(remotePlayerId: Long)
    fun setLobbyInitMode(lobbyInitMode: String)
    fun iceMsg(remotePlayerId: Long, message: String)
    fun sendToGpgNet(command: String, args: List<String>)
    fun quit()
}
