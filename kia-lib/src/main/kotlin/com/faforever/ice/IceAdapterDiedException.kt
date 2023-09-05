package com.faforever.ice

/**
 * This error indicated that the ICE adapter basically died.
 */
class IceAdapterDiedException(message: String, cause: Exception? = null) : Exception(message, cause)
