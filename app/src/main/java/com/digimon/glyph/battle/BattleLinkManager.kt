package com.digimon.glyph.battle

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.digimon.glyph.battle.BattleStateStore.Role
import com.digimon.glyph.battle.BattleStateStore.Status

/**
 * Nearby Connections session manager for Digimon battle mode.
 *
 * This manages peer discovery + encrypted connection between two phones.
 * Emulator signal bridging is intentionally separate and can subscribe via [Listener].
 */
class BattleLinkManager(
    context: Context,
    private val listener: Listener? = null
) {

    interface Listener {
        fun onConnected(peerName: String)
        fun onDisconnected(reason: String)
        fun onMessage(type: String, body: String?)
    }

    private data class WireMessage(
        val type: String,
        val body: String?,
        val timestampMs: Long
    )

    data class PinEdge(
        val port: String,
        val value: Int?,
        val seq: Long,
        val sourceUptimeMs: Long
    )

    companion object {
        private const val TAG = "BattleLinkManager"
        private const val SERVICE_ID = "com.digimon.glyph.battle.v1"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
        private const val KEEPALIVE_INTERVAL_MS = 750L
    }

    private val appContext = context.applicationContext
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val gson = Gson()
    private val endpointName = buildEndpointName()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var role: Role = Role.NONE
    private var connectedEndpointId: String? = null
    private var connectedPeerName: String? = null
    private var pendingEndpointId: String? = null
    private var pendingPeerName: String? = null
    private var serialTxCount: Long = 0
    private var serialRxCount: Long = 0
    private var pinTxCount: Long = 0
    private var pinRxCount: Long = 0
    private var keepaliveScheduled = false

    private val keepaliveRunnable = object : Runnable {
        override fun run() {
            keepaliveScheduled = false
            val endpointId = connectedEndpointId
            if (endpointId == null) return
            sendMessage("ka", null)
            scheduleKeepalive()
        }
    }

    init {
        // Prevent stale "CONNECTED" UI from previous process/session.
        BattleStateStore.setIdle(appContext, "Battle idle")
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            pendingEndpointId = endpointId
            pendingPeerName = connectionInfo.endpointName
            updateState(Status.CONNECTING, "Pairing with ${connectionInfo.endpointName}")
            Log.i(TAG, "Connection initiated from=${connectionInfo.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { err ->
                    Log.w(TAG, "Accept connection failed: ${err.message}")
                    updateState(Status.ERROR, "Accept failed: ${err.message ?: "unknown"}")
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val statusCode = result.status.statusCode
            if (statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointId = endpointId
                connectedPeerName = pendingPeerName ?: connectedPeerName ?: "peer"
                pendingEndpointId = null
                pendingPeerName = null
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                updateState(Status.CONNECTED, "Connected to ${connectedPeerName ?: "peer"}")
                Log.i(TAG, "Connected peer=${connectedPeerName ?: "peer"} endpointId=$endpointId")
                listener?.onConnected(connectedPeerName ?: "peer")
                sendMessage("hello", "digimon-battle-v1")
                scheduleKeepalive()
            } else {
                val reason = when (statusCode) {
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> "Connection rejected"
                    ConnectionsStatusCodes.STATUS_ERROR -> "Connection error"
                    else -> "Connection failed ($statusCode)"
                }
                Log.w(TAG, "Connection result failed endpointId=$endpointId code=$statusCode reason=$reason")
                clearPeer()
                updateState(Status.ERROR, reason)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val peer = connectedPeerName ?: "peer"
            Log.w(TAG, "Disconnected endpointId=$endpointId peer=$peer")
            cancelKeepalive()
            clearPeer()
            updateState(Status.DISCONNECTED, "Disconnected from $peer")
            listener?.onDisconnected("Disconnected from $peer")
            resumeRoleFlow()
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connectedEndpointId != null || pendingEndpointId != null) return
            pendingEndpointId = endpointId
            pendingPeerName = info.endpointName
            updateState(Status.CONNECTING, "Requesting ${info.endpointName}")
            connectionsClient.requestConnection(endpointName, endpointId, connectionCallback)
                .addOnFailureListener { err ->
                    pendingEndpointId = null
                    pendingPeerName = null
                    updateState(Status.ERROR, "Request failed: ${err.message ?: "unknown"}")
                    resumeRoleFlow()
                }
        }

        override fun onEndpointLost(endpointId: String) {
            if (pendingEndpointId == endpointId && connectedEndpointId == null) {
                pendingEndpointId = null
                pendingPeerName = null
                updateState(Status.DISCOVERING, "Peer lost, scanning again")
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val message = runCatching {
                gson.fromJson(String(bytes, Charsets.UTF_8), WireMessage::class.java)
            }.getOrElse { err ->
                if (err is JsonSyntaxException) {
                    Log.w(TAG, "Invalid battle message json")
                } else {
                    Log.w(TAG, "Failed parsing battle message", err)
                }
                null
            } ?: return
            listener?.onMessage(message.type, message.body)
            when (message.type) {
                "ping" -> sendMessage("pong", null)
                "ka" -> sendMessage("ka_ack", null)
                "hello" -> updateState(Status.CONNECTED, "Connected to ${connectedPeerName ?: "peer"}")
                "pong" -> updateState(Status.CONNECTED, "Link OK with ${connectedPeerName ?: "peer"}")
                "serial_tx" -> {
                    serialRxCount++
                    if (serialRxCount % 8L == 1L) {
                        Log.d(TAG, "serial rx count=$serialRxCount last=${message.body}")
                    }
                }
                "pin_tx" -> {
                    pinRxCount++
                    if (pinRxCount % 16L == 1L) {
                        Log.d(TAG, "pin rx count=$pinRxCount last=${message.body}")
                    }
                }
                "pin_edge" -> {
                    pinRxCount++
                    if (pinRxCount % 16L == 1L) {
                        Log.d(TAG, "pin edge rx count=$pinRxCount last=${message.body}")
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Bytes payloads are small; no progress UI needed.
        }
    }

    fun startHost(): String {
        if (!canUseNearby()) return "battle failed: Nearby unavailable"
        if (!hasRuntimePermissions()) return "battle failed: missing nearby permissions"
        Log.i(TAG, "startHost endpointName=$endpointName")
        role = Role.HOST
        clearPeer()
        cancelKeepalive()
        serialTxCount = 0
        serialRxCount = 0
        pinTxCount = 0
        pinRxCount = 0
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        return try {
            connectionsClient.startAdvertising(endpointName, SERVICE_ID, connectionCallback, options)
                .addOnSuccessListener {
                    Log.i(TAG, "Host advertising started serviceId=$SERVICE_ID")
                    updateState(Status.ADVERTISING, "Hosting battle link")
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "Host advertising failed: ${err.message}")
                    updateState(Status.ERROR, "Host failed: ${err.message ?: "unknown"}")
                }
            "battle host requested"
        } catch (sec: SecurityException) {
            Log.w(TAG, "Host start security exception: ${sec.message}")
            updateState(Status.ERROR, "Host failed: missing permission")
            "battle failed: permission"
        }
    }

    fun startJoin(): String {
        if (!canUseNearby()) return "battle failed: Nearby unavailable"
        if (!hasRuntimePermissions()) return "battle failed: missing nearby permissions"
        Log.i(TAG, "startJoin endpointName=$endpointName")
        role = Role.JOIN
        clearPeer()
        cancelKeepalive()
        serialTxCount = 0
        serialRxCount = 0
        pinTxCount = 0
        pinRxCount = 0
        connectionsClient.stopAdvertising()
        connectionsClient.stopAllEndpoints()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        return try {
            connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnSuccessListener {
                    Log.i(TAG, "Join discovery started serviceId=$SERVICE_ID")
                    updateState(Status.DISCOVERING, "Scanning for host")
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "Join discovery failed: ${err.message}")
                    updateState(Status.ERROR, "Join failed: ${err.message ?: "unknown"}")
                }
            "battle join requested"
        } catch (sec: SecurityException) {
            Log.w(TAG, "Join start security exception: ${sec.message}")
            updateState(Status.ERROR, "Join failed: missing permission")
            "battle failed: permission"
        }
    }

    fun stop(): String {
        role = Role.NONE
        cancelKeepalive()
        clearPeer()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        BattleStateStore.setIdle(appContext, "Battle idle")
        return "battle link stopped"
    }

    fun sendPing(): String {
        return if (sendMessage("ping", null)) {
            "battle ping sent"
        } else {
            "battle ping failed: not connected"
        }
    }

    fun sendWaveStart(stepMs: Int, totalMs: Int): Boolean {
        val safeStep = stepMs.coerceIn(4, 1000)
        val safeTotal = totalMs.coerceIn(500, 20_000)
        return sendMessage("wave_start", "$safeStep:$safeTotal")
    }

    fun sendSerialByte(value: Int): Boolean {
        serialTxCount++
        if (serialTxCount % 8L == 1L) {
            Log.d(TAG, "serial tx count=$serialTxCount last=${value and 0xFF}")
        }
        return sendMessage("serial_tx", (value and 0xFF).toString())
    }

    fun sendVpetPacket(packet: Int): Boolean {
        return sendMessage("vpet_packet", packet.toString())
    }

    fun sendPinDrive(port: String, value: Int?): Boolean {
        pinTxCount++
        val body = if (value == null) "$port:-" else "$port:${value and 0xF}"
        if (pinTxCount % 16L == 1L) {
            Log.d(TAG, "pin tx count=$pinTxCount last=$body")
        }
        return sendMessage("pin_tx", body)
    }

    fun sendPinEdge(port: String, value: Int?, seq: Long, sourceUptimeMs: Long): Boolean {
        pinTxCount++
        val normalized = if (value == null) -1 else (value and 0xF)
        val body = "$port,$normalized,$seq,$sourceUptimeMs"
        if (pinTxCount % 16L == 1L) {
            Log.d(TAG, "pin edge tx count=$pinTxCount last=$body")
        }
        return sendMessage("pin_edge", body)
    }

    fun parsePinEdge(body: String?): PinEdge? {
        if (body.isNullOrBlank()) return null
        val parts = body.split(',')
        if (parts.size != 4) return null
        val port = parts[0]
        if (port.isBlank()) return null
        val valueToken = parts[1].toIntOrNull() ?: return null
        val seq = parts[2].toLongOrNull() ?: return null
        val sourceUptimeMs = parts[3].toLongOrNull() ?: return null
        val normalizedValue = if (valueToken < 0) null else (valueToken and 0xF)
        return PinEdge(
            port = port,
            value = normalizedValue,
            seq = seq,
            sourceUptimeMs = sourceUptimeMs
        )
    }

    private fun sendMessage(type: String, body: String?): Boolean {
        val endpointId = connectedEndpointId ?: return false
        val bytes = gson.toJson(
            WireMessage(type = type, body = body, timestampMs = System.currentTimeMillis())
        ).toByteArray(Charsets.UTF_8)
        return try {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage failed type=$type endpointId=$endpointId msg=${e.message}")
            updateState(Status.ERROR, "Send failed: ${e.message ?: "unknown"}")
            false
        }
    }

    private fun scheduleKeepalive() {
        if (keepaliveScheduled) return
        keepaliveScheduled = true
        mainHandler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS)
    }

    private fun cancelKeepalive() {
        keepaliveScheduled = false
        mainHandler.removeCallbacks(keepaliveRunnable)
    }

    private fun resumeRoleFlow() {
        when (role) {
            Role.HOST -> startHost()
            Role.JOIN -> startJoin()
            Role.NONE -> Unit
        }
    }

    private fun updateState(status: Status, message: String) {
        BattleStateStore.update(
            context = appContext,
            status = status,
            role = role,
            peerName = connectedPeerName ?: pendingPeerName,
            message = message
        )
    }

    private fun clearPeer() {
        connectedEndpointId = null
        connectedPeerName = null
        pendingEndpointId = null
        pendingPeerName = null
    }

    private fun canUseNearby(): Boolean {
        val code = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(appContext)
        return code == ConnectionResult.SUCCESS
    }

    private fun hasRuntimePermissions(): Boolean {
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!(coarseGranted || fineGranted)) return false

        val corePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        return corePermissions.all {
            ContextCompat.checkSelfPermission(appContext, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun buildEndpointName(): String {
        val model = (Build.MODEL ?: "android").replace(" ", "-").take(20)
        val suffix = (System.currentTimeMillis() % 10_000L).toString().padStart(4, '0')
        return "$model-$suffix"
    }
}
