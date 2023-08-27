package com.faforever.ice.peering

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

private val logger = KotlinLogging.logger {}

/**
 * A UDP endpoint that allows bridging to any other socket (e.g. TCP).
 * All calls are synchronous, so you should wrap them properly with threads or async calls.
 */
class UdpSocketBridge(
    private val name: String = "unnamed",
    bufferSize: Int = 65536
) : Closeable {
    private val objectLock = Object()
    private var started: Boolean = false
    private var closing: Boolean = false
    private var socket: DatagramSocket? = null
    private val buffer = ByteArray(bufferSize)

    val port: Int? = synchronized(objectLock) { socket?.port }

    private fun checkNotClosing() {
        if (closing) throw IOException("Socket closing for UdpSocketBridge $name")
    }

    private fun getOpenSocket(): DatagramSocket {
        check(started) { "UdpSocketBridge $name not started properly" }
        checkNotClosing()
        return checkNotNull(socket)
    }

    @Throws(IOException::class)
    fun start(): Int {
        synchronized(objectLock) {
            check(!started) { "UdpSocketBridge $name already started" }
            checkNotClosing()

            socket = try {
                DatagramSocket(0)
            } catch (e: IOException) {
                logger.error(e) { "Couldn't start UdpSocketBridge $name" }
                throw e
            }

            started = true
            val port = socket!!.localPort
            logger.info { "UdpSocketBridge $name started on port $port" }
            return port
        }
    }

    @Throws(IOException::class)
    fun read(): ByteArray {
        val packet = DatagramPacket(buffer, buffer.size)
        getOpenSocket().receive(packet)
        logger.trace { "$name: Received ${packet.length} bytes" }
        return buffer.copyOfRange(0, packet.length - 1)
    }

    @Throws(IOException::class)
    fun write(data: ByteArray) {
        logger.trace { "$name: Writing ${data.size} bytes" }
        getOpenSocket().send(DatagramPacket(data, 0, data.size - 1))
    }

    override fun close() {
        synchronized(objectLock) {
            if (closing) return
            closing = true
            socket?.close()
        }
    }
}