package com.faforever.ice

import com.faforever.ice.gpgnet.FakeGpgnetClient
import io.mockk.mockk
import org.junit.jupiter.api.Test

class IceAdapterIT {

    @Test
    fun test() {
        val controlPlane1 = mockk<ControlPlane>()
        val controlPlane2 = mockk<ControlPlane>()

        val adapter1 = IceAdapter(
            iceOptions = IceOptions(
                1,
                "User 1",
                4711,
                false,
                5001,
                5002,
                "telemetryServer"
            ),
            callbacks = controlPlane1,
            coturnServers = emptyList()
        ).apply { start() }
        val client1 = FakeGpgnetClient(5002, 1)
        adapter1.hostGame("myMapName")

        val adapter2 = IceAdapter(
            iceOptions = IceOptions(
                2,
                "User 2",
                4711,
                false,
                6001,
                6002,
                "telemetryServer"
            ),
            callbacks = controlPlane2,
            coturnServers = emptyList()
        ).apply { start() }
        val client2 = FakeGpgnetClient(6002, 2)
        adapter2.joinGame("User 2", 2)
    }
}