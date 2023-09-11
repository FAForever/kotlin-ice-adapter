package com.faforever.ice.gpgnet

import com.faforever.ice.game.GameState
import com.faforever.ice.game.LobbyInitMode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.stream.Stream

class FaStreamIT {
    companion object {
        @JvmStatic
        private fun buildGpgnetMessages(): Stream<GpgnetMessage> = Stream.of(
            GpgnetMessage.CreateLobby(LobbyInitMode.AUTO, 6012, "Brutus5000", 5000),
            GpgnetMessage.HostGame("someMap"),
            GpgnetMessage.JoinGame("remotePlayer", 6000, "somewhere"),
            GpgnetMessage.ConnectToPeer("remotePlayer", 6000, "somewhere"),
            GpgnetMessage.DisconnectFromPeer(6000),
            GpgnetMessage.GameState(GameState.LOBBY),
            GpgnetMessage.GameEnded(),
        )
    }

    @ParameterizedTest
    @MethodSource("buildGpgnetMessages")
    fun `FaStreamReader should be able to read messages from FaStreamWriter`(message: GpgnetMessage) {
        val writtenData: ByteArray = ByteArrayOutputStream().use { out ->
            FaStreamWriter(out).use { writer ->
                writer.writeMessage(message)
                out.toByteArray()
            }
        }

        val readMessage: GpgnetMessage = ByteArrayInputStream(writtenData).use { input ->
            FaStreamReader(input).use { reader ->
                reader.readMessage()
            }
        }

        Assertions.assertEquals(message, readMessage)
    }
}
