package com.faforever.ice

import com.faforever.ice.gpgnet.FakeGpgnetClient
import com.faforever.ice.peering.CoturnServer
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.xml.crypto.Data

class IceAdapterIT {

    @Test
    fun test() {
        Thread(::invokeTest).start()
        while(true) {
            Thread.sleep(1000)
        }
    }

    fun invokeTest() {
        val coturnServers: List<CoturnServer> = listOf(CoturnServer("stun.l.google.com", 19302))

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
            coturnServers = coturnServers
        ).apply { start() }
        val client1 = FakeGpgnetClient(5002, 1)
//        adapter1.hostGame("myMapName")
        adapter1.connectToPeer("User 1", 1, false)

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
            coturnServers = coturnServers
        ).apply { start() }
        val client2 = FakeGpgnetClient(6002, 2)
        adapter2.joinGame("User 2", 2)

        Thread.sleep(1000)

        println("Starting thread to send data...")

        val data = "hello world".encodeToByteArray()


        Thread {
            val listeningSocket = DatagramSocket(8888, InetAddress.getLocalHost())
            val buffer = ByteArray(65536)
            val packet = DatagramPacket(buffer, buffer.size)

            println("Listening on socket 8888")
            listeningSocket.receive(packet)

            println("Receive: $buffer")
        }.start()


        Thread.sleep(1000)
        println("Sending hello world...")
        val sendingSocket = DatagramSocket(9999, InetAddress.getLocalHost())
        sendingSocket.send(DatagramPacket(data, 0, data.size, InetAddress.getLocalHost(), 6001))
    }
}