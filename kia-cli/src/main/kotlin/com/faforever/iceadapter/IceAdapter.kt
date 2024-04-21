package com.faforever.iceadapter

import com.faforever.ice.KiaApplication

/**
 * Class for compatibility with current FAF client which expects java ice adapter main class
 */
object IceAdapter {
    @JvmStatic
    fun main(args: Array<String>) {
        KiaApplication.main(args)
    }
}
