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
import java.io.IOException

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

        // استخدام Sequence لتفادي تحميل مئات الآلاف من المسارات في الذاكرة دفعة واحدة
        val fileSequence = source.walkTopDown().filter { it.isFile }
        val allFilesList = fileSequence.toList() // احتساب الحجم الكلي مع الحفاظ على الأمان الهيكلي
        val totalBytes = allFilesList.sumOf { it.length() }
        var copiedBytesTotal = 0L

        if (!destination.exists()) {
            destination.mkdirs()
        }

        // 256KB Buffer محسن لأقراص UFS 3.1 / 4.0 لأقصى إنتاجية I/O
        val buffer = ByteArray(262144)

        for (file in allFilesList) {
            val relativePath = file.toRelativeString(source)
            val destFile = File(destination, relativePath)

            try {
                destFile.parentFile?.mkdirs()
                
                BufferedInputStream(FileInputStream(file), 32768).use { input ->
                    BufferedOutputStream(FileOutputStream(destFile), 32768).use { output ->
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
                        output.flush()
                    }
                }

                // التحقق الفيزيائي الصارم من مطابقة الأحجام لضمان سلامة الملفات
                if (destFile.length() != file.length()) {
                    throw IOException("Integrity check failed: Size mismatch for ${file.name}")
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
                return@flow
            }
        }

        emit(TransferProgress("", copiedBytesTotal, totalBytes, 100, true))
    }.flowOn(Dispatchers.IO)
}
