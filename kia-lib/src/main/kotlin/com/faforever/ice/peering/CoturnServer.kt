package com.faforever.ice.peering

import org.ice4j.Transport
import org.ice4j.TransportAddress
import java.net.URI

data class CoturnServer(
    val uri: URI,
    val username: String? = null,
    val credential: String? = null,
) {
    companion object {
        const val DEFAULT_PORT = 3478
    }

    fun toUDPTransport() = TransportAddress(
        uri.host,
        if (uri.port == -1) DEFAULT_PORT else uri.port,
        Transport.UDP,
    )
}
