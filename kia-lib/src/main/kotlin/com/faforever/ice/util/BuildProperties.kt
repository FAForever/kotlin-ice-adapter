package com.faforever.ice.util

import java.util.Properties

/**
 * Getter for properties defined at build time in the generated build.properties resource file.
 */
object BuildProperties {

    private val buildProperties by lazy {
        Properties().also { properties ->
            javaClass.getResourceAsStream("/build.properties")?.let {
                properties.load(it)
            }
        }
    }

    val iceAdapterVersion by lazy {
        buildProperties.getProperty("iceAdapterVersion") ?: "undefined"
    }
}
