package com.digimon.glyph.battle

import android.net.Uri
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.digimon.glyph.battle.BattleStateStore.Role
import com.digimon.glyph.battle.BattleStateStore.Status
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Internet relay transport over plain TCP line-delimited JSON.
 *
 * Relay URL format examples:
 * - tcp://example.com:19792/room1
 * - 203.0.113.10:19792/room1
 * - 203.0.113.10:19792  (defaults room to "default")
 */
class InternetBattleTransport(
    context: Context,
    private val listener: BattleTransport.Listener? = null
) : BattleTransport {

    private data class RelayConfig(
        val host: String,
        val port: Int,
        val room: String
    )

    private data class RelayEnvelope(
        val op: String,
        val room: String? = null,
        val role: String? = null,
        val name: String? = null,
        val type: String? = null,
        val body: String? = null,
        val timestampMs: Long? = null,
        val message: String? = null,
        val peer: String? = null,
        val proto: String? = null
    )

    companion object {
        private const val TAG = "InternetBattleTransport"
        private const val DEFAULT_ROOM = "default"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val RECONNECT_DELAY_MS = 1_500L
        private const val RELAY_KEEPALIVE_MS = 2_000L
    }

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val endpointName = buildEndpointName()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)

    @Volatile
    private var role: Role = Role.NONE
    @Volatile
    private var relayConfig: RelayConfig? = null
    @Volatile
    private var socket: Socket? = null
    @Volatile
    private var writer: BufferedWriter? = null
    @Volatile
    private var connectedPeerName: String? = null
    @Volatile
    private var relayConnected = false
    @Volatile
    private var reconnectScheduled = false

    private val keepaliveRunnable = object : Runnable {
        override fun run() {
            val gen = generation.get()
            if (role == Role.NONE || !relayConnected) return
            sendEnvelope(
                RelayEnvelope(
                    op = "ping",
                    timestampMs = System.currentTimeMillis()
                ),
                requirePeer = false
            )
            if (gen == generation.get() && role != Role.NONE) {
                mainHandler.postDelayed(this, RELAY_KEEPALIVE_MS)
            }
        }
    }

    override fun startHost(): String {
        return start(Role.HOST)
    }

    override fun startJoin(): String {
        return start(Role.JOIN)
    }

    override fun stop(): String {
        generation.incrementAndGet()
        cancelReconnect()
        stopKeepalive()
        closeSocket()
        relayConnected = false
        connectedPeerName = null
        role = Role.NONE
        BattleStateStore.setIdle(appContext, "Battle idle")
        return "battle link stopped"
    }

    override fun sendPing(): String {
        return if (sendRelayPayload("ping", null)) {
            "battle ping sent"
        } else {
            "battle ping failed: not connected"
        }
    }

    override fun sendWaveStart(stepMs: Int, totalMs: Int): Boolean {
        val safeStep = stepMs.coerceIn(4, 1000)
        val safeTotal = totalMs.coerceIn(500, 20_000)
        return sendRelayPayload("wave_start", "$safeStep:$safeTotal")
    }

    override fun sendSerialByte(value: Int): Boolean {
        return sendRelayPayload("serial_tx", (value and 0xFF).toString())
    }

    override fun sendVpetPacket(packet: Int): Boolean {
        return sendRelayPayload("vpet_packet", packet.toString())
    }

    override fun sendPinDrive(port: String, value: Int?): Boolean {
        val body = if (value == null) "$port:-" else "$port:${value and 0xF}"
        return sendRelayPayload("pin_tx", body)
    }

    override fun sendPinEdge(port: String, value: Int?, seq: Long, sourceUptimeMs: Long): Boolean {
        val normalized = if (value == null) -1 else (value and 0xF)
        val body = "$port,$normalized,$seq,$sourceUptimeMs"
        return sendRelayPayload("pin_edge", body)
    }

    override fun parsePinEdge(body: String?): BattleTransport.PinEdge? {
        if (body.isNullOrBlank()) return null
        val parts = body.split(',')
        if (parts.size != 4) return null
        val port = parts[0]
        if (port.isBlank()) return null
        val valueToken = parts[1].toIntOrNull() ?: return null
        val seq = parts[2].toLongOrNull() ?: return null
        val sourceUptimeMs = parts[3].toLongOrNull() ?: return null
        val normalizedValue = if (valueToken < 0) null else (valueToken and 0xF)
        return BattleTransport.PinEdge(
            port = port,
            value = normalizedValue,
            seq = seq,
            sourceUptimeMs = sourceUptimeMs
        )
    }

    private fun updateState(status: Status, message: String) {
        BattleStateStore.update(
            context = appContext,
            status = status,
            role = role,
            peerName = connectedPeerName,
            message = message
        )
    }

    private fun start(targetRole: Role): String {
        val config = parseRelayConfig(BattleTransportSettings.getRelayUrl())
            ?: return "battle failed: invalid relay URL (expected tcp://host:port/room)"
        generation.incrementAndGet()
        cancelReconnect()
        stopKeepalive()
        closeSocket()
        connectedPeerName = null
        relayConnected = false
        role = targetRole
        relayConfig = config
        val gen = generation.get()
        updateState(Status.CONNECTING, "Connecting relay ${config.host}:${config.port}/${config.room}")
        ioExecutor.execute {
            connect(gen, config, targetRole)
        }
        return if (targetRole == Role.HOST) "battle host requested" else "battle join requested"
    }

    private fun connect(gen: Long, config: RelayConfig, targetRole: Role) {
        if (gen != generation.get() || role != targetRole) return
        try {
            val sock = Socket()
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
            val localWriter = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
            val localReader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            synchronized(this) {
                if (gen != generation.get() || role != targetRole) {
                    runCatching { sock.close() }
                    return
                }
                closeSocket()
                socket = sock
                writer = localWriter
                relayConnected = true
            }
            updateWaitingState(config)
            startKeepalive(gen)
            sendEnvelope(
                RelayEnvelope(
                    op = "join",
                    room = config.room,
                    role = if (targetRole == Role.HOST) "host" else "join",
                    name = endpointName,
                    proto = "digimon-battle-v1",
                    timestampMs = System.currentTimeMillis()
                ),
                requirePeer = false
            )
            Thread {
                try {
                    var line: String?
                    while (true) {
                        line = localReader.readLine() ?: break
                        onRelayLine(gen, config, line)
                    }
                    onTransportDisconnected(gen, "Relay closed connection")
                } catch (e: Exception) {
                    onTransportDisconnected(gen, "Relay error: ${e.message ?: "unknown"}")
                }
            }.start()
        } catch (e: Exception) {
            onTransportDisconnected(gen, "Relay error: ${e.message ?: "unknown"}")
        }
    }

    private fun onRelayLine(gen: Long, config: RelayConfig, line: String) {
        if (gen != generation.get()) return
        val json = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
            ?: run {
                Log.w(TAG, "Invalid relay json line")
                return
            }
        val op = json.getAsStringOrNull("op")
        when (op) {
            "joined" -> {
                updateWaitingState(config)
            }
            "ready" -> {
                val peer = json.getAsStringOrNull("peer").orEmpty().ifBlank { "peer" }
                connectedPeerName = peer
                updateState(Status.CONNECTED, "Connected to $peer via relay")
                listener?.onConnected(peer)
            }
            "peer_left" -> {
                val previous = connectedPeerName ?: "peer"
                connectedPeerName = null
                updateWaitingState(config)
                listener?.onDisconnected("Disconnected from $previous")
            }
            "error" -> {
                val msg = json.getAsStringOrNull("message").orEmpty().ifBlank { "Relay protocol error" }
                updateState(Status.ERROR, msg)
            }
            "ping" -> {
                sendEnvelope(
                    RelayEnvelope(
                        op = "pong",
                        timestampMs = System.currentTimeMillis()
                    ),
                    requirePeer = false
                )
            }
            "pong" -> {
                if (connectedPeerName != null) {
                    updateState(Status.CONNECTED, "Link OK with ${connectedPeerName ?: "peer"}")
                }
            }
            "msg" -> {
                val type = json.getAsStringOrNull("type") ?: return
                val body = json.getAsStringOrNull("body")
                onRelayPayload(type, body)
            }
            null -> {
                // Fallback: allow raw payload forwarding from simple relays.
                val type = json.getAsStringOrNull("type") ?: return
                val body = json.getAsStringOrNull("body")
                onRelayPayload(type, body)
            }
            else -> {
                Log.d(TAG, "Relay op ignored: $op")
            }
        }
    }

    private fun onRelayPayload(type: String, body: String?) {
        when (type) {
            "ping" -> sendRelayPayload("pong", null)
            "ka" -> sendRelayPayload("ka_ack", null)
            "hello" -> {
                if (connectedPeerName != null) {
                    updateState(Status.CONNECTED, "Connected to ${connectedPeerName ?: "peer"}")
                }
            }
            "pong" -> {
                if (connectedPeerName != null) {
                    updateState(Status.CONNECTED, "Link OK with ${connectedPeerName ?: "peer"}")
                }
            }
        }
        listener?.onMessage(type, body)
    }

    private fun sendRelayPayload(type: String, body: String?): Boolean {
        return sendEnvelope(
            RelayEnvelope(
                op = "msg",
                type = type,
                body = body,
                timestampMs = System.currentTimeMillis()
            ),
            requirePeer = true
        )
    }

    private fun sendEnvelope(envelope: RelayEnvelope, requirePeer: Boolean): Boolean {
        if (requirePeer && connectedPeerName == null) return false
        
        ioExecutor.execute {
            val payload = runCatching { gson.toJson(envelope) }.getOrNull()
            if (payload == null) {
                Log.e(TAG, "Failed encoding payload: $envelope")
                return@execute
            }
            synchronized(this@InternetBattleTransport) {
                val currentWriter = writer ?: return@execute
                try {
                    Log.i(TAG, "TX -> $payload")
                    currentWriter.write(payload)
                    currentWriter.write("\n")
                    currentWriter.flush()
                } catch (e: Exception) {
                    Log.e(TAG, "Socket write failed", e)
                    onTransportDisconnected(generation.get(), "Relay send failed: ${e.message ?: "unknown"}")
                }
            }
        }
        return true
    }

    private fun onTransportDisconnected(gen: Long, reason: String) {
        if (gen != generation.get()) return
        closeSocket()
        stopKeepalive()
        relayConnected = false
        val hadPeer = connectedPeerName != null
        val previousPeer = connectedPeerName ?: "peer"
        connectedPeerName = null
        if (role == Role.NONE) {
            BattleStateStore.setIdle(appContext, "Battle idle")
            return
        }
        updateState(Status.DISCONNECTED, reason)
        if (hadPeer) {
            listener?.onDisconnected("Disconnected from $previousPeer")
        }
        scheduleReconnect(gen)
    }

    private fun scheduleReconnect(gen: Long) {
        if (role == Role.NONE) return
        if (reconnectScheduled) return
        reconnectScheduled = true
        updateState(Status.CONNECTING, "Reconnecting relay…")
        mainHandler.postDelayed({
            reconnectScheduled = false
            if (role == Role.NONE) return@postDelayed
            if (gen != generation.get()) return@postDelayed
            val cfg = relayConfig ?: return@postDelayed
            val roleSnapshot = role
            val nextGen = generation.incrementAndGet()
            ioExecutor.execute {
                connect(nextGen, cfg, roleSnapshot)
            }
        }, RECONNECT_DELAY_MS)
    }

    private fun cancelReconnect() {
        reconnectScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun startKeepalive(gen: Long) {
        mainHandler.removeCallbacks(keepaliveRunnable)
        if (gen != generation.get()) return
        mainHandler.postDelayed(keepaliveRunnable, RELAY_KEEPALIVE_MS)
    }

    private fun stopKeepalive() {
        mainHandler.removeCallbacks(keepaliveRunnable)
    }

    @Synchronized
    private fun closeSocket() {
        runCatching { writer?.close() }
        writer = null
        runCatching { socket?.close() }
        socket = null
    }

    private fun updateWaitingState(config: RelayConfig) {
        val waitingStatus = if (role == Role.HOST) Status.ADVERTISING else Status.DISCOVERING
        val roleLabel = if (role == Role.HOST) "host" else "join"
        updateState(waitingStatus, "Relay connected ($roleLabel), waiting room=${config.room}")
    }

    private fun parseRelayConfig(rawInput: String): RelayConfig? {
        val raw = rawInput.trim()
        if (raw.isEmpty()) {
            updateState(Status.ERROR, "Internet relay URL is empty")
            return null
        }
        val prefixed =
            if (raw.contains("://")) raw
            else "tcp://$raw"
        val uri = runCatching { Uri.parse(prefixed) }.getOrNull()
            ?: run {
                updateState(Status.ERROR, "Invalid relay URL")
                return null
            }
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "tcp") {
            updateState(Status.ERROR, "Relay URL scheme must be tcp://")
            return null
        }
        val host = uri.host?.trim().orEmpty()
        val port = uri.port
        if (host.isEmpty() || port <= 0 || port > 65535) {
            updateState(Status.ERROR, "Relay URL must include host and port")
            return null
        }
        val queryRoom = uri.getQueryParameter("room")?.trim().orEmpty()
        val pathRoom = uri.pathSegments.firstOrNull()?.trim().orEmpty()
        val room = when {
            queryRoom.isNotEmpty() -> queryRoom
            pathRoom.isNotEmpty() -> pathRoom
            else -> DEFAULT_ROOM
        }
        return RelayConfig(host = host, port = port, room = room)
    }

    private fun JsonObject.getAsStringOrNull(key: String): String? {
        if (!has(key) || get(key).isJsonNull) return null
        return runCatching { get(key).asString }.getOrNull()
    }

    private fun buildEndpointName(): String {
        val model = (Build.MODEL ?: "android").replace(" ", "-").take(20)
        val suffix = (System.currentTimeMillis() % 10_000L).toString().padStart(4, '0')
        return "$model-$suffix"
    }
}
