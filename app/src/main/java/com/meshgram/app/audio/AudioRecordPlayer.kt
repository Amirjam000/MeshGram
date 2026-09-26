package com.meshgram.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0,
    val speed: Float = 1.0f,
    val activeMessageId: String? = null
)

/**
 * ضبط و پخش پیام‌های صوتی با ویژوالایزر موج صدا و کنترل سرعت پخش (1x, 1.5x, 2x)
 */
class AudioRecordPlayer(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null

    private val _recordingAmplitudes = MutableStateFlow<List<Int>>(emptyList())
    val recordingAmplitudes: StateFlow<List<Int>> = _recordingAmplitudes.asStateFlow()

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private var recordingJob: Job? = null
    private var playbackProgressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startRecording(): File {
        val audioDir = File(context.cacheDir, "meshgram_voices").apply { mkdirs() }
        val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
        currentOutputFile = file

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        _recordingAmplitudes.value = emptyList()
        recordingJob = scope.launch(Dispatchers.IO) {
            val samples = mutableListOf<Int>()
            while (isActive && mediaRecorder != null) {
                val maxAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) { 0 }
                // Normalize 0..32767 to 10..100
                val normalized = (maxAmp / 350).coerceIn(10, 100)
                samples.add(normalized)
                if (samples.size > 50) samples.removeAt(0)
                _recordingAmplitudes.value = samples.toList()
                delay(80)
            }
        }

        return file
    }

    fun stopRecording(): Pair<File?, List<Int>> {
        recordingJob?.cancel()
        recordingJob = null

        val finalSamples = _recordingAmplitudes.value
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {}
        mediaRecorder = null

        return Pair(currentOutputFile, finalSamples)
    }

    fun playAudio(filePath: String, messageId: String) {
        if (_playbackState.value.isPlaying && _playbackState.value.activeMessageId == messageId) {
            pauseAudio()
            return
        }

        stopAudio()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            val speed = _playbackState.value.speed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                playbackParams = PlaybackParams().setSpeed(speed)
            }
            start()

            setOnCompletionListener {
                _playbackState.value = _playbackState.value.copy(isPlaying = false, currentPositionMs = 0)
                playbackProgressJob?.cancel()
            }
        }

        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            activeMessageId = messageId,
            totalDurationMs = mediaPlayer?.duration ?: 0
        )

        playbackProgressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = mediaPlayer?.currentPosition ?: 0
                )
                delay(100)
            }
        }
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        playbackProgressJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun cyclePlaybackSpeed() {
        val nextSpeed = when (_playbackState.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackState.value = _playbackState.value.copy(speed = nextSpeed)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
            try {
                mediaPlayer?.playbackParams = PlaybackParams().setSpeed(nextSpeed)
            } catch (e: Exception) {}
        }
    }

    fun stopAudio() {
        playbackProgressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        _playbackState.value = _playbackState.value.copy(isPlaying = false, activeMessageId = null)
    }
}
