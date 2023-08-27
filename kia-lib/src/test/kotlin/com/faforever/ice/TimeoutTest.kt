package com.faforever.ice

import dev.failsafe.Failsafe
import dev.failsafe.Timeout
import org.ice4j.ice.Agent
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class TimeoutTest {
    @Test
    fun test() {
        println(LocalDateTime.now())
        val x = Failsafe
            .with(
                Timeout.builder<String>(Duration.ofSeconds(5L))
                    .withInterrupt()
                    .build()
            )
            .onComplete { println("${LocalDateTime.now()}: onComplete $it") }
            .onSuccess { println("${LocalDateTime.now()}: onSuccess $it") }
            .onFailure { println("${LocalDateTime.now()}: onFailure $it") }
            .getAsync { _ ->
                Thread.sleep(12000)
                "bla"
            }
        println("${LocalDateTime.now()}: $x")

        Thread.sleep(10000)
        println("${LocalDateTime.now()}: $x")
    }

    fun testIce4j() {
        val agent1 = Agent().apply {
            isControlling = true
        }
        val mediaStream = agent1.createMediaStream("faData")
    }
}