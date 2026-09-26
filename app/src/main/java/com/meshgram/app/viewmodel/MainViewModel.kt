package com.meshgram.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.meshgram.app.audio.AudioRecordPlayer
import com.meshgram.app.crypto.E2EESecurityManager
import com.meshgram.app.mesh.MeshClusterEngine
import com.meshgram.app.mesh.MeshPacket
import com.meshgram.app.mesh.PacketType
import com.meshgram.app.model.*
import com.meshgram.app.transfer.AppExtractor
import com.meshgram.app.transfer.HighSpeedTransferEngine
import com.meshgram.app.webrtc.P2PCallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()
    private val cryptoManager = E2EESecurityManager(application)
    private val appExtractor = AppExtractor(application)
    val transferEngine = HighSpeedTransferEngine(application)
    val audioRecordPlayer = AudioRecordPlayer(application)
    val callManager = P2PCallManager(application)

    val localUserId = "user_${UUID.randomUUID().toString().take(6)}"
    private val _localUserName = MutableStateFlow("کاربر مش‌گرام")
    val localUserName: StateFlow<String> = _localUserName.asStateFlow()

    private val _localUserBio = MutableStateFlow("فعال در شبکه مش‌آفلاین")
    val localUserBio: StateFlow<String> = _localUserBio.asStateFlow()

    val localKeyFingerprint: String
        get() = cryptoManager.keyFingerprint

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val meshEngine = MeshClusterEngine(
        context = application,
        localUserId = localUserId,
        localUserName = _localUserName.value,
        localPublicKey = cryptoManager.publicKeyBase64,
        localKeyFingerprint = cryptoManager.keyFingerprint
    )

    val connectedPeers = meshEngine.connectedPeers

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    init {
        // پیام خوش‌آمدگویی و معرفی قابلیت‌های پیشرفته
        _messages.value = listOf(
            Message(
                id = "welcome_1",
                senderId = "system",
                senderName = "مش‌گرام (سیستم)",
                text = "به ابر-اپلیکیشن ارتباطی مش‌گرام خوش آمدید! تمام پیام‌ها با امنیت سرتاسری E2EE و قابلیت رله با دانش صفر محافظت می‌شوند. گوشی‌های واسط هیچ دسترسی به پیام‌های شما ندارند.",
                type = MessageType.TEXT,
                status = MessageStatus.SEEN
            )
        )

        // نمونه استوری اولیه
        _stories.value = listOf(
            Story(
                id = "story_sample",
                authorId = "system",
                authorName = "تیم مش‌گرام",
                content = "ارتباط آزاد، فوق سریع و بدون فیلتر با شبکه توری آفلاین 🚀"
            )
        )

        // بارگیری برنامه‌های نصب‌شده
        viewModelScope.launch(Dispatchers.IO) {
            val apps = appExtractor.getInstalledApps()
            _installedApps.value = apps
        }

        // گوش دادن به بسته‌های ورودی از شبکه مش
        viewModelScope.launch {
            meshEngine.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }

        // راه‌اندازی شبکه مش
        meshEngine.startMesh()
    }

    private fun handleIncomingPacket(packet: MeshPacket) {
        when (packet.type) {
            PacketType.ZERO_KNOWLEDGE_RELAY -> {
                // اگر مقصد این دستگاه است، رمزگشایی می‌کنیم
                if (packet.destinationId == localUserId && packet.encryptedPayload != null && packet.ivBase64 != null) {
                    val peer = connectedPeers.value.find { it.id == packet.originId }
                    val peerKey = peer?.publicKey ?: ""
                    if (peerKey.isNotEmpty()) {
                        try {
                            val decryptedJson = cryptoManager.decrypt(
                                com.meshgram.app.crypto.EncryptedBundle(packet.ivBase64, packet.encryptedPayload),
                                peerKey
                            )
                            val msg = gson.fromJson(decryptedJson, Message::class.java)
                            _messages.value = _messages.value + msg
                        } catch (e: Exception) {}
                    }
                }
            }
            PacketType.PUBLIC_BROADCAST -> {
                packet.rawJson?.let { json ->
                    try {
                        val msg = gson.fromJson(json, Message::class.java)
                        if (_messages.value.none { it.id == msg.id }) {
                            // ثبت خوانده شدن پیام در چت عمومی
                            if (!msg.readByList.contains(_localUserName.value)) {
                                msg.readByList.add(_localUserName.value)
                            }
                            _messages.value = _messages.value + msg
                        }
                    } catch (e: Exception) {}
                }
            }
            PacketType.STORY_BROADCAST -> {
                packet.rawJson?.let { json ->
                    try {
                        val story = gson.fromJson(json, Story::class.java)
                        if (_stories.value.none { it.id == story.id }) {
                            _stories.value = _stories.value + story
                        }
                    } catch (e: Exception) {}
                }
            }
            PacketType.CALL_SIGNAL -> {
                packet.rawJson?.let { json ->
                    try {
                        val signal = gson.fromJson(json, com.meshgram.app.mesh.CallSignal::class.java)
                        callManager.handleIncomingSignal(signal) { answerSignal ->
                            val respPacket = MeshPacket(
                                type = PacketType.CALL_SIGNAL,
                                packetId = UUID.randomUUID().toString(),
                                originId = localUserId,
                                destinationId = signal.callerId,
                                rawJson = gson.toJson(answerSignal)
                            )
                            meshEngine.broadcastPacket(respPacket)
                        }
                    } catch (e: Exception) {}
                }
            }
            else -> {}
        }
    }

    fun sendMessage(text: String, recipientId: String?, type: MessageType, payload: Any?) {
        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        var voiceData: VoicePayload? = null
        var videoNoteData: VideoNotePayload? = null
        var mediaPayload: MediaPayload? = null

        when (payload) {
            is VoicePayload -> voiceData = payload
            is VideoNotePayload -> videoNoteData = payload
            is MediaPayload -> mediaPayload = payload
        }

        val newMsg = Message(
            id = msgId,
            senderId = localUserId,
            senderName = _localUserName.value,
            recipientId = recipientId,
            text = text,
            type = type,
            voiceData = voiceData,
            videoNoteData = videoNoteData,
            mediaPayload = mediaPayload,
            status = MessageStatus.SENT,
            isEncrypted = recipientId != null
        )

        _messages.value = _messages.value + newMsg

        // اگر چت ذخیره‌شده است، نیازی به ارسال در شبکه نیست
        if (recipientId == "saved") return

        val json = gson.toJson(newMsg)

        if (recipientId != null) {
            // پیام خصوصی: رمزنگاری سرتاسری و ارسال به صورت بسته رله با دانش صفر
            val peer = connectedPeers.value.find { it.id == recipientId }
            if (peer != null && peer.publicKey.isNotEmpty()) {
                val encBundle = cryptoManager.encrypt(json, peer.publicKey)
                val relayPacket = MeshPacket(
                    type = PacketType.ZERO_KNOWLEDGE_RELAY,
                    packetId = UUID.randomUUID().toString(),
                    originId = localUserId,
                    destinationId = recipientId,
                    ivBase64 = encBundle.ivBase64,
                    encryptedPayload = encBundle.ciphertextBase64
                )
                meshEngine.broadcastPacket(relayPacket)
            }
        } else {
            // چت عمومی مش: ارسال سراسری
            val broadcastPacket = MeshPacket(
                type = PacketType.PUBLIC_BROADCAST,
                packetId = UUID.randomUUID().toString(),
                originId = localUserId,
                rawJson = json
            )
            meshEngine.broadcastPacket(broadcastPacket)
        }
    }

    fun sendInstalledApp(appInfo: InstalledAppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val extractedApk = appExtractor.extractApk(appInfo)
            sendMessage(
                text = "برنامه اندروید: ${appInfo.appName}",
                recipientId = null,
                type = MessageType.APK,
                payload = MediaPayload(
                    fileName = extractedApk.name,
                    fileSize = extractedApk.length(),
                    filePath = extractedApk.absolutePath,
                    isApk = true
                )
            )
        }
    }

    fun startVoiceRecording() {
        _isRecordingVoice.value = true
        audioRecordPlayer.startRecording()
    }

    fun stopVoiceRecording(): Pair<File?, List<Int>> {
        _isRecordingVoice.value = false
        return audioRecordPlayer.stopRecording()
    }

    fun playAudio(path: String, messageId: String) {
        audioRecordPlayer.playAudio(path, messageId)
    }

    fun cycleAudioSpeed() {
        audioRecordPlayer.cyclePlaybackSpeed()
    }

    fun addStory(content: String) {
        val newStory = Story(
            id = "story_${System.currentTimeMillis()}",
            authorId = localUserId,
            authorName = _localUserName.value,
            content = content
        )
        _stories.value = listOf(newStory) + _stories.value
        val packet = MeshPacket(
            type = PacketType.STORY_BROADCAST,
            packetId = UUID.randomUUID().toString(),
            originId = localUserId,
            rawJson = gson.toJson(newStory)
        )
        meshEngine.broadcastPacket(packet)
    }

    fun likeStory(storyId: String) {
        _stories.value = _stories.value.map {
            if (it.id == storyId) it.copy(isLiked = !it.isLiked, likesCount = if (!it.isLiked) it.likesCount + 1 else it.likesCount - 1)
            else it
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun updateProfile(name: String, bio: String) {
        _localUserName.value = name
        _localUserBio.value = bio
    }

    fun generatePairingQr(): Bitmap? {
        return transferEngine.generatePairingQRCode("192.168.49.1", 8988, _localUserName.value)
    }

    fun startTransferServer() {
        viewModelScope.launch(Dispatchers.IO) {
            transferEngine.startServer { receivedFile ->
                sendMessage(
                    text = "فایل دریافت شد: ${receivedFile.name}",
                    recipientId = null,
                    type = if (receivedFile.name.endsWith(".apk")) MessageType.APK else MessageType.FILE,
                    payload = MediaPayload(
                        fileName = receivedFile.name,
                        fileSize = receivedFile.length(),
                        filePath = receivedFile.absolutePath,
                        isApk = receivedFile.name.endsWith(".apk")
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        meshEngine.stopMesh()
        audioRecordPlayer.stopAudio()
        transferEngine.stopServer()
    }
}
