package com.faforever.ice.rpc

import com.faforever.ice.IceAdapter
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.util.ReusableComponent
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nbarraille.jjsonrpc.JJsonPeer
import com.nbarraille.jjsonrpc.TcpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.Volatile

private val logger = KotlinLogging.logger {}

/**
 * Handles communication between client and adapter, opens a server for the client to connect to
 */
class RPCService(
    private val rpcPort: Int, 
    iceAdapter: IceAdapter
) : ReusableComponent {
    private val objectMapper = ObjectMapper()
    private val rpcHandler: RPCHandler = RPCHandler(iceAdapter)
    private val tcpServer: TcpServer = TcpServer(rpcPort, rpcHandler)

    @Volatile
    private var skipRPCMessages = false

    init {
        objectMapper.registerModule(JavaTimeModule())
    }

    override fun start() {
        logger.info { "Created RPC server on port $rpcPort" }
        tcpServer.start()

/*        tcpServer.firstPeer.thenAccept { firstPeer ->
            firstPeer.onConnectionLost {
                val gameState: GameState = GpgnetProxy.getGameState()
                if (gameState === GameState.LAUNCHING) {
                    skipRPCMessages = true
                    logger.warn { "Lost connection to first RPC Peer. GameState: LAUNCHING, NOT STOPPING!" }
                } else {
                    logger.info { "Lost connection to first RPC Peer. GameState: $gameState, Stopping adapter..." }
                    IceAdapter.stop()
                }
            }
        }*/
    }

    fun onConnectionStateChanged(newState: String?) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification("onConnectionStateChanged", listOf(newState))
        }
    }

    fun onGpgNetMessageReceived(header: String?, chunks: List<Any?>?) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification("onGpgNetMessageReceived", listOf(header, chunks))
        }
    }

    fun onIceMsg(candidatesMessage: CandidatesMessage) {
        if (!skipRPCMessages) {
            try {
                peerOrWait?.sendNotification(
                    "onIceMsg",
                    listOf(
                        candidatesMessage.sourceId,
                        candidatesMessage.destinationId,
                        objectMapper.writeValueAsString(candidatesMessage),
                    ),
                )
            } catch (e: JsonProcessingException) {
                throw RuntimeException(e)
            }
        }
    }

    fun onIceConnectionStateChanged(localPlayerId: Long, remotePlayerId: Long, state: String?) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification(
                "onIceConnectionStateChanged",
                listOf(localPlayerId, remotePlayerId, state),
            )
        }
    }

    fun onConnected(localPlayerId: Long, remotePlayerId: Long, connected: Boolean) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification(
                "onConnected",
                listOf(localPlayerId, remotePlayerId, connected),
            )
        }
    }

    private val peerOrWait: JJsonPeer?
        /**
         * Blocks until a peer is connected (the client)
         *
         * @return the currently connected peer (the client)
         */
        get() {
            try {
                return tcpServer.firstPeer.get()
            } catch (e: Exception) {
                logger.error(e) { "Error on fetching first peer" }
            }
            return null
        }

    override fun stop() {
        tcpServer.stop()
    }
}
