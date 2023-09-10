package com.faforever.ice.rpc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

/**
 * Handles calls from JsonRPC (the client)
 */
class RPCHandler {
    private val objectMapper = ObjectMapper()

    init {
        objectMapper.registerModule(JavaTimeModule())
    }
    
}
