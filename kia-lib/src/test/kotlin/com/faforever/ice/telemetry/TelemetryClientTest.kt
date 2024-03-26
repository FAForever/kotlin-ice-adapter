package com.faforever.ice.telemetry

import com.faforever.ice.IceOptions
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import java.net.URI
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Test


@ExtendWith(MockKExtension::class)
class TelemetryClientTest {
    @MockK
    private lateinit var mockIceOptions: IceOptions

    @BeforeEach
    fun beforeEach() {
        every {mockIceOptions.telemetryServer } returns "wss://mock-telemetryserver.xyz:"
        every {mockIceOptions.userId } returns 5000
        every {mockIceOptions.gameId } returns 12345

        mockkConstructor(TelemetryClient.TelemetryWebsocketClient::class)
        every { anyConstructed<TelemetryClient.TelemetryWebsocketClient>().connect() } returns Unit
    }

    @Test
    fun `test update coturn servers`() {
        val messageUuid = UUID.fromString("000e8400-e29b-41d4-a716-446655440000")
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns messageUuid

        val telemetryClient = TelemetryClient(mockIceOptions, jacksonObjectMapper())

        val coturnServers: List<com.faforever.ice.peering.CoturnServer> = listOf(
            com.faforever.ice.peering.CoturnServer(URI.create("stun://coturn1.faforever.com:3478")),
            com.faforever.ice.peering.CoturnServer(URI.create("stun://fr-turn1.xirsys.com:80"))
        )

        telemetryClient.updateCoturnList(coturnServers)

        verify {
            val expected =
                "{\"messageType\":\"UpdateCoturnList\"," +
                "\"connectedHost\":\"coturn1.faforever.com\"," +
                "\"knownServers\":[" +
                  "{\"region\":\"n/a\",\"host\":\"coturn1.faforever.com\",\"port\":3478,\"averageRTT\":0.0}," +
                  "{\"region\":\"n/a\",\"host\":\"fr-turn1.xirsys.com\",\"port\":80,\"averageRTT\":0.0}]," +
                "\"messageId\":\"$messageUuid\"}"
            anyConstructed<TelemetryClient.TelemetryWebsocketClient>().send(expected)
        }
    }

    @Test
    fun `test update coturn servers empty`() {
        val messageUuid = UUID.fromString("000e8400-e29b-41d4-a716-446655440000")
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns messageUuid

        val telemetryClient = TelemetryClient(mockIceOptions, jacksonObjectMapper())
        val coturnServers: List<com.faforever.ice.peering.CoturnServer> = listOf()

        telemetryClient.updateCoturnList(coturnServers)

        verify {
            val expected =
                "{\"messageType\":\"UpdateCoturnList\"," +
                  "\"connectedHost\":\"\"," +
                  "\"knownServers\":[]," +
                "\"messageId\":\"$messageUuid\"}"
            anyConstructed<TelemetryClient.TelemetryWebsocketClient>().send(expected)
        }
    }
}