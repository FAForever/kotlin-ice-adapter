package com.faforever.ice.io

import java.io.IOException
import java.io.InputStream

/**
 * A test input stream that sends no data, but also does not close.
 */
class BlockingNoDataInputStream : InputStream() {
    @Throws(IOException::class)
    override fun read(): Int =
        try {
            // Block indefinitely until data arrives or an error occurs.
            Thread.sleep(Long.MAX_VALUE)
            0
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
}