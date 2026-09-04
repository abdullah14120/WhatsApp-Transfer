package com.file.whatsapp.engine

import com.file.whatsapp.model.TransferStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket

object ReceiverEngine {

    private const val BUFFER_SIZE = 128 * 1024
    private const val PORT = 8998

    suspend fun receiveDirectory(
        targetDirectory: File,
        onProgress: (TransferStats) -> Unit
    ) = withContext(Dispatchers.IO) {

        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }

        ServerSocket(PORT).use { serverSocket ->
            serverSocket.reuseAddress = true
            val clientSocket = serverSocket.accept()
            clientSocket.tcpNoDelay = true
            clientSocket.keepAlive = true
            clientSocket.receiveBufferSize = BUFFER_SIZE * 2

            val inStream = DataInputStream(BufferedInputStream(clientSocket.getInputStream(), BUFFER_SIZE))

            val totalFiles = inStream.readInt()
            val totalBytes = inStream.readLong()

            var bytesTransferred = 0L
            var filesTransferred = 0
            val buffer = ByteArray(BUFFER_SIZE)

            var lastCalcTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            var currentSpeed = 0L

            for (i in 0 until totalFiles) {
                val relativePath = inStream.readUTF()
                val fileSize = inStream.readLong()

                val destinationFile = File(targetDirectory, relativePath)
                destinationFile.parentFile?.mkdirs()

                // استبدال تلقائي ومباشر (Overwrite)
                FileOutputStream(destinationFile, false).buffered(BUFFER_SIZE).use { fos ->
                    var remaining = fileSize
                    while (remaining > 0L) {
                        val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                        val bytesRead = inStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        fos.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                        bytesTransferred += bytesRead
                        bytesSinceLastCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val diff = now - lastCalcTime
                        if (diff >= 500) {
                            currentSpeed = (bytesSinceLastCalc * 1000L) / diff
                            lastCalcTime = now
                            bytesSinceLastCalc = 0L

                            onProgress(
                                TransferStats(
                                    currentFileName = destinationFile.name,
                                    filesTransferred = filesTransferred,
                                    totalFiles = totalFiles,
                                    bytesTransferred = bytesTransferred,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = currentSpeed
                                )
                            )
                        }
                    }
                    fos.flush()
                }
                filesTransferred++
            }

            onProgress(
                TransferStats(
                    currentFileName = "تمت العملية بنجاح",
                    filesTransferred = totalFiles,
                    totalFiles = totalFiles,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0L
                )
            )
        }
    }
}
