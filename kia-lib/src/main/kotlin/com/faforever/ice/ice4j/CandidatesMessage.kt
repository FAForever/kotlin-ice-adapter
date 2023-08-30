package com.faforever.ice.ice4j

import org.ice4j.ice.CandidateType

@JvmRecord
data class CandidatesMessage(
    val sourceId: Int,
    val destinationId: Int,
    val password: String,
    val ufrag: String,
    val candidates: List<CandidateDescriptor>
) {

    @JvmRecord
    data class CandidateDescriptor(
        val foundation: String,
        val protocol: String,
        val priority: Long,
        val ip: String?,
        val port: Int,
        val type: CandidateType,
        val generation: Int,
        val id: String,
        val relAddr: String?,
        val relPort: Int
    ) : Comparable<CandidateDescriptor> {
        override operator fun compareTo(other: CandidateDescriptor) = (other.priority - priority).toInt()
    }

}


