package com.faforever.ice.peering

import com.faforever.ice.util.SocketFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.HexFormat

private val logger = KotlinLogging.logger {}

/**
 * Each remote player has an ice4j-socket that is not necessarily using UDP. But the game only communicates lobby data
 * via UDP.
 *
 * Thus, we use the UdpSocketBridge to forward UDP message in both direction (game<->ice), with ICE being any kind of
 * socket.
 *
 * Note that there are 3 sockets involved:
 * * The game process UDP socket (aka "lobby")
 * * The ice4j peer socket (UDP or TCP)
 * * The UDP socket of this bridge itself
 *
 * But we only have a handle for our self created UDP bridge socket.
 *
 * The game sends to the UDP socket of this bridge. Thus, all messages received from this bridge UDP socket are always
 * from the game. These messages need to be send to the ICE4j socket. We delegate this back to whoever has the handle
 * to it (via injected method in the constructor).
 *
 * The ICE4J socket does not send directly to our UDP socket bridge. Instead, an external component will pass us the
 * message by calling our method. We then forward this message via our UDP bridge socket to the game.
 *
 */
class UdpSocketBridge(
    private val lobbyPort: Int,
    private val forwardToIce: (ByteArray) -> Unit,
    private val name: String = "unnamed",
    bufferSize: Int = 65536,
) : Closeable {

    private val objectLock = Object()

    @Volatile
    var started: Boolean = false
        private set

    val bridgePort: Int? get() = bridgeSocket?.localPort

    @Volatile
    var closing: Boolean = false
        private set

    private var bridgeSocket: DatagramSocket? = null
    private var readingThread: Thread? = null
    private val buffer = ByteArray(bufferSize)

    private fun checkNotClosing() {
        if (closing) throw IOException("Socket closing for UdpSocketBridge $name")
    }

    @Throws(IOException::class)
    fun start() {
        synchronized(objectLock) {
            check(!started) { "UdpSocketBridge $name already started" }
            checkNotClosing()

            bridgeSocket = try {
                SocketFactory.createLocalUDPSocket()
            } catch (e: IOException) {
                logger.error(e) { "Couldn't start UdpSocketBridge $name" }
                throw e
            }

            val port = bridgeSocket!!.localPort

            readingThread = Thread { gameReadAndForwardLoop() }
                .apply { start() }

            started = true
            logger.info { "UdpSocketBridge $name started on port $port" }
        }
    }

    fun forwardToGame(dataPacket: GameDataPacket) {
        check(started) { "UdpSocketBridge $name not started yet" }
        checkNotClosing()
        logger.trace { "Sending to faSocket @$lobbyPort: ${HexFormat.of().formatHex(dataPacket.data)}" }

        val packet = DatagramPacket(dataPacket.data, 0, dataPacket.data.size, InetAddress.getLoopbackAddress(), lobbyPort)
        bridgeSocket!!.send(packet)
    }

    @Throws(IOException::class)
    private fun gameReadAndForwardLoop() {
        logger.info { "UdpSocketBridge $name is forwarding messages from game to ice socket now" }

        while (true) {
            synchronized(objectLock) {
                if (closing) return
            }

            val packet = DatagramPacket(buffer, buffer.size)

            try {
                bridgeSocket!!.receive(packet)
                logger.trace { "$name: Forwarding ${packet.length} bytes" }
                forwardToIce(buffer.copyOfRange(0, packet.length))
            } catch (e: Exception) {
                if (closing) {
                    return
                } else {
                    throw e
                }
            }
        }
    }

    override fun close() {
        synchronized(objectLock) {
            if (closing) return
            readingThread?.interrupt()
            closing = true
            started = false
            bridgeSocket?.close()
        }
    }
}
