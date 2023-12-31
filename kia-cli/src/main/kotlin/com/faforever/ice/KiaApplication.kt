package com.faforever.ice

import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.rpc.RpcService
import com.faforever.ice.util.SocketFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

@Command(
    name = "kotlin-ice-adapter",
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    description = ["An ice (RFC 5245) based network bridge between FAF client and ForgedAlliance.exe"],
)
class KiaApplication : Callable<Int> {

    @Option(names = ["--user-id", "--id"], required = true, description = ["set the ID of the local player"])
    private var userId: Int = 0

    @Option(names = ["--game-id"], required = true, description = ["set the ID of the game"])
    private var gameId: Int = 0

    @Option(
        names = ["--user-name", "--login"],
        required = true,
        description = ["set the login of the local player e.g. \"Rhiza\""],
    )
    private lateinit var userName: String

    @Option(names = ["--force-relay"], description = ["force the usage of relay candidates only"])
    private var forceRelay: Boolean = false

    @Option(names = ["--rpc-port"], defaultValue = "7236", description = ["set the port of internal JSON-RPC server"])
    private var rpcPort: Int = 0

    @Option(names = ["--gpgnet-port"], defaultValue = "0", description = ["set the port of internal GPGNet server"])
    private var gpgnetPort: Int = 0

    @Option(
        names = ["--lobby-port"],
        defaultValue = "0",
        description = ["set the port the game lobby should use for incoming UDP packets from the PeerRelay"],
    )
    private var lobbyPort: Int = 0

    @Option(
        names = ["--telemetry-server"],
        defaultValue = "wss://ice-telemetry.faforever.com",
        description = ["Telemetry server to connect to"],
    )
    private var telemetryServer: String = ""

    private var closing: Boolean = false

    private lateinit var iceOptions: IceOptions
    private lateinit var iceAdapter: IceAdapter
    private lateinit var rpcService: RpcService

    private fun onConnectionStateChanged(newState: String) = rpcService.onConnectionStateChanged(newState)

    private fun onGpgNetMessageReceived(message: GpgnetMessage) = rpcService.onGpgNetMessageReceived(message)

    private fun onIceMsg(candidatesMessage: CandidatesMessage) = rpcService.onIceMsg(candidatesMessage)

    private fun onIceConnectionStateChanged(localPlayerId: Int, remotePlayerId: Int, state: String) = rpcService.onIceConnectionStateChanged(localPlayerId, remotePlayerId, state)

    private fun onConnected(localPlayerId: Int, remotePlayerId: Int, connected: Boolean) = rpcService.onConnected(localPlayerId, remotePlayerId, connected)

    private fun onIceAdapterStopped() {
        rpcService.stop()
        closing = true
    }

    override fun call(): Int {
        val realLobbyPort = if (lobbyPort == 0) {
            SocketFactory.createLocalUDPSocket().use { it.localPort }
        } else {
            lobbyPort
        }

        iceOptions = IceOptions(
            userId,
            userName,
            gameId,
            forceRelay,
            rpcPort,
            realLobbyPort,
            gpgnetPort,
            telemetryServer,
        )

        iceAdapter = IceAdapter(
            iceOptions,
            this::onConnectionStateChanged,
            this::onGpgNetMessageReceived,
            this::onIceMsg,
            this::onIceConnectionStateChanged,
            this::onConnected,
            this::onIceAdapterStopped,
            emptyList(),
        )

        rpcService = RpcService(rpcPort, iceAdapter)

        logger.info { "Starting ICE adapter with options: $iceOptions" }
        iceAdapter.start()
        rpcService.start()

        while (!closing) {
            Thread.sleep(5000)
        }

        return 0
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(KiaApplication()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
