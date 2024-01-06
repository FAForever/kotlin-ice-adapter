package com.faforever.ice

@JvmRecord
data class IceOptions(
    val accessToken: String,
    val userId: Int,
    val userName: String,
    val gameId: Int,
    val forceRelay: Boolean,
    val rpcPort: Int,
    val lobbyPort: Int,
    val gpgnetPort: Int,
    val icebreakerBaseUrl: String,
    val telemetryServer: String,
)
