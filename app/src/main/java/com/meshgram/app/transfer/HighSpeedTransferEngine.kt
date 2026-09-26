package com.meshgram.app.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

data class TransferProgress(
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedMBs: Float = 0f,
    val progressPercent: Int = 0,
    val isCompleted: Boolean = false,
    val isReceiving: Boolean = false,
    val error: String? = null
)

/**
 * موتور انتقال پرسرعت مستقیم فایل با سوکت‌های بهینه‌شده TCP و بافر ۲۵۶ کیلوبایت
 * مناسب انتقال فیلم‌های حجیم و فایل‌های APK با سرعت تا ۵۰ مگابایت بر ثانیه
 */
class HighSpeedTransferEngine(private val context: Context) {

    private val _transferState = MutableStateFlow(TransferProgress())
    val transferState: StateFlow<TransferProgress> = _transferState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    companion object {
        const val DEFAULT_PORT = 8988
        const val BUFFER_SIZE = 256 * 1024 // 256 KB Buffer
    }

    /**
     * تولید کد QR برای اتصال بدون اصطکاک (Zero-Friction QR Pairing)
     */
    fun generatePairingQRCode(ip: String, port: Int = DEFAULT_PORT, deviceName: String): Bitmap? {
        val payload = "meshgram://pair?ip=$ip&port=$port&device=$deviceName"
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * اجرای سرور دریافت‌کننده فایل
     */
    suspend fun startServer(port: Int = DEFAULT_PORT, onFileReceived: (File) -> Unit) = withContext(Dispatchers.IO) {
        try {
            stopServer()
            serverSocket = ServerSocket(port)
            isRunning = true

            while (isRunning) {
                val clientSocket = serverSocket?.accept() ?: break
                handleIncomingConnection(clientSocket, onFileReceived)
            }
        } catch (e: Exception) {
            if (isRunning) {
                _transferState.value = _transferState.value.copy(error = e.localizedMessage)
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket, onFileReceived: (File) -> Unit) {
        Thread {
            try {
                socket.use { s ->
                    val dis = DataInputStream(BufferedInputStream(s.getInputStream(), BUFFER_SIZE))
                    val fileName = dis.readUTF()
                    val fileSize = dis.readLong()

                    val downloadsDir = File(context.getExternalFilesDir(null), "MeshGram_Received").apply { mkdirs() }
                    val targetFile = File(downloadsDir, fileName)
                    val fos = FileOutputStream(targetFile)
                    val bos = BufferedOutputStream(fos, BUFFER_SIZE)

                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalRead = 0L
                    var lastTime = System.currentTimeMillis()
                    var bytesInLastSec = 0L

                    _transferState.value = TransferProgress(
                        fileName = fileName,
                        totalBytes = fileSize,
                        transferredBytes = 0,
                        speedMBs = 0f,
                        progressPercent = 0,
                        isReceiving = true
                    )

                    bos.use { out ->
                        var read = 0
                        while (totalRead < fileSize && dis.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalRead).toInt()).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalRead += read
                            bytesInLastSec += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastTime
                            if (elapsed >= 500) {
                                val speed = (bytesInLastSec.toFloat() / (1024f * 1024f)) / (elapsed.toFloat() / 1000f)
                                val percent = if (fileSize > 0) ((totalRead * 100) / fileSize).toInt() else 0
                                _transferState.value = _transferState.value.copy(
                                    transferredBytes = totalRead,
                                    speedMBs = speed,
                                    progressPercent = percent
                                )
                                lastTime = now
                                bytesInLastSec = 0L
                            }
                        }
                        out.flush()
                    }

                    _transferState.value = _transferState.value.copy(
                        transferredBytes = fileSize,
                        progressPercent = 100,
                        isCompleted = true,
                        speedMBs = 0f
                    )

                    onFileReceived(targetFile)
                }
            } catch (e: Exception) {
                _transferState.value = _transferState.value.copy(error = e.localizedMessage)
            }
        }.start()
    }

    /**
     * ارسال یک فایل به مقصد با سرعت بالا
     */
    suspend fun sendFile(targetIp: String, port: Int = DEFAULT_PORT, file: File) = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(targetIp, port), 5000)
            socket.use { s ->
                val dos = DataOutputStream(BufferedOutputStream(s.getOutputStream(), BUFFER_SIZE))
                val fis = FileInputStream(file)
                val bis = BufferedInputStream(fis, BUFFER_SIZE)

                val fileSize = file.length()
                dos.writeUTF(file.name)
                dos.writeLong(fileSize)
                dos.flush()

                val buffer = ByteArray(BUFFER_SIZE)
                var totalSent = 0L
                var lastTime = System.currentTimeMillis()
                var bytesInLastSec = 0L

                _transferState.value = TransferProgress(
                    fileName = file.name,
                    totalBytes = fileSize,
                    transferredBytes = 0,
                    speedMBs = 0f,
                    progressPercent = 0,
                    isReceiving = false
                )

                bis.use { input ->
                    var bytesRead = 0
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                        totalSent += bytesRead
                        bytesInLastSec += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 500) {
                            val speed = (bytesInLastSec.toFloat() / (1024f * 1024f)) / (elapsed.toFloat() / 1000f)
                            val percent = if (fileSize > 0) ((totalSent * 100) / fileSize).toInt() else 0
                            _transferState.value = _transferState.value.copy(
                                transferredBytes = totalSent,
                                speedMBs = speed,
                                progressPercent = percent
                            )
                            lastTime = now
                            bytesInLastSec = 0L
                        }
                    }
                    dos.flush()
                }

                _transferState.value = _transferState.value.copy(
                    transferredBytes = fileSize,
                    progressPercent = 100,
                    isCompleted = true,
                    speedMBs = 0f
                )
            }
        } catch (e: Exception) {
            _transferState.value = _transferState.value.copy(error = e.localizedMessage)
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }

    fun resetState() {
        _transferState.value = TransferProgress()
    }
}
