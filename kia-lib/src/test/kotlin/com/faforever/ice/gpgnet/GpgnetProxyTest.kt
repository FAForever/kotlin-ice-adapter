package com.faforever.ice.gpgnet

import com.faforever.ice.IceOptions
import com.faforever.ice.io.BlockingNoDataInputStream
import com.faforever.ice.util.SocketFactory
import com.faforever.ice.utils.mockCreateLocalTCPSocket
import com.faforever.ice.utils.mockExecutorSingleThreaded
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch

@ExtendWith(MockKExtension::class)
class GpgnetProxyTest {
    @MockK
    private lateinit var iceOptions: IceOptions

    private lateinit var sut: GpgnetProxy

    private val testTCPPort = 5000
    private val testMessage = GpgnetMessage.HostGame("myMap")

    private var onFailure: (Throwable) -> Unit = {}

    @BeforeEach
    fun beforeEach() {
        onFailure = {}
        every { iceOptions.gpgnetPort } returns testTCPPort

        sut = GpgnetProxy(iceOptions) { onFailure(it) }
    }

    @AfterEach
    fun afterEach() {
        sut.close()
    }

    @Test
    fun `it should be reusable via start-close-start`() {
        mockExecutorSingleThreaded()

        // First start
        val serverSocket = mockCreateLocalTCPSocket(testTCPPort)
        val socket = mockk<Socket>()

        every { serverSocket.accept() } returns socket
        justRun { serverSocket.close() }
        every { socket.getInputStream() } returns BlockingNoDataInputStream()
        every { socket.getOutputStream() } returns OutputStream.nullOutputStream()

        sut.start()

        assertFalse(sut.closing)
        assertEquals(GpgnetProxy.ConnectionState.CONNECTED, sut.state)

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
        assertEquals(GpgnetProxy.ConnectionState.DISCONNECTED, sut.state)

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
        justRun { serverSocket2.close() }
        every { socket2.getInputStream() } returns BlockingNoDataInputStream()
        every { socket2.getOutputStream() } returns OutputStream.nullOutputStream()

        sut.start()

        assertFalse(sut.closing)
        assertEquals(GpgnetProxy.ConnectionState.CONNECTED, sut.state)

        await().untilAsserted {
            verify {
                SocketFactory.createLocalTCPSocket(testTCPPort)
                serverSocket2.accept()
                socket2.getInputStream()
                socket2.getOutputStream()
            }
        }
    }

    @Test
    fun `it should be connected once TCP client connects`() {
        val serverSocket = mockCreateLocalTCPSocket(testTCPPort)
        val socket = mockk<Socket>()
        val countDownLatch = CountDownLatch(1)

        every { serverSocket.accept() } answers {
            countDownLatch.await()
            socket
        }
        justRun { serverSocket.close() }
        every { socket.getInputStream() } returns BlockingNoDataInputStream()
        every { socket.getOutputStream() } returns OutputStream.nullOutputStream()

        sut.start()

        await().until { sut.state == GpgnetProxy.ConnectionState.LISTENING }
        countDownLatch.countDown()
        await().until { sut.state == GpgnetProxy.ConnectionState.CONNECTED }
    }

    @Test
    fun `it should receive GPGnet messages`() {
        sut.start()

        Socket(InetAddress.getLoopbackAddress(), testTCPPort).use { tcpSocket ->

            tcpSocket.getOutputStream().use { tcpStream ->
                FaStreamWriter(tcpStream).use { writer ->
                    writer.writeMessage(testMessage)
                }
            }

            val result = sut.receiveMessage()
            assertEquals(testMessage, result)
        }
    }

    @Test
    fun `it should send GPGnet messages`() {
        sut.start()

        Socket(InetAddress.getLoopbackAddress(), testTCPPort).use { tcpSocket ->
            sut.sendGpgnetMessage(testMessage)

            val result = tcpSocket.getInputStream().use { tcpStream ->
                FaStreamReader(tcpStream).use { writer ->
                    writer.readMessage()
                }
            }
            assertEquals(testMessage, result)
        }
    }
}
