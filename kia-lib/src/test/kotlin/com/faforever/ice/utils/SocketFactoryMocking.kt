package com.faforever.ice.utils

import com.faforever.ice.util.SocketFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import java.net.ServerSocket

fun mockCreateLocalTCPSocket(port: Int): ServerSocket {
    mockkObject(SocketFactory)
    val mockServerSocket = mockk<ServerSocket>()

    every { SocketFactory.createLocalTCPSocket(port) } returns mockServerSocket

    return mockServerSocket
}
