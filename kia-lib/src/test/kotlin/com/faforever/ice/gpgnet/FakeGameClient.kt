package com.faforever.ice.gpgnet

import com.faforever.ice.util.SocketFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.Socket

private val logger = KotlinLogging.logger {}

class FakeGameClient(
    private val gpgNetPort: Int,
    private val gameLobbyPort: Int,
    private val playerId: Int,
) {
    private var proxyLobbyPort: Int? = null
    private val gpgnetSocket = Socket(InetAddress.getLoopbackAddress().hostAddress, gpgNetPort)
    private val lobbySocket = SocketFactory.createLocalUDPSocket(gameLobbyPort)

    fun sendLobbyData(data: ByteArray) {
        lobbySocket.send(DatagramPacket(data, 0, data.size, InetAddress.getLoopbackAddress(), proxyLobbyPort!!))
    }

    fun receiveLobbyData(): ByteArray {
        val buffer = ByteArray(65536)
        val packet = DatagramPacket(buffer, buffer.size)
        lobbySocket.receive(packet)
        return buffer.copyOfRange(0, packet.length)
    }

    init {
        Thread({
            gpgnetSocket.getInputStream().use { inputStream ->
                FaStreamReader(inputStream).use { faStream ->
                    while (true) {
                        val message = faStream.readMessage()
                        when (message) {
                            is GpgnetMessage.JoinGame -> proxyLobbyPort = message.destination.split(":").last().toInt()
                            is GpgnetMessage.ConnectToPeer -> proxyLobbyPort = message.destination.split(":").last().toInt()
                        }
                        logger.debug { "Received GpgNetMessages >>> $message" }
                    }
                }
            }
        }, "gpgnetClient-Reader-$playerId").start()
    }
}
