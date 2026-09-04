package com.file.whatsapp.engine

import com.file.whatsapp.model.TransferStats
import com.file.whatsapp.model.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

object SenderEngine {

    private const val BUFFER_SIZE = 128 * 1024
    private const val PORT = 8998

    @Volatile var isPaused = false
    @Volatile var isCancelled = false

    suspend fun sendDirectory(
        sourceDir: File,
        targetIpProvider: () -> String,
        onProgress: (TransferStats) -> Unit
    ) = withContext(Dispatchers.IO) {
        isPaused = false
        isCancelled = false

        val allFiles = sourceDir.walkTopDown().filter { it.isFile }.toList()
        val totalBytes = allFiles.sumOf { it.length() }
        var currentFileIndex = 0

        while (currentFileIndex < allFiles.size && !isCancelled) {
            try {
                val ip = targetIpProvider()
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.soTimeout = 15_000
                    socket.connect(InetSocketAddress(ip, PORT), 10_000)

                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))
                    val inStream = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))

                    // إرسال إجمالي الملفات والحجم الكلي
                    out.writeInt(allFiles.size)
                    out.writeLong(totalBytes)
                    out.flush()

                    val buffer = ByteArray(BUFFER_SIZE)

                    while (currentFileIndex < allFiles.size && !isCancelled) {
                        // التعامل مع الإيقاف المؤقت
                        while (isPaused && !isCancelled) {
                            onProgress(TransferStats(state = TransferState.PAUSED))
                            delay(500)
                        }
                        if (isCancelled) break

                        val file = allFiles[currentFileIndex]
                        val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                        val fileLength = file.length()

                        out.writeUTF(relativePath)
                        out.writeLong(fileLength)
                        out.flush()

                        // قراءة الإزاحة (Offset) التي يطلبها المستلم للاستئناف
                        val offset = inStream.readLong()

                        if (offset < fileLength) {
                            RandomAccessFile(file, "r").use { raf ->
                                raf.seek(offset)
                                var fileRemaining = fileLength - offset
                                var lastTime = System.currentTimeMillis()
                                var bytesBatch = 0L

                                while (fileRemaining > 0 && !isCancelled && !isPaused) {
                                    val toRead = Math.min(buffer.size.toLong(), fileRemaining).toInt()
                                    val read = raf.read(buffer, 0, toRead)
                                    if (read == -1) break

                                    out.write(buffer, 0, read)
                                    fileRemaining -= read
                                    bytesBatch += read

                                    val now = System.currentTimeMillis()
                                    val diff = now - lastTime
                                    if (diff >= 500) {
                                        val speed = (bytesBatch * 1000L) / diff
                                        lastTime = now
                                        bytesBatch = 0L
                                        onProgress(
                                            TransferStats(
                                                state = TransferState.RUNNING,
                                                currentFileName = file.name,
                                                filesTransferred = currentFileIndex,
                                                totalFiles = allFiles.size,
                                                speedBytesPerSec = speed
                                            )
                                        )
                                    }
                                }
                                out.flush()
                            }
                        }

                        // إذا خرج بسبب الإيقاف المؤقت، لا ننتقل للملف التالي
                        if (isPaused || isCancelled) continue

                        // تأكيد إتمام الملف من المستلم
                        val status = inStream.readByte()
                        if (status == 1.toByte()) {
                            currentFileIndex++
                        }
                    }
                }
            } catch (e: Exception) {
                if (isCancelled) break
                // وضع إعادة الاتصال التلقائي عند انقطاع الشبكة
                onProgress(
                    TransferStats(
                        state = TransferState.RECONNECTING,
                        errorMessage = "انقطع الاتصال، جاري المحاولة والمزامنة..."
                    )
                )
                delay(3000)
            }
        }

        if (isCancelled) {
            onProgress(TransferStats(state = TransferState.IDLE, errorMessage = "تم إلغاء النقل"))
        } else {
            onProgress(TransferStats(state = TransferState.COMPLETED, totalFiles = allFiles.size, filesTransferred = allFiles.size))
        }
    }
}
