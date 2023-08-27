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
import org.ice4j.ice.harvest.TurnCandidateHarvester
import org.ice4j.security.LongTermCredential
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.time.Duration

private val logger = KotlinLogging.logger {}

class AgentWrapper(
    private val localOffer: Boolean,
    private val coturnServers: List<CoturnServer>,
    private val onStateChanged: (IceState, IceState)->Unit,
) : Closeable {

    companion object {
        private const val MINIMUM_PORT = 6112
    }

    private val objectLock = Object()

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
        if(state == newState) return

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
                    addCandidateHarvester(StunCandidateHarvester(it.toTCPTransport()))
                    addCandidateHarvester(
                        TurnCandidateHarvester(
                            it.toTCPTransport(),
                            LongTermCredential("user", "password")
                        )
                    )
                }
            }.also {
                mediaStream = it.createMediaStream("faData")
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
                    MINIMUM_PORT + 1000
                )
            }
    }

    fun receive(): ByteArray {
        val packet = DatagramPacket(readBuffer, readBuffer.size)
        checkNotNull(component).selectedPair.iceSocketWrapper.receive(packet)

        return readBuffer.copyOfRange(0, packet.length - 1)
    }

    @Throws(IOException::class)
    fun send(data: ByteArray) {
        try {
            val component = checkNotNull(this.component)
            val address = component.selectedPair.remoteCandidate.transportAddress.address
            val port = component.selectedPair.remoteCandidate.transportAddress.port
            component.selectedPair.iceSocketWrapper.send(
                DatagramPacket(data, 0, data.size - 1, address, port)
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