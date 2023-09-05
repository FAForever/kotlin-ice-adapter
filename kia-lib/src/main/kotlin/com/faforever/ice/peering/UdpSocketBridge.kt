package com.faforever.ice.peering

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

private val logger = KotlinLogging.logger {}

/**
 * Each remote player has an ice4j-socket that is not necessarily using UDP. But the game only sends lobby data via UDP.
 *
 * Thus, we use the UdpSocketBridge to forward UDP message to any kind of socket.
 */
class UdpSocketBridge(
    private val forwardTo: (ByteArray) -> Unit,
    private val name: String = "unnamed",
    private val port: Int,
    bufferSize: Int = 65536,
) : Closeable {
    private val objectLock = Object()
    private var started: Boolean = false
    private var closing: Boolean = false
    private var socket: DatagramSocket? = null
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

            socket = try {
                DatagramSocket(port)
            } catch (e: IOException) {
                logger.error(e) { "Couldn't start UdpSocketBridge $name" }
                throw e
            }

            val port = socket!!.localPort

            readingThread = Thread { readAndForwardLoop() }
                .apply { start() }

            started = true
            logger.info { "UdpSocketBridge $name started on port $port" }
        }
    }

    @Throws(IOException::class)
    fun readAndForwardLoop() {
        logger.info { "UdpSocketBridge $name is forwarding messages now" }

        while (true) {
            synchronized(objectLock) {
                if (closing) return
            }

            val packet = DatagramPacket(buffer, buffer.size)
            socket!!.receive(packet)
            logger.trace { "$name: Forwarding ${packet.length} bytes" }
            forwardTo(buffer.copyOfRange(0, packet.length))
        }
    }

    override fun close() {
        synchronized(objectLock) {
            if (closing) return
            readingThread?.interrupt()
            closing = true
            socket?.close()
        }
    }
}
