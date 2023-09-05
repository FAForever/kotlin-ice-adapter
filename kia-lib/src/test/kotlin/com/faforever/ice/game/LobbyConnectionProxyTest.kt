package com.faforever.ice.game

import com.faforever.ice.IceOptions
import com.faforever.ice.utils.mockExecutorSingleThreaded
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@ExtendWith(MockKExtension::class)
class LobbyConnectionProxyTest {

    @MockK
    private lateinit var iceOptions: IceOptions

    private lateinit var sut: LobbyConnectionProxy

    private val testUDPPort = 35601

    @BeforeEach
    fun beforeEach() {
        every { iceOptions.lobbyPort } returns testUDPPort

        sut = LobbyConnectionProxy(iceOptions)
    }

    @AfterEach
    fun afterEach() {
        sut.close()
    }

    @Test
    fun `it should be reusable via start-close-start`() {
        mockExecutorSingleThreaded()

        sut.start()
        assertFalse(sut.closing)
        assertTrue(sut.active)

        sut.stop()
        assertTrue(sut.closing)
        assertFalse(sut.active)

        sut.start()

        assertFalse(sut.closing)
        assertTrue(sut.active)
    }

    @Test
    fun `it should send data`() {
        DatagramSocket(testUDPPort, InetAddress.getLoopbackAddress()).use { udpSocket ->
            val sampleData = "hello world".toByteArray()

            sut.start()
            sut.sendData(sampleData)

            val receivedData = ByteArray(sampleData.size)
            val receivePacket = DatagramPacket(receivedData, receivedData.size)
            udpSocket.receive(receivePacket)

            assertArrayEquals(sampleData, receivedData)
        }
    }
}
