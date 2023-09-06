package com.faforever.ice

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

    @Option(names = ["--user-id"], required = true, description = ["set the ID of the local player"])
    private var userId: Int = 0

    @Option(names = ["--game-id"], required = true, description = ["set the ID of the game"])
    private var gameId: Int = 0

    @Option(
        names = ["--user-name"],
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

    @Option(names = ["--allow-host"], description = ["???"])
    private var allowHost: Boolean = true
    
    @Option(names = ["--allow-reflexive"], description = ["???"])
    private var allowReflexive: Boolean = true

    @Option(names = ["--allow-relay"], description = ["???"])
    private var allowRelay: Boolean = true

    override fun call(): Int {
        val iceOptions = IceOptions(userId, userName, gameId, forceRelay, lobbyPort, gpgnetPort, telemetryServer, allowHost, allowReflexive, allowRelay)
        logger.info { "Starting ICE adapter with options: $iceOptions" }
        val iceAdapter = IceAdapter(iceOptions, emptyList(), {})
        iceAdapter.start()
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
