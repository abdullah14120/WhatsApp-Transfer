package com.file.whatsapp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket

class NetworkTransferEngine {

    companion object {
        const val PORT = 8888
        private const val BUFFER_SIZE = 1048576 // 1MB Buffer لأقصى سرعة نقل فيزيائية ممكنة
    }

    suspend fun startServerAndReceive(outputDir: File, onProgress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        ServerSocket(PORT).use { serverSocket ->
            serverSocket.accept().use { clientSocket ->
                DataInputStream(BufferedInputStream(clientSocket.getInputStream(), BUFFER_SIZE)).use { dis ->
                    val filesCount = dis.readInt()
                    val totalSessionBytes = dis.readLong()
                    var receivedBytesTotal = dis.readLong()

                    for (i in 0 until filesCount) {
                        val relativePath = dis.readUTF()
                        val fileLength = dis.readLong()

                        val targetFile = File(outputDir, relativePath)
                        targetFile.parentFile?.mkdirs()

                        // دعم الاستئناف الفوري في حال وجود جزء سابق لنفس الملف
                        val existingLen = if (targetFile.exists()) targetFile.length() else 0L
                        
                        RandomAccessFile(targetFile, "rw").use { raf ->
                            raf.seek(existingLen)
                            clientSocket.getOutputStream().write(byteArrayOf(if (existingLen > 0) 1 else 0))
                            clientSocket.getOutputStream().flush()

                            BufferedOutputStream(FileOutputStream(targetFile, true), BUFFER_SIZE).use { bos ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var remaining = fileLength - existingLen
                                while (remaining > 0) {
                                    val read = dis.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    if (read == -1) break
                                    bos.write(buffer, 0, read)
                                    remaining -= read
                                    receivedBytesTotal += read
                                    
                                    val percent = if (totalSessionBytes > 0) ((receivedBytesTotal * 100) / totalSessionBytes).toInt() else 100
                                    onProgress(relativePath, percent)
                                }
                                bos.flush()
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun connectAndSend(serverIp: String, sourceDir: File, onProgress: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        Socket(serverIp, PORT).use { socket ->
            val allFiles = sourceDir.walkTopDown().filter { it.isFile }.toList()
            val totalBytesAll = allFiles.sumOf { it.length() }
            var sentBytesTotal = 0L

            DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE)).use { dos ->
                dos.writeInt(allFiles.size)
                dos.writeLong(totalBytesAll)
                dos.writeLong(sentBytesTotal)
                dos.flush()

                for (file in allFiles) {
                    val relativePath = file.toRelativeString(sourceDir)
                    dos.writeUTF(relativePath)
                    dos.writeLong(file.length())
                    dos.flush()

                    // قراءة إشارة الاستئناف من الخادم
                    val resumeSignal = socket.getInputStream().read()
                    val startOffset = if (resumeSignal == 1 && file.exists()) file.length() else 0L
                    sentBytesTotal += startOffset

                    RandomAccessFile(file, "r").use { raf ->
                        raf.seek(startOffset)
                        BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE).use { bos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            val channel = raf.channel
                            // استخدام الذاكرة المباشرة والتحويل السريع
                            val byteBuffer = java.nio.ByteBuffer.allocateDirect(BUFFER_SIZE)
                            
                            while (true) {
                                byteBuffer.clear()
                                val read = channel.read(byteBuffer)
                                if (read == -1) break
                                byteBuffer.flip()
                                val array = ByteArray(read)
                                byteBuffer.get(array)
                                
                                bos.write(array, 0, read)
                                sentBytesTotal += read

                                val percent = if (totalBytesAll > 0) ((sentBytesTotal * 100) / totalBytesAll).toInt() else 100
                                onProgress(file.name, percent)
                            }
                            bos.flush()
                        }
                    }
                }
            }
        }
    }
}
