package com.faforever.ice.rpc

import com.faforever.ice.IceAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Handles calls from JsonRPC (the client)
 */
class RPCHandler(
    private val iceAdapter: IceAdapter,
) {
    private val objectMapper = ObjectMapper()

    init {
        objectMapper.registerModule(JavaTimeModule())
    }
    
    fun hostGame(mapName: String) {
        iceAdapter.hostGame(mapName)
    }

    fun joinGame(remotePlayerLogin: String, remotePlayerId: Long) {
        iceAdapter.joinGame(remotePlayerLogin, remotePlayerId.toInt())
    }

    fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Long, offer: Boolean) {
        iceAdapter.connectToPeer(remotePlayerLogin, remotePlayerId.toInt(), offer)
    }

    fun disconnectFromPeer(remotePlayerId: Long) {
        iceAdapter.disconnectFromPeer(remotePlayerId.toInt())
    }

    fun setLobbyInitMode(lobbyInitMode: String) {
        GPGNetServer.lobbyInitMode = LobbyInitMode.getByName(lobbyInitMode)
        logger.debug { "LobbyInitMode set to $lobbyInitMode" }
    }
}
