package com.faforever.ice.gpgnet

import com.faforever.ice.util.SocketFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException

private val logger = KotlinLogging.logger {}

class FakeGameClient(
    private val gpgNetPort: Int,
    private val gameLobbyPort: Int,
    private val playerId: Int,
    private val onReceiveGpgnetMessage: (message: GpgnetMessage) -> Unit = {},
) {
    private var proxyLobbyPort: Int? = null
    private val gpgnetSocket = Socket(InetAddress.getLoopbackAddress().hostAddress, gpgNetPort)
    private val faStreamWriter = FaStreamWriter(gpgnetSocket.getOutputStream())
    private val lobbySocket = SocketFactory.createLocalUDPSocket(gameLobbyPort)
    private val receiveLoopThread: Thread?

    fun sendLobbyData(data: ByteArray) {
        lobbySocket.send(DatagramPacket(data, 0, data.size, InetAddress.getLoopbackAddress(), proxyLobbyPort!!))
    }

    fun sendGpgnetMessage(message: GpgnetMessage) {
        faStreamWriter.writeMessage(message)
    }

    fun receiveLobbyData(): ByteArray {
        val buffer = ByteArray(65536)
        val packet = DatagramPacket(buffer, buffer.size)
        lobbySocket.receive(packet)
        return buffer.copyOfRange(0, packet.length)
    }

    init {
        receiveLoopThread = Thread({
            gpgnetSocket.getInputStream().use { inputStream ->
                FaStreamReader(inputStream).use { faStream ->
                    while (true) {
                        val message = faStream.readMessage()
                        when (message) {
                            is GpgnetMessage.JoinGame ->
                                proxyLobbyPort = message.destination.split(":").last().toInt()
                            is GpgnetMessage.ConnectToPeer ->
                                proxyLobbyPort = message.destination.split(":").last().toInt()
                        }
                        onReceiveGpgnetMessage(message)
                        logger.debug { "Received GpgNetMessages >>> $message" }
                    }
                }
            }
        }, "gpgnetClient-Reader-$playerId")
        receiveLoopThread.start()
        receiveLoopThread.setUncaughtExceptionHandler { _, e ->
            if (e is SocketException) {
                // Expected exception when socket is closed.
            } else {
                throw e
            }
        }
    }

    fun stop() {
        receiveLoopThread?.apply {
            interrupt()
        }
        gpgnetSocket.close()
        lobbySocket.close()
        faStreamWriter.close()
    }
}
