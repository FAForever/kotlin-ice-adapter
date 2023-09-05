package com.faforever.ice.ice4j

import com.faforever.ice.ice4j.CandidatesMessage.CandidateDescriptor
import org.ice4j.Transport
import org.ice4j.TransportAddress
import org.ice4j.ice.Agent
import org.ice4j.ice.CandidateType
import org.ice4j.ice.Component
import org.ice4j.ice.IceMediaStream
import org.ice4j.ice.RemoteCandidate
import java.util.UUID

object CandidateUtil {
    fun packCandidates(
        sourceId: Int,
        destinationId: Int,
        agent: Agent,
        component: Component,
        allowHost: Boolean,
        allowReflexive: Boolean,
        allowRelay: Boolean,
    ) = CandidatesMessage(
        sourceId = sourceId,
        destinationId = destinationId,
        password = agent.localPassword,
        ufrag = agent.localUfrag,
        candidates = component.localCandidates
            .filter { isAllowedCandidate(allowHost, allowReflexive, allowRelay, it.type) }
            .map {
                CandidateDescriptor(
                    foundation = it.foundation,
                    protocol = it.transportAddress.transport.toString(),
                    priority = it.priority,
                    ip = it.transportAddress.hostAddress,
                    port = it.transportAddress.port,
                    type = it.type,
                    generation = agent.generation,
                    id = UUID.randomUUID().toString(),
                    relAddr = it.relatedAddress?.hostAddress,
                    relPort = it.relatedAddress?.port ?: 0,
                )
            }
            .sorted(),
    )

    fun unpackCandidates(
        remoteCandidatesMessage: CandidatesMessage,
        agent: Agent,
        component: Component,
        mediaStream: IceMediaStream,
        allowHost: Boolean,
        allowReflexive: Boolean,
        allowRelay: Boolean,
    ) {
        // Set candidates
        mediaStream.remotePassword = remoteCandidatesMessage.password
        mediaStream.remoteUfrag = remoteCandidatesMessage.ufrag
        remoteCandidatesMessage.candidates
            .sorted() // just in case some ICE adapter implementation did not sort it yet
            .filter { isAllowedCandidate(allowHost, allowReflexive, allowRelay, it.type) }
            .filter { it.generation == agent.generation && it.ip != null && it.port > 0 }
            .map { remoteCandidatePacket ->
                val mainAddress = TransportAddress(
                    remoteCandidatePacket.ip,
                    remoteCandidatePacket.port,
                    Transport.parse(remoteCandidatePacket.protocol.lowercase()),
                )
                val relatedCandidate =
                    if (remoteCandidatePacket.relAddr != null && remoteCandidatePacket.relPort > 0) {
                        component.findRemoteCandidate(
                            TransportAddress(
                                remoteCandidatePacket.relAddr,
                                remoteCandidatePacket.relPort,
                                Transport.parse(remoteCandidatePacket.protocol.lowercase()),
                            ),
                        )
                    } else {
                        null
                    }

                RemoteCandidate(
                    mainAddress,
                    component,
                    remoteCandidatePacket.type, // Expected to not return LOCAL or STUN (old names for host and srflx)
                    remoteCandidatePacket.foundation,
                    remoteCandidatePacket.priority,
                    relatedCandidate,
                )
            }
            .forEach(component::addRemoteCandidate)
    }

    private fun isAllowedCandidate(
        allowHost: Boolean,
        allowReflexive: Boolean,
        allowRelay: Boolean,
        candidateType: CandidateType,
    ) = when (candidateType) {
        CandidateType.HOST_CANDIDATE -> allowHost
        CandidateType.SERVER_REFLEXIVE_CANDIDATE,
        CandidateType.PEER_REFLEXIVE_CANDIDATE,
        -> allowReflexive
        CandidateType.RELAYED_CANDIDATE -> allowRelay

        // Candidate types LOCAL and STUN can never occur as they are deprecated and not used
        CandidateType.LOCAL_CANDIDATE,
        CandidateType.STUN_CANDIDATE,
        -> throw IllegalStateException("Deprecated candidate type: $candidateType")
    }
}
