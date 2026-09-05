package com.file.whatsapp.engine

import com.file.whatsapp.model.TransferStats
import com.file.whatsapp.model.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket

object SenderEngine {

    private const val BUFFER_SIZE = 128 * 1024
    private const val PORT = 8998
    private const val HANDSHAKE_MAGIC = "WA_TRANSFER_SYNC_OK"

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
        var totalBytesTransferred = 0L

        while (currentFileIndex < allFiles.size && !isCancelled) {
            var socket: Socket? = null
            try {
                val ip = targetIpProvider().trim()
                onProgress(
                    TransferStats(
                        state = TransferState.CONNECTING,
                        currentFileName = "جاري التحقق والاتصال بـ: $ip...",
                        totalFiles = allFiles.size,
                        totalBytes = totalBytes
                    )
                )

                socket = Socket()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 20_000
                socket.connect(InetSocketAddress(ip, PORT), 7_000)

                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))
                val inStream = DataInputStream(BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))

                // مصافحة التأكيد
                out.writeUTF(HANDSHAKE_MAGIC)
                out.flush()

                val response = inStream.readUTF()
                if (response != HANDSHAKE_MAGIC) {
                    throw IOException("فشلت مصافحة الاتصال: استجابة غير مطابقة")
                }

                onProgress(
                    TransferStats(
                        state = TransferState.CONNECTED,
                        currentFileName = "تم الاتصال! بدء إرسال البيانات...",
                        totalFiles = allFiles.size,
                        totalBytes = totalBytes
                    )
                )
                delay(400)

                // إرسال الرأس
                out.writeInt(allFiles.size)
                out.writeLong(totalBytes)
                out.flush()

                val buffer = ByteArray(BUFFER_SIZE)

                while (currentFileIndex < allFiles.size && !isCancelled) {
                    while (isPaused && !isCancelled) {
                        onProgress(
                            TransferStats(
                                state = TransferState.PAUSED,
                                filesTransferred = currentFileIndex,
                                totalFiles = allFiles.size,
                                bytesTransferred = totalBytesTransferred,
                                totalBytes = totalBytes
                            )
                        )
                        delay(500)
                    }
                    if (isCancelled) break

                    val file = allFiles[currentFileIndex]
                    val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                    val fileLength = file.length()

                    out.writeUTF(relativePath)
                    out.writeLong(fileLength)
                    out.flush()

                    val offset = inStream.readLong()

                    // إذا كان الملف مكتملاً مسبقاً لدى المستلم
                    if (offset >= fileLength) {
                        totalBytesTransferred += fileLength
                        currentFileIndex++
                        inStream.readByte() // تأكيد المستلم
                        continue
                    }

                    totalBytesTransferred += offset

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
                            totalBytesTransferred += read

                            val now = System.currentTimeMillis()
                            val diff = now - lastTime
                            if (diff >= 300) {
                                val speed = (bytesBatch * 1000L) / diff
                                lastTime = now
                                bytesBatch = 0L
                                onProgress(
                                    TransferStats(
                                        state = TransferState.RUNNING,
                                        currentFileName = file.name,
                                        filesTransferred = currentFileIndex,
                                        totalFiles = allFiles.size,
                                        bytesTransferred = totalBytesTransferred,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed
                                    )
                                )
                            }
                        }
                        out.flush()
                    }

                    if (isPaused || isCancelled) continue

                    val status = inStream.readByte()
                    if (status == 1.toByte()) {
                        currentFileIndex++
                    }
                }
            } catch (e: Exception) {
                if (isCancelled) break
                onProgress(
                    TransferStats(
                        state = TransferState.RECONNECTING,
                        currentFileName = "انقطع الاتصال، جاري المحاولة...",
                        filesTransferred = currentFileIndex,
                        totalFiles = allFiles.size,
                        bytesTransferred = totalBytesTransferred,
                        totalBytes = totalBytes,
                        errorMessage = e.localizedMessage
                    )
                )
                delay(2000)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        if (isCancelled) {
            onProgress(TransferStats(state = TransferState.IDLE, errorMessage = "تم إلغاء النقل"))
        } else {
            onProgress(
                TransferStats(
                    state = TransferState.COMPLETED,
                    currentFileName = "اكتمل النقل بنجاح!",
                    totalFiles = allFiles.size,
                    filesTransferred = allFiles.size,
                    totalBytes = totalBytes,
                    bytesTransferred = totalBytes
                )
            )
        }
    }
}
