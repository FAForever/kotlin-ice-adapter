package com.faforever.ice.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

object ExecutorHolder {

    private var _executor: ScheduledExecutorService? = null

    var executor: ScheduledExecutorService get() {
        val executor = _executor ?: Executors.newScheduledThreadPool(2)
        if (_executor == null) {
            _executor = executor
        }
        return executor
    } set(value) { _executor = value }
}
