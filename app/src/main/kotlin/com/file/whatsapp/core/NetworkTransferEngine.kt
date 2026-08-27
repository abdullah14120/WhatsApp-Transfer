package com.file.whatsapp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

class NetworkTransferEngine {

    companion object {
        const val PORT = 8888
    }

    suspend fun startServerAndReceive(outputDir: File, onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        ServerSocket(PORT).use { serverSocket ->
            serverSocket.accept().use { clientSocket ->
                DataInputStream(BufferedInputStream(clientSocket.getInputStream())).use { dis ->
                    val filesCount = dis.readInt()
                    for (i in 0 until filesCount) {
                        val relativePath = dis.readUTF()
                        val fileLength = dis.readLong()

                        val targetFile = File(outputDir, relativePath)
                        targetFile.parentFile?.mkdirs()

                        BufferedOutputStream(targetFile.outputStream()).use { fos ->
                            val buffer = ByteArray(65536)
                            var remaining = fileLength
                            while (remaining > 0) {
                                val read = dis.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (read == -1) break
                                fos.write(buffer, 0, read)
                                remaining -= read
                            }
                            fos.flush()
                        }
                        onProgress("Received: $relativePath")
                    }
                }
            }
        }
    }

    suspend fun connectAndSend(serverIp: String, sourceDir: File, onProgress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        Socket(serverIp, PORT).use { socket ->
            val allFiles = sourceDir.walkTopDown().filter { it.isFile }.toList()
            
            DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { dos ->
                dos.writeInt(allFiles.size)
                val totalBytesAll = allFiles.sumOf { it.length() }
                var sentBytesTotal = 0L

                for (file in allFiles) {
                    val relativePath = file.toRelativeString(sourceDir)
                    dos.writeUTF(relativePath)
                    dos.writeLong(file.length())

                    BufferedInputStream(file.inputStream()).use { fis ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            dos.write(buffer, 0, bytesRead)
                            sentBytesTotal += bytesRead
                            val percent = if (totalBytesAll > 0) ((sentBytesTotal * 100) / totalBytesAll).toInt() else 100
                            onProgress(file.name, percent)
                        }
                    }
                }
                dos.flush()
            }
        }
    }
}
