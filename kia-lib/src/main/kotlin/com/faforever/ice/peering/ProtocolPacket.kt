package com.faforever.ice.peering

import com.faforever.ice.util.toByteArray

sealed interface ProtocolPacket {
    val prefix: Byte
    fun toByteArray(): ByteArray

    fun buildWireData() = byteArrayOf(prefix) + toByteArray()
}

data class GameDataPacket(private val gameData: ByteArray) : ProtocolPacket {
    companion object {
        const val PREFIX = 'd'.code.toByte()

        fun fromWire(wireData: ByteArray) = GameDataPacket(wireData.copyOfRange(1, wireData.lastIndex))
    }

    override val prefix = PREFIX
    override fun toByteArray() = gameData
}

data class EchoPacket(private val timestamp: Long = System.currentTimeMillis()) : ProtocolPacket {
    companion object {
        const val PREFIX = 'e'.code.toByte()
    }

    override val prefix = PREFIX
    override fun toByteArray() = timestamp.toByteArray()
}
