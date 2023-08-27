package com.faforever.ice.peering

import org.ice4j.Transport
import org.ice4j.TransportAddress

data class CoturnServer (
    val hostname: String,
    val port: Int
) {
    fun toTCPTransport() = TransportAddress(hostname, port, Transport.TCP)
}