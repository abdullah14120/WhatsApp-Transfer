package com.file.whatsapp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

data class TransferProgress(
    val currentFileName: String,
    val copiedBytes: Long,
    val totalBytes: Long,
    val percentage: Int,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

class TransferEngine {

    companion object {
        private const val BUFFER_SIZE = 1048576 // 1MB Direct Buffer لأقصى سرعة نقل
    }

    fun transferFolder(source: File, destination: File): Flow<TransferProgress> = flow {
        if (!source.exists()) {
            emit(TransferProgress("", 0, 0, 0, true, "Source directory does not exist."))
            return@flow
        }

        val allFilesList = source.walkTopDown().filter { it.isFile }.toList()
        val totalBytes = allFilesList.sumOf { it.length() }
        var copiedBytesTotal = 0L

        if (!destination.exists()) {
            destination.mkdirs()
        }

        for (file in allFilesList) {
            val relativePath = file.toRelativeString(source)
            val destFile = File(destination, relativePath)

            try {
                destFile.parentFile?.mkdirs()

                FileInputStream(file).use { fis ->
                    FileOutputStream(destFile).use { fos ->
                        val inputChannel = fis.channel
                        val outputChannel = fos.channel
                        val byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE)

                        while (true) {
                            byteBuffer.clear()
                            val read = inputChannel.read(byteBuffer)
                            if (read == -1) break
                            byteBuffer.flip()
                            
                            outputChannel.write(byteBuffer)
                            copiedBytesTotal += read

                            val percent = if (totalBytes > 0) {
                                ((copiedBytesTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else 100

                            emit(
                                TransferProgress(
                                    currentFileName = file.name,
                                    copiedBytes = copiedBytesTotal,
                                    totalBytes = totalBytes,
                                    percentage = percent
                                )
                            )
                        }
                        outputChannel.force(true)
                    }
                }

                if (destFile.length() != file.length()) {
                    throw IOException("Integrity check failed: Size mismatch for ${file.name}")
                }

            } catch (e: Exception) {
                emit(
                    TransferProgress(
                        currentFileName = file.name,
                        copiedBytes = copiedBytesTotal,
                        totalBytes = totalBytes,
                        percentage = if (totalBytes > 0) ((copiedBytesTotal * 100) / totalBytes).toInt().coerceIn(0, 100) else 0,
                        errorMessage = e.localizedMessage
                    )
                )
                return@flow
            }
        }

        emit(TransferProgress("", copiedBytesTotal, totalBytes, 100, true))
    }.flowOn(Dispatchers.IO)
}
