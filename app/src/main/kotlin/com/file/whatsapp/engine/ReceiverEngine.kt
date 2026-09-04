package com.file.whatsapp.engine

import com.file.whatsapp.model.TransferStats
import com.file.whatsapp.model.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.*
import java.net.ServerSocket

object ReceiverEngine {

    private const val BUFFER_SIZE = 128 * 1024
    private const val PORT = 8998

    @Volatile var isPaused = false
    @Volatile var isCancelled = false

    suspend fun receiveDirectory(
        targetDirectory: File,
        onProgress: (TransferStats) -> Unit
    ) = withContext(Dispatchers.IO) {
        isPaused = false
        isCancelled = false

        if (!targetDirectory.exists()) targetDirectory.mkdirs()

        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(PORT).apply { reuseAddress = true }

            while (!isCancelled) {
                try {
                    onProgress(TransferStats(state = TransferState.RECONNECTING, currentFileName = "في انتظار الاتصال..."))
                    val clientSocket = serverSocket.accept().apply {
                        tcpNoDelay = true
                        soTimeout = 15_000
                    }

                    val inStream = DataInputStream(BufferedInputStream(clientSocket.getInputStream(), BUFFER_SIZE))
                    val out = DataOutputStream(BufferedOutputStream(clientSocket.getOutputStream(), BUFFER_SIZE))

                    val totalFiles = inStream.readInt()
                    val totalBytes = inStream.readLong()

                    val buffer = ByteArray(BUFFER_SIZE)

                    while (!isCancelled) {
                        while (isPaused && !isCancelled) {
                            onProgress(TransferStats(state = TransferState.PAUSED))
                            delay(500)
                        }
                        if (isCancelled) break

                        val relativePath = inStream.readUTF()
                        val fileSize = inStream.readLong()

                        val finalFile = File(targetDirectory, relativePath)
                        finalFile.parentFile?.mkdirs()

                        // ملف مؤقت لحماية البيانات الأصلية
                        val partFile = File(targetDirectory, "$relativePath.part")

                        // التحقق هل الملف موجود مسبقاً ومكتمل
                        if (finalFile.exists() && finalFile.length() == fileSize) {
                            out.writeLong(fileSize) // إشعار المرسل بتخطي الملف
                            out.flush()
                            inStream.readByte() // مزامنة الـ ACK
                            continue
                        }

                        // التحقق من الحجم المحمل سابقاً للاستئناف
                        val existingBytes = if (partFile.exists()) partFile.length() else 0L
                        out.writeLong(existingBytes)
                        out.flush()

                        // استئناف الكتابة بوضع الإلحاق (Append Mode = true)
                        FileOutputStream(partFile, true).buffered(BUFFER_SIZE).use { fos ->
                            var remaining = fileSize - existingBytes
                            var lastTime = System.currentTimeMillis()
                            var bytesBatch = 0L

                            while (remaining > 0 && !isCancelled && !isPaused) {
                                val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                                val read = inStream.read(buffer, 0, toRead)
                                if (read == -1) break

                                fos.write(buffer, 0, read)
                                remaining -= read
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
                                            currentFileName = finalFile.name,
                                            speedBytesPerSec = speed
                                        )
                                    )
                                }
                            }
                            fos.flush()
                        }

                        if (isPaused || isCancelled) break

                        // بمجرد اكتمال تنزيل الملف بنجاح، نحوله لاسمه الحقيقي مع الاستبدال
                        if (partFile.length() == fileSize) {
                            if (finalFile.exists()) finalFile.delete()
                            partFile.renameTo(finalFile)
                            out.writeByte(1) // ACK نجاح للمرسل
                            out.flush()
                        }
                    }
                    clientSocket.close()
                } catch (e: Exception) {
                    if (isCancelled) break
                    delay(2000)
                }
            }
        } finally {
            serverSocket?.close()
        }
    }
}
