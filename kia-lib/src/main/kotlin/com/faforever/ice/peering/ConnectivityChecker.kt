package com.faforever.ice.peering

import com.faforever.ice.util.ExecutorHolder
import com.faforever.ice.util.ReusableComponent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

typealias PlayerId = Int

private val logger = KotlinLogging.logger {}

class ConnectivityChecker: ReusableComponent {
    companion object {
        private val sharedExecutor: ScheduledExecutorService get() = ExecutorHolder.executor
    }

    private val objectLock = Object()
    private var running = false
    private var tickExecutor: ScheduledExecutorService? = null


    data class PlayerState(
        val iceState: IceState,
        val isOfferer: Boolean,
        val lastEchoRequested: LocalDateTime,
        val lastEchoReceived: LocalDateTime,
        val onConnectionLost: () -> Unit,
        val requestEcho: () -> Unit,
    )

    private val states: MutableMap<PlayerId, PlayerState> = ConcurrentHashMap()


    override fun start() {
        synchronized(objectLock) {
            states.clear()
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

    fun registerPlayer(
        playerId: PlayerId,
        isOfferer: Boolean,
        sendEcho: () -> Unit,
        onConnectionLost: () -> Unit,
        ) {
        states[playerId] = PlayerState(
            iceState = IceState.NEW,
            isOfferer = isOfferer,
            lastEchoRequested = LocalDateTime.now(),
            lastEchoReceived = LocalDateTime.now(),
            requestEcho = sendEcho,
            onConnectionLost = onConnectionLost,
        )
    }

    fun onEchoReceived(playerId: PlayerId, ) {
        states[playerId] = states[playerId]!!.copy(
            iceState = IceState.CONNECTED,
            lastEchoReceived = LocalDateTime.now()
        )

        // TODO: Calculate RTT
    }

    fun disconnectPlayer(playerId: PlayerId) {
        states.remove(playerId)
    }

    private fun checkConnectivity() {
        states.forEach { (playerId, state) ->
            val now = LocalDateTime.now()

            when {
                Duration.between(now, state.lastEchoReceived).seconds < 5L ->
                    logger.trace { "Last echo within threshold, connection considered alive" }
                Duration.between(now, state.lastEchoRequested).seconds < 5L ->
                    logger.trace { "Echo waiting time within threshold, keep waiting" }
                Duration.between(now, state.lastEchoReceived).seconds < 60L -> {
                    logger.debug { "No echo received within 1 minute, connection considered dead" }
                    states[playerId] = state.copy(
                        iceState = IceState.DISCONNECTED,
                    )
                    sharedExecutor.submit(state.onConnectionLost)
                }
                state.isOfferer -> {
                        logger.debug { "Initiating echo sequence" }
                        states[playerId] = state.copy(
                            iceState = IceState.CHECKING,
                            lastEchoRequested = now,
                        )
                        sharedExecutor.submit(state.requestEcho)
                    }
                !state.isOfferer -> {
                    logger.debug { "Waiting for echo from offerer" }
                    logger.debug { "Initiating echo sequence" }
                    states[playerId] = state.copy(
                        iceState = IceState.CHECKING,
                        lastEchoRequested = now,
                    )
                }
            }
        }
    }
}