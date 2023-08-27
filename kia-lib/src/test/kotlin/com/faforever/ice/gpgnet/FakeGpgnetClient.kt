package com.faforever.ice.gpgnet

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetAddress
import java.net.Socket

private val logger = KotlinLogging.logger {}

class FakeGpgnetClient(
    port: Int,
    playerId: Int,
) {
    val serverSocket = Socket(InetAddress.getLocalHost(), port)

    init {
        Thread( {
            serverSocket.getInputStream().use { inputStream ->
                val buffer = ByteArray(1024) // Buffer to read data in chunks
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val data = buffer.copyOfRange(0, bytesRead)
                    val text = String(data)
                    logger.debug {"Received data >>> $text" }
                }

                inputStream.close()
                logger.info { "InputStream closed" }
            }
        }, "gpgnetClient-Reader-$playerId").start()
    }
}