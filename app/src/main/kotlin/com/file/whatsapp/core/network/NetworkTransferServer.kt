package com.file.whatsapp.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

object NetworkTransferManager {

    private const val PORT = 8888

    // دور المُستقبل (Receiver Server) عند اختيار Wi-Fi Hotspot
    suspend fun startReceiverServer(outputDir: File, onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket(PORT)
        val clientSocket = serverSocket.accept()
        
        val input = BufferedInputStream(clientSocket.getInputStream())
        val buffer = ByteArray(65536)

        // استقبال الملفات وحفظها
        // (يمكن توسيع البروتوكول لإرسال اسم الملف ثم حجمه ثم محتواه بايت بايت)
        
        clientSocket.close()
        serverSocket.close()
    }

    // دور المُرسل (Sender Client) عند اختيار Wi-Fi Hotspot
    suspend fun sendFilesOverSocket(serverIp: String, files: List<File>, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val socket = Socket(serverIp, PORT)
        val output = BufferedOutputStream(socket.getOutputStream())
        val buffer = ByteArray(65536)

        var totalBytesSent = 0L
        val totalBytesAll = files.sumOf { it.length() }

        for (file in files) {
            BufferedInputStream(file.inputStream()).use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead
                    onProgress(totalBytesSent, totalBytesAll)
                }
            }
        }
        output.flush()
        socket.close()
    }
}
