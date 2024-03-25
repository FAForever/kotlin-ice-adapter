package com.faforever.ice

import com.faforever.ice.game.GameState
import com.faforever.ice.gpgnet.FakeGameClient
import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.icebreaker.ApiClient
import com.faforever.ice.peering.CoturnServer
import com.faforever.ice.telemetry.TelemetryClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Testcontainers
@ExtendWith(MockKExtension::class)
class IceAdapterIT {
    companion object {
        private const val DOCKER_IMAGE_NAME: String = "coturn/coturn"
        private const val COTURN_SERVER_HOSTNAME: String = "test"

        @Container
        val coturnServerContainer: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse(DOCKER_IMAGE_NAME)).apply {
                withCreateContainerCmdModifier { it.withHostName(COTURN_SERVER_HOSTNAME) }
                withAccessToHost(true)
            }
    }

    @MockK
    private lateinit var apiClientMock1: ApiClient

    @MockK
    private lateinit var apiClientMock2: ApiClient

    @MockK(relaxed = true)
    private lateinit var telemetryClientMock: TelemetryClient

    class CandidatesTestForwarder {
        lateinit var adapter1: IceAdapter
        lateinit var adapter2: IceAdapter

        fun onCandidatesFromA1(candidatesMessage: CandidatesMessage) =
            adapter2.receiveIceCandidates(1, candidatesMessage).also {
                logger.info { "Forwarded ice candidates from adapter 1 to adapter 2" }
            }

        fun onCandidatesFromA2(candidatesMessage: CandidatesMessage) =
            adapter1.receiveIceCandidates(2, candidatesMessage).also {
                logger.info { "Forwarded ice candidates from adapter 2 to adapter 1" }
            }
    }

    @BeforeEach
    fun testEnv() {
        assertTrue(coturnServerContainer.isRunning)
    }

    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun `2 ice adapters should exchange a message`() {
        // all ports are open
        val coturnServers: List<CoturnServer> = listOf(CoturnServer(URI.create("stun://${coturnServerContainer.host}:3478")))

        every { apiClientMock1.requestSessionToken("Access Token User 1", 4711) } returns CompletableFuture.completedFuture(null)
        every { apiClientMock1.requestSession(4711) } returns CompletableFuture.completedFuture(
            ApiClient.Session(
                id = "4711",
                servers = listOf(
                    ApiClient.Session.Server(
                        id = "1",
                        username = "User 1",
                        credential = "cred1",
                        urls = listOf(
                            "stun://${coturnServerContainer.host}:3478",
                            "turn://${coturnServerContainer.host}:3478?transport=udp",
                            "turn://${coturnServerContainer.host}:3478?transport=tcp",
                        ),
                    ),
                ),
            ),
        )
        every { apiClientMock2.requestSessionToken("Access Token User 2", 4711) } returns CompletableFuture.completedFuture(null)
        every { apiClientMock2.requestSession(4711) } returns CompletableFuture.completedFuture(
            ApiClient.Session(
                id = "4711",
                servers = listOf(
                    ApiClient.Session.Server(
                        id = "1",
                        username = "User 2",
                        credential = "cred2",
                        urls = listOf(
                            "stun://${coturnServerContainer.host}:3478",
                            "turn://${coturnServerContainer.host}:3478?transport=udp",
                            "turn://${coturnServerContainer.host}:3478?transport=tcp",
                        ),
                    ),
                ),
            ),
        )

        val data = "hello world".encodeToByteArray()

        val candidatesTestForwarder = CandidatesTestForwarder()

        val adapter1 = IceAdapter(
            iceOptions = IceOptions(
                accessToken = "Access Token User 1",
                userId = 1,
                userName = "User 1",
                gameId = 4711,
                forceRelay = false,
                rpcPort = 5236,
                lobbyPort = 5001,
                gpgnetPort = 5002,
                icebreakerBaseUrl = "icebreakerBaseUrl",
                telemetryServer = "telemetryServer",
            ),
            apiClient = apiClientMock1,
            telemetryClient = telemetryClientMock,
            onGameConnectionStateChanged = {},
            onGpgNetMessageReceived = {},
            onIceCandidatesGathered = candidatesTestForwarder::onCandidatesFromA1,
            onIceConnectionStateChanged = { _, _, _ -> },
            onConnected = { _, _, _ -> },
            onIceAdapterStopped = {},
            initialCoturnServers = coturnServers,
        ).apply { start("Access Token User 1", 4711) }

        Thread.sleep(1000)

        val client1 = FakeGameClient(5002, 5001, 1)
        client1.sendGpgnetMessage(GpgnetMessage.GameState(GameState.IDLE))
        client1.sendGpgnetMessage(GpgnetMessage.GameState(GameState.LOBBY))

        val adapter2 = IceAdapter(
            iceOptions = IceOptions(
                accessToken = "Access Token User 2",
                userId = 2,
                userName = "User 2",
                gameId = 4711,
                forceRelay = false,
                rpcPort = 6236,
                lobbyPort = 6001,
                gpgnetPort = 6002,
                icebreakerBaseUrl = "icebreakerBaseUrl",
                telemetryServer = "telemetryServer",
            ),
            apiClient = apiClientMock2,
            telemetryClient = telemetryClientMock,
            onGameConnectionStateChanged = {},
            onGpgNetMessageReceived = {},
            onIceCandidatesGathered = candidatesTestForwarder::onCandidatesFromA2,
            onIceConnectionStateChanged = { _, _, _ -> },
            onConnected = { _, _, _ -> },
            onIceAdapterStopped = {},
            initialCoturnServers = coturnServers,
        ).apply { start("Access Token User 2", 4711) }

        Thread.sleep(1000)

        val client2 = FakeGameClient(6002, 6001, 2)
        client2.sendGpgnetMessage(GpgnetMessage.GameState(GameState.IDLE))
        client2.sendGpgnetMessage(GpgnetMessage.GameState(GameState.LOBBY))

        candidatesTestForwarder.adapter1 = adapter1
        candidatesTestForwarder.adapter2 = adapter2

        adapter1.connectToPeer("User 2", 2, false)
        adapter2.joinGame("User 1", 1)

        Thread.sleep(3000)

        client1.sendLobbyData(data)
        val result = client2.receiveLobbyData()

        assertArrayEquals(data, result)
    }
}
