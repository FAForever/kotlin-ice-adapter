package com.faforever.ice.utils

import com.faforever.ice.util.ExecutorHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService

fun mockExecutorSingleThreaded() {
    mockkObject(ExecutorHolder)
    val submittedTask = slot<Runnable>()
    val mockExecutorService = mockk<ScheduledExecutorService>() {
        every { submit(capture(submittedTask)) } answers {
            submittedTask.captured.run()
            CompletableFuture.completedFuture(Unit)
        }
    }
    every { ExecutorHolder.executor } returns mockExecutorService
}
