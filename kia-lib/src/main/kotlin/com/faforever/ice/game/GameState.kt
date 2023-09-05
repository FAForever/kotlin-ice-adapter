package com.faforever.ice.game

/**
 * GameState as sent by ForgedAlliance.exe
 */
enum class GameState {
    NONE,
    IDLE,
    LOBBY,
    LAUNCHING,

    // Not in the original game, added by FAF project
    ENDED,
}
