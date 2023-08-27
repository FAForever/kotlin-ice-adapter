package com.faforever.ice.peering

import com.faforever.ice.util.ExecutorHolder
import com.faforever.ice.util.isIn
import dev.failsafe.Failsafe
import dev.failsafe.Timeout
import dev.failsafe.TimeoutExceededException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.ice4j.ice.Agent
import org.ice4j.ice.Component
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.IceProcessingState
import org.ice4j.ice.harvest.StunCandidateHarvester
import org.ice4j.ice.harvest.TurnCandidateHarvester
import org.ice4j.security.LongTermCredential
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class Ice4JPlayerWrapper(
    private val remotePlayerId: Int,
    private val localOffer: Boolean,
    private val coturnServers: List<CoturnServer>,
    private val relayToGame: (ByteArray) -> Unit,
) : Closeable {
    companion object {
        private const val MINIMUM_PORT = 6112

        private val executor: ScheduledExecutorService get() = ExecutorHolder.executor
    }


    private val objectLock = Object()
    private var iceState: IceState = IceState.NEW
    private var udpSocketBridge: UdpSocketBridge? = null
    private var agent: Agent? = null
    private var mediaStream: IceMediaStream? = null
    private var component: Component? = null
    private var connected = false
    private var closing = false

    fun initialize() {
        withLoggingContext("remotePlayerId" to remotePlayerId.toString()) {
            synchronized(objectLock) {
                if (this.iceState.isIn(IceState.NEW, IceState.DISCONNECTED)) {
                    logger.warn { "ICE already in progress, aborting re initiation. current state: ${this.iceState}" }
                    return
                }
                this.iceState = IceState.GATHERING
            }

            createSocketBridge()
            createAgent()
            gatherLocalCandidates()
        }
    }

    private fun createSocketBridge() {
        withLoggingContext("remotePlayerId" to remotePlayerId.toString()) {
            synchronized(objectLock) {
                udpSocketBridge = UdpSocketBridge("player-$remotePlayerId")
            }
        }
    }

    private fun createAgent() {
        withLoggingContext("remotePlayerId" to remotePlayerId.toString()) {
            synchronized(objectLock) {
                agent = Agent().apply {
                    isControlling = localOffer

                    addStateChangeListener { event->
                        logger.info { "Agent state property ${event.propertyName} changed from ${event.oldValue} to ${event.newValue}" }

                        if(event.newValue == IceProcessingState.COMPLETED) {
                            Thread { listener() }.apply { start() }
                        }
                    }
                    coturnServers.forEach {
                        addCandidateHarvester(StunCandidateHarvester(it.toTCPTransport()))
                        addCandidateHarvester(
                            TurnCandidateHarvester(
                                it.toTCPTransport(),
                                LongTermCredential("user", "password")
                            )
                        )
                    }
                }.also {
                    mediaStream = it.createMediaStream("faData-player-$remotePlayerId")
                }
            }
        }
    }

    private fun gatherLocalCandidates() {
        logger.info { "Gathering local ICE candidates" }
        Failsafe.with(
            Timeout.builder<Component>(Duration.ofSeconds(5L)).build()
        )
            .onSuccess {
                synchronized(objectLock) {
                    this.component = it.result
                }
            }
            .onFailure {
                when (it.exception) {
                    is TimeoutExceededException -> logger.error { "Gathering candidates timed out" }
                    else -> logger.error(it.exception) { "Error while creating stream component/gathering candidates" }
                }
                onConnectionLost()
            }
            .getAsync { _ ->
                val (agent, mediaStream) = synchronized(objectLock) {
                    checkNotNull(this.agent) to checkNotNull(this.mediaStream)
                }

                agent.createComponent(
                    mediaStream,
                    (MINIMUM_PORT..MINIMUM_PORT + 1000).random(),
                    MINIMUM_PORT,
                    MINIMUM_PORT + 1000
                )
            }
    }

    @Synchronized
    fun onCandidates() {
        TODO()
    }

    private fun onConnectionLost() {
        synchronized(objectLock) {
            when {
                closing -> {
                    logger.warn { "Peer not connected anymore, aborting onConnectionLost of ICE" }
                    return
                }

                iceState == IceState.DISCONNECTED -> {
                    logger.debug { "Gathering candidates aborted, but disconnected anyway" }
                    return
                }
            }

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

    fun listener() {
        logger.info { "Now forwarding data from ICE to FA for peer" }

        // 64KiB = UDP MTU, in practice due to ethernet frames being <= 1500 B, this is often not used
        val data = ByteArray(65536)
        val component = synchronized(objectLock) {
            checkNotNull(this.component)
        }
        var errors = 0

        val gameIsRunningOrSomethingLikeThat = true
        while (gameIsRunningOrSomethingLikeThat) {
            try {
                val packet = DatagramPacket(data, data.size)
                component.selectedPair.iceSocketWrapper.udpSocket.receive(packet)

                when {
                    packet.length == 0 -> continue
                    //Received data
                    data[0] == 'd'.code.toByte() -> TODO()
                    //Received echo req/res
                    data[0] == 'e'.code.toByte() -> send(data.copyOfRange(0, packet.length - 1))
                    else -> logger.warn { "Received invalid packet, first byte: 0x${data[0]}, length: ${packet.length}" }
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

    fun send(data: ByteArray) {
        val (connected, component) = synchronized(objectLock) {
            this.connected to this.component
        }

        when {
            !connected -> logger.warn { "Cannot send, not connected" }
            component == null -> logger.warn { "Cannot send, component null" }
            else -> {
                try {
                    val address = component.selectedPair.remoteCandidate.transportAddress.address
                    val port = component.selectedPair.remoteCandidate.transportAddress.port
                    component.selectedPair.iceSocketWrapper.send(
                        DatagramPacket(data, 0, data.size - 1, address, port)
                    )
                } catch (e: IOException) {
                    logger.error(e) { "Failed to send data via ICE" }
                    executor.submit { onConnectionLost() }
                }
            }
        }
    }

    override fun close() {
        synchronized(objectLock) {
            agent?.free()
            agent = null
            mediaStream = null
            component = null
        }
    }
}