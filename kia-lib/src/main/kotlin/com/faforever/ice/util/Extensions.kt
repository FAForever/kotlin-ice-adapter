package com.faforever.ice.util

import java.nio.ByteBuffer

fun <T : Any> T.isIn(vararg items: T) = items.contains(this)
fun <T : Any> T.isNotIn(vararg items: T) = !items.contains(this)

fun Long.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
    buffer.putLong(this)
    return buffer.array()
}
