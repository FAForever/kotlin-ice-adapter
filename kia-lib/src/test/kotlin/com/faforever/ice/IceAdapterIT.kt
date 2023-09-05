package com.faforever.ice

import com.faforever.ice.gpgnet.FakeGameClient
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.peering.CoturnServer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class IceAdapterIT {
    class CandidatesTestForwarder {
        lateinit var adapter1: IceAdapter
        lateinit var adapter2: IceAdapter

        fun onCandidatesFromA1(candidatesMessage: CandidatesMessage) =
            adapter2.receiveIceCandidates(1, candidatesMessage)

        fun onCandidatesFromA2(candidatesMessage: CandidatesMessage) =
            adapter1.receiveIceCandidates(2, candidatesMessage)
    }

    @Test
    fun `2 ice adapters should exchange a message`() {
        val data = "dhello world".encodeToByteArray()

        val candidatesTestForwarder = CandidatesTestForwarder()
        val coturnServers: List<CoturnServer> = listOf(CoturnServer("stun.l.google.com", 19302))

        val adapter1 = IceAdapter(
            iceOptions = IceOptions(
                1,
                "User 1",
                4711,
                false,
                5001,
                5002,
                "telemetryServer",
            ),
            coturnServers = coturnServers,
            candidatesTestForwarder::onCandidatesFromA1,
        ).apply { start() }

        val client1 = FakeGameClient(5002, 5001, 1)
        adapter1.connectToPeer("User 2", 2, false)

        val adapter2 = IceAdapter(
            iceOptions = IceOptions(
                2,
                "User 2",
                4711,
                false,
                6001,
                6002,
                "telemetryServer",
            ),
            coturnServers = coturnServers,
            candidatesTestForwarder::onCandidatesFromA2,
        ).apply { start() }
        val client2 = FakeGameClient(6002, 6001, 2)

        candidatesTestForwarder.adapter1 = adapter1
        candidatesTestForwarder.adapter2 = adapter2

        adapter2.joinGame("User 1", 1)

        Thread.sleep(5000)

        client1.sendLobbyData(data)
        val result = client2.receiveLobbyData()

        assertArrayEquals(data, result)
    }
}
