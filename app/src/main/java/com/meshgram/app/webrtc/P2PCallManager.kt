package com.meshgram.app.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.meshgram.app.mesh.CallSignal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

enum class CallState {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED
}

/**
 * مدیریت تماس‌های صوتی و تصویری P2P و بیسیم واکی‌تاکی (PTT)
 */
class P2PCallManager(private val context: Context) {

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _activePeerName = MutableStateFlow<String?>(null)
    val activePeerName: StateFlow<String?> = _activePeerName.asStateFlow()

    private val _isVideoCall = MutableStateFlow(false)
    val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

    private val _isWalkieTalkieActive = MutableStateFlow(false)
    val isWalkieTalkieActive: StateFlow<Boolean> = _isWalkieTalkieActive.asStateFlow()

    private var walkieTalkieJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // تنظیمات صوتی واکی‌تاکی (PCM 16kHz)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(1024)

    fun startCall(peerId: String, peerName: String, isVideo: Boolean, sendSignal: (CallSignal) -> Unit) {
        _callState.value = CallState.OUTGOING_RINGING
        _activePeerName.value = peerName
        _isVideoCall.value = isVideo

        val signal = CallSignal(
            callerId = "local",
            callerName = "من",
            targetId = peerId,
            action = "OFFER",
            isVideo = isVideo
        )
        sendSignal(signal)
    }

    fun handleIncomingSignal(signal: CallSignal, sendSignal: (CallSignal) -> Unit) {
        when (signal.action) {
            "OFFER" -> {
                _activePeerName.value = signal.callerName
                _isVideoCall.value = signal.isVideo
                _callState.value = CallState.INCOMING_RINGING
            }
            "ANSWER" -> {
                _callState.value = CallState.CONNECTED
            }
            "HANGUP" -> {
                endCall(sendSignal)
            }
        }
    }

    fun answerCall(peerId: String, sendSignal: (CallSignal) -> Unit) {
        _callState.value = CallState.CONNECTED
        val signal = CallSignal(
            callerId = "local",
            callerName = "من",
            targetId = peerId,
            action = "ANSWER"
        )
        sendSignal(signal)
    }

    fun endCall(sendSignal: ((CallSignal) -> Unit)? = null) {
        _callState.value = CallState.ENDED
        _activePeerName.value = null
        stopWalkieTalkie()
        scope.launch {
            delay(1000)
            _callState.value = CallState.IDLE
        }
    }

    /**
     * فعال‌سازی ارسال زنده صوت واکی‌تاکی (PTT - Push-to-Talk)
     */
    fun startWalkieTalkie(targetIp: String, port: Int = 9050) {
        _isWalkieTalkieActive.value = true
        walkieTalkieJob = scope.launch {
            var audioRecord: AudioRecord? = null
            var socket: DatagramSocket? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                socket = DatagramSocket()
                val targetAddress = InetAddress.getByName(targetIp)
                val buffer = ByteArray(bufferSize)

                audioRecord.startRecording()
                while (isActive && _isWalkieTalkieActive.value) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val packet = DatagramPacket(buffer, read, targetAddress, port)
                        socket.send(packet)
                    }
                }
            } catch (e: Exception) {
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                    socket?.close()
                } catch (e: Exception) {}
            }
        }
    }

    fun stopWalkieTalkie() {
        _isWalkieTalkieActive.value = false
        walkieTalkieJob?.cancel()
        walkieTalkieJob = null
    }
}
