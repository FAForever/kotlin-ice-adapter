package com.faforever.ice.telemetry

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "messageType")
interface OutgoingMessageV1 {
    val messageId: UUID
}

@JvmRecord
data class ConnectToPeer(
    val peerPlayerId: Int,
    val peerName: String,
    val localOffer: Boolean,
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class CoturnServer(
    val region: String,
    val host: String,
    val port: Int,
    val averageRTT: Double,
)

@JvmRecord
data class DisconnectFromPeer(
    val peerPlayerId: Int,
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class RegisterAsPeer(
    val adapterVersion: String,
    val userName: String,
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class UpdateCoturnList(
    val connectedHost: String,
    val knownServers: Collection<CoturnServer>,
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class UpdateGameState(
    val newState: String, // TODO: Use GameState enum
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class UpdateGpgnetState(
    val newState: String, // TODO: Use GameState enum
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class UpdatePeerConnectivity(
    val peerPlayerId: Int,
    val averageRTT: Float,
    val lastReceived: Instant,
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1

@JvmRecord
data class UpdatePeerState(
    val peerPlayerId: Int,
    val iceState: String, // TODO: Use IceState enum
    val localCandidate: String, // TODO: Use CandidateType enum
    val remoteCandidate: String, // TODO: Use CandidateType enum
    override val messageId: UUID = UUID.randomUUID(),
) : OutgoingMessageV1
