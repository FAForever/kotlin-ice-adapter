package com.faforever.ice.peering

import org.ice4j.Transport
import org.ice4j.TransportAddress
import java.net.URI

data class CoturnServer(
    val uri: URI,
    val username: String? = null,
    val credential: String? = null,
) {
    fun toUDPTransport() = TransportAddress(uri.host, uri.port, Transport.UDP)
}
