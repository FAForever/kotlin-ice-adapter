package com.faforever.ice.rpc

import com.faforever.ice.IceAdapter
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.util.ReusableComponent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nbarraille.jjsonrpc.JJsonPeer
import com.nbarraille.jjsonrpc.TcpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.Volatile

private val logger = KotlinLogging.logger {}

/**
 * Handles communication between faf client and adapter, opens a server for the client to connect to
 */
class RpcService(
    private val rpcPort: Int,
    private val iceAdapter: IceAdapter,
) : ReusableComponent {
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
    }
    private val rpcHandler: RpcHandler = RpcHandler(iceAdapter, objectMapper)
    private val tcpServer: TcpServer = TcpServer(rpcPort, rpcHandler)

    @Volatile
    private var skipRPCMessages = false

    override fun start() {
        logger.info { "Created RPC server on port $rpcPort" }
        tcpServer.start()

        tcpServer.firstPeer.thenAccept { firstPeer ->
            firstPeer.onConnectionLost {
                logger.info { "Lost connection to first RPC Peer. Stopping adapter..." }
                iceAdapter.stop()
            }
        }
    }

    fun onConnectionStateChanged(newState: String) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification("onConnectionStateChanged", listOf(newState))
        }
    }

    fun onGpgNetMessageReceived(message: GpgnetMessage) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification(
                "onGpgNetMessageReceived",
                listOf(message.command, message.args),
            )
        }
    }

    fun onIceMsg(candidatesMessage: CandidatesMessage) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification(
                "onIceMsg",
                listOf(
                    candidatesMessage.sourceId,
                    candidatesMessage.destinationId,
                    objectMapper.writeValueAsString(candidatesMessage),
                ),
            )
        }
    }

    fun onIceConnectionStateChanged(localPlayerId: Int, remotePlayerId: Int, state: String) {
        if (!skipRPCMessages) {
            peerOrWait?.sendNotification(
                "onIceConnectionStateChanged",
                listOf(localPlayerId, remotePlayerId, state),
            )
        }
    }

    fun onConnected(localPlayerId: Int, remotePlayerId: Int, connected: Boolean) {
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
        get() = try {
            tcpServer.firstPeer.get()
        } catch (e: Exception) {
            logger.error(e) { "Error on fetching first peer" }
            null
        }

    override fun stop() {
        tcpServer.stop()
    }
}
