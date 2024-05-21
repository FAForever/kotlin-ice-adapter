package com.faforever.ice

import com.faforever.ice.gpgnet.GpgnetMessage
import com.faforever.ice.gpgnet.GpgnetProxy
import com.faforever.ice.ice4j.CandidatesMessage
import com.faforever.ice.icebreaker.ApiClient
import com.faforever.ice.rpc.RpcService
import com.faforever.ice.telemetry.TelemetryClient
import com.faforever.ice.util.SocketFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Unmatched
import java.util.Base64
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

    @Option(names = ["--access-token"], required = true, description = ["valid FAF access token"])
    private lateinit var accessToken: String

    @Option(names = ["--game-id"], required = true, description = ["set the ID of the game"])
    private var gameId: Int = 0

    @Option(
        names = ["--user-name", "--login"],
        required = false,
        description = ["set the login of the local player e.g. \"Rhiza\""],
    )
    private var userName: String? = null

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
        names = ["--icebreaker-base-url"],
        defaultValue = "https://api.faforever.com/ice",
    )
    private var icebreakerBaseUrl: String = ""

    @Option(
        names = ["--telemetry-server"],
        defaultValue = "wss://ice-telemetry.faforever.com",
        description = ["Telemetry server to connect to"],
    )
    private var telemetryServer: String = ""

    @Unmatched
    private var unmatchedOptions: MutableList<String> = mutableListOf()

    private var closing: Boolean = false

    private lateinit var iceOptions: IceOptions
    private lateinit var iceAdapter: IceAdapter
    private lateinit var rpcService: RpcService

    private fun onConnectionStateChanged(newState: GpgnetProxy.ConnectionState) = rpcService.onConnectionStateChanged(newState)

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

        val jwtData = decodeJWT(accessToken)

        iceOptions = IceOptions(
            accessToken = accessToken,
            userId = jwtData.userId,
            userName = userName ?: jwtData.userName,
            gameId = gameId,
            forceRelay = forceRelay,
            rpcPort = rpcPort,
            lobbyPort = realLobbyPort,
            gpgnetPort = gpgnetPort,
            icebreakerBaseUrl = icebreakerBaseUrl,
            telemetryServer = telemetryServer,
        )

        iceAdapter = IceAdapter(
            iceOptions = iceOptions,
            apiClient = ApiClient(iceOptions, jacksonObjectMapper()),
            telemetryClient = TelemetryClient(iceOptions, jacksonObjectMapper()),
            onGameConnectionStateChanged = this::onConnectionStateChanged,
            onGpgNetMessageReceived = this::onGpgNetMessageReceived,
            onIceCandidatesGathered = this::onIceMsg,
            onIceConnectionStateChanged = this::onIceConnectionStateChanged,
            onConnected = this::onConnected,
            onIceAdapterStopped = this::onIceAdapterStopped,
            initialCoturnServers = emptyList(),
        )

        rpcService = RpcService(rpcPort, iceAdapter)

        logger.info { "Starting ICE adapter with options: $iceOptions" }
        iceAdapter.start(accessToken = accessToken, gameId = gameId)
        rpcService.start()

        while (!closing) {
            Thread.sleep(5000)
        }

        return 0
    }

    private data class JWTData(
        val userId: Int,
        val userName: String,
    )

    private fun decodeJWT(accessToken: String): JWTData {
        val body = requireNotNull(accessToken.split(".")[1]) {
            "Access token seems invalid (no body found)"
        }.let {
            Base64.getDecoder().decode(it)
        }

        val json = jacksonObjectMapper().readTree(body)
        val userId = json.get("sub").textValue().toInt()
        val userName = json.get("ext").get("username").textValue()

        return JWTData(userId, userName)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(KiaApplication()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
