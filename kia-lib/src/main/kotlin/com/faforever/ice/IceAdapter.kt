package com.faforever.ice

import com.faforever.ice.game.LobbyConnectionProxy
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.gpgnet.GpgnetMessage.*
import com.faforever.ice.gpgnet.GpgnetProxy
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.peering.ConnectivityChecker
import com.faforever.ice.peering.CoturnServer
import com.faforever.ice.peering.RemotePeerOrchestrator
import com.faforever.ice.telemetry.TelemetryClient
import com.faforever.ice.util.ReusableComponent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.IllegalStateException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class IceAdapter(
    private val iceOptions: IceOptions,
    private val coturnServers: List<CoturnServer>,
    private val onIceCandidatesGathered: (CandidatesMessage)->Unit,
) : ControlPlane, ReusableComponent {
    private val objectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerModule(KotlinModule.Builder().build())
    }

    private val objectLock = Object()
    private val gpgnetProxy = GpgnetProxy(iceOptions)
    private val lobbyConnectionProxy = LobbyConnectionProxy(iceOptions)
    private val connectivityChecker = ConnectivityChecker()
    private val players: MutableMap<Int, RemotePeerOrchestrator> = ConcurrentHashMap()
//    private val telemetryClient = TelemetryClient(iceOptions, objectMapper)

    override fun start() {
        gpgnetProxy.start()
        connectivityChecker.start()
    }

    private fun localDestination(port: Int) = "${InetAddress.getLoopbackAddress().hostAddress}:$port"

    override fun receiveIceCandidates(remotePlayerId: Int, candidatesMessage: CandidatesMessage) {
        val orchestrator = players[remotePlayerId] ?: throw IllegalStateException("Unknown remotePlayerId: $remotePlayerId")
        orchestrator.onRemoteCandidatesReceived(candidatesMessage)
    }

    override fun hostGame(mapName: String) {
        logger.debug { "hostGame: mapName=$mapName" }
        gpgnetProxy.sendGpgnetMessage(HostGame(mapName))
    }

    override fun joinGame(remotePlayerLogin: String, remotePlayerId: Int) {
        logger.debug { "joinGame: remotePlayerLogin=$remotePlayerLogin, remotePlayerId=$remotePlayerId" }

        val remotePeerOrchestrator = RemotePeerOrchestrator(
            localPlayerId = iceOptions.userId,
            remotePlayerId = remotePlayerId,
            localOffer = false,
            coturnServers = coturnServers,
            relayToLocalGame = lobbyConnectionProxy::sendData,
            publishLocalCandidates = onIceCandidatesGathered,
        ).also { it.initialize() }

        players[remotePlayerId] = remotePeerOrchestrator

        gpgnetProxy.sendGpgnetMessage(
            JoinGame(
                remotePlayerLogin = remotePlayerLogin,
                remotePlayerId = remotePlayerId,
                localDestination(remotePeerOrchestrator.udpBridgePort)
            )
        )
    }

    override fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Int, offer: Boolean) {
        logger.debug { "joinGame: remotePlayerLogin=$remotePlayerLogin, remotePlayerId=$remotePlayerId" }

        val remotePeerOrchestrator = RemotePeerOrchestrator(
            localPlayerId = iceOptions.userId,
            remotePlayerId = remotePlayerId,
            localOffer = offer,
            coturnServers = coturnServers,
            relayToLocalGame = lobbyConnectionProxy::sendData,
            publishLocalCandidates = onIceCandidatesGathered,
        ).also { it.initialize() }

        players[remotePlayerId] = remotePeerOrchestrator

        gpgnetProxy.sendGpgnetMessage(
            ConnectToPeer(
                remotePlayerLogin = remotePlayerLogin,
                remotePlayerId = remotePlayerId,
                localDestination(remotePeerOrchestrator.udpBridgePort)
            )
        )
    }

    override fun disconnectFromPeer(remotePlayerId: Int) {
        logger.debug { "disconnectFromPeer: remotePlayerId=$remotePlayerId" }

        players[remotePlayerId]?.close()
        players.remove(remotePlayerId)

        gpgnetProxy.sendGpgnetMessage(DisconnectFromPeer(remotePlayerId))
    }

    override fun stop() {
        logger.debug { "stop" }

        synchronized(objectLock) {
            gpgnetProxy.stop()
            connectivityChecker.stop()
            players.values.forEach { it.close() }
            players.clear()
        }
    }

    override fun sendToGpgNet(message: GpgnetMessage) {
        logger.debug { "sendToGpgNet: message=$message" }
        gpgnetProxy.sendGpgnetMessage(message)
    }
}