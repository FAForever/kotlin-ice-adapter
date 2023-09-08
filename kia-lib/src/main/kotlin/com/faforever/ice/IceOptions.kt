package com.faforever.ice

@JvmRecord
data class IceOptions(
    val userId: Int,
    val userName: String,
    val gameId: Int,
    val forceRelay: Boolean,
    val lobbyPort: Int,
    val gpgnetPort: Int,
    val telemetryServer: String,
)
