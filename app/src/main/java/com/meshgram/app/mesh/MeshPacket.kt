package com.meshgram.app.mesh

import com.google.gson.annotations.SerializedName

enum class PacketType {
    ZERO_KNOWLEDGE_RELAY,
    PUBLIC_BROADCAST,
    DELIVERY_RECEIPT,
    SEEN_RECEIPT,
    STORY_BROADCAST,
    CALL_SIGNAL,
    PEER_DISCOVERY
}

/**
 * بسته عمومی شبکه مش‌گرام برای تبادل داده میان گوشی‌ها
 */
data class MeshPacket(
    @SerializedName("type") val type: PacketType,
    @SerializedName("packetId") val packetId: String,
    @SerializedName("originId") val originId: String,
    @SerializedName("destinationId") val destinationId: String? = null, // null for broadcast
    @SerializedName("hopCount") var hopCount: Int = 0,
    @SerializedName("maxHops") val maxHops: Int = 4,
    @SerializedName("visitedNodes") val visitedNodes: MutableList<String> = mutableListOf(),
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    
    // محتوای بسته (می‌تواند متن عادی یا بسته رمزگذاری شده غیرقابل نفوذ باشد)
    @SerializedName("ivBase64") val ivBase64: String? = null,
    @SerializedName("encryptedPayload") val encryptedPayload: String? = null,
    @SerializedName("rawJson") val rawJson: String? = null
)

data class CallSignal(
    @SerializedName("callerId") val callerId: String,
    @SerializedName("callerName") val callerName: String,
    @SerializedName("targetId") val targetId: String,
    @SerializedName("action") val action: String, // OFFER, ANSWER, ICE, HANGUP, BUSY
    @SerializedName("data") val data: String = "",
    @SerializedName("isVideo") val isVideo: Boolean = false
)
