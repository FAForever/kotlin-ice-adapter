package com.faforever.ice.gpgnet

import com.google.common.io.LittleEndianDataOutputStream
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

private val logger = KotlinLogging.logger {}

/**
 * Not thread safe, as designed to be only accessed from a single thread
 */
internal class FaStreamWriter(outputStream: OutputStream): Closeable {
    private val outputStream = LittleEndianDataOutputStream(BufferedOutputStream(outputStream))

    init {
        logger.debug { "FaStreamWriter opened" }
    }

    private fun writeString(s: String) {
        outputStream.writeInt(s.length)
        outputStream.write(s.toByteArray(FaStreamConstants.charset))
    }

    private fun writeArgs(args: List<Any>) {
        outputStream.writeInt(args.size)
        for ((index, arg) in args.withIndex()) {
            when(arg) {
                is Double -> {
                    outputStream.writeByte(FaStreamConstants.FieldTypes.INT)
                    outputStream.writeInt(arg.toInt())
                }
                is Int -> {
                    outputStream.writeByte(FaStreamConstants.FieldTypes.INT)
                    outputStream.writeInt(arg.toInt())
                }
                is String -> {
                    outputStream.writeByte(FaStreamConstants.FieldTypes.STRING)
                    writeString(arg)
                }
                else -> {
                    logger.warn { "Unexpected type ${arg.javaClass} in arguments (index $index)" }
                }
            }
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun writeMessage(gpgnetMessage: GpgnetMessage) {
        logger.trace { "Writing message: $gpgnetMessage" }

        writeString(gpgnetMessage.command)
        writeArgs(gpgnetMessage.args)
        outputStream.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        logger.debug { "Closing FaStreamWriter" }
        outputStream.close()
    }
}