package com.faforever.ice.game

/**
 * Lobby init mode, set by the client, transmitted to game via CreateLobby
 */
enum class LobbyInitMode(val faId: Int) {
    NORMAL(0),
    AUTO(1),
    ;

    companion object {
        fun parseFromFaId(faId: Int) = LobbyInitMode.entries.first { faId == it.faId }
    }
}
