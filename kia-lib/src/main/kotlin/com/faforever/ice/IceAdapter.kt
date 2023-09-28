package com.faforever.ice

import com.faforever.ice.connectivitycheck.ConnectivityChecker
import com.faforever.ice.game.GameState
import com.faforever.ice.game.LobbyConnectionProxy
import com.faforever.ice.game.LobbyInitMode
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.gpgnet.GpgnetMessage.ConnectToPeer
import com.faforever.ice.gpgnet.GpgnetMessage.DisconnectFromPeer
import com.faforever.ice.gpgnet.GpgnetMessage.HostGame
import com.faforever.ice.gpgnet.GpgnetMessage.JoinGame
import com.faforever.ice.gpgnet.GpgnetProxy
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.peering.CoturnServer
import com.faforever.ice.peering.RemotePeerOrchestrator
import com.faforever.ice.util.ReusableComponent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class IceAdapter(
    private val iceOptions: IceOptions,
    private val coturnServers: List<CoturnServer>,
    private val onIceCandidatesGathered: (CandidatesMessage) -> Unit,
) : ControlPlane, ReusableComponent {

    private val objectLock = Object()
    var gameState: GameState = GameState.NONE
        private set
    private var lobbyStateFuture: CompletableFuture<Unit>? = null
    private val gpgnetProxy = GpgnetProxy(
        iceOptions = iceOptions,
        onMessage = ::onGpgnetMessage,
        onFailure = { throw it },
    )
    private val lobbyConnectionProxy = LobbyConnectionProxy(iceOptions)
    private val connectivityChecker = ConnectivityChecker()
    private val players: MutableMap<Int, RemotePeerOrchestrator> = ConcurrentHashMap()
//    private val telemetryClient = TelemetryClient(iceOptions, objectMapper)

    override fun start() {
        lobbyStateFuture = CompletableFuture<Unit>()
        gpgnetProxy.start()
        lobbyConnectionProxy.start()
        connectivityChecker.start()
    }

    private fun localDestination(port: Int) = "${InetAddress.getLoopbackAddress().hostAddress}:$port"

    private fun onGpgnetMessage(message: GpgnetMessage) {
        when (message) {
            is GpgnetMessage.GameState -> {
                logger.info { "Game State changed from $gameState -> ${message.gameState}" }

                if (message.gameState == GameState.IDLE) {
                    gpgnetProxy.sendGpgnetMessage(
                        GpgnetMessage.CreateLobby(
                            lobbyInitMode = LobbyInitMode.NORMAL, // TODO: Where does it come from?
                            lobbyPort = iceOptions.lobbyPort,
                            localPlayerName = iceOptions.userName,
                            localPlayerId = iceOptions.userId,
                        ),
                    )
                } else if (message.gameState == GameState.LOBBY) {
                    lobbyStateFuture!!.complete(Unit)
                }

                gameState = message.gameState
            }
            is GpgnetMessage.GameEnded -> {
                gameState = GameState.ENDED
            }
        }
    }

    override fun receiveIceCandidates(remotePlayerId: Int, candidatesMessage: CandidatesMessage) {
        val orchestrator = players[remotePlayerId] ?: throw IllegalStateException("Unknown remotePlayerId: $remotePlayerId")
        orchestrator.onRemoteCandidatesReceived(candidatesMessage)
    }

    override fun hostGame(mapName: String) {
        logger.debug { "hostGame: mapName=$mapName" }
        lobbyStateFuture!!.join()
        gpgnetProxy.sendGpgnetMessage(HostGame(mapName))
    }

    override fun joinGame(remotePlayerLogin: String, remotePlayerId: Int) {
        logger.debug { "joinGame: remotePlayerLogin=$remotePlayerLogin, remotePlayerId=$remotePlayerId" }
        val remotePeerOrchestrator = RemotePeerOrchestrator(
            localPlayerId = iceOptions.userId,
            remotePlayerId = remotePlayerId,
            isOfferer = false,
            forceRelay = iceOptions.forceRelay,
            coturnServers = coturnServers,
            relayToLocalGame = lobbyConnectionProxy::sendData,
            publishLocalCandidates = onIceCandidatesGathered,
        )

        val connectivityCheckHandler = connectivityChecker.registerPlayer(remotePeerOrchestrator)
        remotePeerOrchestrator.initialize(connectivityCheckHandler)

        players[remotePlayerId] = remotePeerOrchestrator

        lobbyStateFuture!!.join()
        gpgnetProxy.sendGpgnetMessage(
            JoinGame(
                remotePlayerLogin = remotePlayerLogin,
                remotePlayerId = remotePlayerId,
                localDestination(remotePeerOrchestrator.udpBridgePort!!),
            ),
        )
    }

    override fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Int, offer: Boolean) {
        logger.debug { "connectToPeer: remotePlayerLogin=$remotePlayerLogin, remotePlayerId=$remotePlayerId" }

        val remotePeerOrchestrator = RemotePeerOrchestrator(
            localPlayerId = iceOptions.userId,
            remotePlayerId = remotePlayerId,
            isOfferer = offer,
            forceRelay = iceOptions.forceRelay,
            coturnServers = coturnServers,
            relayToLocalGame = lobbyConnectionProxy::sendData,
            publishLocalCandidates = onIceCandidatesGathered,
        )

        val connectivityCheckHandler = connectivityChecker.registerPlayer(remotePeerOrchestrator)
        remotePeerOrchestrator.initialize(connectivityCheckHandler)

        players[remotePlayerId] = remotePeerOrchestrator

        lobbyStateFuture!!.join()
        gpgnetProxy.sendGpgnetMessage(
            ConnectToPeer(
                remotePlayerLogin = remotePlayerLogin,
                remotePlayerId = remotePlayerId,
                localDestination(remotePeerOrchestrator.udpBridgePort!!),
            ),
        )
    }

    override fun disconnectFromPeer(remotePlayerId: Int) {
        logger.debug { "disconnectFromPeer: remotePlayerId=$remotePlayerId" }

        players[remotePlayerId]?.close()
        players.remove(remotePlayerId)

        lobbyStateFuture!!.join()
        gpgnetProxy.sendGpgnetMessage(DisconnectFromPeer(remotePlayerId))
    }

    override fun stop() {
        logger.debug { "stop" }

        synchronized(objectLock) {
            gpgnetProxy.stop()
            connectivityChecker.stop()
            players.values.forEach { it.close() }
            players.clear()
            lobbyStateFuture?.cancel(true)
        }
    }

    override fun sendToGpgNet(message: GpgnetMessage) {
        logger.debug { "sendToGpgNet: message=$message" }
        gpgnetProxy.sendGpgnetMessage(message)
    }
}
