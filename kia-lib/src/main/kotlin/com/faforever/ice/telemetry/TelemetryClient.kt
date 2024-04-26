package com.faforever.ice.telemetry

import com.faforever.ice.IceAdapter
import com.faforever.ice.IceOptions
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class TelemetryClient(
    private val iceOptions: IceOptions,
    private val objectMapper: ObjectMapper,
) {
    private val serverBaseUrl = iceOptions.telemetryServer

    private var websocketClient: WebSocketClient
    private val sendingLoopThread: Thread
    private var connectionRetryAttempts = 0
    private val messageQueue = LinkedBlockingQueue<OutgoingMessageV1>()

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
        sendingLoopThread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _: Thread?, e: Throwable? ->
            logger.error { "Thread sendingLoop crashed unexpectedly: $e" }
        }

        CompletableFuture.runAsync {
            websocketClient.connectBlocking()
            sendingLoopThread.start()
        }

        registerAsPeer()
    }

    private fun sendingLoop() {
        while (true) {
            val message = messageQueue.take()

            while (websocketClient.isClosed) {
                // Before reconnecting delay 2, 8, 32, 64, 64, 64... seconds.
                val delay = 2.shl((2 * connectionRetryAttempts).coerceAtMost(5))
                TimeUnit.SECONDS.sleep(delay.toLong())
                logger.info { "Attempting reconnect to telemetry server..." }
                websocketClient.reconnectBlocking()
                connectionRetryAttempts++
            }
            connectionRetryAttempts = 0

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

    private fun registerAsPeer() {
        val message = RegisterAsPeer(
            "kotlin-ice-adapter/${IceAdapter.version}",
            iceOptions.userName,
        )
        messageQueue.put(message)
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
        messageQueue.put(message)
    }
}
