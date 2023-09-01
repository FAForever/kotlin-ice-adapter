package com.faforever.ice.util

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlin.random.Random

/**
 * Util class, which helps to build sockets and also allows mocking them for testing
 */
object SocketFactory {
    fun createLocalUDPSocket(portFrom: Int = 40_000, portTo: Int = 60_000, maxAttempts: Int=10): DatagramSocket =
        // since we choose a random port, we'll try again in case the port is already in use
        Failsafe.with(
            RetryPolicy.builder<DatagramSocket>()
                .handle(SocketException::class.java)
                .withMaxAttempts(maxAttempts)
                .build()
        ).get { _ ->
            DatagramSocket(Random.nextInt(portFrom, portTo), InetAddress.getLoopbackAddress())
        }

    fun createLocalUDPSocket(port: Int): DatagramSocket = DatagramSocket(port, InetAddress.getLoopbackAddress())

    fun createLocalTCPSocket(port: Int): ServerSocket = ServerSocket(port, 20, InetAddress.getLoopbackAddress())
}