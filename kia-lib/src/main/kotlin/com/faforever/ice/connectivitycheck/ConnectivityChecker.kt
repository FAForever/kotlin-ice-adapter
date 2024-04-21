package com.faforever.ice.connectivitycheck

import com.faforever.ice.connectivitycheck.ConnectivityCheckHandler.Status.ALIVE
import com.faforever.ice.connectivitycheck.ConnectivityCheckHandler.Status.DEAD
import com.faforever.ice.connectivitycheck.ConnectivityCheckHandler.Status.ECHO_PENDING
import com.faforever.ice.connectivitycheck.ConnectivityCheckHandler.Status.ECHO_REQUIRED
import com.faforever.ice.ice4j.IceState
import com.faforever.ice.util.ExecutorHolder
import com.faforever.ice.util.ReusableComponent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * A component that handles running of connections checks for any components implementing ConnectivityCheckable
 */
class ConnectivityChecker(
    private val clock: Clock = Clock.systemUTC(),
    private val connectionAliveSeconds: Int = 5,
    private val connectionEchoPendingSeconds: Int = 5,
    private val connectionDeadThresholdSeconds: Int = 60,
) : ReusableComponent {
    companion object {
        private val sharedExecutor: ScheduledExecutorService get() = ExecutorHolder.executor
    }

    var running = false
        private set

    private val objectLock = Object()
    private var tickExecutor: ScheduledExecutorService? = null

    data class ConnectionState(
        val iceState: IceState,
        val lastEchoRequested: LocalDateTime,
        val lastEchoReceived: LocalDateTime,
    )

    private val handlers: MutableMap<ConnectivityCheckable, PeerHandler> = ConcurrentHashMap()

    override fun start() {
        synchronized(objectLock) {
            handlers.clear()
            running = true
            tickExecutor = Executors.newSingleThreadScheduledExecutor().apply {
                this.scheduleAtFixedRate(::checkConnectivity, 1L, 1L, TimeUnit.SECONDS)
            }
        }
    }

    override fun stop() {
        synchronized(objectLock) {
            running = false
            tickExecutor?.shutdown()
        }
    }

    private inner class PeerHandler(private val connectivityCheckable: ConnectivityCheckable) :
        ConnectivityCheckHandler {
        var connectionState: ConnectionState = ConnectionState(
            iceState = IceState.NEW,
            lastEchoRequested = LocalDateTime.now(clock),
            lastEchoReceived = LocalDateTime.now(clock),
        )
            internal set

        override val status: ConnectivityCheckHandler.Status
            get() {
                val now = LocalDateTime.now(clock)
                return when {
                    Duration.between(connectionState.lastEchoReceived, now).seconds < connectionAliveSeconds -> ALIVE.also {
                        logger.trace { "[$connectivityCheckable] Last echo within threshold, connection considered alive" }
                    }

                    Duration.between(connectionState.lastEchoRequested, now).seconds > connectionEchoPendingSeconds &&
                        Duration.between(connectionState.lastEchoRequested, connectionState.lastEchoReceived).seconds < connectionEchoPendingSeconds -> ECHO_REQUIRED.also {
                        logger.trace { "[$connectivityCheckable] Echo waiting time within threshold, keep waiting" }
                    }

                    Duration.between(connectionState.lastEchoReceived, now).seconds < connectionDeadThresholdSeconds -> ECHO_PENDING.also {
                        logger.trace { "[$connectivityCheckable] No echo received (but still within 1 minute), connection critical" }
                    }

                    else -> DEAD.also {
                        logger.trace { "[$connectivityCheckable] No echo received within 1 minute, connection considered dead" }
                    }
                }
            }

        override fun echoReceived() = this@ConnectivityChecker.onEchoReceived(connectivityCheckable)
        override fun disconnected() = this@ConnectivityChecker.disconnectPlayer(connectivityCheckable)
    }

    fun registerPlayer(connectivityCheckable: ConnectivityCheckable): ConnectivityCheckHandler {
        synchronized(objectLock) {
            if (!running) {
                throw IllegalStateException("ConnectivityCheckable $connectivityCheckable already registered")
            }

            if (handlers.keys.contains(connectivityCheckable)) {
                throw IllegalStateException("ConnectivityCheckable $connectivityCheckable already registered")
            }

            val peerHandler = PeerHandler(connectivityCheckable)
            handlers[connectivityCheckable] = peerHandler
            return peerHandler
        }
    }

    private fun onEchoReceived(connectivityCheckable: ConnectivityCheckable) {
        synchronized(objectLock) {
            handlers[connectivityCheckable]!!.apply {
                connectionState = connectionState.copy(
                    iceState = IceState.CONNECTED,
                    lastEchoReceived = LocalDateTime.now(clock),
                )
            }
        }

        if (!connectivityCheckable.isOfferer) {
            // respond to echo
            connectivityCheckable.sendEcho()
        }
        // TODO: Calculate RTT
    }

    private fun disconnectPlayer(connectivityCheckable: ConnectivityCheckable) {
        handlers.remove(connectivityCheckable)
    }

    private fun checkConnectivity() {
        handlers.forEach { (connectivityCheckable, handler) ->
            val now = LocalDateTime.now(clock)

            when (handler.status) {
                ALIVE, ECHO_PENDING -> {
                    logger.trace { "Nothing to do" }
                }
                ECHO_REQUIRED -> {
                    handler.connectionState = handler.connectionState.copy(
                        iceState = IceState.CHECKING,
                        lastEchoRequested = now,
                    )

                    if (connectivityCheckable.isOfferer) {
                        logger.debug { "Initiating echo sequence with $connectivityCheckable" }
                        sharedExecutor.submit(connectivityCheckable::sendEcho)
                    } else {
                        logger.debug { "Waiting for echo from offerer from $connectivityCheckable" }
                    }
                }
                DEAD -> {
                    if (handler.connectionState.iceState != IceState.DISCONNECTED) {
                        handler.connectionState = handler.connectionState.copy(
                            iceState = IceState.DISCONNECTED,
                        )
                        logger.info { "Dead connection detected for $connectivityCheckable" }
                        sharedExecutor.submit(connectivityCheckable::onConnectionLost)
                    } else {
                        logger.debug { "Ignore dead connection from $connectivityCheckable, as state is already disconnected." }
                    }
                }
            }
        }
    }
}
