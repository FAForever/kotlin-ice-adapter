package com.faforever.ice.peering

import com.faforever.ice.util.SocketFactory
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.InetAddress

class UdpSocketBridgeTest {
    private lateinit var sut: UdpSocketBridge
    private var lastForwarded = ByteArray(0)

    @BeforeEach
    fun beforeEach() {
        sut = UdpSocketBridge(forwardToIce = { lastForwarded = it })
    }

    @AfterEach
    fun tearDown() {
        sut.close()
    }

    @Test
    fun `it should close properly`() {
        sut.start()
        await().until { sut.started }
        sut.close()
        await().until { !sut.started }
    }

    @Test
    fun `it should forward data`() {
        sut.start()

        await().until { sut.started }

        SocketFactory.createLocalUDPSocket().use { socket ->
            val data = "hello world".encodeToByteArray()
            val packet = DatagramPacket(data, data.size, InetAddress.getLoopbackAddress(), sut.bridgePort!!)
            socket.send(packet)

            await().untilAsserted {
                assertArrayEquals(data, lastForwarded)
            }
        }
    }
}
