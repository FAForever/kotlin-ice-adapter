package com.faforever.ice.telemetry

import com.faforever.ice.IceOptions
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.UUID

private val logger = KotlinLogging.logger {}

class TelemetryClient(
    iceOptions: IceOptions,
    private val objectMapper: ObjectMapper,
) {
    private val serverBaseUrl = iceOptions.telemetryServer
    private val gameId = iceOptions.gameId
    private val userId = iceOptions.userId

    private val websocketClient: WebSocketClient
    private var connectingFuture: CompletableFuture<Void>

    inner class TelemetryWebsocketClient(serverUri: URI): WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake) {
            logger.info {"Telemetry websocket opened"}
        }

        override fun onMessage(message: String) {
            // We don't expect messages in the current protocol though
            logger.info { "Telemetry websocket message: $message" }
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            logger.info { "Telemetry websocket closed (reason: $reason). Trying to reconnect!" }
            connectingFuture = connectAsync()
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
                serverBaseUrl.replaceFirst(
                    "ws",
                    "http",
                )
            }/app.html?gameId=$gameId&playerId=$userId"
        }

        val uri: URI = URI.create("$serverBaseUrl/adapter/v1/game/$gameId/player/$userId")
        websocketClient = TelemetryWebsocketClient(uri)

        connectingFuture = connectAsync()
    }

    private fun connectAsync() = CompletableFuture.runAsync(websocketClient::connect)
        .exceptionally {
            logger.error(it) { "Failed to connect to telemetry websocket" }
            null
        }

    private fun sendMessage(message: OutgoingMessageV1) {
        connectingFuture.thenRun {
            try {
                val json = objectMapper.writeValueAsString(message)
                websocketClient.send(json)
            } catch (e: IOException) {
                logger.error(e) { "Error on serialising message object: $message" }
            }
        }
    }

    fun updateCoturnList(servers: Collection<com.faforever.ice.peering.CoturnServer>)
    {
        val telemetryCoturnServers = servers.map { server ->
            CoturnServer(
                region = "n/a",
                host = server.uri.host,
                port = server.uri.port,
                averageRTT = 0.0
            )
        }

        val connectedHost:String = telemetryCoturnServers.stream().map(CoturnServer::host).findFirst().orElse(null)
        val message = UpdateCoturnList(connectedHost, telemetryCoturnServers, UUID.randomUUID())
        sendMessage(message)
    }
}
