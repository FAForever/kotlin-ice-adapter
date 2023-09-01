package com.faforever.ice.gpgnet

import com.faforever.ice.IceAdapterDiedException
import com.faforever.ice.IceOptions
import com.faforever.ice.util.ExecutorHolder
import com.faforever.ice.util.ReusableComponent
import com.faforever.ice.util.SocketFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ScheduledExecutorService

private val logger = KotlinLogging.logger {}

/**
 * A proxy between the ForgedAlliance.exe game and the FAF lobby server,
 * allowing to listen, intercept and add messages to fulfill ICE connectivity
 */
class GpgnetProxy(
    iceOptions: IceOptions
) : ReusableComponent, Closeable {
    companion object {
        private val sharedExecutor: ScheduledExecutorService get() = ExecutorHolder.executor
    }

    private val gpgnetPort = iceOptions.gpgnetPort

    private val inQueue: BlockingQueue<GpgnetMessage> = ArrayBlockingQueue(32, true)
    private val outQueue: BlockingQueue<GpgnetMessage> = ArrayBlockingQueue(32, true)

    private val objectLock = Object()
    var closing: Boolean = false
        private set
    private var socket: ServerSocket? = null
    private var gameReaderThread: Thread? = null
    private var gameWriterThread: Thread? = null

    fun sendGpgnetMessage(message: GpgnetMessage) = inQueue.add(message)

    fun receiveMessage() = outQueue.take()

    override fun start() {
        synchronized(objectLock) {
            closing = false
            inQueue.clear()
            outQueue.clear()

            socket = try {
                SocketFactory.createLocalTCPSocket(gpgnetPort)
            } catch (e: IOException) {
                logger.error(e) { "Couldn't start GpgnetProxy on port $gpgnetPort" }
                close()
                throw IceAdapterDiedException("Couldn't start GpgnetProxy on port $gpgnetPort", e)
            }
        }

        sharedExecutor.submit { setupSocketToGameInstance() }

        logger.info { "GpgnetProxy started" }
    }

    private fun setupSocketToGameInstance() {
        // A while loop seems unnecessary overhead, we do not accept multiple connections from different games,
        // nor do we expect multiple connection attempts from the same game
        try {
            val socket = requireNotNull(socket).accept()

            gameReaderThread = Thread(
                { readMessagesIntoQueue(socket) },
                "readGpgnetMessagesFromGame"
            ).apply { start() }

            gameWriterThread = Thread(
                { writeMessagesFromQueue(socket) },
                "writeGpgnetMessagesToGame"
            ).apply { start() }

            logger.info { "Connection to game instance established" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to establish connection to game instance" }
            close()
            throw IceAdapterDiedException("Failed to establish connection to game instance", e)
        }
    }

    private fun readMessagesIntoQueue(socket: Socket) {
        socket.getInputStream().use { inputStream ->
            FaStreamReader(inputStream).use { reader ->
                logger.debug { "Ready to read messages" }

                while (!closing) {
                    try {
                        val message = reader.readMessage()
                        outQueue.put(message)
                    } catch (e: InterruptedException) {
                        if (closing) return
                        else throw e
                    }
                }
            }
        }
    }

    private fun writeMessagesFromQueue(socket: Socket) {
        socket.getOutputStream().use { outputStream ->
            FaStreamWriter(outputStream).use { writer ->
                logger.debug { "Ready to write messages" }

                while (!closing) {
                    try {
                        val message = inQueue.take()
                        writer.writeMessage(message)
                    } catch (e: InterruptedException) {
                        if (closing) return
                        else throw e
                    }
                }
            }
        }
    }

    override fun stop() {
        close()
    }

    override fun close() {
        logger.debug { "GpgnetProxy closing" }

        synchronized(objectLock) {
            if (closing) return

            closing = true

            gameReaderThread?.apply {
                interrupt()
                logger.debug { "gameReaderThread interrupted" }
            } ?: run {
                logger.warn { "No gameReaderThread available for closing" }
            }

            gameWriterThread?.apply {
                interrupt()
                logger.debug { "gameWriterThread interrupted" }
            } ?: run {
                logger.warn { "No gameWriterThread available for closing" }
            }

            socket?.close()
        }

        logger.info { "GpgnetProxy closed" }
    }
}