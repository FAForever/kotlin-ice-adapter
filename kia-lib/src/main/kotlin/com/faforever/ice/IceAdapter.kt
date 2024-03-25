package com.faforever.ice

import com.faforever.ice.connectivitycheck.ConnectivityChecker
import com.faforever.ice.game.GameState
import com.faforever.ice.game.LobbyInitMode
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.gpgnet.GpgnetMessage.ConnectToPeer
import com.faforever.ice.gpgnet.GpgnetMessage.DisconnectFromPeer
import com.faforever.ice.gpgnet.GpgnetMessage.HostGame
import com.faforever.ice.gpgnet.GpgnetMessage.JoinGame
import com.faforever.ice.gpgnet.GpgnetProxy
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.icebreaker.ApiClient
import com.faforever.ice.peering.CoturnServer
import com.faforever.ice.peering.RemotePeerOrchestrator
import com.faforever.ice.telemetry.TelemetryClient
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class IceAdapter(
    private val iceOptions: IceOptions,
    private val apiClient: ApiClient,
    private val telemetryClient: TelemetryClient,
    onGameConnectionStateChanged: (String) -> Unit,
    private val onGpgNetMessageReceived: (GpgnetMessage) -> Unit,
    private val onIceCandidatesGathered: (CandidatesMessage) -> Unit,
    private val onIceConnectionStateChanged: (Int, Int, String) -> Unit,
    private val onConnected: (Int, Int, Boolean) -> Unit,
    private val onIceAdapterStopped: () -> Unit,
    initialCoturnServers: List<CoturnServer>,
) {

    private val coturnServers: MutableList<CoturnServer> = ArrayList(initialCoturnServers)
    private val objectLock = Object()
    var lobbyInitMode: LobbyInitMode = LobbyInitMode.NORMAL
        private set
    var gameState: GameState = GameState.NONE
        private set

    private lateinit var lobbyStateFuture: CompletableFuture<Void>
    private val gpgnetProxy = GpgnetProxy(
        iceOptions = iceOptions,
        onGameConnectionStateChanged = onGameConnectionStateChanged,
        onMessage = ::onGpgnetMessage,
        onFailure = { throw it },
    )
    private val connectivityChecker = ConnectivityChecker()
    private val players: MutableMap<Int, RemotePeerOrchestrator> = ConcurrentHashMap()

    fun start(
        accessToken: String,
        gameId: Int,
    ) {
        logger.info { "Starting ICE Adapter for gameId $gameId" }

        apiClient.requestSessionToken(
            accessToken = accessToken,
            gameId = gameId,
        )
            .thenCompose {
                logger.debug { "Session token received" }
                apiClient.requestSession(gameId)
            }
            .thenApply { session ->
                logger.debug { "Coturn servers received" }
                coturnServers.addAll(
                    session.servers.map {
                        CoturnServer(
                            uri = URI.create(it.urls.first()),
                            username = it.username,
                            credential = it.credential,
                        )
                    },
                )

                telemetryClient.updateCoturnList(coturnServers)
                lobbyStateFuture = CompletableFuture<Void>()
                gpgnetProxy.start()
                connectivityChecker.start()
            }
    }

    private fun localDestination(port: Int) = "${InetAddress.getLoopbackAddress().hostAddress}:$port"

    private fun onGpgnetMessage(message: GpgnetMessage) {
        when (message) {
            is GpgnetMessage.GameState -> {
                logger.info { "Game State changed from $gameState -> ${message.gameState}" }

                if (message.gameState == GameState.IDLE) {
                    gpgnetProxy.sendGpgnetMessage(
                        GpgnetMessage.CreateLobby(
                            lobbyInitMode = lobbyInitMode,
                            lobbyPort = iceOptions.lobbyPort,
                            localPlayerName = iceOptions.userName,
                            localPlayerId = iceOptions.userId,
                        ),
                    )
                } else if (message.gameState == GameState.LOBBY) {
                    logger.trace { "Completing lobbyStateFuture" }
                    lobbyStateFuture.complete(null)
                }

                gameState = message.gameState
            }

            is GpgnetMessage.GameEnded -> {
                gameState = GameState.ENDED
            }
        }
        onGpgNetMessageReceived(message)
    }

    fun receiveIceCandidates(remotePlayerId: Int, candidatesMessage: CandidatesMessage) {
        val orchestrator =
            players[remotePlayerId] ?: throw IllegalStateException("Unknown remotePlayerId: $remotePlayerId")
        orchestrator.onRemoteCandidatesReceived(candidatesMessage)
    }

    fun hostGame(mapName: String) {
        logger.debug { "hostGame: mapName=$mapName" }
        lobbyStateFuture.join()
        gpgnetProxy.sendGpgnetMessage(HostGame(mapName))
    }

    fun joinGame(remotePlayerLogin: String, remotePlayerId: Int) {
        logger.debug { "joinGame: remotePlayerLogin=$remotePlayerLogin, remotePlayerId=$remotePlayerId" }
        val remotePeerOrchestrator = RemotePeerOrchestrator(
            lobbyPort = iceOptions.lobbyPort,
            localPlayerId = iceOptions.userId,
            remotePlayerId = remotePlayerId,
            isOfferer = false,
            forceRelay = iceOptions.forceRelay,
            coturnServers = coturnServers,
            publishLocalCandidates = onIceCandidatesGathered,
            publishIceConnectionState = onIceConnectionStateChanged,
        )

        val connectivityCheckHandler = connectivityChecker.registerPlayer(remotePeerOrchestrator)
        remotePeerOrchestrator.initialize(connectivityCheckHandler)

        players[remotePlayerId] = remotePeerOrchestrator

        lobbyStateFuture.join()

        logger.info { "joinGame: $remotePeerOrchestrator initialized" }

        gpgnetProxy.sendGpgnetMessage(
            JoinGame(
                remotePlayerLogin = remotePlayerLogin,
                remotePlayerId = remotePlayerId,
                localDestination(remotePeerOrchestrator.udpBridgePort!!),
            ),
        )
    }

    fun connectToPeer(remotePlayerLogin: String, remotePlayerId: Int, offer: Boolean) {
        logger.debug { "connectToPeer: remotePlayerLogin=$remotePlayerLogin, remotePlayerId=$remotePlayerId" }

        val remotePeerOrchestrator = RemotePeerOrchestrator(
            lobbyPort = iceOptions.lobbyPort,
            localPlayerId = iceOptions.userId,
            remotePlayerId = remotePlayerId,
            isOfferer = offer,
            forceRelay = iceOptions.forceRelay,
            coturnServers = coturnServers,
            publishLocalCandidates = onIceCandidatesGathered,
            publishIceConnectionState = onIceConnectionStateChanged,
        )

        val connectivityCheckHandler = connectivityChecker.registerPlayer(remotePeerOrchestrator)
        remotePeerOrchestrator.initialize(connectivityCheckHandler)

        players[remotePlayerId] = remotePeerOrchestrator

        logger.info { "connectToPeer: $remotePeerOrchestrator initialized" }

        lobbyStateFuture.join()
        gpgnetProxy.sendGpgnetMessage(
            ConnectToPeer(
                remotePlayerLogin = remotePlayerLogin,
                remotePlayerId = remotePlayerId,
                localDestination(remotePeerOrchestrator.udpBridgePort!!),
            ),
        )
    }

    fun disconnectFromPeer(remotePlayerId: Int) {
        logger.debug { "disconnectFromPeer: remotePlayerId=$remotePlayerId" }

        players[remotePlayerId]?.close()
        players.remove(remotePlayerId)

        lobbyStateFuture.join()
        gpgnetProxy.sendGpgnetMessage(DisconnectFromPeer(remotePlayerId))
    }

    fun setLobbyInitMode(lobbyInitMode: LobbyInitMode) {
        this.lobbyInitMode = lobbyInitMode
    }

    fun stop() {
        logger.debug { "stop" }

        synchronized(objectLock) {
            coturnServers.clear()
            gpgnetProxy.stop()
            connectivityChecker.stop()
            players.values.forEach { it.close() }
            players.clear()
            lobbyStateFuture.cancel(true)
        }
        onIceAdapterStopped()
    }

    fun sendToGpgNet(message: GpgnetMessage) {
        logger.debug { "sendToGpgNet: message=$message" }
        gpgnetProxy.sendGpgnetMessage(message)
    }
}
