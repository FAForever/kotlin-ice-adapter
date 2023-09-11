package com.faforever.ice.game

/**
 * GameState as sent by ForgedAlliance.exe
 */
enum class GameState(val gpgnetString: String) {
    NONE("None"),
    IDLE("Idle"),
    LOBBY("Lobby"),
    LAUNCHING("Launching"),

    // Not in the original game, added by FAF project
    ENDED("Ended"),
    ;

    companion object {
        fun parseGpgnetString(string: String) = entries.first { string == it.gpgnetString }
    }
}
