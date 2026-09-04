package com.file.whatsapp.engine

import com.file.whatsapp.model.TransferStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket

object SenderEngine {

    private const val BUFFER_SIZE = 128 * 1024 // 128KB لنقل فائق السرعة عبر Wi-Fi Direct
    private const val PORT = 8998

    suspend fun sendDirectory(
        sourceDir: File,
        targetIp: String,
        onProgress: (TransferStats) -> Unit
    ) = withContext(Dispatchers.IO) {

        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw IllegalArgumentException("مجلد المصدر غير موجود أو تالف: \${sourceDir.absolutePath}")
        }

        val allFiles = sourceDir.walkTopDown().filter { it.isFile }.toList()
        val totalBytes = allFiles.sumOf { it.length() }
        var transferredBytes = 0L
        var transferredFilesCount = 0

        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.sendBufferSize = BUFFER_SIZE * 2
            socket.connect(InetSocketAddress(targetIp, PORT), 30_000)

            val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))

            // إرسال معلومات الرأس (Total Files & Bytes)
            outStream.writeInt(allFiles.size)
            outStream.writeLong(totalBytes)
            outStream.flush()

            val buffer = ByteArray(BUFFER_SIZE)
            var lastCalculationTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            var currentSpeed = 0L

            for (file in allFiles) {
                val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                outStream.writeUTF(relativePath)
                outStream.writeLong(file.length())

                FileInputStream(file).buffered(BUFFER_SIZE).use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        transferredBytes += bytesRead
                        bytesSinceLastCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val diff = now - lastCalculationTime
                        if (diff >= 500) {
                            currentSpeed = (bytesSinceLastCalc * 1000L) / diff
                            lastCalculationTime = now
                            bytesSinceLastCalc = 0L

                            onProgress(
                                TransferStats(
                                    currentFileName = file.name,
                                    filesTransferred = transferredFilesCount,
                                    totalFiles = allFiles.size,
                                    bytesTransferred = transferredBytes,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = currentSpeed
                                )
                            )
                        }
                    }
                }
                outStream.flush()
                transferredFilesCount++
            }

            onProgress(
                TransferStats(
                    currentFileName = "اكتمل",
                    filesTransferred = allFiles.size,
                    totalFiles = allFiles.size,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0L
                )
            )
        }
    }
}
