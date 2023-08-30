package com.faforever.ice.peering

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

private val logger = KotlinLogging.logger {}

/**
 * A unidirectional bridge that receives data via data on a UDP socket and forwards it wherever desired
 */
class UdpSocketBridge(
    private val forwardTo: (ByteArray) -> Unit,
    private val name: String = "unnamed",
    bufferSize: Int = 65536
) : Closeable {
    private val objectLock = Object()
    private var started: Boolean = false
    private var closing: Boolean = false
    private var socket: DatagramSocket? = null
    private var readingThread: Thread? = null
    private val buffer = ByteArray(bufferSize)

    val port: Int? = synchronized(objectLock) { socket?.port }

    private fun checkNotClosing() {
        if (closing) throw IOException("Socket closing for UdpSocketBridge $name")
    }

    @Throws(IOException::class)
    fun start() {
        synchronized(objectLock) {
            check(!started) { "UdpSocketBridge $name already started" }
            checkNotClosing()

            socket = try {
                DatagramSocket(0)
            } catch (e: IOException) {
                logger.error(e) { "Couldn't start UdpSocketBridge $name" }
                throw e
            }

            val port = socket!!.localPort

            readingThread = Thread{ readAndForwardLoop()}
                .apply { start() }

            started = true
            logger.info { "UdpSocketBridge $name started on port $port" }
        }
    }

    @Throws(IOException::class)
    fun readAndForwardLoop() {
        logger.info { "UdpSocketBridge $name is forwarding messages now" }

        while(true) {
            synchronized(objectLock) {
                if (closing) return
            }

            val packet = DatagramPacket(buffer, buffer.size)
            socket!!.receive(packet)
            logger.trace { "$name: Forwarding ${packet.length} bytes" }
            forwardTo(buffer.copyOfRange(0, packet.length - 1))
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