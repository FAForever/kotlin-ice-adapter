package com.faforever.ice.telemetry

import com.faforever.ice.IceAdapter
import com.faforever.ice.IceOptions
import com.fasterxml.jackson.databind.ObjectMapper
import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

private val logger = KotlinLogging.logger {}

class TelemetryClient(
    private val iceOptions: IceOptions,
    private val objectMapper: ObjectMapper,
    private val connectionRetries: Int = 6,
) {
    private val serverBaseUrl = iceOptions.telemetryServer

    private var websocketClient: WebSocketClient
    private val sendingLoopThread: Thread
    private val messageQueue = LinkedBlockingQueue<OutgoingMessageV1>()

    // True if we have given up trying to connect to the telemetry server.
    private var connectionFailed = false

    private enum class ConnectionResult { SUCCESS, FAILURE }

    inner class TelemetryWebsocketClient(serverUri: URI) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake) {
            logger.info { "Telemetry websocket opened" }
        }

        override fun onMessage(message: String) {
            // We don't expect messages in the current protocol though
            logger.info { "Telemetry websocket message: $message" }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            logger.info { "Telemetry websocket closed (reason: $reason). Trying to reconnect!" }
        }

        override fun onError(ex: Exception) {
            if (ex is ConnectException) {
                logger.error(ex) { "Error connecting to Telemetry websocket" }
            } else {
                logger.error(ex) { "Error in Telemetry websocket" }
            }
        }
    }

    init {
        logger.info {
            "Open the telemetry ui via ${
                serverBaseUrl.replaceFirst("ws", "http")
            }/app.html?gameId=${iceOptions.gameId}&playerId=${iceOptions.userId}"
        }

        val uri: URI = URI.create("$serverBaseUrl/adapter/v1/game/${iceOptions.gameId}/player/${iceOptions.userId}")
        websocketClient = TelemetryWebsocketClient(uri)

        sendingLoopThread = Thread(this::sendingLoop)
        sendingLoopThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _: Thread, e: Throwable ->
            logger.error(e) { "Thread sendingLoop crashed unexpectedly: " }
        }

        CompletableFuture.runAsync {
            websocketClient.connectBlocking()
            sendingLoopThread.start()
        }

        registerAsPeer()
    }

    private fun sendingLoop() {
        while (!connectionFailed) {
            val message = messageQueue.take()

            if (websocketClient.isClosed) {
                Failsafe.with(
                    RetryPolicy.builder<ConnectionResult>()
                        .handleResult(ConnectionResult.FAILURE)
                        .withBackoff(Duration.ofSeconds(2), Duration.ofMinutes(1))
                        .withMaxRetries(connectionRetries)
                        .build(),
                )
                    .onFailure {
                        logger.info { "Failed to reconnect to the telemetry server after ${it.attemptCount} attempts." }
                        connectionFailed = true
                    }
                    .get { _ ->
                        logger.info { "Attempting reconnect to telemetry server..." }
                        websocketClient.reconnectBlocking()
                        if (websocketClient.isOpen) ConnectionResult.SUCCESS else ConnectionResult.FAILURE
                    }
            }
            if (websocketClient.isOpen) {
                try {
                    val json = objectMapper.writeValueAsString(message)
                    websocketClient.send(json)
                } catch (e: IOException) {
                    logger.error(e) { "Error serialising message object: $message" }
                } catch (e: Exception) {
                    logger.error(e) { "Error sending message object: $message" }
                }
            }
        }
    }

    private fun sendMessage(message: OutgoingMessageV1) {
        if (connectionFailed) {
            return
        }
        messageQueue.put(message)
    }

    private fun registerAsPeer() {
        val message = RegisterAsPeer(
            "kotlin-ice-adapter/${IceAdapter.version}",
            iceOptions.userName,
        )
        sendMessage(message)
    }

    fun updateCoturnList(servers: Collection<com.faforever.ice.peering.CoturnServer>) {
        val telemetryCoturnServers = servers.map { server ->
            CoturnServer(
                region = "n/a",
                host = server.uri.host,
                port = server.uri.port,
                averageRTT = 0.0,
            )
        }

        val connectedHost: String = telemetryCoturnServers.map { it.host }.firstOrNull() ?: ""
        val message = UpdateCoturnList(connectedHost, telemetryCoturnServers, UUID.randomUUID())
        sendMessage(message)
    }
}
