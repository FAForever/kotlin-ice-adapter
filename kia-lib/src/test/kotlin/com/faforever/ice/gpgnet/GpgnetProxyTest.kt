package com.faforever.ice.gpgnet

import com.faforever.ice.IceOptions
import com.faforever.ice.io.BlockingNoDataInputStream
import com.faforever.ice.util.SocketFactory
import com.faforever.ice.utils.mockCreateLocalTCPSocket
import com.faforever.ice.utils.mockExecutorSingleThreaded
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.OutputStream
import java.net.Socket

@ExtendWith(MockKExtension::class)
class GpgnetProxyTest {
    @MockK
    private lateinit var iceOptions: IceOptions

    private lateinit var sut: GpgnetProxy

    private val testTCPPort = 5000

    @BeforeEach
    fun beforeEach() {
        every { iceOptions.gpgnetPort } returns testTCPPort

        sut = GpgnetProxy(iceOptions)
    }

    @Test
    fun `it should be reusable via start-close-start`() {
        mockExecutorSingleThreaded()

        // First start
        val serverSocket = mockCreateLocalTCPSocket(testTCPPort)
        val socket = mockk<Socket>()

        every { serverSocket.accept() } returns socket
        every { socket.getInputStream() } returns BlockingNoDataInputStream()
        every { socket.getOutputStream() } returns OutputStream.nullOutputStream()

        sut.start()

        assertFalse(sut.closing)

        verify {
            SocketFactory.createLocalTCPSocket(testTCPPort)
            serverSocket.accept()
            socket.getInputStream()
            socket.getOutputStream()
        }

        // First close
        justRun { serverSocket.close() }

        sut.stop()
        assertTrue(sut.closing)

        verify {
            serverSocket.close()
        }

        // Second start
        clearAllMocks()

        mockExecutorSingleThreaded()

        // First start
        val serverSocket2 = mockCreateLocalTCPSocket(testTCPPort)
        val socket2 = mockk<Socket>()

        every { serverSocket2.accept() } returns socket2
        every { socket2.getInputStream() } returns BlockingNoDataInputStream()
        every { socket2.getOutputStream() } returns OutputStream.nullOutputStream()

        sut.start()

        assertFalse(sut.closing)

        verify {
            SocketFactory.createLocalTCPSocket(testTCPPort)
            serverSocket2.accept()
            socket2.getInputStream()
            socket2.getOutputStream()
        }
    }
}