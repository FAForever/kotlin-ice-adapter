package com.faforever.ice.peering

import com.faforever.ice.ice4j.AgentWrapper
import com.faforever.ice.ice4j.IceState
import com.faforever.ice.util.ExecutorHolder
import com.faforever.ice.util.isIn
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Allowed state transitions:
 * NEW -> GATHERING
 * NEW -> AWAITING_CANDIDATES
 */

class RemotePeerOrchestrator(
    private val remotePlayerId: Int,
    private val localOffer: Boolean,
    private val coturnServers: List<CoturnServer>,
    private val relayToLocalGame: (ByteArray) -> Unit,
) : Closeable {
    companion object {
        private val executor: ScheduledExecutorService get() = ExecutorHolder.executor
    }

    private val toRemoteQueue: BlockingQueue<ByteArray> = ArrayBlockingQueue(32, true)

    private val objectLock = Object()
    private var iceState: IceState = IceState.NEW
    private var udpSocketBridge: UdpSocketBridge? = null
    private var agent: AgentWrapper? = null
    private var remoteListenerThread: Thread? = null
    private var remoteSenderThread: Thread? = null

    @Volatile
    private var connected = false

    @Volatile
    private var closing = false

    fun initialize() {
        withLoggingContext("remotePlayerId" to remotePlayerId.toString()) {
            synchronized(objectLock) {
                if (this.iceState.isIn(IceState.NEW, IceState.DISCONNECTED)) {
                    logger.warn { "ICE already in progress, aborting re initiation. current state: ${this.iceState}" }
                    return
                }
                this.iceState = IceState.GATHERING

                udpSocketBridge = UdpSocketBridge(toRemoteQueue::put, "player-$remotePlayerId")
                    .apply { start() }
                agent = AgentWrapper(localOffer, coturnServers, ::onIceStateChange)
                    .apply { start() }

                remoteListenerThread= Thread { readFromRemotePlayerLoop() }
                    .apply { start() }
                remoteSenderThread = Thread { sendToRemotePlayerLoop() }
                    .apply { start() }
            }
        }
    }

    private fun onIceStateChange(oldState: IceState, newState: IceState) {
        if (newState == IceState.DISCONNECTED && !closing) {
            onConnectionLost()
        }
    }

    private fun onConnectionLost() {
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
                if (localOffer) {
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
                    //Received data
                    data[0] == 'd'.code.toByte() -> TODO()
                    //Received echo req/res
                    data[0] == 'e'.code.toByte() -> relayToLocalGame(data)
                    else -> logger.warn { "Received invalid packet, first byte: 0x${data[0]}, length: ${data.size}" }
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

    private fun sendToRemotePlayerLoop() {
        while (!closing) {
            if (!connected) {
                Thread.sleep(500)
                continue
            }

            when {
                closing -> break
                !connected -> {
                    logger.info { "sendToRemotePlayerLoop shutdown due to connection lost" }
                    break
                }

                else -> {
                    val message = toRemoteQueue.peek()
                    val success = sendToRemotePlayer(message)
                    if (success) {
                        // in case we could send successfully, discard it
                        toRemoteQueue.remove()
                    } else {
                        // otherwise, we keep it and wait for better times (reconnect)
                        Thread.sleep(500)
                    }
                }
            }

        }

        logger.info { "sendToRemotePlayerLoop shutdown due to closing" }
    }

    private fun sendToRemotePlayer(data: ByteArray): Boolean =
        try {
            checkNotNull(agent).send(data)
            true
        } catch (e: IOException) {
            false
        }

    override fun close() {
        synchronized(objectLock) {
            if(!closing) return

            closing = true
            remoteListenerThread?.interrupt()
            remoteSenderThread?.interrupt()
            udpSocketBridge?.close()
            agent?.close()
        }
    }
}