package com.meshgram.app.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.meshgram.app.model.PeerUser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * هسته ارتباطی خودکار شبکه مش آفلاین (AutoSync P2P Cluster)
 * با قابلیت رله چندجهشی با دانش صفر (Zero-Knowledge Multi-Hop Relay)
 */
class MeshClusterEngine(
    private val context: Context,
    val localUserId: String,
    val localUserName: String,
    val localPublicKey: String,
    val localKeyFingerprint: String
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.meshgram.app.mesh_channel"
    private val strategy = Strategy.P2P_CLUSTER
    private val gson = Gson()

    // تمام دستگاه‌های متصل مستقیم در شعاع وای‌فای/بلوتوث
    private val connectedEndpoints = mutableMapOf<String, PeerUser>()
    private val _connectedPeers = MutableStateFlow<List<PeerUser>>(emptyList())
    val connectedPeers: StateFlow<List<PeerUser>> = _connectedPeers.asStateFlow()

    // بسته‌های دریافت شده برای جلوگیری از حلقه (Loop Prevention)
    private val seenPacketIds = mutableSetOf<String>()

    // رویدادهای تحویل به لایه UI/ViewModel
    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val json = String(bytes, StandardCharsets.UTF_8)
                try {
                    val packet = gson.fromJson(json, MeshPacket::class.java)
                    handleIncomingPacket(packet, endpointId)
                } catch (e: Exception) {}
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // پذیرش خودکار برای ایجاد ساختار مش بدون تاخیر
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            val peer = PeerUser(
                id = endpointId,
                endpointId = endpointId,
                name = info.endpointName,
                publicKey = "",
                keyFingerprint = ""
            )
            connectedEndpoints[endpointId] = peer
            updatePeersState()
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                // ارسال کارت هویت و کلید عمومی خود به دستگاه تازه متصل‌شده
                sendDiscoveryGreeting(endpointId)
            } else {
                connectedEndpoints.remove(endpointId)
                updatePeersState()
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            updatePeersState()
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // اتصال خودکار پس از کشف همتا
            connectionsClient.requestConnection(
                localUserName,
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            updatePeersState()
        }
    }

    fun startMesh() {
        startAdvertising()
        startDiscovery()
    }

    fun stopMesh() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            connectedEndpoints.clear()
            updatePeersState()
        } catch (e: Exception) {}
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(localUserName, serviceId, connectionLifecycleCallback, options)
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, options)
    }

    private fun sendDiscoveryGreeting(targetEndpointId: String) {
        val greetingPacket = MeshPacket(
            type = PacketType.PEER_DISCOVERY,
            packetId = "greet_${System.currentTimeMillis()}_$localUserId",
            originId = localUserId,
            destinationId = targetEndpointId,
            rawJson = gson.toJson(
                mapOf(
                    "userId" to localUserId,
                    "userName" to localUserName,
                    "publicKey" to localPublicKey,
                    "keyFingerprint" to localKeyFingerprint
                )
            )
        )
        sendPacketDirect(targetEndpointId, greetingPacket)
    }

    private fun handleIncomingPacket(packet: MeshPacket, fromEndpointId: String) {
        if (seenPacketIds.contains(packet.packetId)) return
        seenPacketIds.add(packet.packetId)

        // مدیریت کشف هویت و کلید عمومی همتا
        if (packet.type == PacketType.PEER_DISCOVERY) {
            try {
                val data = gson.fromJson(packet.rawJson, Map::class.java)
                val peerId = data["userId"] as? String ?: fromEndpointId
                val name = data["userName"] as? String ?: "کاربر مش"
                val pubKey = data["publicKey"] as? String ?: ""
                val fingerprint = data["keyFingerprint"] as? String ?: ""

                connectedEndpoints[fromEndpointId] = PeerUser(
                    id = peerId,
                    endpointId = fromEndpointId,
                    name = name,
                    publicKey = pubKey,
                    keyFingerprint = fingerprint
                )
                updatePeersState()
            } catch (e: Exception) {}
            return
        }

        // بررسی مقصد بسته
        if (packet.destinationId == null || packet.destinationId == localUserId) {
            // بسته متعلق به این دستگاه است -> تحویل به UI
            _incomingPackets.tryEmit(packet)
        }

        // رله چندجهشی با دانش صفر (Zero-Knowledge Relay)
        // اگر بسته متعلق به دیگری بود یا عمومی بود، به گره‌های بعدی ارسال می‌شود
        if (packet.hopCount < packet.maxHops && !packet.visitedNodes.contains(localUserId)) {
            packet.hopCount++
            packet.visitedNodes.add(localUserId)

            // رله به تمام گره‌های متصل دیگر به جز فرستنده اصلی
            val packetBytes = gson.toJson(packet).toByteArray(StandardCharsets.UTF_8)
            val payload = Payload.fromBytes(packetBytes)

            for ((endpoint, _) in connectedEndpoints) {
                if (endpoint != fromEndpointId) {
                    connectionsClient.sendPayload(endpoint, payload)
                }
            }
        }
    }

    fun broadcastPacket(packet: MeshPacket) {
        seenPacketIds.add(packet.packetId)
        packet.visitedNodes.add(localUserId)
        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
    }

    fun sendPacketDirect(endpointId: String, packet: MeshPacket) {
        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
    }

    private fun updatePeersState() {
        _connectedPeers.value = connectedEndpoints.values.toList()
    }
}
