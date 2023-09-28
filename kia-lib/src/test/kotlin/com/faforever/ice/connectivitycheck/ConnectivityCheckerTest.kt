package com.faforever.ice.connectivitycheck

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class ConnectivityCheckerTest {
    @MockK
    private lateinit var mockClock: Clock

    private lateinit var sut: ConnectivityChecker

    @MockK(relaxed = true)
    private lateinit var connectivityCheckable: ConnectivityCheckable

    @BeforeEach
    fun beforeEach()  {
        every { mockClock.instant() } returns Instant.now()
        every { mockClock.zone } returns ZoneOffset.UTC

        sut = ConnectivityChecker(
            clock = mockClock,
            connectionAliveSeconds = 2,
            connectionEchoPendingSeconds = 3,
            connectionDeadThresholdSeconds = 5,
        )
    }

    @Test
    fun `it should be reusable via start-close-start`() {
        assertFalse(sut.running)

        sut.start()
        assertTrue(sut.running)

        sut.stop()
        assertFalse(sut.running)

        sut.start()
        assertTrue(sut.running)
    }

    @Test
    fun `it should fail to register a ConnectivityCheckable before initializing`() {
        assertThrows<IllegalStateException> {
            sut.registerPlayer(connectivityCheckable)
        }
    }

    @Test
    fun `it should fail to register a ConnectivityCheckable twice`() {
        sut.start()

        sut.registerPlayer(connectivityCheckable)

        assertThrows<IllegalStateException> {
            sut.registerPlayer(connectivityCheckable)
        }
    }

    @Test
    fun `it should be able to register-disconnect-reregister a ConnectivityCheckable`() {
        sut.start()

        val handler = sut.registerPlayer(connectivityCheckable)
        handler.disconnected()

        val handler2 = sut.registerPlayer(connectivityCheckable)
        handler2.disconnected()

        assertNotEquals(handler, handler2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `it should do nothing on connection alive`(isOfferer: Boolean) {
        every { connectivityCheckable.isOfferer } returns isOfferer

        sut.start()

        val handler = sut.registerPlayer(connectivityCheckable)

        assertEquals(ConnectivityCheckHandler.Status.ALIVE, handler.status)
    }

    @Test
    fun `it should request echos for offerers`() {
        val now = Instant.now()
        every { connectivityCheckable.isOfferer } returns true
        every { mockClock.instant() } returns now

        sut.start()

        val handler = sut.registerPlayer(connectivityCheckable)
        assertEquals(ConnectivityCheckHandler.Status.ALIVE, handler.status)

        every { mockClock.instant() } returns now.plusSeconds(6)
        await().until { handler.status == ConnectivityCheckHandler.Status.ECHO_REQUIRED }
        await().untilAsserted { verify { connectivityCheckable.sendEcho() } }

        handler.echoReceived()
        assertEquals(ConnectivityCheckHandler.Status.ALIVE, handler.status)
    }

    @Test
    fun `it should respond to echos for non-offerers`() {
        val now = Instant.now()
        every { connectivityCheckable.isOfferer } returns false
        every { mockClock.instant() } returns now

        sut.start()

        val handler = sut.registerPlayer(connectivityCheckable)
        assertEquals(ConnectivityCheckHandler.Status.ALIVE, handler.status)

        every { mockClock.instant() } returns now.plusSeconds(6)
        await().until { handler.status == ConnectivityCheckHandler.Status.ECHO_REQUIRED }

        handler.echoReceived()
        assertEquals(ConnectivityCheckHandler.Status.ALIVE, handler.status)

        verify(exactly = 0) { connectivityCheckable.sendEcho() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `it should disconnect on timeout`(isOfferer: Boolean) {
        val now = Instant.now()
        every { mockClock.instant() } returns now
        every { connectivityCheckable.isOfferer } returns isOfferer

        sut.start()

        val handler = sut.registerPlayer(connectivityCheckable)
        assertEquals(ConnectivityCheckHandler.Status.ALIVE, handler.status)

        every { mockClock.instant() } returns now.plusSeconds(60)
        await().until { handler.status == ConnectivityCheckHandler.Status.DEAD }
        await().untilAsserted { verify { connectivityCheckable.onConnectionLost() } }
    }
}