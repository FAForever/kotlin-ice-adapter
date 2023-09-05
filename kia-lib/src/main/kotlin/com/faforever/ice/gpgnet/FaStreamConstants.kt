@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.faforever.ice.gpgnet

/**
 * All knowledge about the FA binary protocol in one place
 */
internal object FaStreamConstants {
    object FieldTypes {
        const val INT = 0
        const val STRING = 1
        const val FOLLOWUP_STRING = 2
    }
    const val DELIMITER = "\b"
    const val TABULATOR = "/t"
    const val LINEBREAK = "/n"

    val charset = Charsets.UTF_8

    fun parseString(input: String) = input
        .replace(TABULATOR, "\t")
        .replace(LINEBREAK, "\n")
}
