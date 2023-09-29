package com.faforever.ice.peering

import org.ice4j.Transport
import org.ice4j.TransportAddress

data class CoturnServer(
    val hostname: String,
    val port: Int,
    val username: String? = null,
    val credential: String? = null,
) {
    fun toTCPTransport() = TransportAddress(hostname, port, Transport.TCP)
}
