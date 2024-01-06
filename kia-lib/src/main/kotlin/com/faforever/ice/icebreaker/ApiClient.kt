package com.faforever.ice.icebreaker

import com.faforever.ice.IceOptions
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class ApiClient(
    private val iceOptions: IceOptions,
    private val objectMapper: ObjectMapper,
) {
    private val httpClient =
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build()

    @Volatile
    private var sessionToken: String? = null
        @Synchronized private set

    private data class SessionTokenRequest(val gameId: Int)
    private data class SessionTokenResponse(val jwt: String)
    data class Session(val id: String, val servers: List<Server>) {
        data class Server(
            val id: String,
            val username: String?,
            val credential: String?,
            val urls: List<String>,
        )
    }

    private fun uri(uriPath: String) = URI.create("${iceOptions.icebreakerBaseUrl}/$uriPath")

    private fun toJson(payload: Any): BodyPublisher = BodyPublishers.ofString(objectMapper.writeValueAsString(payload))

    private inline fun <reified T> postJson(
        uri: URI,
        payload: Any,
        accessToken: String? = null,
    ): CompletableFuture<T> {
        val bearerToken = accessToken
            ?: requireNotNull(sessionToken) { "If no access token is defined, a session token must be present." }

        val httpRequest =
            HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("Authorization", "Bearer $bearerToken").POST(toJson(payload)).build()

        return httpClient.sendAsync(httpRequest, BodyHandlers.ofString()).thenApply {
            if (it.statusCode() < 400) {
                objectMapper.readValue<T>(it.body())
            } else {
                throw IOException("HTTP request to $uri failed with status code ${it.statusCode()}. Response: ${it.body()}")
            }
        }
    }

    private inline fun <reified T> getJson(uri: URI): CompletableFuture<T> {
        requireNotNull(sessionToken) { "GET calls require a session token to be obtained first" }

        val httpRequest =
            HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("Authorization", "Bearer $sessionToken").GET().build()

        return httpClient.sendAsync(httpRequest, BodyHandlers.ofString()).thenApply {
            if (it.statusCode() < 400) {
                objectMapper.readValue<T>(it.body())
            } else {
                throw IOException("HTTP request to $uri failed with status code ${it.statusCode()}. Response: ${it.body()}")
            }
        }
    }

    fun requestSessionToken(
        accessToken: String,
        gameId: Int,
    ): CompletableFuture<Void> = postJson<SessionTokenResponse>(
        uri = uri("/session/token"),
        payload = SessionTokenRequest(gameId = gameId),
        accessToken = accessToken,
    ).thenAccept {
        logger.info { "Session token acquired" }
        sessionToken = it.jwt
    }

    fun requestSession(gameId: Int): CompletableFuture<Session> = getJson(uri("/session/game/$gameId"))
}
