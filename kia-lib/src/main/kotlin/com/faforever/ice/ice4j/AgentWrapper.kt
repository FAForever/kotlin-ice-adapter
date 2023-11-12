package com.faforever.ice.ice4j

import com.faforever.ice.peering.CoturnServer
import dev.failsafe.Failsafe
import dev.failsafe.Timeout
import dev.failsafe.TimeoutExceededException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.ice4j.ice.Agent
import org.ice4j.ice.Component
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.IceProcessingState
import org.ice4j.ice.harvest.StunCandidateHarvester
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.time.Duration

private val logger = KotlinLogging.logger {}

class AgentWrapper(
    private val localPlayerId: Int,
    private val remotePlayerId: Int,
    private val localOffer: Boolean,
    private val forceRelay: Boolean,
    private val coturnServers: List<CoturnServer>,
    private val onStateChanged: (IceState, IceState) -> Unit,
    private val onCandidatesGathered: (CandidatesMessage) -> Unit,
) : Closeable {

    companion object {
        private const val MINIMUM_PORT = 6112
    }

    private val objectLock = Object()

    val port: Int? get() = component?.selectedPair?.iceSocketWrapper?.localPort

    // 64KiB = UDP MTU, in practice due to ethernet frames being <= 1500 B, this is often not used
    private val readBuffer = ByteArray(65536)

    @Volatile
    private var agent: Agent? = null

    @Volatile
    private var mediaStream: IceMediaStream? = null

    @Volatile
    private var component: Component? = null

    @Volatile
    private var closing = false

    @Volatile
    private var state: IceState = IceState.NEW

    fun start() {
        createAgent()
        gatherLocalCandidates()
    }

    private fun setState(newState: IceState) {
        if (state == newState) return

        logger.debug { "ICE state changed from $state -> $newState" }

        val oldState = this.state
        state = newState

        onStateChanged(oldState, newState)
    }

    private fun createAgent() {
        synchronized(objectLock) {
            agent = Agent().apply {
                isControlling = localOffer

                addStateChangeListener { event ->
                    logger.info { "Agent state property ${event.propertyName} changed from ${event.oldValue} to ${event.newValue}" }

                    if (event.newValue == IceProcessingState.COMPLETED) {
                        setState(IceState.CONNECTED)
                    }
                }
                coturnServers.forEach {
                    addCandidateHarvester(StunCandidateHarvester(it.toUDPTransport()))
                }
            }.also {
                mediaStream = it.createMediaStream("faData")
            }
        }
    }

    private fun gatherLocalCandidates() {
        logger.info { "Gathering local ICE candidates" }
        Failsafe.with(
            Timeout.builder<Component>(Duration.ofSeconds(5L)).build(),
        )
            .onSuccess {
                logger.info { "Harvesting finished, component created" }
                synchronized(objectLock) {
                    this.component = it.result
                    setState(IceState.AWAITING_CANDIDATES)
                }

                val candidates = CandidateUtil.packCandidates(
                    sourceId = localPlayerId,
                    destinationId = remotePlayerId,
                    agent = agent!!,
                    component = it.result,
                    allowHost = !forceRelay,
                    allowReflexive = !forceRelay,
                    allowRelay = true,
                )
                onCandidatesGathered(candidates)
            }
            .onFailure {
                when (it.exception) {
                    is TimeoutExceededException -> logger.error { "Gathering candidates timed out" }
                    else -> logger.error(it.exception) { "Error while creating stream component/gathering candidates" }
                }
                setState(IceState.DISCONNECTED)
            }
            .getAsync { _ ->
                val (agent, mediaStream) = synchronized(objectLock) {
                    checkNotNull(this.agent) to checkNotNull(this.mediaStream)
                }

                agent.createComponent(
                    mediaStream,
                    (MINIMUM_PORT..MINIMUM_PORT + 1000).random(),
                    MINIMUM_PORT,
                    MINIMUM_PORT + 1000,
                )
            }
    }

    fun onRemoteCandidatesReceived(candidatesMessage: CandidatesMessage) {
        if (closing) {
            logger.warn { "Agent not connected anymore, discarding candidates message" }
            return
        }

        logger.info { "Remote candidates received: $candidatesMessage" }

        // TODO: We skipped some state checks from the original ice adapter

        setState(IceState.CHECKING)

        CandidateUtil.unpackCandidates(
            remoteCandidatesMessage = candidatesMessage,
            agent = this.agent!!,
            component = this.component!!,
            mediaStream = this.mediaStream!!,
            allowHost = true,
            allowReflexive = true,
            allowRelay = true,
        )

        startIce()
    }

    private fun startIce() {
        val agent = requireNotNull(agent)
        agent.startConnectivityEstablishment()

        // Wait for termination/completion of the agent
        val iceStartTime = System.currentTimeMillis()
        while (agent.state != IceProcessingState.COMPLETED) { // TODO include more?, maybe stop on COMPLETED, is that to early?
            try {
                Thread.sleep(20)
            } catch (e: InterruptedException) {
                logger.error(e) { "Interrupted while waiting for ICE" }
            }
            if (agent.state == IceProcessingState.FAILED) { // TODO null pointer due to no agent?
                logger.info { "onConnectionLost()" }
                return
            }
            if (System.currentTimeMillis() - iceStartTime > 15000) {
                logger.error { "ABORTING ICE DUE TO TIMEOUT" }

                logger.info { "onConnectionLost()" }
                return
            }
        }
    }

    fun receive(): ByteArray {
        val packet = DatagramPacket(readBuffer, readBuffer.size)
        checkNotNull(component).selectedPair.iceSocketWrapper.receive(packet)

        val data = readBuffer.copyOfRange(0, packet.length)
        logger.trace { "Received data from Ice4j socket on port $port (${data.size} bytes)" }
        return data
    }

    @Throws(IOException::class)
    fun send(data: ByteArray) {
        try {
            val component = checkNotNull(this.component)
            val address = component.selectedPair.remoteCandidate.transportAddress.address
            val port = component.selectedPair.remoteCandidate.transportAddress.port
            component.selectedPair.iceSocketWrapper.send(
                DatagramPacket(data, 0, data.size, address, port),
            )
        } catch (e: IOException) {
            // TODO: Maybe reconnect?
            throw e
        } catch (e: Exception) {
            // TODO: Maybe reconnect?
            throw IOException("Error during sending, probably not ready to send", e)
        }
    }

    override fun close() {
        synchronized(objectLock) {
            if (closing) return

            closing = true
            agent?.free()
            agent = null
            mediaStream = null
            component = null

            setState(IceState.DISCONNECTED)
        }
    }
}
