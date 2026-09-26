package com.meshgram.app.model

import com.google.gson.annotations.SerializedName

enum class MessageType {
    TEXT,
    VOICE,
    ROUND_VIDEO,
    FILE,
    APK,
    CALL_SIGNAL
}

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    SEEN
}

enum class ChatFolder(val title: String) {
    ALL("همه"),
    PUBLIC_MESH("مش عمومی"),
    DIRECT_E2EE("پیام‌های امن"),
    SAVED_MESSAGES("ذخیره‌شده‌ها")
}

data class VoicePayload(
    @SerializedName("waveforms") val waveforms: List<Int> = emptyList(),
    @SerializedName("durationSec") val durationSec: Int = 0,
    @SerializedName("audioBase64") val audioBase64: String? = null,
    @SerializedName("filePath") val filePath: String? = null
)

data class VideoNotePayload(
    @SerializedName("durationSec") val durationSec: Int = 0,
    @SerializedName("videoBase64") val videoBase64: String? = null,
    @SerializedName("filePath") val filePath: String? = null
)

data class MediaPayload(
    @SerializedName("fileName") val fileName: String = "",
    @SerializedName("fileSize") val fileSize: Long = 0L,
    @SerializedName("mimeType") val mimeType: String = "",
    @SerializedName("filePath") val filePath: String? = null,
    @SerializedName("isApk") val isApk: Boolean = false
)

data class Message(
    @SerializedName("id") val id: String,
    @SerializedName("senderId") val senderId: String,
    @SerializedName("senderName") val senderName: String,
    @SerializedName("recipientId") val recipientId: String? = null, // null for public mesh
    @SerializedName("text") val text: String = "",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("type") val type: MessageType = MessageType.TEXT,
    @SerializedName("voiceData") val voiceData: VoicePayload? = null,
    @SerializedName("videoNoteData") val videoNoteData: VideoNotePayload? = null,
    @SerializedName("mediaPayload") val mediaPayload: MediaPayload? = null,
    @SerializedName("replyToMessageId") val replyToMessageId: String? = null,
    @SerializedName("replyToText") val replyToText: String? = null,
    @SerializedName("replyToSender") val replyToSender: String? = null,
    @SerializedName("forwardFrom") val forwardFrom: String? = null,
    @SerializedName("isSelfDestruct") val isSelfDestruct: Boolean = false,
    @SerializedName("destructTimerSec") val destructTimerSec: Int = 0,
    @SerializedName("isRead") var isRead: Boolean = false,
    @SerializedName("readByList") val readByList: MutableList<String> = mutableListOf(),
    @SerializedName("status") var status: MessageStatus = MessageStatus.SENT,
    @SerializedName("reactions") val reactions: MutableMap<String, Int> = mutableMapOf(),
    @SerializedName("isEncrypted") val isEncrypted: Boolean = true,
    @SerializedName("fingerprint") val fingerprint: String? = null
)

data class Story(
    @SerializedName("id") val id: String,
    @SerializedName("authorId") val authorId: String,
    @SerializedName("authorName") val authorName: String,
    @SerializedName("content") val content: String, // text or image base64
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("expiresAt") val expiresAt: Long = System.currentTimeMillis() + 86400000L,
    @SerializedName("likesCount") var likesCount: Int = 0,
    @SerializedName("isLiked") var isLiked: Boolean = false,
    @SerializedName("hasSeen") var hasSeen: Boolean = false
)

data class PeerUser(
    @SerializedName("id") val id: String,
    @SerializedName("endpointId") val endpointId: String,
    @SerializedName("name") val name: String,
    @SerializedName("bio") val bio: String = "کاربر شبکه مش‌گرام",
    @SerializedName("avatarIndex") val avatarIndex: Int = 0,
    @SerializedName("publicKey") val publicKey: String = "",
    @SerializedName("keyFingerprint") val keyFingerprint: String = "",
    @SerializedName("isOnline") var isOnline: Boolean = true,
    @SerializedName("lastSeen") var lastSeen: Long = System.currentTimeMillis(),
    @SerializedName("hops") var hops: Int = 1,
    @SerializedName("relayThrough") var relayThrough: String? = null
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val sourceDir: String,
    val apkSize: Long,
    val versionName: String,
    val isSystemApp: Boolean = false
)
