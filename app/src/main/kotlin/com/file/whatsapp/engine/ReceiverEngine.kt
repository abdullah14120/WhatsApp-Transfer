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
    private const val HANDSHAKE_MAGIC = "WA_TRANSFER_SYNC_OK"

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
                    onProgress(
                        TransferStats(
                            state = TransferState.CONNECTING,
                            currentFileName = "المنفذ مفتوح: في انتظار إشارة الجهاز المرسل..."
                        )
                    )

                    val clientSocket = serverSocket.accept().apply {
                        tcpNoDelay = true
                        soTimeout = 25_000
                    }

                    val inStream = DataInputStream(BufferedInputStream(clientSocket.getInputStream(), BUFFER_SIZE))
                    val out = DataOutputStream(BufferedOutputStream(clientSocket.getOutputStream(), BUFFER_SIZE))

                    // التحقق من المصافحة
                    val handshake = inStream.readUTF()
                    if (handshake != HANDSHAKE_MAGIC) {
                        clientSocket.close()
                        continue
                    }

                    // رد التأكيد للمرسل
                    out.writeUTF(HANDSHAKE_MAGIC)
                    out.flush()

                    onProgress(
                        TransferStats(
                            state = TransferState.CONNECTED,
                            currentFileName = "تم الاتصال بالمرسل بنجاح! جاري البدء..."
                        )
                    )
                    delay(500)

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

                        val partFile = File(targetDirectory, "$relativePath.part")

                        // فحص الملفات المكتملة مسبقاً
                        if (finalFile.exists() && finalFile.length() == fileSize) {
                            out.writeLong(fileSize)
                            out.flush()
                            inStream.readByte()
                            continue
                        }

                        val existingBytes = if (partFile.exists()) partFile.length() else 0L
                        out.writeLong(existingBytes)
                        out.flush()

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

                        if (partFile.length() == fileSize) {
                            if (finalFile.exists()) finalFile.delete()
                            partFile.renameTo(finalFile)
                            out.writeByte(1)
                            out.flush()
                        }
                    }
                    clientSocket.close()
                } catch (e: Exception) {
                    if (isCancelled) break
                    delay(1500)
                }
            }
        } finally {
            serverSocket?.close()
        }
    }
}
