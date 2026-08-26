package com.file.whatsapp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class TransferProgress(
    val currentFileName: String,
    val copiedBytes: Long,
    val totalBytes: Long,
    val percentage: Int,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

class TransferEngine {

    fun transferFolder(source: File, destination: File): Flow<TransferProgress> = flow {
        if (!source.exists()) {
            emit(TransferProgress("", 0, 0, 0, true, "Source directory does not exist."))
            return@flow
        }

        val allFiles = source.walkTopDown().filter { it.isFile }.toList()
        val totalBytes = allFiles.sumOf { it.length() }
        var copiedBytesTotal = 0L

        if (!destination.exists()) {
            destination.mkdirs()
        }

        val buffer = ByteArray(65536) // 64KB Buffer

        for (file in allFiles) {
            val relativePath = file.toRelativeString(source)
            val destFile = File(destination, relativePath)

            try {
                destFile.parentFile?.mkdirs()
                
                BufferedInputStream(FileInputStream(file)).use { input ->
                    BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            copiedBytesTotal += bytesRead

                            val percent = if (totalBytes > 0) {
                                ((copiedBytesTotal * 100) / totalBytes).toInt()
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
                    }
                }
            } catch (e: Exception) {
                emit(
                    TransferProgress(
                        currentFileName = file.name,
                        copiedBytes = copiedBytesTotal,
                        totalBytes = totalBytes,
                        percentage = if (totalBytes > 0) ((copiedBytesTotal * 100) / totalBytes).toInt() else 0,
                        errorMessage = e.localizedMessage
                    )
                )
            }
        }

        emit(TransferProgress("", copiedBytesTotal, totalBytes, 100, true))
    }.flowOn(Dispatchers.IO)
}
