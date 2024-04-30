package com.faforever.ice.telemetry

import com.faforever.ice.IceAdapter
import com.faforever.ice.IceOptions
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.UUID

@ExtendWith(MockKExtension::class)
class TelemetryClientTest {
    private lateinit var sut: TelemetryClient

    @MockK
    private lateinit var mockIceOptions: IceOptions

    private val messageUuid = UUID.fromString("000e8400-e29b-41d4-a716-446655440000")

    @BeforeEach
    fun beforeEach() {
        every { mockIceOptions.telemetryServer } returns "wss://mock-telemetryserver.xyz:"
        every { mockIceOptions.userName } returns "Player1"
        every { mockIceOptions.userId } returns 5000
        every { mockIceOptions.gameId } returns 12345

        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns messageUuid

        mockkObject(IceAdapter)
        every { IceAdapter.version } returns "9.9.9-SNAPSHOT"

        mockkConstructor(TelemetryClient.TelemetryWebsocketClient::class)
        every { anyConstructed<TelemetryClient.TelemetryWebsocketClient>().connectBlocking() } returns true
        every { anyConstructed<TelemetryClient.TelemetryWebsocketClient>().send(any<String>()) } returns Unit
        every { anyConstructed<TelemetryClient.TelemetryWebsocketClient>().isOpen } returns true

        sut = TelemetryClient(mockIceOptions, jacksonObjectMapper())
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(UUID::class)
    }

    @Test
    fun `test init connects and registers as peer`() {
        sut = TelemetryClient(mockIceOptions, jacksonObjectMapper())

        await().untilAsserted {
            verify {
                anyConstructed<TelemetryClient.TelemetryWebsocketClient>().connectBlocking()
                val expected =
                    """
                        {
                        "messageType":"RegisterAsPeer",
                        "adapterVersion":"kotlin-ice-adapter/9.9.9-SNAPSHOT",
                        "userName":"Player1",
                        "messageId":"$messageUuid"
                        }
                    """.trimIndent().replace("\n", "")
                anyConstructed<TelemetryClient.TelemetryWebsocketClient>().send(expected)
            }
        }
    }

    @Test
    fun `test update coturn servers`() {
        val coturnServers: List<com.faforever.ice.peering.CoturnServer> = listOf(
            com.faforever.ice.peering.CoturnServer(URI.create("stun://coturn1.faforever.com:3478")),
            com.faforever.ice.peering.CoturnServer(URI.create("stun://fr-turn1.xirsys.com:80")),
        )

        sut.updateCoturnList(coturnServers)

        await().untilAsserted {
            verify {
                val expected =
                    """
                    {
                    "messageType":"UpdateCoturnList",
                    "connectedHost":"coturn1.faforever.com",
                    "knownServers":[
                    {"region":"n/a","host":"coturn1.faforever.com","port":3478,"averageRTT":0.0},
                    {"region":"n/a","host":"fr-turn1.xirsys.com","port":80,"averageRTT":0.0}],
                    "messageId":"$messageUuid"
                    }
                    """.trimIndent().replace("\n", "")
                anyConstructed<TelemetryClient.TelemetryWebsocketClient>().send(expected)
            }
        }
    }

    @Test
    fun `test update coturn servers empty`() {
        val coturnServers: List<com.faforever.ice.peering.CoturnServer> = listOf()

        sut.updateCoturnList(coturnServers)

        await().untilAsserted {
            verify {
                val expected =
                    """
                        {
                        "messageType":"UpdateCoturnList",
                        "connectedHost":"",
                        "knownServers":[],
                        "messageId":"$messageUuid"
                        }
                    """.trimIndent().replace("\n", "")

                anyConstructed<TelemetryClient.TelemetryWebsocketClient>().send(expected)
            }
        }
    }
}
