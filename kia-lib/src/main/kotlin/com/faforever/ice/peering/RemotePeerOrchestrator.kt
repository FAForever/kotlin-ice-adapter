package com.faforever.ice.peering

import com.faforever.ice.connectivitycheck.ConnectivityCheckHandler
import com.faforever.ice.connectivitycheck.ConnectivityCheckable
import com.faforever.ice.ice4j.AgentWrapper
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.ice4j.IceState
import com.faforever.ice.util.ExecutorHolder
import com.faforever.ice.util.isNotIn
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class RemotePeerOrchestrator(
    private val lobbyPort: Int,
    private val localPlayerId: Int,
    private val remotePlayerId: Int,
    override val isOfferer: Boolean,
    private val forceRelay: Boolean,
    private val coturnServers: List<CoturnServer>,
    private val publishLocalCandidates: (CandidatesMessage) -> Unit,
    private val publishIceConnectionState: (Int, Int, String) -> Unit,
) : Closeable, ConnectivityCheckable {
    companion object {
        private val executor: ScheduledExecutorService get() = ExecutorHolder.executor
    }

    override fun toString() =
        "RemotePeerOrchestrator(localPlayerId=$localPlayerId,remotePlayerId=$remotePlayerId,isOfferer=$isOfferer,forceRelay=$forceRelay,...)"

    val udpBridgePort: Int? get() = udpSocketBridge?.bridgePort

    private val objectLock = Object()
    private var iceState: IceState = IceState.NEW
    private var udpSocketBridge: UdpSocketBridge? = null
    private var agent: AgentWrapper? = null
    private var remoteListenerThread: Thread? = null
    private var remoteSenderThread: Thread? = null
    private var connectivityCheckHandler: ConnectivityCheckHandler? = null

    @Volatile
    private var connected = false

    @Volatile
    private var closing = false

    fun initialize(connectivityCheckHandler: ConnectivityCheckHandler) {
        withLoggingContext("remotePlayerId" to remotePlayerId.toString()) {
            synchronized(objectLock) {
                if (this.iceState.isNotIn(IceState.NEW, IceState.DISCONNECTED)) {
                    logger.warn { "ICE already in progress, aborting re initiation. current state: ${this.iceState}" }
                    return
                }
                this.iceState = IceState.GATHERING

                udpSocketBridge = UdpSocketBridge(
                    lobbyPort = lobbyPort,
                    forwardToIce = { sendToRemotePlayer(GameDataPacket(it)) },
                    name = "player-$remotePlayerId",
                ).apply { start() }

                this.connectivityCheckHandler = connectivityCheckHandler
                agent = AgentWrapper(
                    localPlayerId = localPlayerId,
                    remotePlayerId = remotePlayerId,
                    localOffer = isOfferer,
                    forceRelay = forceRelay,
                    coturnServers = coturnServers,
                    onStateChanged = ::onIceStateChange,
                    onCandidatesGathered = ::onLocalCandidatesGathered,
                ).apply { start() }
            }
        }
    }

    private fun onIceStateChange(oldState: IceState, newState: IceState) {
        if (closing) return

        publishIceConnectionState(localPlayerId, remotePlayerId, newState.message)

        when {
            newState == IceState.DISCONNECTED -> onConnectionLost()
            newState == IceState.CONNECTED -> {
                synchronized(objectLock) {
                    connected = true
                    remoteListenerThread = Thread { readFromRemotePlayerLoop() }
                        .apply { start() }

                    logger.info { "$this connected: ice port ${this.agent?.port} <-> udp bridge port ${this.udpBridgePort}" }
                }
            }
        }
    }

    private fun onLocalCandidatesGathered(candidatesMessage: CandidatesMessage) {
        logger.info { "Sending own candidates to $remotePlayerId: $candidatesMessage" }
        publishLocalCandidates(candidatesMessage)
    }

    fun onRemoteCandidatesReceived(candidatesMessage: CandidatesMessage) {
        if (closing) {
            logger.warn { "Peer not connected anymore, discarding candidates message" }
            return
        }

        agent!!.onRemoteCandidatesReceived(candidatesMessage)
    }

    override fun sendEcho() {
        sendToRemotePlayer(EchoPacket())
    }

    override fun onConnectionLost() {
        synchronized(objectLock) {
            logger.debug { "Shutting down listener thread" }
            logger.debug { "Shutting refresh module" }
            logger.debug { "Shutting connectivity checker" }

            val previousState = iceState
            if (connected) {
                connected = false
                logger.warn { "ICE connection has been lost for peer " }
                logger.debug { "Notify FAF client" }
            }

            iceState = IceState.DISCONNECTED
            close()

            if (previousState == IceState.CONNECTED) {
                logger.debug { "Notify FAF client about connection lost + reconnecting" }

                // We were connected before, retry immediately
                if (isOfferer) {
                    executor.submit { reinitIce() }
                }
            } else {
                // Last ice attempt didn't succeed, so wait a bit
                executor.schedule({ reinitIce() }, 5, TimeUnit.SECONDS)
            }

            return
        }
    }

    fun reinitIce() {
        logger.info { "Reinit ICE" }
    }

    private fun readFromRemotePlayerLoop() {
        logger.info { "Now forwarding data from ICE to FA for peer" }

        val agent = checkNotNull(this.agent)

        var errors = 0

        val gameIsRunningOrSomethingLikeThat = true
        while (gameIsRunningOrSomethingLikeThat) {
            try {
                val data = agent.receive()

                when {
                    data.isEmpty() -> continue
                    // Received data
                    data[0] == GameDataPacket.PREFIX -> udpSocketBridge!!.forwardToGame(GameDataPacket.fromWire(data))
                    // Received echo req/res
                    data[0] == EchoPacket.PREFIX -> connectivityCheckHandler!!.echoReceived()
                    else -> logger.warn {
                        "Received invalid packet, first byte: 0x${data[0]}, length: ${data.size}, as String: ${
                            String(data)
                        }"
                    }
                }
            } catch (e: IOException) {
                logger.warn { "Error while reading from ICE adapter" }
                errors++

                // TODO: What could a good threshold be?
                if (errors > 10) {
                    logger.error { "Too many errors, closing connection" }
                    executor.submit { onConnectionLost() }
                    return
                }
            }
        }

        logger.debug { "No longer listening for messages from ICE" }
    }

    private fun sendToRemotePlayer(data: ProtocolPacket): Boolean =
        try {
            checkNotNull(agent).send(data.buildPrefixedWireData())
            true
        } catch (e: IOException) {
            false
        }

    override fun close() {
        synchronized(objectLock) {
            if (!closing) return

            closing = true
            connectivityCheckHandler?.disconnected()
            remoteListenerThread?.interrupt()
            remoteSenderThread?.interrupt()
            udpSocketBridge?.close()
            agent?.close()
        }
    }
}
